package br.edu.usjt.dinga.skills;

import br.edu.usjt.dinga.llm.HybridLlmClient;
import br.edu.usjt.dinga.model.ChatRequest;
import br.edu.usjt.dinga.model.KnowledgeChunk;
import br.edu.usjt.dinga.model.SkillDefinition;
import br.edu.usjt.dinga.rag.Bm25RagRetriever;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Skill especializada em leitura de documentação técnica (Microsoft Learn, IA, Cloud, APIs, Git)
 * e análise estruturada de trechos reais de documentação e erros de código/terminal.
 */
@Component
public class InglesTecnicoSkill extends AbstractTeacherSkill {

    private static final Pattern CODE_OR_TERMINAL_ERROR_PATTERN = Pattern.compile(
            "(nullpointerexception|illegalargumentexception|classnotfoundexception|indexoutofboundsexception|"
                    + "stackoverflowerror|outofmemoryerror|typeerror|syntaxerror|referenceerror|modulenotfounderror|"
                    + "cannot find module|exception in thread|traceback|fatal:|non-fast-forward|rejected\\s*\\(|"
                    + "git push.*rejected|failed to push|merge conflict|connection refused|econnrefused|"
                    + "permission denied|eacces|accessdeniedexception|timeoutexception|timed out|"
                    + "http\\s*(400|401|403|404|429|500|502|503)|too many requests|bad request|unauthorized|"
                    + "forbidden|internal server error|err_[a-z_]+|cannot invoke|is null|undefined is not)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern DOC_MODAL_PATTERN = Pattern.compile(
            "\\b(must not|must|should not|should|may not|may|shall|required|recommended|optional|deprecated|warning|note:)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern TECH_DOC_ENGLISH_SENTENCE = Pattern.compile(
            "\\b(you must|you should|you may|this method|this endpoint|returns|throws|parameter|environment variable|"
                    + "deprecated|rate limit|before running|make sure|set the|if the|when the|must be|should be|may be)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Map<String, String> TECH_GLOSSARY = buildTechGlossary();

    private static Map<String, String> buildTechGlossary() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("null", "**null / NullPointer:** referência vazia (variável sem objeto instanciado na memória).");
        map.put("exception", "**Exception:** exceção/erro lançado em tempo de execução quando algo inesperado acontece.");
        map.put("rejected", "**rejected:** rejeitado pelo servidor remoto (no Git, normalmente porque existem commits novos lá que você ainda não baixou).");
        map.put("non-fast-forward", "**non-fast-forward:** seu histórico local está atrás da branch remota; exige sincronizar (`git pull`) antes do `git push`.");
        map.put("conflict", "**conflict (merge conflict):** duas alterações editaram o mesmo trecho de código e precisam de decisão manual.");
        map.put("deprecated", "**deprecated:** recurso obsoleto — ainda pode funcionar hoje, mas será removido em versões futuras; prefira a alternativa recomendada.");
        map.put("endpoint", "**endpoint:** URL/rota exposta por uma API (ex.: `POST /api/chat`).");
        map.put("payload", "**payload:** corpo de dados (geralmente JSON) enviado ou retornado pela API.");
        map.put("rate limit", "**rate limit:** limite máximo de requisições/tokens permitidos em um intervalo de tempo (HTTP 429).");
        map.put("timeout", "**timeout:** tempo limite esgotado aguardando resposta do servidor ou banco.");
        map.put("refused", "**connection refused:** a porta/serviço de destino recusou a conexão (geralmente o servidor não está rodando ou a porta está incorreta).");
        map.put("permission", "**permission denied:** falta permissão de leitura/execução ou credencial/token válido.");
        map.put("unauthorized", "**unauthorized (HTTP 401):** faltou autenticação (chave API ou token ausente/inválido).");
        map.put("forbidden", "**forbidden (HTTP 403):** você está autenticado, mas não tem permissão para acessar esse recurso.");
        map.put("not found", "**not found (HTTP 404):** rota, arquivo ou recurso não existe no caminho informado.");
        map.put("environment variable", "**environment variable:** variável de ambiente do sistema (ex.: `JAVA_HOME`, `GEMINI_API_KEY`).");
        map.put("fallback", "**fallback:** plano B automático acionado quando o serviço principal falha.");
        map.put("latency", "**latency:** tempo de espera entre o envio da requisição e a resposta.");
        map.put("grounding", "**grounding:** ancorar respostas da IA em documentos confiáveis (via RAG) para evitar invenções.");
        return Map.copyOf(map);
    }

