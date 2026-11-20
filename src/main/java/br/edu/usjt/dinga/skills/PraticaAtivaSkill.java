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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Skill de prática ativa: micro-exercícios, simulação de daily/entrevista e motor expandido
 * de correção gramatical contrastiva (Português → Inglês) para 18+ erros clássicos de brasileiros.
 */
@Component
public class PraticaAtivaSkill extends AbstractTeacherSkill {

    private static final Pattern AGE_HAVE_PATTERN = Pattern.compile(
            "\\b(i|you|we|they|he|she)\\s+(have|has)\\s+(\\d{1,2}|twenty|thirty|eighteen|nineteen)\\s+years?(\\s+old)?\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PAST_MARKER_PATTERN = Pattern.compile(
            "\\b(yesterday|last\\s+(week|month|year|night|monday|tuesday|wednesday|thursday|friday)|\\d+\\s+days?\\s+ago)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern REGULAR_PAST_TRAP = Pattern.compile(
            "\\b(work|study|play|finish|create|fix|test|deploy|start|watch|learn|push|pull|open|close|help)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Map<String, String> REGULAR_PAST_FORMS = Map.ofEntries(
            Map.entry("work", "worked"),
            Map.entry("study", "studied"),
            Map.entry("play", "played"),
            Map.entry("finish", "finished"),
            Map.entry("create", "created"),
            Map.entry("fix", "fixed"),
            Map.entry("test", "tested"),
            Map.entry("deploy", "deployed"),
            Map.entry("start", "started"),
            Map.entry("watch", "watched"),
            Map.entry("learn", "learned"),
            Map.entry("push", "pushed"),
            Map.entry("pull", "pulled"),
            Map.entry("open", "opened"),
            Map.entry("close", "closed"),
            Map.entry("help", "helped")
    );
    private static final Map<String, String> IRREGULAR_PAST_FORMS = buildIrregularPastForms();
    private static final Pattern IRREGULAR_PAST_TRAP = Pattern.compile(
            "\\b(go|do|see|make|have|write|buy|come|take|give|speak|read|get|find|think|know|run|build|send)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern THIRD_PERSON_MISSING_S = Pattern.compile(
            "\\b(he|she|it)\\s+(work|study|live|like|want|need|play|use|make|go|do|have|speak|read|write|run|return|send|show|don't|dont)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern HAVE_A_DOUBT = Pattern.compile("\\b(have|has|had)\\s+(a\\s+)?doubt(s)?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DEPEND_OF = Pattern.compile("\\bdepend(s|ed|ing)?\\s+of\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXPLAIN_ME = Pattern.compile("\\bexplain(ed|ing|s)?\\s+(me|him|her|us|them)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern MAKE_A_QUESTION = Pattern.compile("\\b(make|made|making)\\s+(a\\s+)?question(s)?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern UNCOUNTABLE_PLURAL = Pattern.compile(
            "\\b(informations|feedbacks|advices|knowledges|softwares|equipments|homeworks)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PRETEND_TO = Pattern.compile("\\b(i|we|they|you|he|she)\\s+pretend(s)?\\s+to\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern LISTEN_NO_TO = Pattern.compile("\\blisten(ed|ing|s)?\\s+(music|podcast|me|him|her|the|a|this|that)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern GO_TO_HOME = Pattern.compile("\\b(go|went|going|goes|come|came)\\s+to\\s+home\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DOUBLE_PAST_OR_NEGATION = Pattern.compile(
            "\\b(didn't|didnt|did\\s+not|did)\\s+(went|worked|studied|made|saw|had|wrote|bought|came|took|played|finished|tested)\\b"
                    + "|\\b(doesn't|doesnt|does\\s+not)\\s+(works|studies|goes|has|likes|needs|wants)\\b"
                    + "|\\b(don't|dont|do\\s+not)\\s+have\\s+nothing\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern FOR_PLUS_INFINITIVE = Pattern.compile(
            "\\bfor\\s+(learn|study|work|understand|improve|fix|test|build|practice|speak|read|write|to\\s+[a-z]+)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern MISSING_SUBJECT_IT_THERE = Pattern.compile(
            "(^|[.!?\\n:]\\s*)(is\\s+(very|so|really|possible|important|good|bad|easy|hard|necessary)|have\\s+(many|a\\s+lot\\s+of|some|two|three|\\d+)\\s+[a-z]+)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern MUCH_MANY_SWAP = Pattern.compile(
            "\\b(much\\s+(people|friends|bugs|errors|questions|tasks|students|books|hours|days)|many\\s+(information|time|money|work|water|advice|feedback))\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern I_AM_AGREE = Pattern.compile("\\b(i\\s+am|i'm|you\\s+are|he\\s+is|she\\s+is|we\\s+are|they\\s+are)\\s+(agree|disagree)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern IN_THE_API_OR_DAY = Pattern.compile(
            "\\bin\\s+(the\\s+api|the\\s+internet|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static Map<String, String> buildIrregularPastForms() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("go", "went");
        map.put("do", "did");
        map.put("see", "saw");
        map.put("make", "made");
        map.put("have", "had");
        map.put("write", "wrote");
        map.put("buy", "bought");
        map.put("come", "came");
        map.put("take", "took");
        map.put("give", "gave");
        map.put("speak", "spoke");
        map.put("get", "got");
        map.put("find", "found");
        map.put("think", "thought");
        map.put("know", "knew");
        map.put("run", "ran");
        map.put("build", "built");
        map.put("send", "sent");
        return Map.copyOf(map);
    }

    public PraticaAtivaSkill() {
        super(new SkillDefinition(
                "pratica-ativa",
                "Prática Ativa & Correção",
                "Exercício",
                "Propõe micro-exercícios e revisa frases escritas pelo estudante com motor contrastivo Português→Inglês (18+ padrões).",
                "Validar primeiro o que o aluno acertou, apontar ajustes específicos e apresentar a forma natural em inglês.",
                "ODS 4 (Aprendizagem ativa e autonomia)",
                "Corrija minha frase de daily: 'Yesterday I work in the API and have 20 years old.'"
        ));
    }

    @Override
    public HybridLlmClient.GenerationOptions generationOptions() {
        return HybridLlmClient.GenerationOptions.of(0.55, 0.92);
    }

    @Override
    protected String buildFewShotExamples(String level, boolean lowDataMode) {
        return switch (level) {
            case "A1" -> """
                    Entrada: "Corrija: I have 19 years and study TI."
                    Resposta ideal:
                    - **O que acertou:** A apresentação está super clara!
                    - **O que ajustar:** Em inglês, idade usa **to be** (*I am 19 years old*), e TI se diz **IT** (*Information Technology*).
                    - **Forma natural:** "I am 19 years old and I study IT."
                    Agora tente trocar a idade ou o curso e escrever uma nova frase!
                    """;
            case "B1" -> """
                    Entrada: "Corrija: I have a doubt, it depend of the API."
                    Resposta ideal:
                    - **O que acertou:** Você expressou a condição técnica com objetividade.
                    - **O que ajustar:** Para "tenho uma dúvida", use **have a question**; com **it**, o verbo recebe **-s** e pede **on** (*depends on*).
                    - **Forma natural:** "I have a question: does it depend on the API?"
                    Experimente criar outra pergunta técnica usando **depends on**!
                    """;
            case "B2" -> """
                    Entrada: "Corrija: Yesterday I didn't went to the review because I pretend to finish the deploy."
                    Resposta ideal:
                    - **O que acertou:** Boa conexão de causa e efeito usando *because*.
                    - **O que ajustar:** Após **didn't**, o verbo volta à forma base (*didn't go*); e **pretend** significa *fingir* — para "pretendia", use **intended to** / **planned to**.
                    - **Forma natural:** "Yesterday I didn't go to the review because I planned to finish the deploy."
                    Reescreva agora contando um ajuste real da sua última sprint!
                    """;
            default -> """
                    Entrada: "Corrija: Yesterday I work in the API."
                    Resposta ideal:
                    - **O que acertou:** Estrutura de daily bem objetiva!
                    - **O que ajustar:** Com **Yesterday**, use o passado **worked**, e para projetos/APIs prefira a preposição **on**.
                    - **Forma natural:** "Yesterday I worked on the API."
                    Tente agora adicionar o que você vai fazer hoje (*Today I will...*)!
                    """;
        };
    }

    @Override
    public int matchScore(ChatRequest request) {
        int score = scoreKeywords(
                request.message(),
                "exercicio", "pratica", "praticar", "corrija", "corrigir", "revisa", "revisar",
                "minha frase", "daily", "entrevista", "simular", "simulacao", "desafio", "teste",
                "esta certo", "esta correto", "yesterday", "i have"
        );
        GrammarAnalysis analysis = analyzeBrazilianPatterns(request.message());
        if (!analysis.issues().isEmpty()) {
            score += Math.min(30, analysis.issues().size() * 15);
        }
        return score;
    }

    @Override
    public String buildSystemPrompt(ChatRequest request, List<KnowledgeChunk> chunks) {
        String base = super.buildSystemPrompt(request, chunks);
        return base + "\nDIRETRIZ DE FEEDBACK ESTRUTURADO (PRÁTICA ATIVA):\n"
                + "Sempre que revisar ou corrigir uma frase do aluno, estruture o retorno nestas 3 partes:\n"
                + "- **O que acertou:** valorize a comunicação e acertos reais.\n"
                + "- **O que ajustar:** explique de forma clara e contrastiva (PT → EN) o porquê de cada ajuste.\n"
                + "- **Forma natural:** apresente a frase reescrita naturalmente em inglês entre aspas duplas.\n";
    }

    @Override
    public String buildLocalResponse(ChatRequest request, List<KnowledgeChunk> chunks) {
        StringBuilder feedback = new StringBuilder();
        GrammarAnalysis analysis = analyzeBrazilianPatterns(request.message());

        feedback.append("Modo **Prática Ativa** ativado! Vamos revisar com foco contrastivo (Português → Inglês):\n\n");

        if (!analysis.issues().isEmpty()) {
            feedback.append("- **O que acertou:** ").append(analysis.whatWasGood()).append("\n");
            feedback.append("- **O que ajustar:**\n");
            for (GrammarIssue issue : analysis.issues()) {
                feedback.append("  - **").append(issue.title()).append(":** ").append(issue.explanation()).append("\n");
            }
            feedback.append("- **Forma natural:** *\"").append(analysis.naturalSuggestion()).append("\"*\n");
        } else {
            feedback.append("Envie uma frase curta sua em inglês (por exemplo, sobre sua daily, um erro que enfrentou ou sua rotina de estudos) para eu analisar com feedback estruturado (**O que acertou · O que ajustar · Forma natural**).\n");
        }

        return formatGroundedLocalReply(
                request,
                chunks,
                feedback.toString().trim(),
                "Leia a **Forma natural** em voz alta usando o botão 'EN' para fixar ritmo e pronúncia!"
        );
    }

    @Override
    public String buildPracticePrompt(ChatRequest request, List<KnowledgeChunk> chunks) {
        GrammarAnalysis analysis = analyzeBrazilianPatterns(request.message());
        if (!analysis.issues().isEmpty()) {
            return "Agora crie uma nova frase curta em inglês aplicando o ajuste acima (ex.: \"" + analysis.naturalSuggestion() + "\").";
        }
        return "Reescreva agora em inglês respondendo às 2 primeiras perguntas de uma daily: 'What did you do yesterday?' e 'What will you do today?'";
    }

    /**
     * Analisador contrastivo Português → Inglês cobrindo 18+ erros clássicos de estudantes brasileiros.
     */
    public static GrammarAnalysis analyzeBrazilianPatterns(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return new GrammarAnalysis(List.of(), "", "");
        }

        String msg = rawMessage.trim();
        String norm = Bm25RagRetriever.normalizeText(msg);
        List<GrammarIssue> issues = new ArrayList<>();
        String corrected = extractCandidateSentence(msg);

        // 1. Idade com "have" ("I have 20 years")
        Matcher ageMatcher = AGE_HAVE_PATTERN.matcher(msg);
        if (ageMatcher.find()) {
            String subj = ageMatcher.group(1);
            String num = ageMatcher.group(3);
            String be = toBeForSubject(subj);
            issues.add(new GrammarIssue(
                    "Idade com verbo 'to be'",
                    "Diferente do português (*ter X anos*), em inglês usamos **to be**: *\"" + capitalize(subj) + " " + be + " " + num + " years old\"* (não *\"have " + num + " years\"*)."
            ));
            corrected = AGE_HAVE_PATTERN.matcher(corrected).replaceFirst(subj + " " + be + " " + num + " years old");
        } else if (norm.contains("have") && norm.contains("year")) {
            issues.add(new GrammarIssue(
                    "Idade com verbo 'to be'",
                    "Em inglês, idade usa o verbo **to be**: *\"I am 20 years old\"* (não *\"I have 20 years\"*)."
            ));
            corrected = corrected.replaceAll("(?i)\\bhave\\s+(\\d{1,2})\\s+years?(\\s+old)?\\b", "am $1 years old");
        }

        // 2 & 3. Passado regular (-ed) e irregular com marcador de passado ("Yesterday I work" / "Yesterday I go")
        boolean hasPastMarker = PAST_MARKER_PATTERN.matcher(msg).find();
        if (hasPastMarker) {
            String pastClause = extractPastClause(corrected);
            List<String> regFixes = new ArrayList<>();
            Matcher regPast = REGULAR_PAST_TRAP.matcher(pastClause);
            while (regPast.find()) {
                String verb = regPast.group(1).toLowerCase();
                if (isPrecededByInfinitiveOrAux(pastClause, regPast.start())) {
                    continue;
                }
                String pastForm = REGULAR_PAST_FORMS.get(verb);
                if (pastForm != null && !regFixes.contains(verb)) {
                    regFixes.add(verb);
                    issues.add(new GrammarIssue(
                            "Passado simples regular (-ed)",
                            "Com marcador de passado concluído (*yesterday / last week*), o verbo regular **`" + verb + "`** vai para **`" + pastForm + "`**."
                    ));
                    corrected = replaceVerbOutsideInfinitiveOrAux(corrected, verb, pastForm);
                }
            }
            List<String> irregFixes = new ArrayList<>();
            Matcher irregPast = IRREGULAR_PAST_TRAP.matcher(pastClause);
            while (irregPast.find()) {
                String verb = irregPast.group(1).toLowerCase();
                if (isPrecededByInfinitiveOrAux(pastClause, irregPast.start())) {
                    continue;
                }
                // Não confunde "have 20 years" já tratado acima
                boolean isAgeHave = "have".equals(verb) && norm.contains("year");
                String pastForm = IRREGULAR_PAST_FORMS.get(verb);
                if (!isAgeHave && pastForm != null && !irregFixes.contains(verb)) {
                    irregFixes.add(verb);
                    issues.add(new GrammarIssue(
                            "Passado simples irregular",
                            "Com marcador de passado (*yesterday / last week*), **`" + verb + "`** é irregular e vira **`" + pastForm + "`** (ex.: *\"Yesterday I " + pastForm + "...\"*)."
                    ));
                    corrected = replaceVerbOutsideInfinitiveOrAux(corrected, verb, pastForm);
                }
            }
        }

        // 4. 3ª pessoa do singular no Presente Simples sem -s ("he work", "she study", "it don't")
        if (!hasPastMarker) {
            Matcher thirdPerson = THIRD_PERSON_MISSING_S.matcher(corrected);
            if (thirdPerson.find()) {
                String subj = thirdPerson.group(1);
                String verb = thirdPerson.group(2).toLowerCase();
                String fixedVerb = conjugateThirdPerson(verb);
                issues.add(new GrammarIssue(
                        "3ª pessoa do singular (-s / doesn't)",
                        "Com **`he / she / it`** no presente simples, o verbo recebe **`-s/-es`** (ou **`doesn't`** na negativa): troque *\"" + subj + " " + verb + "\"* por **\"" + subj + " " + fixedVerb + "\"**."
                ));
                corrected = THIRD_PERSON_MISSING_S.matcher(corrected).replaceFirst(subj + " " + fixedVerb);
            }
        }

        // 5. "I have a doubt" -> "I have a question"
        if (HAVE_A_DOUBT.matcher(msg).find()) {
            issues.add(new GrammarIssue(
                    "'Have a doubt' vs. 'Have a question'",
                    "Em inglês, *doubt* indica desconfiança/ceticismo. Para \"tenho uma dúvida\", diga **\"I have a question\"**."
            ));
            corrected = HAVE_A_DOUBT.matcher(corrected).replaceAll("$1 a question");
        }

        // 6. "depend of" -> "depend on"
        if (DEPEND_OF.matcher(msg).find()) {
            issues.add(new GrammarIssue(
                    "Preposição de 'depend'",
                    "Em português dizemos \"depende de\", mas em inglês o verbo **depend** exige **`on`**: **\"depends on\"** / **\"depend on\"**."
            ));
            corrected = DEPEND_OF.matcher(corrected).replaceAll("depend$1 on");
        }

        // 7. "explain me" -> "explain to me"
        Matcher explainMe = EXPLAIN_ME.matcher(msg);
        if (explainMe.find()) {
            String obj = explainMe.group(2);
            issues.add(new GrammarIssue(
                    "'Explain to me'",
                    "Em inglês, o verbo **explain** precisa da preposição **`to`** antes da pessoa: **\"explain to " + obj + "\"** (não *\"explain " + obj + "\"*)."
            ));
            corrected = EXPLAIN_ME.matcher(corrected).replaceAll("explain$1 to $2");
        }

        // 8. "make a question" -> "ask a question"
        if (MAKE_A_QUESTION.matcher(msg).find()) {
            issues.add(new GrammarIssue(
                    "'Ask a question' (collocation)",
                    "Em inglês não se diz *\"make a question\"*; a combinação natural é **\"ask a question\"**."
            ));
            corrected = fixMakeQuestion(corrected);
        }

        // 9. Substantivos incontáveis no plural ("informations", "feedbacks", "advices"...)
        Matcher uncountable = UNCOUNTABLE_PLURAL.matcher(msg);
        if (uncountable.find()) {
            String wrongWord = uncountable.group(1);
            String singular = wrongWord.substring(0, wrongWord.length() - 1);
            issues.add(new GrammarIssue(
                    "Substantivo incontável",
                    "**`" + singular + "`** é incontável em inglês e não leva `-s`: use **\"" + singular + "\"** (ou *\"some " + singular + "\"*)."
            ));
            corrected = corrected.replaceAll("(?i)\\b" + Pattern.quote(wrongWord) + "\\b", singular);
        }

        // 10. Falso cognato "actually" usado como "atualmente"
        if (norm.contains("actually") && (norm.contains("hoje") || norm.contains("agora") || norm.contains("atual"))) {
            issues.add(new GrammarIssue(
                    "Falso cognato: 'actually' vs. 'currently'",
                    "**`actually`** significa **na verdade**; para \"atualmente\", prefira **`currently`** ou **`nowadays`**."
            ));
            corrected = corrected.replaceAll("(?i)\\bactually\\b", "currently");
        }

        // 11. Falso cognato "pretend to" usado como "pretender"
        if (PRETEND_TO.matcher(msg).find()) {
            issues.add(new GrammarIssue(
                    "Falso cognato: 'pretend' vs. 'intend / plan'",
                    "**`pretend`** significa **fingir**! Para dizer \"eu pretendo\", use **\"I intend to...\"** ou **\"I plan to...\"**."
            ));
            corrected = fixPretendTo(corrected);
        }

        // 12. "listen music" / "listen me" -> "listen to ..."
        if (LISTEN_NO_TO.matcher(msg).find()) {
            issues.add(new GrammarIssue(
                    "Preposição 'listen to'",
                    "O verbo **listen** sempre pede **`to`** antes do que você ouve: **\"listen to music\"** / **\"listen to me\"**."
            ));
            corrected = LISTEN_NO_TO.matcher(corrected).replaceAll("listen$1 to $2");
        }

        // 13. "go to home" -> "go home"
        if (GO_TO_HOME.matcher(msg).find()) {
            issues.add(new GrammarIssue(
                    "'Go home' sem preposição 'to'",
                    "Com **`home`** após verbos de movimento (*go / come*), não usamos `to`: diga **\"go home\"** / **\"went home\"**."
            ));
            corrected = GO_TO_HOME.matcher(corrected).replaceAll("$1 home");
        }

        // 14. Auxiliar + verbo conjugado / dupla negação ("didn't went", "did worked", "don't have nothing")
        if (DOUBLE_PAST_OR_NEGATION.matcher(msg).find()) {
            issues.add(new GrammarIssue(
                    "Auxiliar + verbo base / dupla negação",
                    "Quando já usamos **`did / didn't / doesn't`**, o verbo principal volta à forma base (*\"I didn't go\"*, *\"I didn't work\"*). E na negativa evite dupla negação: use **\"I don't have anything\"**."
            ));
            corrected = fixDoublePastOrNegation(corrected);
        }

        // 15. "for + infinitivo" ("for learn", "for to work")
        if (FOR_PLUS_INFINITIVE.matcher(msg).find()) {
            issues.add(new GrammarIssue(
                    "Finalidade com 'to + verbo'",
                    "Para indicar objetivo/finalidade antes de um verbo, use **`to`** (não `for`): **\"to learn\"**, **\"to work\"**, **\"to understand\"**."
            ));
            corrected = corrected
                    .replaceAll("(?i)\\bfor\\s+to\\s+", "to ")
                    .replaceAll("(?i)\\bfor\\s+(learn|study|work|understand|improve|fix|test|build|practice|speak|read|write)\\b", "to $1");
        }

        // 16. Omissão de sujeito ("Is very good" / "Have many bugs")
        if (MISSING_SUBJECT_IT_THERE.matcher(msg).find()) {
            issues.add(new GrammarIssue(
                    "Sujeito obrigatório ('It is' / 'There are')",
                    "Em inglês, a frase precisa de sujeito explícito: use **\"It is very good\"** (não *\"Is very good\"*) e **\"There are many bugs\"** para \"tem/existem\" (não *\"Have many bugs\"*)."
            ));
            corrected = corrected
                    .replaceAll("(?i)(^|[.!?\\n:]\\s*)is\\s+(?=(?:very|so|really|possible|important|good|bad|easy|hard|necessary)\\b)", "$1It is ")
                    .replaceAll("(?i)(^|[.!?\\n:]\\s*)have\\s+(many|a\\s+lot\\s+of|some|two|three|\\d+)\\b", "$1There are $2");
        }

        // 17. "much" vs "many"
        if (MUCH_MANY_SWAP.matcher(msg).find()) {
            issues.add(new GrammarIssue(
                    "'Many' (contável) vs. 'Much' (incontável)",
                    "Use **`many`** para itens contáveis (*many people, many bugs*) e **`much`** para incontáveis (*much time, much information*)."
            ));
            corrected = corrected
                    .replaceAll("(?i)\\bmuch\\s+(people|friends|bugs|errors|questions|tasks|students|books|hours|days)\\b", "many $1")
                    .replaceAll("(?i)\\bmany\\s+(information|time|money|work|water|advice|feedback)\\b", "much $1");
        }

        // 18. "I am agree" -> "I agree"
        if (I_AM_AGREE.matcher(msg).find()) {
            issues.add(new GrammarIssue(
                    "'I agree' (sem verbo 'to be')",
                    "**`agree`** já é um verbo em inglês (não adjetivo). Diga **\"I agree\"** ou **\"I disagree\"** (sem *am/is/are*)."
            ));
            corrected = fixBeAgree(corrected);
        }

        // 19. "in the API / in the internet / in Monday" -> "on ..."
        if (IN_THE_API_OR_DAY.matcher(msg).find()) {
            issues.add(new GrammarIssue(
                    "Preposição 'on' (projetos, internet e dias)",
                    "Para trabalhar em um projeto/API, internet ou dias da semana, prefira **`on`**: **\"worked on the API\"**, **\"on the internet\"**, **\"on Monday\"**."
            ));
            corrected = IN_THE_API_OR_DAY.matcher(corrected).replaceAll("on $1");
        }

        String whatWasGood = "Sua ideia ficou clara e compreensível em inglês — esse é o passo mais importante para ganhar fluência!";
        String natural = cleanNaturalSentence(corrected, issues);
        return new GrammarAnalysis(List.copyOf(issues), whatWasGood, natural);
    }

    private static String extractPastClause(String sentence) {
        Matcher futureSplit = Pattern.compile("(?i)\\b(today|tomorrow|now|next\\s+\\w+)\\b").matcher(sentence);
        if (futureSplit.find() && futureSplit.start() > 0) {
            return sentence.substring(0, futureSplit.start());
        }
        return sentence;
    }

    private static boolean isPrecededByInfinitiveOrAux(String text, int verbStart) {
        String prefix = text.substring(0, verbStart);
        return Pattern.compile("(?i)\\b(to|will|would|can|could|should|must|may|might|did|didn't|didnt|did\\s+not|don't|dont|do\\s+not|doesn't|doesnt|does\\s+not)\\s+$")
                .matcher(prefix)
                .find();
    }

    private static String replaceVerbOutsideInfinitiveOrAux(String sentence, String verb, String pastForm) {
        String pastClause = extractPastClause(sentence);
        String tail = sentence.substring(pastClause.length());
        Matcher m = Pattern.compile("(?i)\\b" + Pattern.quote(verb) + "\\b").matcher(pastClause);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            if (isPrecededByInfinitiveOrAux(pastClause, m.start())) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group()));
            } else {
                m.appendReplacement(sb, Matcher.quoteReplacement(pastForm));
            }
        }
        m.appendTail(sb);
        return sb.append(tail).toString();
    }

