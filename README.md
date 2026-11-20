# Dinga AI — Tutor de Idiomas com Skills, RAG e MTG-IA

MVP em **Java 25 + Spring Boot 4** construído para a entrega da Etapa 4 do projeto **Educação de Idiomas** (Caso 3 — *Educação personalizada com IA na plataforma Microsoft Learn*).

- **Documentação completa em `AiContext/`:**
  - [Contexto do Projeto (`contexto.md`)](AiContext/contexto.md) — visão holística, problema social, histórico DreamShaper, 5 Skills, base RAG curada, MTG-IA, ODS 4/10/16 e análise crítica.
  - [Arquitetura Técnica (`arquitetura.md`)](AiContext/arquitetura.md) — stack Java 25 + Spring Boot 4.1.1, diagrama de fluxo em 5 etapas, estrutura de pacotes, fórmula BM25, cliente LLM híbrido e contratos REST.
  - [Extração DreamShaper (`DREAMSHAPER.md`)](AiContext/DREAMSHAPER.md) — registro integral da trilha acadêmica e entregas anteriores.
  - [Apresentação de Slides (`slides/`)](AiContext/slides/APRESENTACAO_SLIDES.md) — deck interativo 16:9 (`/slides`), PDF e roteiro de apresentação.

## O que o protótipo demonstra na prática

- **5 Skills Modulares (`SkillRouter`):**
  1. **Diagnóstico & Trilha (`diagnostico-perfil`):** nivelamento A1–B2 e roteiro sem criar bolhas de aprendizado.
  2. **Tutor Socrático (`tutor-socratico`):** explica o porquê da gramática/vocabulário sem dar cola pronta.
  3. **Inglês Técnico & Learn (`ingles-tecnico`):** destrava termos de IA, Cloud, APIs, Git e Microsoft Learn com analogias acessíveis.
  4. **Prática Ativa & Correção (`pratica-ativa`):** feedback construtivo em frases de *daily*, entrevista e escrita.
  5. **Guardião Ético MTG-IA (`guardiao-etico-mtg`):** explica governança ética, LGPD, EU AI Act, UNESCO e ODS 4, 10 e 16.
- **Mini-Teste de Nivelamento CEFR (4 questões rápidas):** botão discreto *"Descobrir meu nível"* (`A1 → B2`) que define automaticamente o nível CEFR do estudante e recomenda a primeira trilha de estudo.
- **Streaming de Resposta no Chat (SSE — `/api/chat/stream`):** exibe a resposta do **Gemini 3.8 Flash** progressivamente via *Server-Sent Events*, com fallback transparente e rápido no modo **RAG Local**.
- **Simulador Guiado Multi-Turno (`/api/simulator/*`):** prática estruturada de **Daily Standup** (3 etapas) e **Entrevista Técnica** (4 etapas) com avaliação por turno (Clareza, Gramática, Vocabulário Técnico) e mini-boletim final.
- **Quiz Adaptativo & Gerador Sob Demanda (`/api/quiz/*`):** banco curado + geração de novas questões ancoradas no RAG via **Gemini 3.8 Flash** (com fallback determinístico local).
- **Caderno de Vocabulário com Flashcards SRS:** sistema de repetição espaçada (4 *boxes* Leitner) no navegador, **busca rápida** instantânea por termo e **exportação Anki (`.csv`)**.
- **Motor RAG real em Java puro (`Bm25RagRetriever`) + Painel Docente:** carrega 10 documentos curados, suporta cadastro manual e **upload de arquivos `.md`/`.txt`** (`/api/knowledge/upload`) indexados ao vivo no BM25 com persistência local em JSON.
- **Modo Híbrido com Cache LLM (`HybridLlmClient`):**
  - **Sem chave de API:** roda imediatamente em **RAG Local (Modo Baixa Conectividade)** — custo zero e sem limite de cota.
  - **Com chave opcional (`GEMINI_API_KEY` com padrão `gemini-3.8-flash`, `GROQ_API_KEY`, `OPENAI_API_KEY` ou `OLLAMA_URL`):** gera respostas conversacionais ancoradas no RAG, usa **cache leve LRU + TTL** para evitar chamadas duplicadas idênticas na mesma sessão e cai automaticamente para o modo local se a cota acabar.
