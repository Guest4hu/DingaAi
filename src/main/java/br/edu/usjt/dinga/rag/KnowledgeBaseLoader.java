package br.edu.usjt.dinga.rag;

import br.edu.usjt.dinga.model.KnowledgeChunk;
import br.edu.usjt.dinga.model.KnowledgeDocumentSummary;
import br.edu.usjt.dinga.model.NewKnowledgeDocumentRequest;
import br.edu.usjt.dinga.storage.JsonDataStore;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Carrega e segmenta a base curada de conhecimento em Markdown (compatível com execução via JAR)
 * e permite cadastro/remoção dinâmica de novos tópicos pelo Painel do Professor com persistência em JSON (data/).
 */
@Component
public class KnowledgeBaseLoader {

    public static final String STORAGE_FILE = "custom-knowledge.json";
    public static final int TARGET_CHUNK_CHARS = 900;
    public static final int MAX_CHUNK_CHARS = 1050;
    public static final int OVERLAP_CHARS = 120;
    private static final Pattern CUSTOM_SEQ_PATTERN = Pattern.compile("^(\\d+)-docente-");
    private static final Pattern PARAGRAPH_SPLIT_PATTERN = Pattern.compile("\\n\\s*\\n+");
    private static final Pattern SENTENCE_SPLIT_PATTERN = Pattern.compile("(?<=[.!?])\\s+");

    private final JsonDataStore dataStore;
    private final List<KnowledgeChunk> baseChunks;
    private final List<KnowledgeDocumentSummary> baseDocuments;
    private final CopyOnWriteArrayList<PersistedCustomDoc> persistedCustomDocs = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<KnowledgeChunk> customChunks = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<KnowledgeDocumentSummary> customDocuments = new CopyOnWriteArrayList<>();
    private final AtomicInteger customCounter = new AtomicInteger(0);
    private final AtomicInteger version = new AtomicInteger(1);

    public KnowledgeBaseLoader() {
        this(null);
    }

