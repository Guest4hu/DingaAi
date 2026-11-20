title: Governança Ética (MTG-IA), LGPD, EU AI Act e Microsoft Responsible AI
level: ALL
category: Ética & Governança
source: Projeto Educação de Idiomas — Revisão Bibliográfica MTG-IA (Etapa 3) & Microsoft Responsible AI Standard v2.0
tags: mtg-ia, governanca etica, tripla governanca, lgpd, dados pessoais, pii, privacidade, watermarking, marca dagua, rotulagem, eu ai act, unesco, human in the loop, humano no circuito, ripd etico, responsible ai standard, azure ai content safety, transparencia algoritmica, auditoria, vies algoritmico
---

## Modelo de Tripla Governança (MTG-IA) na Educação
Proposta de intervenção para o uso ético, transparente e auditável da Inteligência Artificial em instituições de ensino brasileiras, integrando engenharia, pedagogia e conformidade legal:
- **Pilar A — Design de Sistemas e Transparência Algorítmica (Nível Tecnológico):**
  - *Rotulagem e Rastreabilidade (Watermarking):* toda saída assistida por IA recebe selo explícito, identificador único (`watermarkId`), modo de execução (Gemini 3.8 Flash ou RAG Local) e lista de fontes curadas consultadas.
  - *Mitigação de Viés e Alucinação:* ancoragem obrigatória em base curada via **RAG Híbrido** (BM25 + embeddings com fallback offline) e canal aberto de reporte de vieses e falhas.
  - *Guardrail Ativo de Privacidade (PII / LGPD):* detecção e mascaramento automático de CPF, e-mail e telefone antes de qualquer chamada a modelos externos.
- **Pilar B — Protocolo de Letramento Digital e Verificação (Nível Educacional):**
  - Regra **Humano no Circuito (*Human-in-the-loop*):** *"A IA sugere, você valida."* Estudantes e docentes verificam criticamente toda saída sintética nas fontes originais.
  - Combate ao plágio acrítico e respeito a direitos autorais com redirecionamento socrático.
- **Pilar C — Auditoria Institucional e Responsabilização (Nível Legal):**
  - Métricas agregadas sem PII, relatórios exportáveis para acompanhamento pedagógico e Relatório de Impacto à Proteção de Dados e Ética (**RIPD-Ético**).
- **Contrastes práticos:**
  - ❌ Evite: enviar CPF, e-mail pessoal ou dados sensíveis de alunos em prompts de IA externa. → ✅ Prefira: aplicar mascaramento automático de PII e minimização de dados (*privacy by design*).
  - ❌ Evite: apresentar texto gerado por IA sem rotulagem nem fontes. → ✅ Prefira: incluir **watermarking** rastreável e validação humana (*human-in-the-loop*).
- **Frases naturais para praticar (🔊):**
  - *"Every AI-assisted response includes a traceable watermark and cites its curated sources."*
  - *"Human-in-the-loop governance means the AI suggests and the student validates critically."*
  - *"Active privacy guardrails mask personal data before sending any prompt to external APIs."*
  - *"Institutional audits track aggregate metrics without storing sensitive student data."*

## Marcos Regulatórios e Microsoft Responsible AI Standard v2.0
A governança da Dinga AI alinha-se aos principais marcos jurídicos e padrões técnicos internacionais (também cobrados no módulo *Principles of Responsible AI* do **Microsoft Learn AI-900**):
- **LGPD (Lei nº 13.709/2018):** Art. 6º (finalidade, adequação, necessidade, prevenção, transparência, não discriminação e responsabilização) e Art. 20 (direito à revisão de decisões automatizadas). A plataforma não exige cadastro sensível e permite limpar o histórico local a qualquer momento.
- **EU AI Act (Regulamento UE 2024/1689):** classificação baseada em risco, exigindo transparência explícita em sistemas de IA generativa e supervisão humana reforçada em contextos educacionais.
- **UNESCO (2021) & Floridi et al. (2018):** transparência, explicabilidade, beneficência, não-malefício, autonomia humana e justiça social.
- **Microsoft Responsible AI Standard v2.0 & Azure AI Content Safety:** operacionaliza os 6 princípios (*Fairness, Reliability & Safety, Privacy & Security, Inclusiveness, Transparency, Accountability*) por meio de avaliações de risco, *Responsible AI Toolbox* e filtros de segurança de conteúdo.
- **Contrastes práticos:**
  - ❌ Evite: tratar ética em IA apenas como um texto de aviso no rodapé. → ✅ Prefira: transformar princípios regulatórios em **controles técnicos verificáveis** no código (guardrails, RAG, logs sem PII, testes automatizados).
- **Frases naturais para praticar (🔊):**
  - *"Privacy by design minimizes personal data collection in compliance with LGPD."*
  - *"The EU AI Act requires clear transparency and human oversight in educational AI."*
  - *"Microsoft's Responsible AI Standard turns ethical principles into verifiable engineering controls."*
