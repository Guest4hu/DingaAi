title: ODS da ONU, Inclusão Digital, Acessibilidade e Baixa Conectividade
level: ALL
category: Impacto Social & ODS
source: Projeto Educação de Idiomas — Caso 3 & Agenda 2030 ONU
tags: ods, ods 4, ods 10, ods 16, agenda 2030, inclusao digital, acessibilidade, baixa conectividade, modo leve, low data mode, offline, 3g, alto contraste, tts, espeak, web speech, economia de banda, fosso digital
---

## Alinhamento com os Objetivos de Desenvolvimento Sustentável (ODS)
A arquitetura da **Dinga AI** conecta engenharia de software e impacto social em três metas centrais da **Agenda 2030 da ONU**:
- **ODS 4 — Educação de Qualidade:** democratiza a aprendizagem adaptativa de inglês e letramento digital crítico de forma gratuita, prevenindo a precarização cognitiva por meio da tutoria socrática.
- **ODS 10 — Redução das Desigualdades:** derruba barreiras linguísticas e socioeconômicas no acesso à documentação técnica internacional, rodando de maneira leve em celulares modestos, notebooks antigos e conexões instáveis.
- **ODS 16 — Paz, Justiça e Instituições Eficazes:** promove transparência algorítmica, proteção de dados pessoais (LGPD), rotulagem de conteúdo sintético (*watermarking*) e combate à desinformação.
- **Contrastes práticos:**
  - ❌ Evite: projetar plataformas educacionais que só funcionam em fibra ótica e aparelhos caros. → ✅ Prefira: adotar arquitetura *offline-first / text-first* que inclua estudantes de qualquer região.
- **Frases naturais para praticar (🔊):**
  - *"Accessible educational technology helps reduce inequalities in line with SDG 4 and SDG 10."*
  - *"Students should not be excluded from learning English because of slow internet connections."*
  - *"Transparent AI governance supports fair and accountable educational institutions."*

## Engenharia para Baixa Conectividade e Acessibilidade Real
Para cumprir o **Caso 3** sem ampliar o fosso digital, cada decisão técnica prioriza leveza, autonomia e inclusão:
- **Modo Baixa Conexão (`lowDataMode`):** interface *text-first* sem vídeos pesados nem bibliotecas externas pesadas; desativa chamadas remotas extras de embeddings e aciona o **BM25 RAG Local** em Java puro, economizando até **97% de banda** em redes 3G ou offline.
- **Baixa Latência (TTFT rápido):** streaming progressivo via SSE com o **Gemini 3.8 Flash**, *fast-path* BM25 de alta confiança (0 chamadas remotas extras de embedding) e cache persistente em disco.
- **Acessibilidade Universal (WCAG):** modo alto contraste, ajuste progressivo de tamanho de fonte (`A- / A+`), navegação completa por teclado, atalhos rápidos e síntese de voz (*Text-to-Speech*) híbrida com fallback offline (`eSpeak-NG` e `Web Speech API`).
- **Contrastes práticos:**
  - ❌ Evite: bloquear o estudo inteiro quando a API remota cai ou o sinal 3G oscila. → ✅ Prefira: manter resposta imediata via **RAG Local** e **TTS offline**.
- **Frases naturais para praticar (🔊):**
  - *"Low-data mode switches to a lightweight text-first experience that saves up to 97 percent bandwidth."*
  - *"Local BM25 fallback keeps the tutor responsive even completely offline."*
  - *"High-contrast themes, keyboard shortcuts, and offline speech synthesis improve accessibility for everyone."*