- **TTS Híbrido + Acessibilidade + Atalhos:** leitura em voz alta híbrida (`/api/tts` online + `eSpeak-NG` offline + fallback `Web Speech API`, velocidade `1.0x`/`0.75x`), **Modo Leve**, **Alto Contraste**, ajuste de fonte (`A- / A+`), atalhos de teclado (`1`–`4` no Quiz, `Espaço` no Flashcard, `Esc` para áudio, `Ctrl+K` para focar chat), limpeza LGPD e **Boletim Completo da Sessão** (`.md` e versão imprimível 100% lado do cliente).
- **Governança MTG-IA, Segurança e Observabilidade:** guardrails ativos de **LGPD** (mascaramento automático de CPF, e-mail e telefone) e **Anti-Plágio**, `watermarkId` único por resposta, canal de auditoria (`/api/feedback`), **Métricas da Sessão** (`/api/metrics`), **Rate Limiting endurecido** (eviction automática no `ConcurrentHashMap` + validação segura de `X-Forwarded-For`) e documentação interativa **OpenAPI / Swagger UI** (`/swagger-ui.html`).
- **Qualidade Verificada:** **38 testes automatizados** (`JUnit 5` + `SpringBootTest` + `MockMvc` em `DingaAiTests` e `DingaBoostImprovementsTests`).

---

## Como rodar localmente

### Pré-requisitos
- **JDK 25** (nesta máquina, o JDK completo está em `~/.jdks/temurin-25.0.4.1`, pois `/bin/java` tem apenas JRE).
- O projeto já inclui o Maven Wrapper (`mvnw`).

### Modo padrão (Sem API Key — RAG Local)

```bash
export JAVA_HOME="$HOME/.jdks/temurin-25.0.4.1"
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw spring-boot:run
```

Depois acesse no navegador: **http://localhost:8080**

*(No IntelliJ IDEA, basta abrir o projeto pelo `pom.xml`, confirmar o SDK `temurin-25.0.4.1` e executar `DingaAiApplication`.)*

### Modo Generativo opcional (com chave gratuita Gemini ou Groq)

```bash
export JAVA_HOME="$HOME/.jdks/temurin-25.0.4.1"
export PATH="$JAVA_HOME/bin:$PATH"
export GEMINI_API_KEY="sua-chave-aqui"
./mvnw spring-boot:run
```

Ou usando Groq (`GROQ_API_KEY`), OpenAI-compatível (`OPENAI_API_KEY`), ou Ollama local (`OLLAMA_URL=http://localhost:11434`).

### Executar os testes

```bash
JAVA_HOME="$HOME/.jdks/temurin-25.0.4.1" PATH="$HOME/.jdks/temurin-25.0.4.1/bin:$PATH" ./mvnw test
```

---

## Deploy gratuito via Docker (Render / Railway)

O projeto já inclui [Dockerfile](Dockerfile) pronto para nuvem (porta configurável via variável `PORT`):

1. Suba esta pasta para um repositório no GitHub.
2. No **Render** (Web Service → Environment: **Docker**), conecte o repositório.
3. *(Opcional)* Adicione `GEMINI_API_KEY` ou `GROQ_API_KEY` nas variáveis de ambiente — sem ela, o app funciona normalmente em **RAG Local**.
4. Use o link público gerado para anexar na etapa **Entrega** do DreamShaper.

---

## Roteiro rápido de apresentação (3–5 minutos)

1. **Abertura (30s):** mostrar o problema do Caso 3 (barreira do inglês na área de tecnologia + riscos de alucinação e exclusão digital) e o nome **Dinga AI**.
2. **Demo ao vivo (2 min):**
   - Clicar em um prompt rápido de **Inglês Técnico** (*grounding/RAG no Microsoft Learn*) e expandir **"Fontes curadas consultadas (RAG)"** para mostrar o BM25 citando a fonte exata.
   - Clicar no prompt de **Prática Ativa** (*daily standup / idade em inglês*) para mostrar correção pedagógica + botão **Ouvir resposta**.
   - Ativar **Modo Leve** e **Alto Contraste** no topo para provar acessibilidade em dispositivos simples / baixa conexão.
3. **Governança MTG-IA & ODS (1 min):**
   - Mostrar o `watermarkId`, clicar em **"Reportar erro/viés"** e abrir a aba **"Skills & Base Curada"** exibindo o registro de auditoria.
   - Abrir a aba **"Arquitetura, ODS & Crítica"** para apresentar o diagrama, ODS 4/10/16 e a análise crítica honesta das limitações.
