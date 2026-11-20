title: ODS da ONU, Inclusão Digital e Baixa Conectividade
level: ALL
category: Impacto Social & ODS
source: Projeto Educação de Idiomas — Caso 3 & Agenda 2030 ONU
---

## Alinhamento com os Objetivos de Desenvolvimento Sustentável (ODS)
A arquitetura da **Dinga AI** conecta tecnologia educacional e impacto social em três metas da Agenda 2030:
- **ODS 4 — Educação de Qualidade:** promove aprendizagem adaptativa gratuita, tutoria socrática e letramento digital crítico para estudantes de escolas e universidades públicas ou privadas, evitando a precarização cognitiva.
- **ODS 10 — Redução das Desigualdades:** reduz barreiras socioeconômicas e linguísticas no acesso à documentação técnica em inglês, com interface leve para dispositivos simples e auditoria contra vieses algorítmicos.
- **ODS 16 — Paz, Justiça e Instituições Eficazes:** fortalece a transparência algorítmica, a proteção de dados (LGPD), a rotulagem de conteúdo sintético e o combate à desinformação/alucinações.

## Engenharia para Baixa Conectividade e Acessibilidade
Para cumprir os requisitos do **Caso 3** sem aprofundar o fosso digital:
- **Modo Leve (Baixa Conexão):** interface *text-first* sem vídeos pesados ou dependências pesadas no cliente, com fallback **RAG Local** que funciona em Java puro mesmo sem API externa ou com conexão instável.
- **Acessibilidade na Interface:** suporte a alto contraste, ajuste progressivo de fonte (`A- / A+`), navegação por teclado e leitura em voz alta (*Text-to-Speech*) usando a `Web Speech API` nativa do navegador sem custo extra de banda.
