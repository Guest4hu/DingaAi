title: Inglês para Mensagens de Erro, Logs, Stack Traces e Debugging
level: B1
category: Inglês Técnico & Desenvolvimento
source: Dinga AI Curadoria — Trilha Microsoft Learn (Troubleshoot & Debug Apps, Azure Monitor & Application Insights)
tags: stack trace, pilha de chamadas, debugging, depuracao, depurar, root cause, causa raiz, nullpointerexception, excecao, exception, logs, log, expected actual, steps to reproduce, troubleshooting, syntax error, runtime error, connection refused, timed out, azure monitor, application insights
---

## Como Ler um Stack Trace e Mensagens de Erro em Inglês
Nos módulos *Troubleshoot and Debug Apps* do **Microsoft Learn**, mensagens de erro em Java, JavaScript, Python ou terminais seguem uma anatomia previsível: **Tipo do Erro + Causa Imediata + Localização (arquivo e linha)**.
- **NullPointerException / TypeError (undefined/null):** o código tentou chamar um método ou ler um campo de uma referência vazia (`null`).
- **Syntax Error vs. Runtime Error:** *syntax error* impede a compilação (falta `;`, `}` ou parêntese); *runtime error* só acontece com o programa em execução.
- **Unhandled Exception / Uncaught Error:** exceção lançada sem tratamento em um bloco `try/catch`.
- **Connection Refused / Timed Out:** o serviço destino não está ouvindo naquela porta (*connection refused*) ou demorou mais que o limite configurado (*timed out*).
- **Permission Denied / Access Denied:** permissão insuficiente no sistema operacional, token expirado ou credencial incorreta.
- **Contrastes práticos:**
  - ❌ Evite: ler apenas a última linha genérica e entrar em pânico. → ✅ Prefira: procurar a linha **`Caused by:`** e o primeiro frame do seu pacote (ex.: `br.edu.usjt.dinga`).
  - ❌ Evite: confundir **Connection Refused** (porta fechada/serviço parado) com **Timed Out** (rede lenta ou firewall bloqueando). → ✅ Prefira: testar primeiro se o serviço está rodando localmente.
- **Frases naturais para praticar (🔊):**
  - *"Cannot invoke String length because the input value is null."*
  - *"Read the stack trace from top to bottom until you find your application package."*
  - *"The client received a connection refused error because the local server was not running."*
  - *"Always handle network timeouts gracefully with a local fallback."*

## Palavras-Chave em Logs, JUnit e Azure Monitor / Application Insights
Dominar o vocabulário de observabilidade (usado no **Azure Monitor**, **Application Insights** e suítes **JUnit**) acelera o *troubleshooting* sem depender de tradutor:
- **Expected vs. Actual:** em testes automatizados, *expected* é o resultado esperado e *actual* é o valor realmente retornado pelo código.
- **Stack Trace (Pilha de Chamadas):** rastro completo de chamadas de método até a linha exata da falha.
- **Root Cause (Causa Raiz):** o defeito verdadeiro por trás do sintoma visível (*symptom*).
- **Reproduce / Steps to Reproduce:** conseguir repetir o bug de forma determinística antes e depois da correção.
- **Log Levels (`DEBUG`, `INFO`, `WARN`, `ERROR`):** severidade das mensagens registradas em ferramentas como *Application Insights*.
- **Contrastes práticos:**
  - ❌ Evite: dizer *"It doesn't work"* sem mostrar evidência. → ✅ Prefira: informar **Expected**, **Actual** e **Steps to Reproduce**.
  - ❌ Evite: mascarar exceções com `catch (Exception e) {}` vazio. → ✅ Prefira: registrar o erro em log estruturado sem expor dados pessoais (LGPD).
- **Frases naturais para praticar (🔊):**
  - *"Expected HTTP 200 OK, but the actual status was 400 Bad Request."*
  - *"Check the Caused by line in the Java log to identify the root cause."*
  - *"Can you reproduce this bug consistently in low-data mode?"*
  - *"Structured logs in Azure Monitor help teams trace latency spikes quickly."*

## Como Pesquisar e Descrever Bugs em Inglês com Segurança (LGPD)
Ao buscar ajuda no **Microsoft Learn Q&A**, Stack Overflow ou GitHub Issues:
- **Higienize antes de colar:** remova caminhos locais com seu nome de usuário, chaves de API, CPFs, e-mails e tokens (alinhado à LGPD).
- **Consultas precisas:** combine framework + classe exata da exceção (`"Spring Boot 4" "NoResourceFoundException"` ou `"git push rejected non-fast-forward"`).
- **Estrutura de pedido de ajuda (3 blocos):** **What I tried** (o que tentei), **What happened** (mensagem exata) e **What I expected** (comportamento esperado).
- **Contrastes práticos:**
  - ❌ Evite: colar o log inteiro com `GEMINI_API_KEY` ou e-mail pessoal em fóruns públicos. → ✅ Prefira: recortar apenas a exceção relevante e mascarar credenciais.
- **Frases naturais para praticar (🔊):**
  - *"Remove personal data and local secrets before sharing a stack trace online."*
  - *"Here are the steps to reproduce the issue, what I tried, and what I expected."*
  - *"I narrowed down the root cause to an empty list in the retriever."*
