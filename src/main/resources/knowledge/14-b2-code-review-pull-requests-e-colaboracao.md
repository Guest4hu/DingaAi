title: Comunicação Assíncrona: Code Review, Pull Requests e GitHub (Microsoft Learn)
level: B2
category: Carreira & Prática
source: Dinga AI Curadoria — Trilha Microsoft Learn (Collaborate with Pull Requests in GitHub)
tags: code review, revisao de codigo, pull request, pr, lgtm, nitpick, nit, non blocking, github, conventional commits, bug report, github issue, github actions, ci checks, requesting changes, colaboracao assincrona
---

## Como Escrever Comentários Educados em Code Review
No módulo *Collaborate with Pull Requests in GitHub* do **Microsoft Learn** e em equipes internacionais, uma regra de ouro guia toda revisão: **critique o código, nunca a pessoa** — e sinalize claramente o que é detalhe opcional versus bloqueio real.
- **Nit / Nitpick (detalhe menor opcional):** sugestão cosmética de nomenclatura ou estilo que não bloqueia o *merge*.
- **Non-blocking suggestion (sugestão não bloqueante):** melhoria válida que pode ficar para um PR futuro.
- **Question / Curiosity (perguntar antes de julgar):** entender a motivação técnica antes de sugerir mudança.
- **Approval (`LGTM` — *Looks Good To Me*):** aprovação explícita destacando também um ponto positivo do código.
- **Requesting changes com respeito:** apontar um risco concreto (ex.: `NullPointerException`, vazamento de PII) propondo solução clara.
- **Contrastes práticos:**
  - ❌ Evite: *"Why did you write this ugly code? Fix it."* (agressivo e vago) → ✅ Prefira: *"This could throw a null pointer when the history is empty. Could we add a guard clause here?"*
  - ❌ Evite: bloquear um PR inteiro por um detalhe mínimo de nome de variável. → ✅ Prefira: marcar com prefixo **`Nit:`** e aprovar o PR se o restante estiver sólido.
- **Frases naturais para praticar (🔊):**
  - *"Nit: could we rename this variable to chunkOverlap for better clarity?"*
  - *"Non-blocking: we might extract this helper method in a follow-up pull request."*
  - *"Could you clarify why we skipped the local cache in this branch?"*
  - *"Looks good to me! Great job keeping the local RAG fallback offline-ready."*

## Estruturando Pull Requests, GitHub Issues e Conventional Commits
No roteiro *Manage Repository Changes using Pull Requests* do **Microsoft Learn**, um PR claro reduz idas e vindas e facilita revisões assíncronas entre fusos horários:
- **Summary / Context:** explique *o que* mudou e *por quê* (vinculando a issue relacionada, ex.: `Closes #42`).
- **How to Test / Verification & CI Checks:** documente comandos rodados (`./mvnw test`) e confirme que os *GitHub Actions / CI checks* passaram.
- **Bug Report (GitHub Issue):** organize sempre em **Description**, **Steps to Reproduce**, **Expected Behavior** e **Actual Behavior**.
- **Conventional Commits em inglês:** use verbos no imperativo curto no presente (`feat:`, `fix:`, `docs:`, `test:`, `refactor:`).
- **Contrastes práticos:**
  - ❌ Evite: commit `fixed stuff` ou PR vazio sem contexto. → ✅ Prefira: `feat: index bilingual frontmatter tags in BM25 retriever` com seção *How to Test*.
  - ❌ Evite: usar passado nos commits (`added`, `fixed`). → ✅ Prefira: imperativo em inglês (`add`, `fix`, `update`, `refactor`).
- **Frases naturais para praticar (🔊):**
  - *"This pull request adds bilingual tag weighting to improve RAG recall on colloquial queries."*
  - *"I ran the full Maven test suite and all CI checks passed without regressions."*
  - *"Please include clear steps to reproduce and expected behavior in the GitHub issue."*
  - *"Use imperative verbs in Conventional Commits, such as add, fix, or refactor."*