    public InglesTecnicoSkill() {
        super(new SkillDefinition(
                "ingles-tecnico",
                "Inglês Técnico & Learn",
                "Tech + Microsoft Learn",
                "Traduz conceitos de IA, Cloud, APIs, trechos de documentação e erros de código/terminal sem tradução robótica.",
                "Conectar vocabulário técnico global a analogias brasileiras e estruturar erros/docs em sentido real, termos-chave/modais e ação prática.",
                "ODS 4 e ODS 10 (Acesso à tecnologia e redução de barreiras)",
                "O que significam RAG, grounding e hallucination em módulos do Microsoft Learn?"
        ));
    }

    public static boolean containsCodeErrorOrTechSnippet(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        if (CODE_OR_TERMINAL_ERROR_PATTERN.matcher(message).find()) {
            return true;
        }
        return TECH_DOC_ENGLISH_SENTENCE.matcher(message).find()
                && (DOC_MODAL_PATTERN.matcher(message).find() || message.length() >= 35);
    }

    @Override
    public HybridLlmClient.GenerationOptions generationOptions() {
        return HybridLlmClient.GenerationOptions.of(0.25, 0.85);
    }

    @Override
    protected String buildFewShotExamples(String level, boolean lowDataMode) {
        return switch (level) {
            case "A1" -> """
                    Entrada: "O que significa 'endpoint'?"
                    Resposta ideal: **Endpoint** é o endereço (URL) onde um serviço de API recebe pedidos, como um balcão de atendimento digital. Exemplo: "The app calls the /api/chat endpoint." Tente citar um endereço de API que você já viu!
                    """;
            case "A2" -> """
                    Entrada: "O que significa 'git push rejected'?"
                    Resposta ideal: Significa que o servidor recusou o envio dos seus commits porque já existem alterações novas na branch remota. Exemplo: "The push was rejected, so I ran git pull first." O que você rodaria antes de tentar o push de novo?
                    """;
            case "B2" -> """
                    Entrada: "Como explicar grounding e fallback em uma revisão de arquitetura?"
                    Resposta ideal: **Grounding** ancora o LLM em evidências verificáveis (RAG) para mitigar alucinações, enquanto **fallback** garante continuidade com degradação graciosa sob falha ou limite de cota. Exemplo: "Our RAG pipeline grounds Gemini 3.8 Flash responses and falls back to local BM25 under rate limits." Como você descreveria a resiliência da sua API em 1 frase?
                    """;
            default -> """
                    Entrada: "O que significam grounding e hallucination no Microsoft Learn?"
                    Resposta ideal: **Grounding** é ancorar o modelo em documentos confiáveis (via RAG) para que ele responda com base em fatos; **hallucination** é quando a IA gera algo plausível, mas inventado. Exemplo: "Grounding with RAG reduces hallucinations in technical documentation." Consegue resumir essa diferença em 1 frase sua?
                    """;
        };
    }

    @Override
    public int matchScore(ChatRequest request) {
        int score = scoreKeywords(
                request.message(),
                "microsoft learn", "documentacao", "docs", "readme", "api", "endpoint",
                "deploy", "git", "pull request", "cloud", "rag", "prompt", "token",
                "hallucination", "alucinacao", "grounding", "fine-tuning", "latency", "payload",
                "fallback", "skimming", "scanning", "tecnico", "erro", "terminal", "stack trace"
        );
        if (containsCodeErrorOrTechSnippet(request.message())) {
            score += 30;
        }
        return score;
    }

