title: Inglês Técnico — Microsoft Learn, AI-900, IA Generativa e Responsible AI
level: B1
category: Inglês Técnico & Microsoft Learn
source: Trilha Microsoft Learn — AI-900: Azure AI Fundamentals, Azure AI Foundry & Responsible AI
tags: microsoft learn, ai-900, azure ai fundamentals, azure ai foundry, ia generativa, generative ai, grounding, rag, hallucination, alucinacao, ancoragem, token, context window, system prompt, fine tuning, responsible ai, groundedness, content safety, guardrails, bias, vies
---

## Vocabulário Essencial de IA Generativa (AI-900 & Azure AI Foundry)
Nos módulos do **Microsoft Learn** — especialmente *AI-900: Microsoft Azure AI Fundamentals*, *Fundamentals of Generative AI* e *Azure AI Foundry* — os termos abaixo são a espinha dorsal de provas, documentação e projetos reais:
- **Prompt & System Prompt (System Instruction):** *prompt* é a instrução enviada ao modelo; *system prompt* define o papel, o tom pedagógico e os limites éticos fixos do assistente.
- **Token:** menor unidade de texto processada pelo LLM (parte de palavra, palavra ou pontuação). Menos tokens redundantes significam menor custo e menor latência.
- **Context Window (Janela de Contexto):** limite máximo de tokens (entrada + histórico + fontes RAG + saída) que o modelo considera em uma chamada.
- **RAG (Retrieval-Augmented Generation) & Grounding:** técnica que recupera trechos confiáveis antes da geração para **ancorar** (*ground*) a resposta em fatos verificáveis.
- **Hallucination (Alucinação):** quando o modelo gera informação falsa ou sem evidência com tom convincente.
- **Fine-tuning vs. Grounding:** *fine-tuning* ajusta os pesos do modelo para estilo/domínio; *grounding* via RAG injeta conhecimento atualizado e auditável sem retreinar o modelo.
- **Contrastes práticos:**
  - ❌ Evite: achar que *fine-tuning* resolve dados que mudam toda semana. → ✅ Prefira: usar **RAG / Grounding** para manter respostas ancoradas em documentos atualizáveis.
  - ❌ Evite: confiar em uma resposta do LLM sem checar as fontes. → ✅ Prefira: verificar o **groundedness** citando explicitamente os documentos recuperados.
- **Frases naturais para praticar (🔊):**
  - *"Use Retrieval-Augmented Generation to ground the model in trusted documents."*
  - *"Grounding significantly reduces hallucinations in educational AI tutors."*
  - *"Write a clear system prompt with explicit role, tone, and safety constraints."*
  - *"Keep the context window concise to lower both token cost and latency."*

## Analogias Acessíveis e os 6 Pilares do Responsible AI (Microsoft Learn)
No módulo *Principles of Responsible AI* do **Microsoft Learn (AI-900)** e no *Microsoft Responsible AI Standard v2.0*, a ética em IA se traduz em 6 pilares práticos — que podemos conectar a analogias do cotidiano brasileiro:
- **Prompt** é como passar um recado bem explicado: sem contexto e objetivo, o resultado vem incompleto.
- **RAG & Grounding** funcionam como consultar a página exata de uma apostila confiável antes de responder em sala, em vez de chutar "de cabeça".
- **Os 6 pilares de Responsible AI da Microsoft:**
  1. **Fairness (Equidade):** tratar grupos sem discriminação ou viés (*bias*).
  2. **Reliability and Safety (Confiabilidade e Segurança):** funcionar de forma previsível, com testes e *fallback* seguro.
  3. **Privacy and Security (Privacidade e Segurança):** proteger dados pessoais (PII/LGPD).
  4. **Inclusiveness (Inclusão):** empoderar todos os perfis, incluindo acessibilidade e baixa conectividade.
  5. **Transparency (Transparência):** deixar claro quando há IA, quais fontes foram usadas e quais são os limites.
  6. **Accountability (Responsabilização):** humanos respondem pelas decisões e pela governança do sistema.
- **Contrastes práticos:**
  - ❌ Evite: confundir **Bias** (viés sistemático nos dados/modelo) com erro aleatório de digitação. → ✅ Prefira: auditar e reportar vieses para garantir **Fairness**.
- **Frases naturais para praticar (🔊):**
  - *"Microsoft's Responsible AI framework is built on six core ethical principles."*
  - *"Transparency means showing the user which curated sources grounded the answer."*
  - *"Inclusiveness ensures that students on slow networks still receive quality tutoring."*
  - *"Accountability keeps human educators in the loop for final validation."*

## Métricas e Guardrails em Sistemas de IA (Azure AI Foundry)
Ao avaliar um tutor de IA no **Microsoft Learn** (como no *Azure AI Foundry Evaluation* e *Azure AI Content Safety*), três indicadores técnicos aparecem o tempo todo:
- **Groundedness:** mede se cada afirmação da resposta está realmente sustentada pelas fontes recuperadas pelo RAG.
- **Latency & TTFT (Time to First Token):** tempo até o aluno ver a primeira palavra na tela — streaming SSE e modelos rápidos como o **Gemini 3.8 Flash** reduzem drasticamente a espera percebida.
- **Guardrails (Content Safety):** filtros determinísticos aplicados antes e depois do LLM para mascarar PII (CPF, e-mail, telefone) e bloquear usos inadequados (como cola/plágio).
- **Contrastes práticos:**
  - ❌ Evite: medir qualidade de IA apenas por "resposta bonita". → ✅ Prefira: avaliar **groundedness**, **relevance**, **TTFT** e eficácia dos **guardrails**.
- **Frases naturais para praticar (🔊):**
  - *"Groundedness measures whether the generated answer strictly follows the retrieved sources."*
  - *"Streaming responses via SSE lowers the perceived Time to First Token."*
  - *"Content safety guardrails mask sensitive personal data before calling external models."*
