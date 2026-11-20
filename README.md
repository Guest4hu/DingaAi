# Dinga AI — Tutor Inteligente de Inglês Técnico e Cotidiano

**Dinga AI** é uma plataforma web de ensino personalizado de inglês — construída em **Java 25** e **Spring Boot 4** — projetada para ajudar estudantes e profissionais de língua portuguesa a vencer a barreira do idioma no estudo de tecnologia, leitura de documentação técnica e comunicação profissional.

---

## Propósito e Intuito do Projeto

Aprender inglês voltado para tecnologia e carreira costuma esbarrar em três problemas recorrentes:

1. **Barreira de acesso e conectividade:** soluções tradicionais dependem de assinaturas caras ou conexões estáveis em tempo integral.
2. **IA genérica e pouco pedagógica:** chatbots comuns frequentemente entregam respostas prontas (desestimulando o raciocínio), misturam níveis de dificuldade ou alucinam regras e termos técnicos.
3. **Falta de contexto real:** cursos genéricos raramente trabalham situações reais do dia a dia de quem estuda e trabalha com software — como ler documentação técnica, interpretar logs/erros, participar de uma *Daily Standup* ou fazer *Code Review*.

O intuito da **Dinga AI** é oferecer um **tutor socrático, transparente e acessível** que:
- **Ensina com autonomia:** explica o *porquê* da gramática e do vocabulário, corrige erros típicos de brasileiros (*falsos cognatos*, tempos verbais, estruturas literais) e propõe micro-desafios práticos em vez de dar respostas prontas.
- **Ancora respostas em conhecimento curado:** utiliza **RAG Híbrido** para fundamentar explicações em uma base didática verificável, reduzindo alucinações e exibindo sempre as fontes consultadas.
- **Funciona com ou sem chave de API:** integra-se ao **Gemini 3.8 Flash** (além de Groq, OpenAI-compatível e Ollama) com streaming em tempo real, mas mantém um modo **100% local/offline** com baixa latência e consumo reduzido de dados.
- **Respeita privacidade e transparência por design:** aplica proteção ativa contra dados pessoais (LGPD) na entrada e na saída, calcula índice de ancoragem (*Groundedness*) em cada resposta e mantém o progresso do estudante exclusivamente no navegador.

---

## Principais Funcionalidades

### Tutoria Personalizada e Coesão de IA
- **5 Skills Pedagógicas Especializadas (`SkillRouter`):**
  1. **Diagnóstico & Trilha (`diagnostico-perfil`):** orienta a jornada do iniciante ao avançado (`A1` → `B2`) sem criar bolhas de aprendizado.
  2. **Tutor Socrático (`tutor-socratico`):** conduz o raciocínio com exemplos contrastivos (`❌ Evite → ✅ Prefira`) e perguntas guiadas.
  3. **Inglês Técnico (`ingles-tecnico`):** destrava vocabulário de IA, Cloud, APIs, Git, logs/stack traces e documentação técnica.
  4. **Prática Ativa (`pratica-ativa`):** corrige frases de *Daily Standup*, entrevistas e escrita, detectando armadilhas comuns de falantes de português.
  5. **Guardião Ético & Governança (`guardiao-etico-mtg`):** responde sobre uso responsável de IA, privacidade, transparência e boas práticas.
- **Coesão Multi-turno com Gemini 3.8 Flash:**
  - **Memória Pedagógica (`LearnerState`):** acompanha pontos a reforçar e tópicos dominados ao longo da sessão (salvo localmente no navegador) para conectar explicações entre turnos.
  - ***Few-Shot Prompting* calibrado por Skill e CEFR (`A1`–`B2`)** e **decodificação dinâmica** (`temperature` e `topP` ajustados por especialidade).
  - ***Conversation Stickiness*:** mantém continuidade de contexto em perguntas de acompanhamento sem trocar de persona abruptamente.
  - **Streaming progressivo (SSE — `/api/chat/stream`):** exibe respostas em tempo real com cache leve (LRU + TTL) e fallback transparente para o modo local.

### Motor RAG Híbrido em Java Puro
- **Base Curada de 15 Documentos (`91` chunks indexados):** cobre níveis `A1` a `B2`, falsos cognatos, *phrasal verbs* de tecnologia, leitura de documentação, *debugging*/logs, *prompt engineering*, *code review*, pronúncia e ética em IA — todos com `tags:` bilíngues e contrastes práticos.
- **Busca Híbrida (BM25 + Embeddings Vetoriais com RRF):** combina **Okapi BM25 bilíngue** (expansão de sinônimos PT↔EN, *stemming*, *fuzzy matching* Damerau-Levenshtein para erros de digitação, expansão contextual multi-turno e diversidade de fontes) com **Embeddings Gemini** em cache local (`data/embeddings-cache.json`) fundidos via **Reciprocal Rank Fusion (RRF)**.
- **Baixa Latência:** *fast-path* local no BM25 para consultas de alta confiança, pré-aquecimento assíncrono de embeddings em background e chunking inteligente com *overlap*.
- **Curadoria Docente ao Vivo:** permite cadastrar novos conteúdos ou importar arquivos `.md` e `.txt` (`/api/knowledge/upload`), indexados imediatamente no motor RAG.