    private static String fixMakeQuestion(String sentence) {
        Matcher m = MAKE_A_QUESTION.matcher(sentence);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String makeForm = m.group(1).toLowerCase();
            String askForm = switch (makeForm) {
                case "made" -> "asked";
                case "making" -> "asking";
                default -> "ask";
            };
            String article = m.group(2) != null ? m.group(2) : "";
            String plural = m.group(3) != null ? m.group(3) : "";
            m.appendReplacement(sb, Matcher.quoteReplacement(askForm + " " + article + "question" + plural));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String fixPretendTo(String sentence) {
        Matcher m = PRETEND_TO.matcher(sentence);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String subj = m.group(1);
            boolean thirdPerson = "he".equalsIgnoreCase(subj) || "she".equalsIgnoreCase(subj);
            String planVerb = thirdPerson ? "plans" : "plan";
            m.appendReplacement(sb, Matcher.quoteReplacement(subj + " " + planVerb + " to"));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String fixDoublePastOrNegation(String sentence) {
        Map<String, String> pastToBase = Map.ofEntries(
                Map.entry("went", "go"),
                Map.entry("worked", "work"),
                Map.entry("studied", "study"),
                Map.entry("made", "make"),
                Map.entry("saw", "see"),
                Map.entry("had", "have"),
                Map.entry("wrote", "write"),
                Map.entry("bought", "buy"),
                Map.entry("came", "come"),
                Map.entry("took", "take"),
                Map.entry("played", "play"),
                Map.entry("finished", "finish"),
                Map.entry("tested", "test"),
                Map.entry("works", "work"),
                Map.entry("studies", "study"),
                Map.entry("goes", "go"),
                Map.entry("has", "have"),
                Map.entry("likes", "like"),
                Map.entry("needs", "need"),
                Map.entry("wants", "want")
        );
        String updated = sentence.replaceAll("(?i)\\b(don't|dont|do\\s+not)\\s+have\\s+nothing\\b", "$1 have anything");
        Matcher auxMatcher = Pattern.compile(
                "(?i)\\b(didn't|didnt|did\\s+not|did|doesn't|doesnt|does\\s+not)\\s+([a-z]+)\\b"
        ).matcher(updated);
        StringBuilder sb = new StringBuilder();
        while (auxMatcher.find()) {
            String aux = auxMatcher.group(1);
            String verb = auxMatcher.group(2).toLowerCase();
            String base = pastToBase.get(verb);
            if (base == null) {
                auxMatcher.appendReplacement(sb, Matcher.quoteReplacement(auxMatcher.group()));
            } else if ("did".equalsIgnoreCase(aux)) {
                auxMatcher.appendReplacement(sb, Matcher.quoteReplacement(verb));
            } else {
                auxMatcher.appendReplacement(sb, Matcher.quoteReplacement(aux + " " + base));
            }
        }
        auxMatcher.appendTail(sb);
        return sb.toString();
    }

    private static String fixBeAgree(String sentence) {
        Matcher m = I_AM_AGREE.matcher(sentence);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String rawSubjBe = m.group(1).toLowerCase();
            String verb = m.group(2).toLowerCase();
            String subj;
            boolean thirdPerson = false;
            if (rawSubjBe.startsWith("i")) {
                subj = "I";
            } else if (rawSubjBe.startsWith("you")) {
                subj = "you";
            } else if (rawSubjBe.startsWith("he")) {
                subj = "he";
                thirdPerson = true;
            } else if (rawSubjBe.startsWith("she")) {
                subj = "she";
                thirdPerson = true;
            } else if (rawSubjBe.startsWith("we")) {
                subj = "we";
            } else {
                subj = "they";
            }
            if (Character.isUpperCase(m.group(1).charAt(0)) && !"I".equals(subj)) {
                subj = capitalize(subj);
            }
            String fixedVerb = thirdPerson ? verb + "s" : verb;
            m.appendReplacement(sb, Matcher.quoteReplacement(subj + " " + fixedVerb));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String extractCandidateSentence(String msg) {
        Matcher quoted = Pattern.compile("\"([^\"]{4,200})\"|'([^']{4,200})'").matcher(msg);
        if (quoted.find()) {
            return quoted.group(1) != null ? quoted.group(1).trim() : quoted.group(2).trim();
        }
        int colon = msg.indexOf(':');
        if (colon > 0 && colon < msg.length() - 4) {
            String after = msg.substring(colon + 1).trim();
            if (!after.isBlank()) {
                return after;
            }
        }
        return msg;
    }

    private static String cleanNaturalSentence(String candidate, List<GrammarIssue> issues) {
        if (issues.isEmpty()) {
            return candidate;
        }
        String clean = candidate.replaceAll("^[\"'`]+|[\"'`]+$", "").replaceAll("\\s+", " ").trim();
        if (clean.isEmpty()) {
            return "Yesterday I worked on the API, and I am 20 years old.";
        }
        // Garante inicial maiúscula e pontuação final
        clean = Character.toUpperCase(clean.charAt(0)) + clean.substring(1);
        if (!clean.endsWith(".") && !clean.endsWith("!") && !clean.endsWith("?")) {
            clean = clean + ".";
        }
        return clean;
    }

    private static String toBeForSubject(String subj) {
        String s = subj.toLowerCase();
        if ("i".equals(s)) {
            return "am";
        }
        if ("he".equals(s) || "she".equals(s) || "it".equals(s)) {
            return "is";
        }
        return "are";
    }

    private static String conjugateThirdPerson(String verb) {
        return switch (verb) {
            case "have" -> "has";
            case "go" -> "goes";
            case "do" -> "does";
            case "study" -> "studies";
            case "don't", "dont" -> "doesn't";
            default -> verb + "s";
        };
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1).toLowerCase();
    }

    public record GrammarIssue(String title, String explanation) {
    }

    public record GrammarAnalysis(List<GrammarIssue> issues, String whatWasGood, String naturalSuggestion) {
    }
}
