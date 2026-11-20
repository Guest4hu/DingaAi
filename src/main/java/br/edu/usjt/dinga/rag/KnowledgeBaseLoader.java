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
    private static final Pattern CUSTOM_SEQ_PATTERN = Pattern.compile("^(\\d+)-docente-");

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
                        parsed.chunks().size()
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
                    doc.content()
            );
            KnowledgeDocumentSummary summary = new KnowledgeDocumentSummary(
                    doc.docId(),
                    doc.title(),
                    doc.level(),
                    doc.category(),
                    doc.sourceRef(),
                    docChunks.size()
            );
            persistedCustomDocs.add(doc);
            customChunks.addAll(docChunks);
            customDocuments.add(summary);

            Matcher m = CUSTOM_SEQ_PATTERN.matcher(doc.docId());
            if (m.find()) {
                int num = Integer.parseInt(m.group(1));
                maxOffset = Math.max(maxOffset, Math.max(0, num - 10));
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
        if (request == null || request.title().isBlank() || request.content().isBlank()) {
            throw new IllegalArgumentException("Título e conteúdo do documento RAG são obrigatórios.");
        }
        int seq = 10 + customCounter.incrementAndGet();
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
                normalizedBody
        );

        KnowledgeDocumentSummary summary = new KnowledgeDocumentSummary(
                docId,
                request.title(),
                request.level(),
                request.category(),
                request.sourceRef(),
                newChunks.size()
        );

        persistedCustomDocs.add(new PersistedCustomDoc(
                docId,
                request.title(),
                request.level(),
                request.category(),
                request.sourceRef(),
                normalizedBody
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
        ));
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
            if (key.equals("title") || key.equals("level") || key.equals("category") || key.equals("source")) {
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

        for (String line : headerBlock.split("\n")) {
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = line.substring(0, colon).trim().toLowerCase();
            String value = line.substring(colon + 1).trim();
            switch (key) {
                case "title" -> title = value;
                case "level" -> level = value.toUpperCase();
                case "category" -> category = value;
                case "source" -> sourceRef = value;
                default -> {
                    // Ignora metadados extras desconhecidos
                }
            }
        }

        List<KnowledgeChunk> docChunks = splitSections(docId, title, level, category, sourceRef, bodyBlock);
        return new ParsedDoc(title, level, category, sourceRef, docChunks);
    }

    private List<KnowledgeChunk> splitSections(
            String docId,
            String title,
            String level,
            String category,
            String sourceRef,
            String body
    ) {
        List<KnowledgeChunk> result = new ArrayList<>();
        String currentSection = "Visão Geral";
        StringBuilder currentContent = new StringBuilder();
        int sectionIdx = 1;

        for (String line : body.split("\n")) {
            if (line.startsWith("## ")) {
                String text = currentContent.toString().trim();
                if (!text.isEmpty()) {
                    result.add(new KnowledgeChunk(
                            docId + "#" + sectionIdx++,
                            docId,
                            title,
                            level,
                            category,
                            sourceRef,
                            currentSection,
                            text,
                            0.0
                    ));
                }
                currentSection = line.substring(3).trim();
                currentContent.setLength(0);
            } else {
                currentContent.append(line).append('\n');
            }
        }

        String trailing = currentContent.toString().trim();
        if (!trailing.isEmpty()) {
            result.add(new KnowledgeChunk(
                    docId + "#" + sectionIdx,
                    docId,
                    title,
                    level,
                    category,
                    sourceRef,
                    currentSection,
                    trailing,
                    0.0
            ));
        }
        return result;
    }

    public record PersistedCustomDoc(
            String docId,
            String title,
            String level,
            String category,
            String sourceRef,
            String content
    ) {
    }

    private record ParsedDoc(
            String title,
            String level,
            String category,
            String sourceRef,
            List<KnowledgeChunk> chunks
    ) {
    }
}