    @Autowired
    public KnowledgeBaseLoader(JsonDataStore dataStore) {
        this.dataStore = dataStore;
        List<KnowledgeChunk> loadedChunks = new ArrayList<>();
        List<KnowledgeDocumentSummary> loadedDocs = new ArrayList<>();

        try {
            var resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath*:knowledge/*.md");
            Arrays.sort(resources, Comparator.comparing(r -> r.getFilename() == null ? "" : r.getFilename()));

            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null) {
                    continue;
                }
                String docId = filename.replaceFirst("\\.md$", "");
                String raw = readResource(resource);
                ParsedDoc parsed = parseDocument(docId, raw);
                loadedChunks.addAll(parsed.chunks());
                loadedDocs.add(new KnowledgeDocumentSummary(
                        docId,
                        parsed.title(),
                        parsed.level(),
                        parsed.category(),
                        parsed.sourceRef(),
                        parsed.chunks().size(),
                        parsed.tags()
                ));
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao carregar base de conhecimento RAG em classpath*:knowledge/*.md", ex);
        }

        this.baseChunks = List.copyOf(loadedChunks);
        this.baseDocuments = List.copyOf(loadedDocs);
        loadPersistedCustomDocuments();
    }

    private void loadPersistedCustomDocuments() {
        if (dataStore == null) {
            return;
        }
        List<PersistedCustomDoc> saved = dataStore.readList(STORAGE_FILE, PersistedCustomDoc.class);
        int maxOffset = 0;
        for (PersistedCustomDoc doc : saved) {
            if (doc == null || doc.docId() == null || doc.docId().isBlank() || doc.content() == null || doc.content().isBlank()) {
                continue;
            }
            List<KnowledgeChunk> docChunks = splitSections(
                    doc.docId(),
                    doc.title(),
                    doc.level(),
                    doc.category(),
                    doc.sourceRef(),
                    doc.content(),
                    doc.tags()
            );
            KnowledgeDocumentSummary summary = new KnowledgeDocumentSummary(
                    doc.docId(),
                    doc.title(),
                    doc.level(),
                    doc.category(),
                    doc.sourceRef(),
                    docChunks.size(),
                    doc.tags()
            );
            persistedCustomDocs.add(doc);
            customChunks.addAll(docChunks);
            customDocuments.add(summary);

            Matcher m = CUSTOM_SEQ_PATTERN.matcher(doc.docId());
            if (m.find()) {
                int num = Integer.parseInt(m.group(1));
                maxOffset = Math.max(maxOffset, Math.max(0, num - baseDocuments.size()));
            }
        }
        customCounter.set(Math.max(maxOffset, persistedCustomDocs.size()));
        if (!persistedCustomDocs.isEmpty()) {
            version.incrementAndGet();
        }
    }

    public List<KnowledgeChunk> getChunks() {
        if (customChunks.isEmpty()) {
            return baseChunks;
        }
        List<KnowledgeChunk> all = new ArrayList<>(baseChunks.size() + customChunks.size());
        all.addAll(baseChunks);
        all.addAll(customChunks);
        return List.copyOf(all);
    }

    public List<KnowledgeDocumentSummary> getDocumentsSummary() {
        if (customDocuments.isEmpty()) {
            return baseDocuments;
        }
        List<KnowledgeDocumentSummary> all = new ArrayList<>(baseDocuments.size() + customDocuments.size());
        all.addAll(baseDocuments);
        all.addAll(customDocuments);
        return List.copyOf(all);
    }

    public int getVersion() {
        return version.get();
    }

    public boolean isBaseDocument(String docId) {
        if (docId == null || docId.isBlank()) {
            return false;
        }
        return baseDocuments.stream().anyMatch(d -> d.docId().equalsIgnoreCase(docId.trim()));
    }

    /**
     * Cadastra um novo documento/tópico enviado pelo Painel do Professor em tempo real e persiste em JSON.
     */
    public synchronized KnowledgeDocumentSummary addCustomDocument(NewKnowledgeDocumentRequest request) {
        return addCustomDocument(request, request == null ? List.of() : request.tags());
    }

    public synchronized KnowledgeDocumentSummary addCustomDocument(NewKnowledgeDocumentRequest request, List<String> tags) {
        if (request == null || request.title().isBlank() || request.content().isBlank()) {
            throw new IllegalArgumentException("Título e conteúdo do documento RAG são obrigatórios.");
        }
        List<String> cleanTags = tags == null ? List.of() : tags.stream()
                .filter(t -> t != null && !t.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        int seq = baseDocuments.size() + customCounter.incrementAndGet();
        String slug = slugify(request.title());
        String docId = String.format("%02d-docente-%s", seq, slug);

        String normalizedBody = request.content().replace("\r\n", "\n").trim();
        if (!normalizedBody.startsWith("## ")) {
            normalizedBody = "## " + request.section() + "\n" + normalizedBody;
        }

        List<KnowledgeChunk> newChunks = splitSections(
                docId,
                request.title(),
                request.level(),
                request.category(),
                request.sourceRef(),
                normalizedBody,
                cleanTags
        );

        KnowledgeDocumentSummary summary = new KnowledgeDocumentSummary(
                docId,
                request.title(),
                request.level(),
                request.category(),
                request.sourceRef(),
                newChunks.size(),
                cleanTags
        );

        persistedCustomDocs.add(new PersistedCustomDoc(
                docId,
                request.title(),
                request.level(),
                request.category(),
                request.sourceRef(),
                normalizedBody,
                cleanTags
        ));
        customChunks.addAll(newChunks);
        customDocuments.add(summary);
        version.incrementAndGet();
        persistCustomDocuments();
        return summary;
    }

    /**
     * Importa um arquivo .md ou .txt enviado pelo Painel Docente, interpretando cabeçalho opcional
     * (title/level/category/source separados por ---) e particionando por seções "## ".
     */
    public synchronized KnowledgeDocumentSummary addUploadedDocument(
            String filename,
            String rawContent,
            String overrideTitle,
            String overrideLevel,
            String overrideCategory,
            String overrideSourceRef
    ) {
        if (rawContent == null || rawContent.isBlank()) {
            throw new IllegalArgumentException("O arquivo enviado está vazio.");
        }
        String cleanName = (filename == null || filename.isBlank()) ? "documento-docente.md" : filename.trim();
        String baseName = cleanName.replaceFirst("(?i)\\.(md|txt)$", "").replaceAll("[_-]+", " ").trim();
        if (baseName.isBlank()) {
            baseName = "Documento Docente";
        }

        String normalized = rawContent.replace("\r\n", "\n").trim();
        String[] parts = normalized.split("\n---\n", 2);
        boolean hasFrontmatter = parts.length == 2 && isFrontmatterBlock(parts[0]);
        String headerBlock = hasFrontmatter ? parts[0] : "";
        String bodyBlock = hasFrontmatter ? parts[1].trim() : normalized;

        String title = baseName;
        String level = "B1";
        String category = "Curadoria Docente (Upload)";
        String sourceRef = "Upload Docente (" + cleanName + ")";
        List<String> tags = new ArrayList<>();

        for (String line : headerBlock.split("\n")) {
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            if (value.isBlank()) {
                continue;
            }
            switch (key) {
                case "title" -> title = value;
                case "level" -> level = value.toUpperCase(Locale.ROOT);
                case "category" -> category = value;
                case "source" -> sourceRef = value;
                case "tags" -> tags.addAll(parseTags(value));
                default -> {
                    // ignora outros metadados
                }
            }
        }

        // Se o conteúdo começar com "# Titulo", usa como título se não houver frontmatter nem override
        if (headerBlock.isBlank() && bodyBlock.startsWith("# ")) {
            int nl = bodyBlock.indexOf('\n');
            if (nl > 2) {
                title = bodyBlock.substring(2, nl).trim();
                bodyBlock = bodyBlock.substring(nl + 1).trim();
            }
        }

        if (overrideTitle != null && !overrideTitle.isBlank()) {
            title = overrideTitle.trim();
        }
        if (overrideLevel != null && !overrideLevel.isBlank()) {
            level = overrideLevel.trim().toUpperCase(Locale.ROOT);
        }
        if (overrideCategory != null && !overrideCategory.isBlank()) {
            category = overrideCategory.trim();
        }
        if (overrideSourceRef != null && !overrideSourceRef.isBlank()) {
            sourceRef = overrideSourceRef.trim();
        }
        if (title.length() > 160) {
            title = title.substring(0, 160).trim();
        }

        if (bodyBlock.isBlank()) {
            throw new IllegalArgumentException("O arquivo não contém conteúdo textual indexável.");
        }

        return addCustomDocument(new NewKnowledgeDocumentRequest(
                title,
                level,
                category,
                sourceRef,
                "Conteúdo Importado (" + cleanName + ")",
                bodyBlock
        ), tags);
    }

    private static boolean isFrontmatterBlock(String block) {
        if (block == null || block.isBlank()) {
            return false;
        }
        boolean hasRecognizedKey = false;
        for (String rawLine : block.split("\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.equals("---")) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                return false;
            }
            String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            if (!key.matches("[a-z][a-z0-9_-]{0,31}")) {
                return false;
            }
            if (key.equals("title") || key.equals("level") || key.equals("category")
                    || key.equals("source") || key.equals("tags")) {
                hasRecognizedKey = true;
            }
        }
        return hasRecognizedKey;
    }

    /**
     * Remove um documento customizado criado pelo Painel do Professor e atualiza o JSON em disco.
     */
    public synchronized boolean removeCustomDocument(String docId) {
        if (docId == null || docId.isBlank()) {
            return false;
        }
        String cleanId = docId.trim();
        boolean removedDoc = customDocuments.removeIf(d -> d.docId().equalsIgnoreCase(cleanId));
        if (!removedDoc) {
            return false;
        }
        customChunks.removeIf(c -> c.docId().equalsIgnoreCase(cleanId));
        persistedCustomDocs.removeIf(d -> d.docId().equalsIgnoreCase(cleanId));
        version.incrementAndGet();
        persistCustomDocuments();
        return true;
    }

    /**
     * Remove documentos customizados adicionados em tempo de execução (útil para isolamento de testes).
     */
    public synchronized void resetCustomDocuments() {
        persistedCustomDocs.clear();
        customChunks.clear();
        customDocuments.clear();
        customCounter.set(0);
        version.incrementAndGet();
        persistCustomDocuments();
    }

    private void persistCustomDocuments() {
        if (dataStore != null) {
            dataStore.writeList(STORAGE_FILE, List.copyOf(persistedCustomDocs));
        }
    }

    private static String slugify(String text) {
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (decomposed.isBlank()) {
            return "topico";
        }
        return decomposed.length() > 36 ? decomposed.substring(0, 36).replaceAll("-$", "") : decomposed;
    }

    private String readResource(Resource resource) throws IOException {
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private ParsedDoc parseDocument(String docId, String raw) {
        String normalized = raw.replace("\r\n", "\n");
        String[] parts = normalized.split("\n---\n", 2);
        String headerBlock = parts.length == 2 ? parts[0] : "";
        String bodyBlock = parts.length == 2 ? parts[1] : normalized;

        String title = docId;
        String level = "ALL";
        String category = "Geral";
        String sourceRef = "Curadoria Dinga AI";
        List<String> tags = new ArrayList<>();

        for (String line : headerBlock.split("\n")) {
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            switch (key) {
                case "title" -> title = value;
                case "level" -> level = value.toUpperCase(Locale.ROOT);
                case "category" -> category = value;
                case "source" -> sourceRef = value;
                case "tags" -> tags.addAll(parseTags(value));
                default -> {
                    // Ignora metadados extras desconhecidos
                }
            }
        }

        List<String> normalizedTags = List.copyOf(new LinkedHashSet<>(tags));
        List<KnowledgeChunk> docChunks = splitSections(docId, title, level, category, sourceRef, bodyBlock, normalizedTags);
        return new ParsedDoc(title, level, category, sourceRef, normalizedTags, docChunks);
    }

    public static List<String> parseTags(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return List.of();
        }
        String cleaned = rawValue.trim();
        if (cleaned.startsWith("[") && cleaned.endsWith("]") && cleaned.length() >= 2) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String part : cleaned.split("[,;]")) {
            String tag = part.trim().replaceAll("^[\"']+|[\"']+$", "").trim();
            if (!tag.isEmpty()) {
                result.add(tag);
            }
        }
        return List.copyOf(result);
    }

    private List<KnowledgeChunk> splitSections(
            String docId,
            String title,
            String level,
            String category,
            String sourceRef,
            String body,
            List<String> tags
    ) {
        List<KnowledgeChunk> result = new ArrayList<>();
        String currentSection = "Visão Geral";
        StringBuilder currentContent = new StringBuilder();
        int[] chunkSeq = {1};

        for (String line : body.split("\n")) {
            if (line.startsWith("## ")) {
                String text = currentContent.toString().trim();
                if (!text.isEmpty()) {
                    appendSectionChunks(result, docId, title, level, category, sourceRef, currentSection, text, tags, chunkSeq);
                }
                currentSection = line.substring(3).trim();
                currentContent.setLength(0);
            } else {
                currentContent.append(line).append('\n');
            }
        }

        String trailing = currentContent.toString().trim();
        if (!trailing.isEmpty()) {
            appendSectionChunks(result, docId, title, level, category, sourceRef, currentSection, trailing, tags, chunkSeq);
        }
        return result;
    }

    private static void appendSectionChunks(
            List<KnowledgeChunk> result,
            String docId,
            String title,
            String level,
            String category,
            String sourceRef,
            String sectionName,
            String sectionText,
            List<String> tags,
            int[] chunkSeq
    ) {
        List<String> parts = subdivideWithOverlap(sectionText, TARGET_CHUNK_CHARS, MAX_CHUNK_CHARS, OVERLAP_CHARS);
        int totalParts = parts.size();
        for (int i = 0; i < totalParts; i++) {
            String effectiveSection = totalParts == 1
                    ? sectionName
                    : sectionName + " (parte " + (i + 1) + "/" + totalParts + ")";
            result.add(new KnowledgeChunk(
                    docId + "#" + chunkSeq[0]++,
                    docId,
                    title,
                    level,
                    category,
                    sourceRef,
                    effectiveSection,
                    parts.get(i),
                    0.0,
                    tags
            ));
        }
    }

    /**
     * Subdivide seções longas por parágrafos (e linhas/frases quando necessário) com tamanho alvo
     * (~900 caracteres) e sobreposição (~120 caracteres) alinhada a fronteiras de palavras.
     */
    public static List<String> subdivideWithOverlap(String text, int targetChars, int maxChars, int overlapChars) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        int safeTarget = Math.max(100, targetChars);
        int safeMax = Math.max(safeTarget, maxChars);
        int safeOverlap = Math.max(0, Math.min(overlapChars, safeTarget / 2));

        String trimmed = text.trim();
        if (trimmed.length() <= safeMax) {
            return List.of(trimmed);
        }

        List<String> units = splitIntoUnits(trimmed, safeTarget);
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String unit : units) {
            if (unit.isBlank()) {
                continue;
            }
            if (!current.isEmpty() && current.length() + 2 + unit.length() > safeTarget) {
                String emitted = current.toString().trim();
                if (!emitted.isEmpty()) {
                    chunks.add(emitted);
                }
                current.setLength(0);
                String overlap = extractOverlapTail(emitted, safeOverlap);
                if (!overlap.isEmpty() && overlap.length() + 2 + unit.length() <= safeMax) {
                    current.append(overlap).append("\n\n");
                }
            } else if (!current.isEmpty()) {
                current.append("\n\n");
            }
            current.append(unit);
        }

        String tail = current.toString().trim();
        if (!tail.isEmpty()) {
            // Evita emitir último chunk contendo apenas o overlap já presente no chunk anterior
            if (chunks.isEmpty() || !chunks.getLast().endsWith(tail)) {
                chunks.add(tail);
            }
        }
        return chunks.isEmpty() ? List.of(trimmed) : List.copyOf(chunks);
    }

