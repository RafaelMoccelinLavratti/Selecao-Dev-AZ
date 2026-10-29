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
| Senha nunca em texto puro (**Argon2id** + salt, com migração automática de BCrypt) | **Implementado** | `config/CodificadorDeSenhaDelegado.java`, `config/PasswordEncoderConfig.java` |
| **JWT assimétrico (RS256)** com rotação de chaves (`kid`) | **Implementado (opt-in por perfil)** | `config/ChaveJwtProvider.java`, `business/TokenJwtBO.java` |
| **Rotação de token de renovação com detecção de reuso** | **Implementado** | `business/AutenticacaoBO.java` |
| Validação de entrada no servidor (Bean Validation) | **Implementado** | `dto/*Request.java` + `@Valid` nos controllers |
| Proteção contra SQL Injection (JPA/JPQL parametrizado) | **Implementado** | `repository/*.java` |
| CORS fechado (lista de origens) | **Implementado** | `config/CorsConfig.java` |
| Headers de segurança (CSP, COOP, CORP, Permissions-Policy, HSTS) | **Implementado** | `config/SecurityHeadersFilter.java`, `config/SecurityConfig.java` |
| Sem vazamento de stack trace nas respostas | **Implementado** | `exception/GlobalExceptionHandler.java` |
| Segredos fora do Git (variáveis de ambiente) | **Implementado** | `application.properties`, `application-prod.properties` |
| Autenticação HTTP Basic **ou** JWT, pelo mesmo código | **Implementado (opt-in por perfil)** | `config/SecurityConfig.java` |
| Controle anti-força-bruta (limite de tentativas) | **Implementado (opt-in por perfil)** | `config/LoginAttemptService.java` |
| **Limitador de volume por IP (rate limiting, janela deslizante)** | **Implementado** | `config/LimitadorDeRequisicoesFilter.java` |
| **Trilha de auditoria à prova de adulteração (cadeia HMAC)** | **Implementado** | `business/AuditoriaBO.java`, `entity/Auditoria.java` |
| **Identificador de correlação em toda requisição** | **Implementado** | `config/CorrelationIdFilter.java` |
| **SBOM (Ciclo de Vida das Dependências) gerado a cada build** | **Implementado** | `pom.xml` (cyclonedx-maven-plugin) |
| **Verificação automática de CVEs (CVSS ≥ 7)** | **Implementado** | `pom.xml` (perfil `security-scan`) |
| **Atualização automática de dependências** | **Implementado** | `.github/dependabot.yml` |
| **Pipeline de CI (build + testes + lint)** | **Implementado** | `.github/workflows/ci.yml` |
| **Política de segurança e canal privado de reporte** | **Implementado** | `.github/SECURITY.md` |
| Menor privilégio no banco de dados | **Script pronto (opcional)** | `sql/seguranca/03_permissoes.sql` |
| TLS/HTTPS em trânsito | **Fora do escopo da aplicação** | responsabilidade do proxy/infra |
| Segredos em cofre (Vault / AWS Secrets Manager) | **Não implementado** | ver [seção 4](#4-o-que-o-projeto-não-protege) |

> **A grande decisao de projeto:** a autenticação e o controle de força bruta
> existem, mas **ligados por configuração**, e desligados por padrão.
> O enunciado da avaliação exige que `GET /unidades` responda **sem
> login**. Ao mesmo tempo, "sem segurança nenhuma" seria irresponsável.
> A solução é ter os dois caminhos no mesmo código, e o perfil de
> produção liga a segurança.

> **A segunda grande decisão:** a proteção de senha foi **reforçada sem
> quebrar ninguém**. O projeto nasceu com BCrypt; hoje grava em Argon2id
> (padrão OWASP 2023). A troca foi feita por **delegação**: o
> codificador identifica o algoritmo do hash já gravado, continua
> validando os hashes antigos e os **reescreve sozinhos no primeiro
> login**. Nenhum usuário foi trancado fora do sistema — detalhe em
> [seção 3.2](#32-senha-argon2id-com-migração-automática-de-bcrypt).

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
  pelo `PasswordEncoder`, que hoje é o **`CodificadorDeSenhaDelegado`** —
  um codificador que grava em **Argon2id** e sabe ler os hashes antigos
  em BCrypt.
- **Argon2id, o padrão da OWASP.** O Argon2id usa **memória** como
  recurso principal (64 MiB por hash, 2 iterações, sal de 16 bytes).
  Um ataque com GPU — que tem milhares de núcleos e muita memória — tem
  dificuldade grande de paralelizar ataques ao Argon2, porque cada
  tentativa consome dezenas de megabytes. O BCrypt tem custo
  computacional, mas usa pouca memória, e GPUs são excellentes em
  calcular em massa.
- **Migração automática, sem quebrar ninguém.** O codificador identifica
  o algoritmo pelo formato do hash gravado: `{argon2}$argon2id$…` vai
  para o Argon2id, `$2a$…` (BCrypt antigo, sem prefixo) vai para o
  BCrypt. Ao mesmo tempo, `upgradeEncoding` sinaliza que o hash antigo
  deve ser reescrito — e o sistema o reescreve **no primeiro login**.
  A base migra sozinha, no ritmo do uso. Não existe janela de migração,
  nem script de conversão em lote, nem risco de trancar usuário fora.
- **Salt sorteado a cada hash e embutido no resultado.** Duas empresas
  com a mesma senha ficam com hashes **diferentes** — o que impede que
  um vazamento revele quais usuários compartilham senha.
- **A senha não volta na resposta.** O `EmpresaResponse` não tem campo
  `senha`. Nem o hash, nem o texto. Nos DTOs de saída, o que a API não
  precisa mostrar simplesmente não existe.
- **Segredos fora do código.** Usuário, senha e string de conexão vêm de
  variáveis de ambiente (`SECURITY_USERNAME`, `SECURITY_PASSWORD`,
  `DATABASE_PASSWORD`...). O valor padrão no `application.properties` é
  existe apenas para desenvolvimento.
- **Chave de assinatura assimétrica.** Os tokens JWT são assinados com
  **RS256** (chave privada) e verificados com a **chave pública**. Com
  uma chave simétrica, quem valida um token poderia forjar um — e o
  "segredo" acabaria exposto em todo serviço que valida o token.

**Onde**

- `config/CodificadorDeSenhaDelegado.java` — a delegação entre algoritmos
- `config/PasswordEncoderConfig.java` — o bean e os parâmetros do Argon2id
- `config/ChaveJwtProvider.java` — as chaves RSA e a rotação
- `business/TokenJwtBO.java` — emissão e validação do token
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
- **A chave HMAC da auditoria, sem cofre, é uma variável de ambiente.**
  Ela não está num gerenciador de segredos; está no ambiente, como as
  demais. Um cofre (Vault, AWS Secrets Manager) seria o próximo passo.

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
| `Content-Security-Policy` | `default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'` | `SecurityHeadersFilter` |
| `Permissions-Policy` | todas as APIs de navegador desligadas (câmera, microfone, geolocalização…) | `SecurityHeadersFilter` |
| `Cross-Origin-Opener-Policy` | `same-origin` (anti tabnabbing) | `SecurityHeadersFilter` |
| `Cross-Origin-Resource-Policy` | `same-origin` (impede consumo da API por outro site) | `SecurityHeadersFilter` |
| `Cross-Origin-Embedder-Policy` | `require-corp` | `SecurityHeadersFilter` |
| `Referrer-Policy` | `no-referrer` | `SecurityConfig` |
| `Cache-Control` | `no-store, no-cache, must-revalidate` | `SecurityConfig` |
| HSTS | 1 ano, incluindo subdomínios | `SecurityConfig` |
| Frameworks expostos | `X-Powered-By` removido | `application.properties` |
| Swagger / console H2 | não existem no projeto | — |
| Endpoint de erro | página de erro genérica, sem stack trace | `application.properties` |
| Segredos de auditoria | chave HMAC por variável de ambiente, fora do banco | `application.properties` |

**O que NÃO é coberto**

- O projeto **não** usa `CookieCsrfTokenRepository` porque não usa
  cookie. Se algum dia passar a autenticar por cookie, o CSRF **precisa**
  ser religado — está comentado no código exatamente onde fica.
- O `Content-Security-Policy` é restritivo (`default-src 'none'`), o que
  é correto para uma API que devolve JSON. Se a API passasse a servir
  HTML (uma tela de login, por exemplo), a diretiva precisaria ser
  relaxada com cuidado — e o `frame-ancestors 'none'` mantido.

---

### A06 — Componentes vulneráveis

> *Bibliotecas com falhas de segurança conhecidas.*

**O que o projeto faz**

- Dependências enxutas: apenas o que é usado. `spring-boot-starter-web`,
  `-data-jpa`, `-validation`, `-security`, `-crypto`, `-actuator`,
  `postgresql`, `jjwt` (tokens), `bcprov` (Argon2id), `lombok`,
  `spring-security-test` (só em testes).
- O **starter de segurança** foi adicionado deliberadamente para poder
  *desligar* a autenticação por perfil — e a dependência `crypto` (sem
  autenticação automática) é usada para codificar a senha.
- O Spring Boot 2.7.0 mantém as dependências transitivas alinhadas e
  corrigidas dentro da sua linha.
- **SBOM gerado a cada build.** O `cyclonedx-maven-plugin` produz
  `target/bom.json`, o *Software Bill of Materials* — o inventário de
  tudo que está dentro do artefato. Com ele, quando uma vulnerabilidade
  aparece em uma biblioteca (o Log4Shell foi o alerta mais famoso),
  responde-se em segundos "usamos essa versão?".
- **Varredura automática de CVEs.** O perfil `security-scan` roda o
  `dependency-check-maven` e **falha o build** quando encontra uma
  vulnerabilidade de CVSS ≥ 7.
- **Atualização automática de dependências.** O Dependabot abre pull
  requests semanais com as versões corrigidas — Maven, npm e GitHub
  Actions.

**Como verificar**

```bash
mvn dependency:tree          # ver a árvore completa
mvn package -DskipTests      # gera o SBOM em target/bom.json

# Varredura de CVEs (precisa de rede e da chave da NVD)
export NVD_API_KEY=<sua-chave>
mvn -Psecurity-scan verify
```

**O que NÃO é coberto**

- A varredura de CVEs **não roda no pipeline padrão**, porque a base NVD
  exige chave de API e uma primeira carga que leva horas. O perfil
  `security-scan` existe para ser executado sob demanda (ou em um
  pipeline noturno, com a chave em segredo do CI).

---

### A07 — Falhas de identificação e autenticação

> *Falha em identificar ou autenticar o usuário.*

Este é o item mais desenvolvido do projeto. Detalhamento em
[seção 3](#3-os-controles-um-a-um).

**Resumo**

- Senha com **Argon2id** (64 MiB, 2 iterações, salt de 16 bytes) e
  migração automática de BCrypt.
- **Dois modos de autenticação pelo mesmo código**: HTTP Basic (padrão) ou
  JWT — trocados por **uma propriedade**.
- **JWT RS256** com rotação de chaves via `kid`, token de acesso de
  15 min e token de renovação de 7 dias.
- **Rotação do token de renovação com detecção de reuso**: um token
  usado de novo revoga a família inteira.
- **Limite de tentativas de login** (5 por usuário / 20 por IP, janela
  de 15 minutos), com resposta 429 e cabeçalho `Retry-After`.
- **Bloqueio por IP** antes mesmo de validar a senha (economiza CPU e
  encurta o ataque).
- **Resposta idêntica** para "usuário inexistente" e "senha errada" —
  impede enumeração de usuários.
- **Trilha de auditoria** de cada tentativa, com sucesso e com falha.
- **Limitador de volume por IP** em todas as rotas (janela deslizante).

---

### A08 — Falhas de integridade de software e dados

> *Dados e software podem ser alterados sem detecção.*

**O que o projeto faz**

- **Trilha de auditoria** de todas as operações que alteram dados
  (`POST`, `PUT`, `PATCH`, `DELETE`): método, caminho, usuário, IP,
  status e duração.
- **Trilha de auditoria das autenticações**: sucesso, falha e bloqueio.
- **Trilha à prova de adulteração.** A trilha não é um log comum: cada
  registro é **selado por HMAC-SHA256**, encadeado no registro anterior
  (`hash_N = HMAC(chave, sequência_N ‖ hash_{N-1} ‖ conteúdo_N)`).
  Quem tiver acesso ao banco, mas não à chave, **não consegue forjar um
  registro válido** — e qualquer alteração ou remoção no meio da cadeia
  é detectada, com a indicação exata de onde a cadeia foi rompida.
  Detalhe em [seção 3.5](#35-auditoria-a-prova-de-adulteração).
- **Rastreabilidade nas entidades**: `createdAt` e `updatedAt` em todas
  as tabelas, preenchidos por `@PrePersist`/`@PreUpdate`.
- **O corpo da requisição nunca é registrado em log** — ver
  [seção 3.6](#36-auditoria-o-que-e-e-o-que-não-e-registrado).

**Onde**

- `business/AuditoriaBO.java` — o selo e a verificação da cadeia
- `entity/Auditoria.java` — a estrutura do registro selado
- `repository/AuditoriaRepository.java` — o encadeamento
- `service/AuditoriaService.java` — `GET /auditoria` e `GET /auditoria/verificar`
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
- **Identificador de correlação em todas as requisições.** Cada requisição
  recebe um UUID (`X-Correlation-Id`), que aparece em **todas** as linhas
  de log daquela requisição (via MDC) e volta no cabeçalho de resposta.
  O usuário que vê um erro na tela passa o código ao suporte, e o time
  localiza a requisição exata com um `grep`. O valor recebido do cliente
  é **saneado** (só alfanuméricos, `-` e `_`), para impedir injeção de
  linhas falsas no log (*log forging*).
- **Trilha de auditoria persistente e verificável**, com endpoint
  público de verificação (`GET /auditoria/verificar`).
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
- **A trilha de auditoria é selada, mas não é um cofre.** A chave HMAC
  está no ambiente, como as demais credenciais. Quem tiver acesso ao
  processo **e** à chave ainda poderia reescrever a cadeia inteira — o
  que exigiria um cofre de segredos ou um HSM para fechar o circuito.

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

**Dois mecanismos, o mesmo código.** A escolha é feita por **uma
propriedade** (`app.security.mode`), e não por uma reescrita.

| Modo | Como autentica | Quando usar |
|---|---|---|
| `basic` (padrão) | HTTP Basic (RFC 7617) — cabeçalho `Authorization: Basic …` | Operação simples, um servidor, time pequeno |
| `jwt` | `POST /auth/login` devolve um par de tokens; o acesso vai em `Authorization: Bearer …` | Várias réplicas da API, mobile, SPA |

**Por que os dois:**

| Opção | Prós | Contras | Escolhido? |
|---|---|---|---|
| **HTTP Basic** | Simples, padronizado, sem estado no servidor | Reenvia credenciais a cada requisição (precisa de TLS) | **Sim** (padrão) |
| **JWT** | Stateless, escala horizontalmente, token de curta duração | Exige refresh token, rotação de chaves, revogação | **Sim** (alternativo) |
| Sessão (cookie) | Token único, revogável | Exige estado no servidor, exige CSRF, exige cookie | Não |

O critério de escolha inicial foi o enunciado: a avaliação pede
endpoints abertos por padrão e a possibilidade de ligar a segurança sem
reescrever o código. O HTTP Basic com chave de configuração atendia a
ambos com o menor número de peças. O modo JWT foi acrescentado depois
porque é o que uma arquitetura de microsserviços realmente exige — e,
como os dois produzem **o mesmo objeto de autenticação** no contexto de
segurança, as regras de acesso (`authorizeRequests`) são **idênticas**
nos dois casos.

**Como liga e desliga:**

```bash
# Desenvolvimento/avaliação (padrão) — endpoints abertos
SECURITY_ENABLED=false

# Produção — tudo exige credencial
SECURITY_ENABLED=true
SECURITY_USERNAME=admin
SECURITY_PASSWORD=<senha forte>

# Modo alternativo: token JWT em vez de HTTP Basic
SECURITY_MODE=jwt
```

O que muda entre os modos está concentrado em **um único `if`**, em
`SecurityConfig.securityFilterChain` — e não espalhado por 40 classes.

### 3.2 Senha: Argon2id com migração automática de BCrypt

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new CodificadorDeSenhaDelegado(
            comprimentoSalArgon2,   // 16 bytes
            tamanhoHashArgon2,      // 32 bytes
            paralelismoArgon2,      // 1
            memoriaArgon2,          // 65.536 KB = 64 MiB
            iteracoesArgon2);       // 2
}
```

**Por que Argon2id e não BCrypt:**

| | BCrypt | Argon2id |
|---|---|---|
| Recurso principal | CPU | **Memória** (64 MiB por hash) |
| Contra GPU | Bom | **Muito melhor** (cada tentativa consome dezenas de MB) |
| Contra ASIC | Bom | **Muito melhor** |
| Recomendação OWASP 2023 | Segunda opção | **Primeira opção** |

**A migração, que é a parte interessante.** Trocar o encoder "de um dia
para o outro" quebraria o acesso de todo mundo cuja senha ainda
estivesse em BCrypt. O `CodificadorDeSenhaDelegado` resolve:

```
hash no banco              →  como a senha é conferida
─────────────────────────────────────────────────────
{argon2}$argon2id$…        →  Argon2id
{bcrypt}$2a$10$…           →  BCrypt
$2a$10$… (sem prefixo)     →  BCrypt   ← formato antigo do projeto
$argon2id$… (sem prefixo)  →  Argon2id
```

E, ao gravar, usa **sempre** o Argon2id. Quando o Spring Security chama
`upgradeEncoding(hash)`, o método devolve `true` para um hash em
BCrypt — e o hash é reescrito no formato novo. **A base migra sozinha,
no ritmo do uso**: sem script de conversão em lote, sem janela de
manutenção, sem risco de trancar ninguém fora.

> **Detalhe de projeto:** o `DelegatingPasswordEncoder` oficial do Spring
> exige que *todos* os hashes gravados tenham o prefixo `{id}` — um hash
> antigo sem prefixo faria ele lançar exceção. Por isso esta classe usa o
> `DelegatingPasswordEncoder` do Spring como **motor** e acrescenta por
> cima a única regra que faltava: reconhecer o hash legado sem prefixo.

**O custo, e por que o anti-força-bruta virou obrigatório.** Argon2id com
64 MiB custa ~100 ms por verificação e **consome memória**. Num login
por segundo, são 64 MB/s de memória — o que exige dimensionar o pool de
conexões e o número de réplicas. E é o motivo pelo qual o controle
anti-força-bruta deixa de ser um extra: sem ele, cada tentativa de login
seria uma alocação de 64 MiB, e o próprio login viraria um vetor de
esgotamento de memória.

- **Salt**: sorteado a cada hash, embutido no resultado.
- **Comparação em tempo constante**, o que evita que o atacante descubra
  a senha medindo o tempo de resposta.

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
    │      senha conferida com Argon2id (ou BCrypt, no hash legado)
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
   depois, o atacante continuaria gastando CPU com o Argon2id mesmo com a
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

### 3.5 Auditoria à prova de adulteração

**Arquivos:** `business/AuditoriaBO.java`, `entity/Auditoria.java`,
`repository/AuditoriaRepository.java`, `service/AuditoriaService.java`,
`config/AuditTrailFilter.java`.

**O problema.** Uma trilha de auditoria comum é um log. Quem tem acesso ao
banco pode apagá-lo — e, num incidente, é exatamente quem a auditoria
precisa investigar. Uma prova que pode ser apagada por quem deveria estar
sendo auditado não serve para nada.

**A solução: uma corrente de hashes.** Cada registro guarda o hash do
registro **anterior**:

```
registro 1   hash = HMAC(chave, sequência_1 ‖ conteúdo_1)
registro 2   hash = HMAC(chave, sequência_2 ‖ hash_1 ‖ conteúdo_2)
registro 3   hash = HMAC(chave, sequência_3 ‖ hash_2 ‖ conteúdo_3)
```

- **HMAC-SHA256**, e não SHA-256 simples. Com hash simples, quem tivesse
  acesso ao banco recalcularia o hash do registro alterado e gravaria o
  resultado — o hash não provaria nada, porque a "chave" seria o próprio
  algoritmo. Com HMAC, a verificação depende de uma **chave secreta que
  fica fora do banco** (variável de ambiente `AUDIT_HMAC_KEY`): quem tem
  o banco, mas não a chave, não consegue forjar um registro válido.
- **A sequência** (`BIGINT` monotônico) é o segundo mecanismo: ela
  detecta **remoção**. Se o registro 5 for apagado, a sequência salta de
  4 para 6, e a verificação acusa o buraco. Hash detecta *alteração*;
  sequência detecta *remoção*.

**Três scenarios, uma verificação:**

| O atacante faz… | Como é detectado |
|---|---|
| Altera o **conteúdo** de um registro | O hash recalculado não bate com o hash gravado |
| Troca o **hash anterior** (forja um registro) | O encadeamento não fecha: o hash anterior não confere |
| **Apaga** um registro do meio | A sequência fica com buraco |

**Como verificar:**

```bash
# A cadeia está íntegra?
curl -s http://localhost:8081/auditoria/verificar

# {"integra":true,"registrosVerificados":42,"totalAnalisado":42,
#  "ultimaSequencia":42,"mensagem":"A cadeia de auditoria esta integra: 42 registro(s) verificado(s)."}

# Se algo foi adulterado, o status é 409 (não 200) e a mensagem aponta o ponto:
# {"integra":false,"sequenciaRompida":17,
#  "mensagem":"Registro adulterado na sequencia 17: o conteudo gravado nao corresponde ao hash selado."}
```

O endpoint é **público de propósito**: um auditor externo não precisa de
credencial de administrador para provar que a trilha não foi mexida.

**Detalhe de robustez:** a gravação da trilha acontece em uma
**transação separada** (`REQUIRES_NEW`) da operação de negócio. Se as
duas dividissem a mesma transação, uma falha ao gravar o registro
derrubaria a operação do usuário — ele perderia o dado *e* não teria
registro. E, se a gravação falhar, o `AuditoriaBO` registra o erro no log
e **não derruba a requisição**: perder o registro é melhor do que perder
a operação.

### 3.6 Auditoria: o que é e o que **não** é registrado

**Registrado:**

```
AUDITORIA | AUTENTICACAO_CONFIRMADA | usuario=admin ip=10.0.0.1
AUDITORIA | LOGIN_FALHOU | usuario=admin ip=10.0.0.1 motivo=BadCredentialsException
AUDITORIA | LOGIN_BLOQUEADO | usuario=admin ip=10.0.0.1 segundos_ate_liberar=899
AUDITORIA | ACESSO_NEGADO | ip=10.0.0.1 caminho=/empresas motivo=CONTA_BLOQUEADA
AUDITORIA | MUTACAO | metodo=POST caminho=/empresas usuario=admin ip=10.0.0.1 status=201 duracao_ms=45
```

E, na tabela `tb_auditoria`, o registro selado (sequência, instante,
usuário, IP, método, caminho, status, duração, ação, hash anterior e
hash atual).

**NÃO registrado — e essa parte é a mais importante:**

- **O corpo da requisição.** Seria fácil registrar, e seria um erro grave:
  o cadastro de empresa recebe o campo `senha` no corpo. Se o log
  guardasse o corpo, a senha em texto claro passaria a estar gravada em
  um arquivo de log — um lugar muito mais acessível e muito menos
  protegido que o banco de dados.
- **A query string** (`?senha=...`). É comum um desenvolvedor colocar
  parâmetro sensível na URL por descuido.
- **A senha e o token**, em qualquer hipótese.
- **Os hashes da cadeia**, na resposta da API. Eles são o mecanismo de
  prova; expô-los permitiria a um cliente tentar forjar registros.

O filtro registra apenas **metadados**: método, caminho, usuário, IP,
status e duração.

### 3.7 Token JWT: emissão, rotação e revogação

**Arquivos:** `config/ChaveJwtProvider.java`, `business/TokenJwtBO.java`,
`business/AutenticacaoBO.java`, `config/JwtAuthenticationFilter.java`,
`entity/RefreshToken.java`, `service/AutenticacaoService.java`.

**Fluxo completo:**

```
POST /auth/login
  1. busca o usuário
  2. compara a senha (Argon2id)
  3. emite o par de tokens
  4. registra o token de renovação no banco (só o hash)

POST /auth/refresh
  1. valida a assinatura do token de renovação
  2. procura o hash no banco
  3. se o token JÁ FOI USADO → DETECÇÃO DE REUSO → revoga a FAMÍLIA inteira
  4. marca o token antigo como usado
  5. emite um par novo, na MESMA família

POST /auth/logout
  1. revoga todos os tokens da família do token apresentado
```

**Decisões que valem explicar:**

- **Assinatura assimétrica (RS256).** A chave privada fica só na
  aplicação (assina); a pública pode ser distribuída (verifica). Com
  chave simétrica, quem valida um token poderia forjar um — e o segredo
  acabaria exposto em todo serviço que valida o token.
- **Rotação de chaves via `kid`.** O cabeçalho do token guarda o
  identificador da chave. A aplicação carrega a chave antiga (ainda
  valida) **e** a nova (já assina) — cada token continua sendo conferido
  com a chave que o assinou. Trocar de chave não derruba ninguém.
- **Dois tempos de vida.** Token de acesso: 15 minutos (é o que viaja em
  toda requisição — janela de dano pequena). Token de renovação: 7 dias
  (é o que evita digitar a senha a cada 15 minutos — mas vive num lugar
  só, o endpoint de renovação).
- **O token de renovação é de uso único**, e o hash **SHA-256** dele é o
  que fica no banco. Se o banco vazar, os hashes são inúteis: o token
  em si nunca é gravado.
- **Detecção de reuso.** Se um token *usado* for apresentado de novo, o
  sinal é de que alguém copiou o token. A resposta correta não é recusar
  só o token apresentado: é **revogar toda a família**. No cenário de
  roubo, o atacante e o usuário legítimo usam o mesmo token — revogar a
  família obriga os dois a se autenticarem de novo, e o usuário legítimo
  percebe o problema imediatamente.
- **O `typ` no token.** Sem ele, um token de renovação (7 dias) seria
  aceito em qualquer endpoint protegido, multiplicando por 7 o tempo de
  vida de um vazamento.

### 3.8 Limitador de volume por IP

**Arquivos:** `config/LimitadorDeRequisicoesFilter.java`.

O controle anti-força-bruta protege a **conta**. Este protege o
**recurso**: um atacante que não quer nem tentar logar ainda consegue
derrubar a API disparando requisições, ou consumir CPU com consultas
pesadas.

| Item | Padrão |
|---|---|
| Máximo de requisições por IP | 120 |
| Janela deslizante | 60 segundos |
| Resposta ao estourar | `429 Too Many Requests` + `Retry-After` |

**Janela deslizante, não bloco fixo.** Num bloco fixo ("100 por minuto,
a contar do minuto cheio"), um atacante dispara 100 requisições no
último segundo do minuto e outras 100 no primeiro segundo do seguinte — o
dobro do limite num instante. A janela deslizante conta as requisições
dos **últimos N segundos**, e o limite é sempre respeitado.

Os cabeçalhos `X-RateLimit-Limit` e `X-RateLimit-Remaining` vêm em
**todas** as respostas, mesmo quando a requisição passa — é o que permite
ao cliente se auto-regular em vez de descobrir o limite só quando o erro
429 acontece.

### 3.9 Identificador de correlação

**Arquivo:** `config/CorrelationIdFilter.java`.

Cada requisição recebe um identificador único, que:

- entra no **MDC** (contexto de log da thread), aparecendo em **todas**
  as linhas de log daquela requisição;
- volta no cabeçalho de resposta **`X-Correlation-Id`**;
- pode ser **enviado pelo cliente** — o valor é reaproveitado (é o que
  liga o log de vários serviços).

O valor recebido do cliente é **saneado**: só são aceitos caracteres
alfanuméricos, `-` e `_`, até 64 caracteres. Sem isso, um cliente poderia
mandar um cabeçalho com quebra de linha e **injetar linhas falsas no
log** (*log forging*) — e ainda forjar o formato das linhas reais, para
o log parecer legítimo.

O MDC é limpo no `finally`, para que o identificador de uma requisição
não vaze para a seguinte (o Tomcat reaproveita threads).

### 3.10 Validação de entrada

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

### 3.11 Proteção do banco de dados

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

### 3.12 Menos privilégio no sistema operacional

A API **não roda como root**. No `Dockerfile`, o processo Java roda com
o usuário não privilegiado `app`. Isso reduz o estrago de uma possível
exploração de uma vulnerabilidade na JVM ou em uma biblioteca.

### 3.13 Cadeia de suprimento (SBOM, CVEs e Dependabot)

O projeto trata a **cadeia de suprimento** — as bibliotecas de terceiros
que entram no artefato — como parte da segurança, e não como detalhe de
empacotamento:

| Mecanismo | O que faz | Onde |
|---|---|---|
| **SBOM** (CycloneDX) | Gera o inventário de todas as dependências e versões | `pom.xml` |
| **Varredura de CVEs** | Falha o build se houver vulnerabilidade de CVSS ≥ 7 | `pom.xml`, perfil `security-scan` |
| **Dependabot** | Abre PRs semanais com as versões corrigidas | `.github/dependabot.yml` |
| **CI** | Build + testes + lint + SBOM a cada push | `.github/workflows/ci.yml` |
| **SECURITY.md** | Canal privado e prazo de resposta para relatos | `.github/SECURITY.md` |

O caso do **Log4Shell** (dezembro de 2021) é o exemplo canônico: uma
vulnerabilidade em uma biblioteca de log afetou milhões de aplicações no
mundo. A única forma de saber se você é afetado é ter um inventário e um
monitoramento automático — é exatamente o que esta seção descreve.

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

### 4.4 No modo HTTP Basic, a senha é reenviada a cada requisição

No modo `basic` (que é o padrão), não há token de acesso: a senha é
reenviada a cada requisição. Consequências: não há como revogar "aquela
sessão" sem trocar a senha, e não há expiração.

É por isso que o modo `jwt` existe (seção 3.7). Mas note: com HTTP Basic
**sem TLS**, as credenciais viajam codificadas em Base64 — que **não é
criptografia**. É por isso que o perfil de produção **não pode** ser
usado atrás de HTTP puro.

### 4.5 Não há alerta automático de incidentes

O log registra tudo, mas nada **observa** o log e avisa. Um pico de
falhas de autenticação passaria despercebido até alguém olhar.

### 4.6 Não há campo de "tentativas de login" no banco

O controle é em memória, não persistido. Não há, portanto, um registro
durável de "tentativas de login desta conta nos últimos 30 dias".

### 4.7 O controle anti-força-bruta é opcional por padrão

Ele só age quando `SECURITY_ENABLED=true` (perfil de produção). No modo
de avaliação, os endpoints estão abertos — como o enunciado exige. Isso
é uma decisão consciente, não um esquecimento, e está documentado no
`README`.

> **Nota:** o *rate limiting de volume* (`LimitadorDeRequisicoesFilter`)
> **não** depende de `SECURITY_ENABLED` — ele atua em todos os modos,
> inclusive no de avaliação, justamente porque proteger o recurso não
> depende de autenticação.

### 4.9 Não há revogação de um token de acesso já emitido

O token de acesso é válido até a expiração (15 minutos). A revogação
disponível hoje é a do **token de renovação** (que revoga a família
inteira). Um token de acesso vazado, portanto, continua válido por até
15 minutos.

**Como seria feito:** manter uma lista de tokens revogados (Revogação por
`jti`) consultada a cada requisição — o que reintroduz estado no servidor
e contraria o ganho do JWT. A alternativa, mais usada em produção, é
manter a expiração curta (o que já é o caso) e aceitar a janela de
exposição. Outra saída é a **versão do token** por usuário: um contador
no banco que, ao ser incrementado, invalida todos os tokens anteriores
emitidos para aquele usuário — sem lista, sem consulta por requisição.

### 4.10 A trilha de auditoria não é um cofre

A cadeia de hash torna a adulteração **detectável**, mas quem tiver
simultaneamente acesso ao **processo** e à **chave HMAC** ainda poderia
reescrever a cadeia inteira e recalcular os selos. Fechar esse circuito
exigiria um cofre de segredos (Vault, AWS Secrets Manager) ou um HSM para
a chave — infrastructure que está fora do escopo de um projeto de
avaliação.

### 4.11 O controle de tentativas e o rate limiting vivem na memória

Assim como o `LoginAttemptService` (4.2), o
`LimitadorDeRequisicoesFilter` guarda os contadores em um
`ConcurrentHashMap` na RAM. Com múltiplas instâncias da aplicação, cada
uma teria o seu próprio contador. A correção é a mesma: Redis.

### 4.12 A varredura de CVEs não roda no pipeline padrão

O perfil `security-scan` existe e está configurado para **falhar o
build** com CVSS ≥ 7, mas ele precisa de rede e de uma chave da NVD —
cuja carga inicial leva horas. Rodar isso a cada push atrasaria o
pipeline em minutos. Em produção, o costume é rodar à noite, com a chave
em segredo do CI.

---

## 5. Checklist de produção

O que fazer antes de colocar isso na internet de verdade:

- [ ] **TLS** no proxy, com certificado válido (e HSTS ativo).
- [ ] `SECURITY_ENABLED=true` e `SECURITY_PASSWORD` de verdade forte
      (não o padrão `troque-esta-senha`).
- [ ] `SECURITY_MODE=jwt` se houver mais de uma instância da API.
- [ ] `JWT_KEYS_DIR` apontando para a pasta com o par `.pem` das chaves
      (montada por Kubernetes Secret / Vault), e `JWT_ACTIVE_KEY_ID`
      definido.
- [ ] `AUDIT_HMAC_KEY` com **pelo menos 32 bytes aleatórios**, vindos de
      um cofre de segredos (não de um `.env` no repositório).
- [ ] `DATABASE_PASSWORD` forte e **diferente** do usuário do banco.
- [ ] Aplicar `sql/segurenca/03_permissoes.sql` e trocar a conexão para
      o usuário de menor privilégio.
- [ ] A porta da aplicação **não** exposta publicamente — só o proxy
      alcança (é o que torna `trust-proxy=true` seguro).
- [ ] `trust-proxy=true` **somente** se essa condição for verdadeira.
- [ ] Mover o controle de tentativas e o rate limiting para o Redis
      (múltiplas instâncias).
- [ ] Centralizar os logs e criar alerta por taxa de falha de login.
- [ ] Rodar `mvn -Psecurity-scan verify` à noite, com `NVD_API_KEY` em
      segredo do CI.
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
  "x-frame|x-content-type|referrer|cache-control|x-powered-by|strict-transport|content-security|permissions-policy|cross-origin"
```

Esperado: `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`,
`Referrer-Policy: no-referrer`, `Cache-Control: no-store...`,
`Content-Security-Policy: default-src 'none'; …`,
`Permissions-Policy: …`, `Cross-Origin-Opener-Policy: same-origin`,
`Cross-Origin-Resource-Policy: same-origin`,
`Cross-Origin-Embedder-Policy: require-corp` e **nenhuma** linha
`X-Powered-By`.

### 6.6 Auditoria

```bash
# durante a API no ar, em outro terminal
tail -f log.txt | grep AUDITORIA

# a trilha persistida e verificável
curl -s http://localhost:8081/auditoria | head -c 400
curl -s http://localhost:8081/auditoria/verificar
```

### 6.7 Identificador de correlação

```bash
curl -s -D - -o /dev/null http://localhost:8081/unidades | grep -i x-correlation-id
# X-Correlation-Id: 4f2c8a1e-...

# o mesmo identificador aparece em TODAS as linhas de log da requisição
grep 4f2c8a1e log.txt
```

### 6.8 Fluxo JWT

```bash
# 1) Login → par de tokens
curl -s -X POST http://localhost:8081/auth/login \
  -H "Content-Type: application/json" \
  -d '{"usuario":"admin","senha":"<senha>"}'
# {"tokenAcesso":"eyJ...","tokenRenovacao":"eyJ...","tipoToken":"Bearer","expiraEmSegundos":900,"usuario":"admin"}

# 2) Usar o token de acesso
curl -s -H "Authorization: Bearer $TOKEN_ACESSO" http://localhost:8081/unidades

# 3) Renovar o token
curl -s -X POST http://localhost:8081/auth/refresh \
  -H "Content-Type: application/json" \
  -d "{\"tokenRenovacao\":\"$TOKEN_RENOVACAO\"}"

# 4) Reusar um token JÁ usado → revoga a família inteira
curl -s -X POST http://localhost:8081/auth/refresh \
  -H "Content-Type: application/json" \
  -d "{\"tokenRenovacao\":\"$TOKEN_RENOVACAO\"}"
# {"status":401,"erro":"Nao autorizado",
#  "mensagem":"Token de renovacao invalido. Todos os tokens desta sessao foram revogados."}
```

### 6.9 Rate limiting de volume

```bash
for i in $(seq 1 130); do
  curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8081/unidades
done | sort | uniq -c
# 120 respostas 200 e 10 respostas 429
```

### 6.10 Suíte de testes automatizados

```bash
cd leilao-api && mvn test
```

Os testes de segurança (`SegurancaIntegracaoTest`) verificam
automaticamente os itens 6.1 a 6.3.

| Arquivo de teste | O que prova |
|---|---|
| `SegurancaIntegracaoTest` | A cadeia de filtros de ponta a ponta (401/429/200, JSON, cabeçalhos) |
| `PasswordEncoderTest` | Hash nunca é texto puro, salt funciona, migração do BCrypt legado |
| `AuditoriaBOTest` | A adulteração, a quebra de encadeamento e a remoção são **detectadas** |
| `TokenJwtBOTest` | Token adulterado, expirado, de tipo errado ou de outra chave é recusado |
| `LimitadorDeRequisicoesFilterTest` | 429 no estouro do limite, `Retry-After` real, isolamento por IP |
| `CorrelationIdFilterTest` | ID único, sem vazamento entre requisições, valor malicioso descartado |
| `LoginAttemptServiceTest` | Contadores, janela deslizante, bloqueio por usuário e por IP |

---

## 7. Modelo de ameaça

Para mostrar que as decisões foram pensadas, e não copiadas:

| Atacante | Objetivo | Vetor | Defesa |
|---|---|---|---|
| Curioso na internet | Ler dados | API aberta | `SECURITY_ENABLED=true` + TLS |
| Automação de varredura | Descobrir usuários | Enumeração | Resposta idêntica + rate limit |
| Atacante com dicionário | Entrar como admin | Força bruta | Limite de tentativas + Argon2id |
| Atacante com GPU | Quebrar o hash | Força bruta massiva | Argon2id (64 MiB por tentativa) |
| Atacante distribuído | Sair do limite por IP | Muitos IPs | Limite por **usuário** (5 falhas) |
| Desenvolvedor hostil | SQL Injection | Parâmetro malicioso | JPA parametrizado + validação |
| Site terceiro | Ler dados via navegador | Requisição cross-origin | CORS fechado |
| Página maliciosa | Roubo via iframe | Clickjacking | `X-Frame-Options: DENY` + `frame-ancestors 'none'` |
| Curioso com o banco | Ler tudo | Roubo de backup | Argon2id + menor privilégio |
| Invasor da aplicação | Escalar no banco | SQL Injection | Usuário de menor privilégio |
| **Invasor com acesso ao banco** | **Apagar as pistas** | **DELETE na trilha** | **Cadeia HMAC: a remoção é detectada** |
| **Invasor com acesso ao banco** | **Forjar uma trilha** | **INSERT na trilha** | **HMAC com chave fora do banco** |
| **Capturador de rede** | **Roubar o token** | **Sniffing em HTTP** | **TLS no proxy (obrigatório em produção)** |
| **Ladrão de log** | **Roubar a senha** | **Log com corpo da requisição** | **O corpo nunca é registrado** |
| **Invasor do CI** | **Injetar código** | **Dependência maliciosa** | **SBOM + Dependabot + CODEOWNERS** |

---

## 8. Referências

- OWASP Top 10 (2021) — <https://owasp.org/Top10/>
- OWASP Cheat Sheet Series — <https://cheatsheetseries.owasp.org/>
- OWASP Password Storage Cheat Sheet (Argon2id) — <https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html>
- OWASP Authentication Cheat Sheet — <https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html>
- OWASP Logging Cheat Sheet — <https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html>
- RFC 7519 — *JSON Web Token (JWT)*
- RFC 6750 — *The OAuth 2.0 Authorization Framework: Bearer Token Usage*
- RFC 7617 — *The 'Basic' HTTP Authentication Scheme*
- Spring Security Reference — <https://docs.spring.io/spring-security/reference/>
- NIST SP 800-63 — *Digital Identity Guidelines*

---

**Documento relacionado:** [`README.md`](README.md) (visão geral do
projeto) · [`TECNOLOGIAS.md`](TECNOLOGIAS.md) (guia das tecnologias) ·
[`MANUAL-OPERACAO.md`](MANUAL-OPERACAO.md) (como rodar) ·
[`ENTREVISTA.md`](ENTREVISTA.md) (banco de perguntas)