    @Override
    public String buildSystemPrompt(ChatRequest request, List<KnowledgeChunk> chunks) {
        String base = super.buildSystemPrompt(request, chunks);
        if (!containsCodeErrorOrTechSnippet(request.message())) {
            return base;
        }
        return base + "\nDIRETRIZ DE ANÁLISE DE TRECHO TÉCNICO / ERRO DE CÓDIGO:\n"
                + "A mensagem do aluno contém um trecho real de documentação em inglês ou erro de código/terminal.\n"
                + "Estruture obrigatoriamente sua resposta nestas 3 seções claras:\n"
                + "### 1. Sentido real (sem tradução robótica)\n"
                + "### 2. Termos-chave e verbos modais (must / should / may)\n"
                + "### 3. O que fazer na prática\n";
    }

    @Override
    public String buildLocalResponse(ChatRequest request, List<KnowledgeChunk> chunks) {
        if (containsCodeErrorOrTechSnippet(request.message())) {
            return buildTechnicalSnippetAnalysis(request, chunks);
        }
        String intro = "Ativei o modo **Inglês Técnico** para destravar termos usados em documentação real (como *Microsoft Learn*, GitHub e APIs):";
        return formatGroundedLocalReply(
                request,
                chunks,
                intro,
                "Ao abrir uma documentação em inglês, aplique primeiro o *skimming* (títulos e exemplos de código) antes de traduzir parágrafos inteiros."
        );
    }

    @Override
    public String buildPracticePrompt(ChatRequest request, List<KnowledgeChunk> chunks) {
        if (containsCodeErrorOrTechSnippet(request.message())) {
            return "Explique em 1 frase curta em inglês o que causou esse erro ou regra da documentação (ex.: \"The push was rejected because the remote branch has new commits.\").";
        }
        return "Tente resumir em 1 frase (pode ser em português + termos em inglês) qual a diferença entre 'grounding (RAG)' e 'hallucination'.";
    }