    private static List<String> splitIntoUnits(String text, int targetChars) {
        List<String> units = new ArrayList<>();
        for (String para : PARAGRAPH_SPLIT_PATTERN.split(text)) {
            String cleanPara = para.trim();
            if (cleanPara.isEmpty()) {
                continue;
            }
            if (cleanPara.length() <= targetChars) {
                units.add(cleanPara);
                continue;
            }
            for (String line : cleanPara.split("\n")) {
                String cleanLine = line.trim();
                if (cleanLine.isEmpty()) {
                    continue;
                }
                if (cleanLine.length() <= targetChars) {
                    units.add(cleanLine);
                    continue;
                }
                for (String sentence : SENTENCE_SPLIT_PATTERN.split(cleanLine)) {
                    String cleanSentence = sentence.trim();
                    if (cleanSentence.isEmpty()) {
                        continue;
                    }
                    if (cleanSentence.length() <= targetChars) {
                        units.add(cleanSentence);
                    } else {
                        hardSplitOnWordBoundary(cleanSentence, targetChars, units);
                    }
                }
            }
        }
        return units;
    }

    private static void hardSplitOnWordBoundary(String text, int targetChars, List<String> out) {
        int start = 0;
        int len = text.length();
        while (start < len) {
            int end = Math.min(len, start + targetChars);
            if (end < len) {
                int space = text.lastIndexOf(' ', end);
                if (space > start + Math.max(20, targetChars / 3)) {
                    end = space;
                }
            }
            String piece = text.substring(start, end).trim();
            if (!piece.isEmpty()) {
                out.add(piece);
            }
            start = Math.max(end, start + 1);
            while (start < len && Character.isWhitespace(text.charAt(start))) {
                start++;
            }
        }
    }

