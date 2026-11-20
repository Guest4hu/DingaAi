title: Inglês Técnico — Cloud (AZ-900), APIs REST, Back-end Java e Git/GitHub
level: B1
category: Inglês Técnico & Desenvolvimento
source: Dinga AI Curadoria — Trilha Microsoft Learn (AZ-900, Azure API Management & Git/GitHub)
tags: endpoint, payload, latencia, latency, fallback, contingencia, rate limit, http 429, api rest, sse streaming, caching, deploy, deployment, environment variable, git, github, push, pull, merge, rebase, branch, az-900, azure fundamentals, sla, failover, status http
---

## Termos de APIs REST, Back-end e Resiliência (AZ-900 & Azure API Management)
Nos módulos *Describe Cloud Concepts (AZ-900)* e *Build Web APIs* do **Microsoft Learn**, entender os termos de comunicação entre cliente e servidor é essencial para ler logs, contratos JSON e documentação de nuvem:
- **Endpoint:** endereço/rota HTTP de um serviço (ex.: `POST /api/chat`, `GET /api/health`).
- **Payload:** carga útil de dados (geralmente JSON) enviada no corpo (*body*) da requisição ou retornada na resposta.
- **Latency (Latência) & Throughput:** *latency* é o tempo de espera entre requisição e resposta; *throughput* é a vazão de requisições por segundo.
- **Fallback / Failover:** plano B automático quando um serviço externo falha, excede cota ou fica sem conexão (ex.: alternar do Gemini remoto para o modo **RAG Local**).
- **Rate Limit (Limite de Taxa):** proteção contra abuso de chamadas (retorna HTTP `429 Too Many Requests` com cabeçalho `Retry-After`).
- **Streaming (SSE — Server-Sent Events) & Caching:** *SSE* envia a resposta em fragmentos progressivos; *caching* guarda resultados ou *embeddings* para responder em milissegundos.
- **Contrastes práticos:**
  - ❌ Evite: confundir **endpoint** (URL/rota) com **payload** (dados JSON dentro da requisição). → ✅ Prefira: *"Send the JSON payload to the `/api/chat` endpoint."*
  - ❌ Evite: deixar o app travar ao receber HTTP `429`. → ✅ Prefira: acionar **fallback** automático e respeitar o *rate limit*.
- **Frases naturais para praticar (🔊):**
  - *"The POST endpoint validates the JSON payload before processing the request."*
  - *"Our hybrid architecture triggers a local fallback to guarantee low latency offline."*
  - *"When the API hits the rate limit, it returns HTTP 429 Too Many Requests."*
  - *"Caching computed embeddings reduces network calls and improves throughput."*

## Cloud (AZ-900), Variáveis de Ambiente e Git/GitHub no Microsoft Learn
No roteiro *Introduction to Git and GitHub* e *AZ-900: Azure Fundamentals* do **Microsoft Learn**, estes termos aparecem em praticamente todo projeto colaborativo:
- **Deploy / Deployment & SLA:** *deploy* é publicar a aplicação em nuvem (como Azure App Service); *SLA (Service Level Agreement)* define a disponibilidade prometida pelo provedor.
- **Environment Variable:** configuração externa segura (`GEMINI_API_KEY`, `JAVA_HOME`, `PORT`) que nunca deve ser salva no código-fonte.
- **Repository / Branch / Pull Request (PR):** repositório versionado, ramificação isolada de desenvolvimento e pedido formal de revisão antes do *merge* na `main`.
- **Commit / Push / Pull / Merge / Rebase:** salvar snapshot local (*commit*), **enviar** commits ao remoto (*push*), **baixar** atualizações (*pull*), unir históricos (*merge*) ou reaplicar commits sobre uma base atualizada (*rebase*).
- **Warning vs. Error:** *warning* é aviso não bloqueante; *error* interrompe a execução atual.
- **Contrastes práticos:**
  - ❌ Evite: achar que `git push` puxa arquivos do servidor (falso amigo de "puxar"). → ✅ Prefira: `git pull` = baixar (puxar) e `git push` = enviar (empurrar).
  - ❌ Evite: commitar senhas ou chaves de API no repositório. → ✅ Prefira: injetar segredos via **environment variables**.
- **Frases naturais para praticar (🔊):**
  - *"Always run git pull before you push new commits to the remote branch."*
  - *"Never hardcode secrets; store your API key in an environment variable."*
  - *"We deployed the Spring Boot service to the cloud with high availability."*
  - *"Open a pull request so your teammates can review the changes before merging."*

## Códigos de Status HTTP Essenciais em Inglês
Reconhecer rapidamente os códigos HTTP acelera o *troubleshooting* em qualquer API REST:
- **200 OK / 201 Created:** sucesso na leitura/operação (200) ou novo recurso criado (201).
- **400 Bad Request:** requisição malformada ou campos obrigatórios ausentes no *payload*.
- **401 Unauthorized / 403 Forbidden:** sem autenticação válida (401) ou autenticado mas sem permissão (403).
- **404 Not Found:** rota, modelo ou recurso inexistente no servidor.
- **429 Too Many Requests:** limite de taxa (*rate limit*) atingido — aguarde ou acione *fallback*.
- **500 Internal Server Error / 503 Service Unavailable:** falha interna no servidor ou indisponibilidade temporária.
- **Contrastes práticos:**
  - ❌ Evite: confundir **401 Unauthorized** (falta login/token válido) com **403 Forbidden** (você está logado, mas não tem permissão). → ✅ Prefira: checar autenticação no `401` e papéis/permissões (*RBAC*) no `403`.
- **Frases naturais para praticar (🔊):**
  - *"The endpoint returned HTTP 400 Bad Request because a required field was missing."*
  - *"HTTP 401 means invalid credentials, while HTTP 403 means insufficient permissions."*
  - *"On HTTP 503 Service Unavailable, the client automatically retries or falls back."*