    private String buildTechnicalSnippetAnalysis(ChatRequest request, List<KnowledgeChunk> chunks) {
        String msg = request.message();
        String lower = msg.toLowerCase();
        String norm = Bm25RagRetriever.normalizeText(msg);

        String realMeaning;
        String practicalAction;
        String englishModelSentence;

        if (lower.contains("nullpointerexception") || lower.contains("cannot invoke") || lower.contains("is null")) {
            realMeaning = "O código tentou chamar um método ou acessar um campo de uma variável que está vazia (`null`), ou seja, nenhum objeto foi criado/atribuído a ela antes do uso.";
            practicalAction = "1) Veja a linha exata indicada no *stack trace*; 2) Verifique qual variável pode chegar `null`; 3) Inicialize o objeto, valide com `if (obj != null)` ou use `Optional`.";
            englishModelSentence = "The application threw a NullPointerException because the variable was not initialized.";
        } else if (lower.contains("non-fast-forward") || lower.contains("failed to push")
                || ((lower.contains("git") || lower.contains("refs")) && lower.contains("rejected"))) {
            realMeaning = "O Git bloqueou o envio dos seus commits (`git push`) porque o repositório remoto já tem alterações mais novas que ainda não estão na sua máquina.";
            practicalAction = "Execute `git pull --rebase` (ou `git pull`), resolva eventuais conflitos se houver e só então rode `git push` novamente.";
            englishModelSentence = "The git push was rejected because the remote branch contains newer commits.";
        } else if (lower.contains("merge conflict") || lower.contains("conflict")) {
            realMeaning = "Duas branches alteraram as mesmas linhas do mesmo arquivo, e o Git não consegue decidir automaticamente qual versão manter.";
            practicalAction = "Abra os arquivos marcados com `<<<<<<<`, escolha o código correto, salve, rode `git add .` e finalize o commit/merge.";
            englishModelSentence = "I resolved the merge conflict before pushing my changes to the branch.";
        } else if (lower.contains("modulenotfounderror") || lower.contains("cannot find module") || lower.contains("classnotfoundexception")) {
            realMeaning = "O programa tentou importar uma biblioteca, módulo ou classe que não foi instalada ou não está no caminho de build (*classpath* / `node_modules`).";
            practicalAction = "Instale as dependências do projeto (`mvn clean install`, `npm install` ou `pip install`) e confira o nome exato do import.";
            englishModelSentence = "The build failed with a missing module error because the dependency was not installed.";
        } else if (lower.contains("typeerror") || lower.contains("syntaxerror") || lower.contains("referenceerror")) {
            realMeaning = "Há um erro de escrita na sintaxe (`SyntaxError`), variável não declarada (`ReferenceError`) ou operação com tipo incompatível (`TypeError`).";
            practicalAction = "Confira a linha apontada no console, verifique parênteses/chaves/vírgulas e inspecione o tipo real da variável antes de operá-la.";
            englishModelSentence = "A TypeError happens when you try to use a value with an incompatible type.";
        } else if (lower.contains("connection refused") || lower.contains("econnrefused")) {
            realMeaning = "Não foi possível abrir conexão com o servidor/porta de destino — normalmente o serviço está desligado ou escutando em outra porta.";
            practicalAction = "Confirme se o servidor backend/banco está rodando, se a porta (ex.: `8080`) está correta e se não há bloqueio de firewall.";
            englishModelSentence = "The client received a connection refused error because the local server was not running.";
        } else if (lower.contains("permission denied") || lower.contains("eacces") || lower.contains("accessdeniedexception")) {
            realMeaning = "O sistema operacional ou servidor bloqueou o acesso ao arquivo, pasta ou comando por falta de permissão.";
            practicalAction = "Verifique as permissões do arquivo (`chmod` / usuário atual) ou credenciais SSH/token configuradas.";
            englishModelSentence = "Permission denied occurred because the user did not have write access to the directory.";
        } else if (matchesHttpStatus(lower, "429") || lower.contains("too many requests") || lower.contains("rate limit")) {
            realMeaning = "A API atingiu o limite máximo de chamadas ou tokens por minuto (*rate limit*) e pediu para aguardar antes de tentar novamente.";
            practicalAction = "Aguarde alguns segundos, reduza a frequência de chamadas ou acione o modo de contingência (*fallback* local).";
            englishModelSentence = "The API returned HTTP 429 because the request exceeded the rate limit.";
        } else if (matchesHttpStatus(lower, "401") || lower.contains("unauthorized") || matchesHttpStatus(lower, "403") || lower.contains("forbidden")) {
            realMeaning = "O servidor recusou a chamada por problema de autenticação (`401 Unauthorized` — chave/token ausente ou inválido) ou falta de permissão (`403 Forbidden`).";
            practicalAction = "Confira se a chave de API (`Authorization` / variável de ambiente) está configurada corretamente e se a conta tem permissão no recurso.";
            englishModelSentence = "The endpoint returned HTTP 401 because the API key was missing or invalid.";
        } else if (matchesHttpStatus(lower, "400") || lower.contains("bad request") || lower.contains("illegalargumentexception")) {
            realMeaning = "A requisição chegou ao servidor, mas os dados enviados (*payload* ou parâmetros) estão incompletos ou em formato inválido.";
            practicalAction = "Compare o JSON/parâmetros enviados com a documentação do endpoint e verifique campos obrigatórios e tipos de dados.";
            englishModelSentence = "The server returned HTTP 400 because the request payload was missing required fields.";
        } else if (matchesHttpStatus(lower, "404") || lower.contains("not found")) {
            realMeaning = "O endereço/rota (*endpoint*) ou recurso solicitado não existe no servidor.";
            practicalAction = "Verifique ortografia da URL, método HTTP (`GET`/`POST`) e se o ID consultado realmente existe.";
            englishModelSentence = "The API returned HTTP 404 because the requested resource was not found.";
        } else if (lower.contains("deprecated")) {
            realMeaning = "A documentação está avisando que este método/configuração ficou obsoleto e será descontinuado em versões futuras.";
            practicalAction = "Procure na mesma página da documentação qual é o substituto recomendado (*recommended replacement*) e planeje a migração.";
            englishModelSentence = "This method is deprecated and should be replaced with the newer API.";
        } else {
            realMeaning = "Este trecho em inglês técnico define uma regra, comportamento ou instrução operacional de documentação/sistema que precisa ser lida pelo seu efeito prático, não palavra por palavra.";
            practicalAction = "Identifique primeiro o sujeito técnico (qual serviço/variável/comando), depois o nível de obrigatoriedade dos verbos modais e aplique o comando/configuração no seu ambiente testando o resultado.";
            englishModelSentence = "You must follow the required configuration steps before running the application.";
        }

        List<String> keyTermsAndModals = new ArrayList<>();
        // Detecção de verbos modais de documentação técnica (RFC 2119 / Microsoft Learn)
        if (lower.contains("must not") || lower.contains("mustn't")) {
            keyTermsAndModals.add("**`must not` (modal):** proibição estrita — nunca faça isso, pois quebra segurança ou contrato da API.");
        }
        if (Pattern.compile("\\bmust\\b(?!\\s+not\\b)", Pattern.CASE_INSENSITIVE).matcher(msg).find()) {
            keyTermsAndModals.add("**`must` (modal):** requisito obrigatório — sem cumprir isso, o sistema falha.");
        }
        if (lower.contains("should not") || lower.contains("shouldn't")) {
            keyTermsAndModals.add("**`should not` (modal):** forte recomendação para evitar essa prática.");
        }
        if (Pattern.compile("\\bshould\\b(?!\\s+not\\b)", Pattern.CASE_INSENSITIVE).matcher(msg).find()) {
            keyTermsAndModals.add("**`should` (modal):** recomendação/boa prática — funciona sem, mas é fortemente aconselhado.");
        }
        if (Pattern.compile("\\bmay\\b", Pattern.CASE_INSENSITIVE).matcher(msg).find()) {
            keyTermsAndModals.add("**`may` (modal):** opcional/permitido — você escolhe usar conforme a necessidade.");
        }
        if (Pattern.compile("\\brequired\\b", Pattern.CASE_INSENSITIVE).matcher(msg).find()) {
            keyTermsAndModals.add("**`required`:** campo ou passo obrigatório.");
        }
        if (Pattern.compile("\\boptional\\b", Pattern.CASE_INSENSITIVE).matcher(msg).find()) {
            keyTermsAndModals.add("**`optional`:** configuração opcional (tem valor padrão se omitida).");
        }

        for (Map.Entry<String, String> entry : TECH_GLOSSARY.entrySet()) {
            if (lower.contains(entry.getKey()) || norm.contains(entry.getKey())) {
                keyTermsAndModals.add(entry.getValue());
            }
        }
        if (keyTermsAndModals.isEmpty()) {
            keyTermsAndModals.add("**Modais em docs técnicas:** `must` = obrigatório · `should` = boa prática recomendada · `may` = opcional.");
        }

        StringBuilder analysis = new StringBuilder();
        analysis.append("Ativei o **Analisador de Documentação e Erros de Código** (`Inglês Técnico`):\n\n");
        analysis.append("### 1. Sentido real (sem tradução robótica)\n");
        analysis.append(realMeaning).append("\n\n");
        analysis.append("### 2. Termos-chave e verbos modais (`must` / `should` / `may`)\n");
        for (String item : keyTermsAndModals) {
            analysis.append("- ").append(item).append("\n");
        }
        analysis.append("\n### 3. O que fazer na prática\n");
        analysis.append(practicalAction).append("\n\n");
        analysis.append("Exemplo em inglês para relatar isso no time: *\"").append(englishModelSentence).append("\"*");

        return formatGroundedLocalReply(
                request,
                chunks,
                analysis.toString().trim(),
                "Ao ler logs ou docs em inglês, procure primeiro o tipo do erro/verbo modal (`must/should/may`) e a ação esperada."
        );
    }

    private static boolean matchesHttpStatus(String lowerText, String code) {
        return Pattern.compile("(?<!:)\\b(?:http\\s*|status\\s*)?" + Pattern.quote(code) + "\\b")
                .matcher(lowerText)
                .find();
    }
}