    private static String extractOverlapTail(String chunk, int overlapChars) {
        if (chunk == null || chunk.isEmpty() || overlapChars <= 0) {
            return "";
        }
        if (chunk.length() <= overlapChars) {
            return chunk;
        }
        int rawStart = chunk.length() - overlapChars;
        int searchLimit = Math.min(chunk.length(), rawStart + 28);
        int boundary = rawStart;
        // Avança até a próxima fronteira de palavra para não cortar uma palavra ao meio
        if (rawStart > 0 && !Character.isWhitespace(chunk.charAt(rawStart - 1))) {
            while (boundary < searchLimit && !Character.isWhitespace(chunk.charAt(boundary))) {
                boundary++;
            }
            while (boundary < chunk.length() && Character.isWhitespace(chunk.charAt(boundary))) {
                boundary++;
            }
        }
        if (boundary >= chunk.length()) {
            boundary = rawStart;
        }
        return chunk.substring(boundary).trim();
    }

    public record PersistedCustomDoc(
            String docId,
            String title,
            String level,
            String category,
            String sourceRef,
            String content,
            List<String> tags
    ) {
        public PersistedCustomDoc {
            tags = tags == null ? List.of() : List.copyOf(tags);
        }
    }

    private record ParsedDoc(
            String title,
            String level,
            String category,
            String sourceRef,
            List<String> tags,
            List<KnowledgeChunk> chunks
    ) {
    }
}