### Prática Interativa, Áudio e Acessibilidade
- **Mini-Teste de Nivelamento CEFR:** avaliação rápida em 4 perguntas (`A1` → `B2`) que define o nível inicial e sugere o primeiro foco de estudo.
- **Simulador Guiado Multi-Turno (`/api/simulator/*`):** simula **Daily Standup** (3 etapas) e **Entrevista Técnica** (4 etapas) usando *Structured Outputs* (JSON Schema), com feedback por resposta e boletim final.
- **Quiz Adaptativo Sob Demanda (`/api/quiz/*`):** banco curado + geração de questões inéditas ancoradas no RAG com Gemini 3.8 Flash e fallback determinístico local.
- **Caderno de Vocabulário / Flashcards SRS:** revisão espaçada (4 caixas Leitner), busca instantânea e exportação em `.csv` compatível com **Anki**.
- **TTS Híbrido (`/api/tts`):** síntese de pronúncia em `en-US` e `pt-BR` com velocidade ajustável (`1.0x` / `0.75x`), alternando automaticamente entre áudio natural online e **`eSpeak-NG` offline** (com fallback para `Web Speech API`).
- **Interface Minimalista e Acessível:** Modo Leve (economia de banda), Alto Contraste, ajuste de fonte (`A- / A+`), atalhos de teclado (`Ctrl+K`, `1`–`4`, `Espaço`, `Esc`), limpeza completa de dados locais e exportação do **Boletim da Sessão** (`.md` e versão imprimível).

### Governança, Segurança e Qualidade
- **Privacidade e Guardrails Ativos:** mascaramento automático de CPF, e-mail e telefone na entrada **e na saída** (inclusive durante streaming SSE via buffer sanitizador) e redirecionamento socrático anti-plágio.
- **Verificação Pós-Geração (`GroundednessVerifier`):** calcula o índice de ancoragem (`groundednessScore`) da resposta frente aos trechos RAG recuperados sem adicionar latência.
- **Observabilidade e Proteção de API:** `watermarkId` por resposta, canal de auditoria (`/api/feedback`), métricas da sessão (`/api/metrics`), *Rate Limiting* com *eviction* automática e validação estrita de IP, respostas de erro padronizadas (`@RestControllerAdvice`) e **OpenAPI / Swagger UI** (`/swagger-ui.html`).
- **58 testes automatizados** (`JUnit 5`, `SpringBootTest` e `MockMvc`), incluindo benchmark de qualidade do RAG (`Recall@3 >= 0.95` e `MRR >= 0.85`).

---

## Stack Tecnológica

- **Backend:** Java 25, Spring Boot 4.1.1, Spring WebMVC, SpringDoc OpenAPI 3
- **IA & Recuperação:** Okapi BM25 + Embeddings Vetoriais (RRF) em Java puro, cliente HTTP nativo (`java.net.http.HttpClient`) para Gemini (`gemini-3.8-flash`), Groq, OpenAI-compatível e Ollama
- **Frontend:** HTML5, CSS3 e JavaScript vanilla (zero dependências externas de build)
- **Persistência Leve:** arquivos JSON locais em `data/` (curadoria docente, quiz gerado, auditoria e cache de embeddings) e `localStorage` no navegador

---

## Como Executar Localmente

### Pré-requisitos
- **JDK 25** instalado (`JAVA_HOME` apontando para um JDK completo).
- Maven Wrapper incluso (`./mvnw`).
- *(Opcional)* `espeak-ng` instalado no sistema para fallback de síntese de voz 100% offline.

### 1. Modo Padrão (Offline / RAG Local — sem API Key)

```bash
./mvnw spring-boot:run
```

Acesse:
- Aplicação web: **http://localhost:8080**
- Documentação interativa da API (Swagger UI): **http://localhost:8080/swagger-ui.html**

### 2. Modo Híbrido com LLM Generativo (Opcional)

```bash
export GEMINI_API_KEY="sua-chave-aqui"
# Opcional: export GEMINI_MODEL="gemini-3.8-flash"
./mvnw spring-boot:run
```

Também suporta outros provedores via variáveis de ambiente:
- **Groq:** `GROQ_API_KEY` (padrão `llama-3.1-8b-instant`)
- **OpenAI-compatível:** `OPENAI_API_KEY` + `OPENAI_BASE_URL` + `OPENAI_MODEL`
- **Ollama local:** `OLLAMA_URL=http://localhost:11434` + `OLLAMA_MODEL`

### 3. Executar a Suíte de Testes (58 testes)

```bash
./mvnw test
```

### 4. Executar via Docker

```bash
docker build -t dinga-ai .
docker run -p 8080:8080 -e GEMINI_API_KEY="sua-chave-opcional" dinga-ai
```
