# SEGURANÇA DA INFORMAÇÃO — Sistema de Leilões

> Documento de referência do projeto **Avaliação AZ Tecnologia**.
> Ele responde a uma pergunta simples: **"o que exatamente está protegido,
> onde está o código que protege, e o que NÃO está protegido?"**
>
> Um relatório de segurança que só diz "está tudo certo" não vale nada.
> O que vale é mostrar o mecanismo, o limite dele e o que falta.

---

## Sumário

1. [Resumo em 1 minuto](#1-resumo-em-1-minuto)
2. [Mapa contra a OWASP Top 10 (2021)](#2-mapa-contra-a-owasp-top-10-2021)
3. [Os controles, um a um](#3-os-controles-um-a-um)
4. [O que o projeto NÃO protege](#4-o-que-o-projeto-não-protege)
5. [Checklist de produção](#5-checklist-de-produção)
6. [Como verificar cada proteção](#6-como-verificar-cada-proteção)
7. [Modelo de ameaça](#7-modelo-de-ameaca)

---

## 1. Resumo em 1 minuto

| Controle | Estado | Onde está |
|---|---|---|
| Senha nunca em texto puro (BCrypt + salt) | **Implementado** | `PasswordEncoderConfig`, `EmpresaBO` |
| Validação de entrada no servidor (Bean Validation) | **Implementado** | `dto/*Request.java` + `@Valid` nos controllers |
| Proteção contra SQL Injection (JPA/JPQL parametrizado) | **Implementado** | `repository/*.java` |
| CORS fechado (lista de origens) | **Implementado** | `config/CorsConfig.java` |
| Headers de segurança (X-Frame-Options, nosniff, HSTS) | **Implementado** | `config/SecurityConfig.java` |
| Sem vazamento de stack trace nas respostas | **Implementado** | `exception/GlobalExceptionHandler.java` |
| Segredos fora do Git (variáveis de ambiente) | **Implementado** | `application.properties`, `application-prod.properties` |
| Autenticação HTTP Basic | **Implementado (opt-in por perfil)** | `config/SecurityConfig.java` |
| Controle anti-força-bruta (limite de tentativas) | **Implementado (opt-in por perfil)** | `config/LoginAttemptService.java` |
| Trilha de auditoria (login e alterações) | **Implementado** | `config/AuditTrailFilter.java`, `config/AuditedBasicAuthenticationFilter.java` |
| Menor privilégio no banco de dados | **Script pronto (opcional)** | `sql/seguranca/03_permissoes.sql` |
| TLS/HTTPS em trânsito | **Fora do escopo da aplicação** | responsabilidade do proxy/infra |
| Segredos em cofre (Vault / AWS Secrets Manager) | **Não implementado** | ver [seção 4](#4-o-que-o-projeto-não-protege) |

> **A grande decisao de projeto:** a autenticação e o controle de força bruta
> existem, mas **ligados por configuração**, e desligados por padrão.
> O enunciado da avaliação exige que `GET /unidades` responda **sem
> login**. Ao mesmo tempo, "sem segurança nenhuma" seria irresponsável.
> A solução é ter os dois caminhos no mesmo código, e o perfil de
> produção liga a segurança.

---

## 2. Mapa contra a OWASP Top 10 (2021)

A OWASP Top 10 é a lista dos 10 riscos de segurança de aplicações web mais
comuns. Vamos item por item.

---

### A01 — Controle de acesso quebrado

> *As permissões não são aplicadas corretamente, e uma pessoa consegue
> acessar ou alterar o que não deveria.*

**O que o projeto faz**

- Os controllers não recebem a entidade do banco diretamente: recebem
  **DTOs** (`dto/*Request.java`), onde só existem os campos que o
  cliente pode enviar. O campo `id` não faz parte do DTO de entrada, e
  o `id` da URL é quem define **qual** registro será alterado — nunca o
  corpo da requisição.
- A camada de negócio (`business/`) valida a existência dos registros
  relacionados antes de gravar (ex.: um lote só é criado se o
  `\"leilao\": 999` informado realmente existir).
- Não há "IDOR" (referência direta a objeto insegura) nas operações de
  alteração: `PUT /unidades/{id}` altera exatamente o `{id}` da URL.

**Onde**

- `service/*.java` — a URL define o alvo
- `business/*.java` — as regras que impedem gravar lixo
- `dto/*Request.java` — a fronteira que limita o que entra

**O que NÃO é coberto**

Não existe **autorização por perfil** (RBAC). Com a autenticação ligada,
*qualquer* usuário autenticado acessa *todas* as rotas. O modelo de
papéis é o passo seguinte natural — ver [seção 4](#4-o-que-o-projeto-não-protege).

---

### A02 — Falhas criptográficas

> *Dados sensíveis trafegam ou ficam guardados sem proteção adequada.*

**O que o projeto faz**

- **Senha nunca em texto puro.** A `EmpresaBO` grava a senha passando
  pelo `PasswordEncoder`, que é um `BCryptPasswordEncoder(10)`.
- **BCrypt com salt.** O salt é sorteado a cada hash e embutido no
  próprio resultado. Duas empresas com a mesma senha ficam com hashes
  **diferentes** — o que impede que um vazamento revele quais usuários
  compartilham senha.
- **Custo configurável.** O custo 10 significa ~100 ms por verificação.
  Um ataque de força bruta testaria milhares de senhas por segundo
  contra um SHA-256, mas poucas contra BCrypt. O custo sai do atacante
  e entra no servidor, que faz isso uma única vez por login.
- **A senha não volta na resposta.** O `EmpresaResponse` não tem campo
  `senha`. Nem o hash, nem o texto. Nos DTOs de saída, o que a API não
  precisa mostrar simplesmente não existe.
- **Segredos fora do código.** Usuário, senha e string de conexão vêm de
  variáveis de ambiente (`SECURITY_USERNAME`, `SECURITY_PASSWORD`,
  `DATABASE_PASSWORD`...). O valor padrão no `application.properties` é
  existe apenas para desenvolvimento.

**Onde**

- `config/PasswordEncoderConfig.java` — o codificador
- `business/EmpresaBO.java` — a gravação da senha
- `dto/EmpresaResponse.java` — a ausência de senha na saída
- `application.properties` / `application-prod.properties` — os segredos

**O que NÃO é coberto**

- **Não há TLS dentro da aplicação.** O HTTPS é responsabilidade do proxy
  reverso ou da nuvem. O HSTS já está configurado para instruir o
  navegador, mas ele só tem efeito se o TLS existir na frente.
- **Não há criptografia em repouso do banco.** Os dados em disco não
  estão cifrados (dependeria de Transparent Data Encryption do PostgreSQL
  ou de cifragem do volume).

---

### A03 — Injeção (SQL Injection)

> *Dados do usuário entram em uma consulta e viram parte do comando.*

**O que o projeto faz**

- **Nenhuma concatenação de SQL.** Todos os acessos passam pelo Spring
  Data JPA, que gera consultas **parametrizadas** (o driver envia os
  valores separados do texto da consulta).
- A única consulta escrita à mão é a JPQL do `LeilaoRepository` que soma
  o total dos lotes — e ela também não concatena nada do usuário.
- **Identificadores dinâmicos foram removidos.** Na primeira versão
  havia um repositório com `ORDER BY` montado por concatenação. Hoje não
  existe mais; a ordenação é feita pelo framework.

**Onde**

- `repository/*.java` — todas as consultas
- `business/*.java` — nenhuma string de SQL

**O que NÃO é coberto**

Não há teste de injeção automatizado. As consultas parametrizadas tornam
o risco estruturalmente baixo, mas um teste seria uma garantia extra.

---

### A04 — Design inseguro

> *O desenho do sistema tem fragilidades que o atacante explora.*

**O que o projeto faz**

- **Camadas com responsabilidades separadas.** `controller` (HTTP) →
  `business` (regras) → `repository` (dados). Uma regra de negócio
  escrita no controller seria aplicada a qualquer chamador da API.
- **Validação em duas camadas, com papéis distintos.** O frontend valida
  para **experiência**; o backend valida para **garantia**. Se só o
  frontend validasse, um `curl` burlaria tudo.
- **Defesas independentes.** Senha hasheada, validação no servidor, CORS
  fechado, SQL parametrizado, rate limiting. Nenhuma defesa depende
  exclusivamente da outra.

**Onde** — a estrutura de pastas inteira (`service/`, `business/`,
`repository/`, `dto/`).

---

### A05 — Configuração de segurança fraca

> *A aplicação foi instalada com padrões inseguros ou desnecessários.*

**O que o projeto faz**

| Item | Configuração | Onde |
|---|---|---|
| Spring Boot | 2.7.0 (versão com suporte extended) | `pom.xml` |
| TLS/cookie de sessão | CSRF desativado **justamente** porque a API é stateless e não usa cookie | `SecurityConfig` |
| Sessão | `STATELESS` — nenhum `JSESSIONID` é emitido | `SecurityConfig` |
| Exposição do Actuator | só `/actuator/health` e `/actuator/info` | `application.properties` |
| Segredos | variáveis de ambiente, nunca no Git | `application-prod.properties` |
| `X-Frame-Options` | `DENY` (mais restritivo que o padrão `sameOrigin`) | `SecurityConfig` |
| `X-Content-Type-Options` | `nosniff` | `SecurityConfig` |
| `Referrer-Policy` | `no-referrer` | `SecurityConfig` |
| `Cache-Control` | `no-store, no-cache, must-revalidate` | `SecurityConfig` |
| HSTS | 1 ano, incluindo subdomínios | `SecurityConfig` |
| Frameworks expostos | `X-Powered-By` removido | `application.properties` |
| Swagger / console H2 | não existem no projeto | — |
| Endpoint de erro | página de erro genérica, sem stack trace | `application.properties` |

**O que NÃO é coberto**

- O projeto **não** usa `CookieCsrfTokenRepository` porque não usa
  cookie. Se algum dia passar a autenticar por cookie, o CSRF **precisa**
  ser religado — está comentado no código exatamente onde fica.
- Falta um `Content-Security-Policy` (CSP). Ele é mais relevante para
  aplicações que servem HTML; esta API serve JSON.

---

### A06 — Componentes vulneráveis

> *Bibliotecas com falhas de segurança conhecidas.*

**O que o projeto faz**

- Dependências enxutas: apenas o que é usado. `spring-boot-starter-web`,
  `-data-jpa`, `-validation`, `-security`, `-crypto`, `-actuator`,
  `postgresql`, `lombok`, `spring-security-test` (só em testes).
- O **starter de segurança** foi adicionado deliberadamente para poder
  *desligar* a autenticação por perfil — e a dependência `crypto` (sem
  autenticação automática) é usada para o BCrypt.
- O Spring Boot 2.7.0 mantém as dependências transitivas alinhadas e
  corrigidas dentro da sua linha.

**Como verificar**

```bash
mvn dependency:tree          # ver a árvore completa
mvn org.owasp:dependency-check-maven:check   # (requer rede; CVE scan)
```

**O que NÃO é coberto**

Não há verificação automática de CVEs no pipeline (o `dependency-check`
precisa de acesso à base NVD e de uma chave de API). **É a primeira
coisa a adicionar** num projeto real.

---

### A07 — Falhas de identificação e autenticação

> *Falha em identificar ou autenticar o usuário.*

Este é o item mais desenvolvido do projeto. Detalhamento em
[seção 3](#3-os-controles-um-a-um).

**Resumo**

- Senha com BCrypt (custo 10) e salt.
- HTTP Basic, ativado por perfil de produção.
- **Limite de tentativas de login** (5 por usuário / 20 por IP, janela
  de 15 minutos), com resposta 429 e cabeçalho `Retry-After`.
- **Bloqueio por IP** antes mesmo de validar a senha (economiza CPU e
  encurta o ataque).
- **Resposta idêntica** para "usuário inexistente" e "senha errada" —
  impede enumeração de usuários.
- **Trilha de auditoria** de cada tentativa, com sucesso e com falha.

---

### A08 — Falhas de integridade de software e dados

> *Dados e software podem ser alterados sem detecção.*

**O que o projeto faz**

- **Trilha de auditoria** de todas as operações que alteram dados
  (`POST`, `PUT`, `PATCH`, `DELETE`): método, caminho, usuário, IP,
  status e duração.
- **Trilha de auditoria das autenticações**: sucesso, falha e bloqueio.
- **Rastreabilidade nas entidades**: `createdAt` e `updatedAt` em todas
  as tabelas, preenchidos por `@PrePersist`/`@PreUpdate`.
- **O corpo da requisição nunca é registrado em log** — ver
  [seção 3.5](#35-auditoria-o-que-e-e-o-que-não-e-registrado).

**Onde**

- `config/AuditTrailFilter.java`
- `config/AuditedBasicAuthenticationFilter.java`
- `entity/*.java` (os campos `createdAt`/`updatedAt`)

---

### A09 — Falhas de registro e monitoramento

> *Eventos não são registrados, ou não são detectados.*

**O que o projeto faz**

- **Log estruturado** com prefixo `AUDITORIA |`, o que permite filtrar
  com `grep AUDITORIA` e depois encaminhar para um SIEM.
- Nível `INFO` para sucesso, `WARN` para falha e bloqueio, `ERROR` para
  erro de servidor.
- **Endpoint de saúde** em `/actuator/health`, lido por Docker ou
  Kubernetes para saber se a aplicação está viva. Esse endpoint é
  **protegido do rate limiting** de propósito (explicado em
  `BruteForceProtectionFilter`): se devolvesse 429 durante um ataque, o
  orquestrador reiniciaria o contêiner e o ataque de senha viraria um
  ataque de negação de serviço.

**O que NÃO é coberto**

- **Não há alerta automático.** O log é gravado, mas ninguém é avisado
  quando há 50 falhas de login em um minuto. Num ambiente real, o
  próximo passo seria um alerta por contagem de linhas no log
  (Prometheus + Alertmanager, ou Loki + Grafana).
- **Não há correlação de requisições.** Um identificador por requisição
  (o `X-Request-Id`) ajudaria a amarrar as várias linhas de log de uma
  mesma operação.

---

### A10 — SSRF

> *O servidor é usado para alcançar recursos internos, por conta de dados
> controlados por quem faz a requisição.*

**O que o projeto faz**

- **A aplicação não faz requisição HTTP de saída em lugar nenhum.** Não
  há integração com API de terceiro, não há download de arquivo por
  URL, não há webhook. Sem superfície de SSRF, sem a vulnerabilidade.

**O que NÃO é coberto**

- O campo `site` da empresa aceita uma URL. Ele é **apenas gravado e
  exibido** — nunca é acessado pelo servidor. Se um dia a aplicação
  buscasse essa URL para gerar uma pré-visualização, ela passaria a ter
  uma superfície de SSRF e precisaria de uma lista de destinos
  permitidos.

---

## 3. Os controles, um a um

### 3.1 Autenticação

**Mecanismo:** HTTP Basic (RFC 7617).

**Por que HTTP Basic e não sessão ou JWT:**

| Opção | Prós | Contras | Escolhido? |
|---|---|---|---|
| **HTTP Basic** | Simples, padronizado, sem estado no servidor | Reenvia credenciais a cada requisição (precisa de TLS) | **Sim** |
| Sessao (cookie) | Token unico, revogavel | Exige estado no servidor, exige CSRF, exige cookie | Não |
| JWT | Stateless, escalável | Exige refresh token, blacklist, rotação de chaves | Não (ver seção 4) |

O critério de escolha foi o enunciado: a avaliação pede endpoints
abertos por padrão e a possibilidade de ligar a segurança sem reescrever
o código. O HTTP Basic com chave de configuração atende a ambos com o
menor número de peças.

**Como liga e desliga:**

```bash
# Desenvolvimento/avaliação (padrão) — endpoints abertos
SECURITY_ENABLED=false

# Produção — tudo exige credencial
SECURITY_ENABLED=true
SECURITY_USERNAME=admin
SECURITY_PASSWORD=<senha forte>
```

O que muda entre os dois modos está concentrado em **um único `if`**, em
`SecurityConfig.securityFilterChain` — e não espalhado por 40 classes.

### 3.2 Senha: BCrypt

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(10);
}
```

- **Salt**: sorteado por hash, embutido no resultado.
- **Custo 10**: ~2^10 iterações, ~100 ms por verificação.
- **`BCryptPasswordEncoder` faz o `equals` de forma segura** (tempo
  constante), o que evita que o atacante descubra a senha medindo o
  tempo de resposta.

### 3.3 Controle anti-força-bruta

**Arquivos:** `config/LoginAttemptService.java`,
`config/BruteForceProtectionFilter.java`, `config/AuthenticationAuditHandler.java`.

**As duas dimensões do limite:**

| Limite | Padrão | Protege |
|---|---|---|
| Falhas do mesmo **usuário** (no mesmo IP) | 5 | A **conta** do usuário |
| Falhas do mesmo **IP** (somando todos os usuários) | 20 | A **aplicação** |

O limite por IP existe porque o limite por usuário, sozinho, não
protege o servidor: um atacante poderia tentar 5 senhas para 1.000
usuários diferentes a partir do mesmo IP, e o limite individual nunca
seria estourado.

**Fluxo de uma requisição com credencial errada:**

```
requisição
    │
    ├─► BruteForceProtectionFilter
    │      IP já bloqueado? ── SIM ──► 429 (Retry-After), nem valida a senha
    │            │
    │           NÃO
    │            ▼
    ├─► AuditedBasicAuthenticationFilter
    │      senha conferida com BCrypt
    │      falhou? ──► AuthenticationAuditHandler
    │                      conta a falha
    │                      conta já bloqueada? ── SIM ──► 429
    │                                            NÃO ──► 401 (JSON)
    │
    ▼
FiltroSecurityInterceptor ──► sem autenticação ──► AccessDeniedException
    │
    ▼
ExceptionTranslationFilter ──► JsonAuthenticationEntryPoint
                                   escreve 401 ou 429 (JSON)
```

**Três decisões de projeto que valem explicar:**

1. **O filtro de bloqueio roda ANTES do de autenticação.** Se rodasse
   depois, o atacante continuaria gastando CPU com o BCrypt mesmo com a
   conta bloqueada — a defesa existiria, mas não serviria para nada.

2. **A resposta de acesso negado tem um único escritor.** No Spring
   Security 5.7, o `BasicAuthenticationFilter` **não interrompe** a cadeia
   de filtros quando recusa uma credencial: a requisição segue até o
   `FilterSecurityInterceptor`, que recusa, e o `ExceptionTranslationFilter`
   chama o `AuthenticationEntryPoint`. Se o handler de falha de
   autenticação *também* escrevesse a resposta, teríamos dois JSONs
   colados no mesmo corpo e o status 401 sobrescreveria o 429.
   Por isso o handler de falha apenas **marca a requisição**
   (`request.setAttribute(...)`) e o `JsonAuthenticationEntryPoint` é o
   único que escreve.

3. **O IP só é confiável atrás de proxy.** O cabeçalho `X-Forwarded-For`
   pode ser enviado por qualquer cliente — se fosse confiado sem
   condição, o atacante burlaria o bloqueio trocando o valor a cada
   tentativa. Por isso, `app.security.trust-proxy` fica `false` por
   padrão e só é ligado no perfil de produção, junto com a exigência de
   que a aplicação não seja alcançável sem passar pelo proxy.

### 3.4 Enumeração de usuários

Se a API devolvesse "usuário não encontrado" para um caso e "senha
incorreta" para outro, um atacante montaria uma lista de usuários
válidos e só precisaria adivinhar as senhas. A resposta é **sempre a
mesma**:

```json
{ "status": 401, "erro": "Nao autorizado", "mensagem": "Credenciais invalidas." }
```

O detalhe real fica no log do servidor, onde só a TI chega. Isso é
verificado por um teste automatizado (`SegurancaIntegracaoTest`),
comparando o corpo das duas respostas.

### 3.5 Auditoria: o que é e o que **não** é registrado

**Registrado:**

```
AUDITORIA | AUTENTICACAO_CONFIRMADA | usuario=admin ip=10.0.0.1
AUDITORIA | LOGIN_FALHOU | usuario=admin ip=10.0.0.1 motivo=BadCredentialsException
AUDITORIA | LOGIN_BLOQUEADO | usuario=admin ip=10.0.0.1 segundos_ate_liberar=899
AUDITORIA | ACESSO_NEGADO | ip=10.0.0.1 caminho=/empresas motivo=CONTA_BLOQUEADA
AUDITORIA | MUTACAO | metodo=POST caminho=/empresas usuario=admin ip=10.0.0.1 status=201 duracao_ms=45
```

**NÃO registrado — e essa parte é a mais importante:**

- **O corpo da requisição.** Seria fácil registrar, e seria um erro grave:
  o cadastro de empresa recebe o campo `senha` no corpo. Se o log
  guardasse o corpo, a senha em texto claro passaria a estar gravada em
  um arquivo de log — um lugar muito mais acessível e muito menos
  protegido que o banco de dados.
- **A query string** (`?senha=...`). É comum um desenvolvedor colocar
  parâmetro sensível na URL por descuido.
- **A senha e o token**, em qualquer hipótese.

O filtro registra apenas **metadados**: método, caminho, usuário, IP,
status e duração.

### 3.6 Validação de entrada

Duas camadas, com papéis diferentes:

| Camada | Ferramenta | Papel |
|---|---|---|
| Frontend | `utils/mascaras.js` | **Experiência** — erro na hora, sem perder o que foi digitado |
| Backend | Bean Validation (`@Valid` + `@NotNull`, `@Size`, `@Min`, `@Pattern`, `@Email`) | **Garantia** — é o único lugar que pode garantir |

O frontend valida porque é mais confortável para o usuário. O backend
valida porque qualquer um pode chamar a API direto, sem passar pela tela:

```bash
curl -X POST http://localhost:8081/unidades \
  -H "Content-Type: application/json" \
  -d '{"nome":""}'
# → HTTP 400, o corpo nem chega na camada de negócio
```

### 3.7 Proteção do banco de dados

`sql/seguranca/03_permissoes.sql` (opcional) cria um usuário de banco
com **menor privilégio**: ele pode fazer `SELECT`, `INSERT`, `UPDATE` e
`DELETE` nas tabelas do projeto, e **não** pode:

- derrubar o banco (`DROP DATABASE`);
- alterar a estrutura (`ALTER TABLE`);
- criar outros usuários (`CREATE USER`);
- ler os outros bancos do servidor.

Sem isso, a aplicação conecta como superusuário `postgres` e, se for
invadida (um SQL Injection, uma dependência maliciosa), o atacante
herda todo esse poder. Com isso, o dano fica limitado às tabelas do
projeto.

### 3.8 Menos privilégio no sistema operacional

A API **não roda como root**. No `Dockerfile`, o processo Java roda com
o usuário não privilegiado `app`. Isso reduz o estrago de uma possível
exploração de uma vulnerabilidade na JVM ou em uma biblioteca.

---

## 4. O que o projeto NÃO protege

Esta seção é a mais importante do documento. Um relatório honesto vale
mais que um relatório bonito.

### 4.1 Não há autorização por perfil (RBAC)

Com a autenticação ligada, **qualquer** usuário autenticado acessa
**todas** as rotas. Não existe "administrador" e "usuário comum".

**Como seria feito:** anotação `@PreAuthorize("hasRole('ADMIN')")` nos
métodos, com um `GrantedAuthority` atribuído no `UserDetailsService`. O
custo é baixo e o Spring Security já dá o suporte.

### 4.2 O controle de tentativas vive na memória do processo

`LoginAttemptService` guarda os contadores em um `ConcurrentHashMap`, na
RAM. Consequências:

- Com **3 instâncias** da aplicação, o atacante ganha 3× o limite.
- Um **restart** zera os contadores.

**Como seria feito em produção:** guardar o estado no **Redis**, com TTL
igual à janela. A interface do serviço já está isolada para isso — só
trocar o `Map` por um `RedisTemplate`. Nenhuma outra classe mudaria.

### 4.3 Não há TLS dentro da aplicação

O HTTPS é responsabilidade do proxy reverso ou da plataforma de nuvem.
O HSTS já está configurado para instruir o navegador, mas ele só tem
efeito se o TLS existir na frente.

> **Nota importante:** com HTTP Basic **sem TLS**, as credenciais viajam
> codificadas em Base64 — que **não é criptografia**. É por isso que o
> perfil de produção **não pode** ser usado atrás de HTTP puro.

### 4.4 Não há token, rotação nem revogação

HTTP Basic não tem token de acesso: a senha é reenviada a cada
requisição. Consequências: não há como revogar "aquela sessão" sem
trocar a senha, e não há expiração.

### 4.5 Não há verificação automática de CVEs

As dependências não são varridas por vulnerabilidades conhecidas no
pipeline. É a primeira coisa a adicionar.

### 4.6 Não há alerta automático de incidentes

O log registra tudo, mas nada **observa** o log e avisa. Um pico de
falhas de autenticação passaria despercebido até alguém olhar.

### 4.7 Não há campo de "tentativas de login" no banco

O controle é em memória, não persistido. Não há, portanto, um registro
durável de "tentativas de login desta conta nos últimos 30 dias".

### 4.8 O controle anti-força-bruta é opcional por padrão

Ele só age quando `SECURITY_ENABLED=true` (perfil de produção). No modo
de avaliação, os endpoints estão abertos — como o enunciado exige. Isso
é uma decisão consciente, não um esquecimento, e está documentado no
`README`.

### 4.9 Não há rate limiting nas rotas de negócio

O limite de tentativas protege a **autenticação**. Não há limite de
requisições por IP nas rotas de negócio (o que exigiria um filtro
adicional ou um bucket do Redis).

---

## 5. Checklist de produção

O que fazer antes de colocar isso na internet de verdade:

- [ ] **TLS** no proxy, com certificado válido (e HSTS ativo).
- [ ] `SECURITY_ENABLED=true` e `SECURITY_PASSWORD` de verdade forte
      (não o padrão `troque-esta-senha`).
- [ ] `DATABASE_PASSWORD` forte e **diferente** do usuário do banco.
- [ ] Aplicar `sql/segurenca/03_permissoes.sql` e trocar a conexão para
      o usuário de menor privilégio.
- [ ] A porta da aplicação **não** exposta publicamente — só o proxy
      alcança (é o que torna `trust-proxy=true` seguro).
- [ ] `trust-proxy=true` **somente** se essa condição for verdadeira.
- [ ] Mover o controle de tentativas para o Redis (múltiplas instâncias).
- [ ] Centralizar os logs e criar alerta por taxa de falha de login.
- [ ] `dependency-check` no pipeline.
- [ ] Backup do banco com teste de restauração periódico.
- [ ] Definir `app.cors.origins` com os domínios reais (nunca `*`).

---

## 6. Como verificar cada proteção

Todos os comandos abaixo rodam com a API no ar (perfil de produção).

### 6.1 Autenticação e resposta padronizada

```bash
# Sem credencial → 401 em JSON
curl -i http://localhost:8081/unidades

# Credencial errada → 401 em JSON
curl -i -u admin:senha-errada http://localhost:8081/unidades

# Credencial certa → 200 com o dado do controller
curl -i -u admin:$SENHA_CORRETA http://localhost:8081/unidades
```

### 6.2 Controle de força bruta

```bash
for i in $(seq 1 6); do
  curl -s -o /dev/null -w "tentativa $i -> HTTP %{http_code}\n" \
       -u admin:senha-errada http://localhost:8081/unidades
done

# Esperado: cinco 401 e depois 429.
curl -s -D - -o /dev/null -u admin:senha-errada http://localhost:8081/unidades | grep -i retry-after
# Retry-After: 899
```

### 6.3 Enumeração de usuários (respostas idênticas)

```bash
curl -s -u inexistente:x http://localhost:8081/unidades
curl -s -u admin:x        http://localhost:8081/unidades
# As duas respostas devem ser byte a byte iguais.
```

> **Atenção:** rode este teste em uma API **recem-iniciada**. Se a conta
> `admin` já estiver bloqueada por tentativas anteriores, ela responderá
> **429** enquanto a conta inexistente responderá **401** — e as respostas
> serão diferentes por um motivo legítimo (a conta está bloqueada), não
> por vazamento de informação. Para um estado limpo, reinicie a API.
>
> O teste automatizado `SegurancaIntegracaoTest` já cuida disso: ele
> zera os contadores antes de cada caso.

### 6.4 Validação de entrada

```bash
curl -i -X POST http://localhost:8081/unidades \
  -H "Content-Type: application/json" -d '{"nome":""}'
# → HTTP 400
```

### 6.5 Headers de segurança

```bash
curl -s -D - -o /dev/null http://localhost:8081/unidades | grep -iE \
  "x-frame|x-content-type|referrer|cache-control|x-powered-by|strict-transport"
```

Esperado: `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`,
`Referrer-Policy: no-referrer`, `Cache-Control: no-store...` e **nenhuma**
linha `X-Powered-By`.

### 6.6 Auditoria

```bash
# durante a API no ar, em outro terminal
tail -f log.txt | grep AUDITORIA
```

### 6.7 Suíte de testes automatizados

```bash
cd leilao-api && mvn test
```

Os testes de segurança (`SegurancaIntegracaoTest`) verificam
automaticamente os itens 6.1 a 6.3.

---

## 7. Modelo de ameaça

Para mostrar que as decisões foram pensadas, e não copiadas:

| Atacante | Objetivo | Vetor | Defesa |
|---|---|---|---|
| Curioso na internet | Ler dados | API aberta | `SECURITY_ENABLED=true` + TLS |
| Automação de varredura | Descobrir usuários | Enumeração | Resposta idêntica + rate limit |
| Atacante com dicionário | Entrar como admin | Força bruta | Limite de tentativas + BCrypt |
| Atacante distribuído | Sair do limite por IP | Muitos IPs | Limite por **usuário** (5 falhas) |
| Desenvolvedor hostil | SQL Injection | Parâmetro malicioso | JPA parametrizado + validação |
| Site terceiro | Ler dados via navegador | Requisição cross-origin | CORS fechado |
| Página maliciosa | Roubo via iframe | Clickjacking | `X-Frame-Options: DENY` |
| Curioso com o banco | Ler tudo | Roubo de backup | BCrypt + menor privilégio |
| Invasor da aplicação | Escalar no banco | SQL Injection | Usuário de menor privilégio |

---

## 8. Referências

- OWASP Top 10 (2021) — <https://owasp.org/Top10/>
- OWASP Cheat Sheet Series — <https://cheatsheetseries.owasp.org/>
- RFC 7617 — *The 'Basic' HTTP Authentication Scheme*
- Spring Security Reference — <https://docs.spring.io/spring-security/reference/>
- NIST SP 800-63 — *Digital Identity Guidelines*

---

**Documento relacionado:** [`README.md`](README.md) (visão geral do
projeto) · [`TECNOLOGIAS.md`](TECNOLOGIAS.md) (guia das tecnologias) ·
[`MANUAL-OPERACAO.md`](MANUAL-OPERACAO.md) (como rodar) ·
[`ENTREVISTA.md`](ENTREVISTA.md) (banco de perguntas)
