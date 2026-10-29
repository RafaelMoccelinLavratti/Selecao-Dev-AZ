# Sistema de Leilões — Avaliação AZ Tecnologia

Aplicação **full stack** para consulta e gestão de leilões, desenvolvida para a
avaliação técnica da **AZ Tecnologia em Gestão**.

Construída sobre o *starter* fornecido, seguindo a arquitetura em camadas
exigida pelo enunciado e cobrindo **todos os 8 exercícios**.

---

## 1. Sumário

| # | Exercício | Onde está | Status |
|---|-----------|-----------|--------|
| 1 | Script DDL (tabelas, constraints, sequences) | [`leilao-api/src/main/resources/sql/01_ddl.sql`](leilao-api/src/main/resources/sql/01_ddl.sql) | ✅ |
| 2 | Script DML (massa de dados, ≥10 por tabela) | [`leilao-api/src/main/resources/sql/02_dml.sql`](leilao-api/src/main/resources/sql/02_dml.sql) | ✅ |
| 3 | CRUD REST de todas as tabelas | pacote `service` + `business` + `repository` | ✅ |
| 4 | Tela principal, menu e rotas `#/nomedatela` | [`leilao-app/src/App.vue`](leilao-app/src/App.vue), [`router/index.js`](leilao-app/src/router/index.js) | ✅ |
| 5 | Tela de Unidades com grid editável (CRUD) | [`views/unidade/Unidades.vue`](leilao-app/src/views/unidade/Unidades.vue) | ✅ |
| 6 | Tela de Empresas somente leitura | [`views/empresa/Empresas.vue`](leilao-app/src/views/empresa/Empresas.vue) | ✅ |
| 7 | Tela de Leilões com total calculado | [`views/leilao/Leiloes.vue`](leilao-app/src/views/leilao/Leiloes.vue) | ✅ |
| 8 | Tela de cadastro/edição de empresa com validações | [`views/empresa/Empresa.vue`](leilao-app/src/views/empresa/Empresa.vue) | ✅ |

---

## 2. Stack de tecnologia

| Camada | Tecnologia | Versão |
|--------|-----------|--------|
| Banco de dados | PostgreSQL | 16 (funciona do 10 em diante) |
| Backend | Spring Boot | 2.7.0 |
| Backend | Java | 11 |
| Backend | Spring Data JPA / Hibernate | 5.6.9 |
| Backend | Bean Validation (Jakarta) | 2.0 |
| Backend | Spring Security (Argon2id + HTTP Basic/JWT) | 5.7 |
| Backend | JJWT (tokens RS256) + BouncyCastle (Argon2id) | 0.12.6 / 1.78.1 |
| Frontend | Vue | 2.7.16 |
| Frontend | Vue Router | 3.6 |
| Frontend | Vuetify | 2.7 |
| Frontend | Axios | 1.6 |
| Frontend | Vue CLI / webpack | 5.0 / 5.x |
| Frontend | Tipografia | Barlow + Tomorrow (as oficiais da AZ) |

> A **identidade visual** (paleta, fonte e logo, extraídos do site
> institucional da empresa) está registrada em
> [`IDENTIDADE-VISUAL.md`](IDENTIDADE-VISUAL.md).

---

## 3. Como rodar

### 3.1 Opção A — Docker (banco pronto, um comando)

Pré-requisito: Docker Desktop instalado.

```bash
docker compose -f docker-database.yml up -d
```

Na **primeira** subida, o container do Postgres executa automaticamente
`01_ddl.sql` e `02_dml.sql` (o `compose` monta a pasta `sql/` dentro de
`/docker-entrypoint-initdb.d`). O banco `az` já fica criado **e populado**.

Para recomeçar do zero (apagando os dados):

```bash
docker compose -f docker-database.yml down -v
docker compose -f docker-database.yml up -d
```

### 3.2 Opção B — PostgreSQL instalado na máquina

```bash
createdb -U postgres az
psql -U postgres -d az -f leilao-api/src/main/resources/sql/01_ddl.sql
psql -U postgres -d az -f leilao-api/src/main/resources/sql/02_dml.sql
```

### 3.3 Backend (API)

```bash
cd leilao-api
mvn spring-boot:run
```

Sobe na **porta 8081**.
Para ver o SQL no console: `mvn spring-boot:run -Dspring-boot.run.profiles=local`

### 3.4 Frontend

```bash
cd leilao-app
npm install
npm run serve
```

Sobe na **porta 8080**. Acesse <http://localhost:8080>.

### 3.5 Testes

```bash
cd leilao-api
mvn test
```

**37 testes**, todos automatizados e **sem precisar de banco de dados** —
rodam em qualquer máquina, em menos de 5 segundos.

---

## 4. Arquitetura

O enunciado define 4 camadas. Cada uma tem uma responsabilidade única:

```
   Navegador
      │  HTTP/JSON
      ▼
┌──────────────────────────────┐
│ service    (UnidadeService,  │  controllers REST — traduzem HTTP ↔ Java
│            EmpresaService, …)│  NÃO contêm regra de negócio
└──────────────┬───────────────┘
               ▼
┌──────────────────────────────┐
│ business   (UnidadeBO,       │  REGRAS DE NEGÓCIO
│            EmpresaBO, …)     │  valida, normaliza, calcula
└──────────────┬───────────────┘
               ▼
┌──────────────────────────────┐
│ repository (UnidadeRepository,│  ACESSO A DADOS
│              LeilaoRepository…)│  Spring Data JPA gera o SQL
└──────────────┬───────────────┘
               ▼
┌──────────────────────────────┐
│ entity     (Unidade, Empresa, │  DOMÍNIO
│            Leilao, Lote, …)   │  mapeamento do banco
└──────────────────────────────┘
```

Por que separar?

- **Testabilidade**: as regras podem ser testadas sem subir servidor nem banco.
- **Manutenção**: mexer no SQL não afeta o controller; mudar o formato da
  resposta não afeta a regra de negócio.
- **Substituição**: a camada `business` não sabe se o dado está em PostgreSQL,
  MySQL ou num cache — o `repository` é quem sabe.

### Estrutura de pastas

```
leilao-api/src/main/java/br/com/selecao/locadora/
├── LeilaoApplication.java          # ponto de entrada do Spring Boot
├── business/                       # camada de negócio (regras)
│   ├── UnidadeBO.java  EmpresaBO.java
│   ├── LeilaoBO.java   LoteBO.java
│   └── CompradorBO.java
├── config/                         # configurações (CORS, segurança, senhas)
│   ├── CorsConfig.java
│   ├── PasswordEncoderConfig.java
│   ├── SecurityConfig.java
│   └── SecurityHeadersFilter.java
├── controller (service)/           # camada de serviço (controllers REST)
│   ├── UnidadeService.java  EmpresaService.java
│   ├── LeilaoService.java   LoteService.java
│   └── CompradorService.java
├── dto/                            # objetos de entrada e saída da API
├── entity/                         # camada de domínio
│   ├── Auditable.java              # base com created_at / updated_at
│   ├── Unidade.java  Empresa.java
│   ├── Leilao.java   Lote.java
│   └── Comprador.java + CompradorId.java
├── exception/                      # tratamento global de erros
└── repository/                     # camada de acesso a dados

leilao-app/src/
├── App.vue                         # tela principal + menu (exercício 4)
├── components/                     # componentes reutilizáveis
│   ├── AppMenu.vue  AppNotificacoes.vue  ConfirmDialog.vue
├── plugins/vuetify.js              # configuração do Vuetify (tema, ícones)
├── router/index.js                 # rotas com hash (#/unidades)
├── services/api.js                 # único ponto de contato com a API
├── utils/mascaras.js               # máscaras e validadores (pt-BR)
└── views/
    ├── unidade/Unidades.vue        # exercício 5
    ├── empresa/Empresas.vue        # exercício 6
    ├── empresa/Empresa.vue         # exercício 8
    └── leilao/Leiloes.vue          # exercício 7
```

---

## 5. Modelo de dados

### Estrutura da pasta `sql/`

```
leilao-api/src/main/resources/sql/
├── 01_ddl.sql          estrutura das 5 tabelas   ← executado pelo Docker
├── 02_dml.sql          massa de dados inicial    ← executado pelo Docker
├── database.png        o diagrama do enunciado
├── segurca/
│   └── 03_permissoes.sql   papel de menor privilégio (OPCIONAL, manual)
└── exemplos/
    └── spcript.sql      script original do starter (NÃO é executado)
```

> **Por que `spcript.sql` e `03_permissoes.sql` estão em subpastas?**
> O container do PostgreSQL executa automaticamente **apenas** os arquivos
> `.sql` que estão **diretamente** na pasta montada em
> `/docker-entrypoint-initdb.d` (o utilitário do container usa `maxdepth 1`
> e não entra em subpastas).
>
> O `spcript.sql` do starter criaria a tabela `tb_unidade` e a sequence
> `seq_unidade` — que o `01_ddl.sql` **já cria**. Se ele fosse executado
> automaticamente, o container abortaria a inicialização com
> `relation tb_unidade already exists` e **o banco nem subiria**. Por isso
> foi para a subpasta `exemplos/`, onde fica como referência.
>
> O `03_permissoes.sql` também é opcional e manual (ver seção 7.3).

### Tabelas

```
empresa ──< leilao ──< lote
   │           │
   └───────────┴──< comprador >── empresa
```

| Tabela | Chave primária | Observações |
|--------|-----------------|-------------|
| `tb_empresa` | `id` (sequência) | `cnpj` e `usuario` são **UNIQUE** (sublinhados no diagrama). `razaoSocial` e `cnpj` são **NOT NULL** (asterisco). |
| `tb_leilao` | `id` (sequência) | `vendedor` → FK `tb_empresa` (**ON DELETE RESTRICT**). `codigo` e `vendedor` são NOT NULL. |
| `tb_lote` | `id` (sequência) | `leilao` → FK `tb_leilao` (ON DELETE CASCADE). `valor_inicial` é **NUMERIC(14,2)** — nunca `float`. |
| `tb_unidade` | `id` (sequência) | `nome` NOT NULL e UNIQUE. |
| `tb_comprador` | **(empresa, leilao)** — composta | Tabela de junção. Mapeada no JPA com `@EmbeddedId`. |

### Decisões de modelagem que valem explicar

1. **`NUMERIC(14,2)` para dinheiro.** Ponto flutuante "perde" centavos
   (`0.1 + 0.2 ≠ 0.3` em `double`). Dinheiro é exato por definição.
2. **Sequências (`nextval`) para todo `id`.** Evita "duplicate key" quando
   duas pessoas cadastram ao mesmo tempo — algo que um `MAX(id)+1` não
   resolve.
3. **`ALTER SEQUENCE ... OWNED BY`.** Sem esse vínculo, o
   `TRUNCATE ... RESTART IDENTITY` do script de dados **não** reinicia os
   contadores e a massa de dados quebra (a empresa de id 1 deixa de existir).
4. **`ON DELETE RESTRICT` em `leilao.vendedor`.** Apagar uma vendedora
   quebraria o histórico dos leiloes que ela vendeu.
5. **`ON DELETE CASCADE` em `lote.leilao`.** Um lote sem leilão não faz
   sentido; a aplicação, porém, **avisa antes** de excluir um leilão que
   ainda tenha lotes.
6. **Colunas em `snake_case`.** O diagrama usa `camelCase`
   (`razaoSocial`); no banco usamos `snake_case` (`razao_social`), que é o
   padrão do mercado. A ligação é explícita na anotação `@Column` de cada
   entidade.
7. **`CHECK` constraints.** Além do NOT NULL do enunciado, o banco
   garante que CNPJ tenha 14 dígitos, e-mail e site tenham formato válido,
   telefone só com dígitos e `quantidade`/`valor_inicial` não sejam
   negativos. Validação no banco **e** na aplicação: quem buscar um dado
   direto no banco também fica protegido.

---

## 6. API REST

Todas as respostas de erro seguem o **mesmo formato**:

```json
{
  "timestamp": "2026-10-29T12:37:42.577",
  "status": 400,
  "erro": "Dados invalidos",
  "mensagem": "Verifique os campos destacados.",
  "campos": { "cnpj": "CNPJ invalido. Use 14 digitos ou o formato 00.000.000/0000-00." }
}
```

| Método | URL | Descrição |
|--------|-----|-----------|
| GET | `/unidades`, `/unidade` | Lista todas |
| GET | `/unidades/{id}` | Busca por id |
| POST | `/unidades` | Insere |
| PUT | `/unidades/{id}` | Altera |
| DELETE | `/unidades/{id}` | Remove |
| GET | `/empresas`, `/empresa` | Lista todas |
| GET | `/empresas/{id}` | Busca por id |
| POST | `/empresas` | Insere |
| PUT | `/empresas/{id}` | Altera |
| DELETE | `/empresas/{id}` | Remove |
| GET | `/leiloes`, `/leilao` | Lista **com** vendedor e total calculado |
| GET | `/leiloes/{id}` | Busca por id |
| POST | `/leiloes` | Insere |
| PUT | `/leiloes/{id}` | Altera |
| DELETE | `/leiloes/{id}` | Remove |
| GET | `/lotes`, `/lote` | Lista todos |
| GET | `/lotes/leilao/{leilaoId}` | Lotes de um leilão |
| GET | `/lotes/{id}` | Busca por id |
| POST/PUT/DELETE | `/lotes`, `/lotes/{id}` | CRUD |
| GET | `/compradores`, `/comprador` | Lista todos |
| GET | `/compradores/{empresaId}/{leilaoId}` | Busca o vínculo |
| GET | `/compradores?empresa=X&leilao=Y` | Busca o vínculo (outra forma) |
| POST/PUT/DELETE | `/compradores`, `/compradores/{empresaId}/{leilaoId}` | CRUD |
| POST | `/auth/login` | Autentica e devolve o par de tokens (JWT) |
| POST | `/auth/refresh` | Renova o token de acesso |
| POST | `/auth/logout` | Revoga a sessão (204) |
| GET | `/auth/me` | Quem está autenticado |
| GET | `/auditoria` | Registros da trilha de auditoria |
| GET | `/auditoria/verificar` | Verifica a integridade da cadeia |
| GET | `/actuator/health` | Health check da aplicação |

> **Por que singular e plural?** O enunciado é inconsistente: no exercício 3
> os exemplos usam singular (`GET /unidade/1`, `POST /leiao`), mas nos
> exercícios 5–7 as URLs são no plural (`/unidades`, `/empresas`, `/leiloes`).
> Em vez de escolher e torcer, a API responde às duas formas — são o mesmo
> código, apenas dois "apelidos".
>
> Os endpoints `/auth/*` também respondem sob o prefixo `/autenticacao/*`
> (mesma estratégia dos "apelidos" singular/plural).

---

## 7. Segurança da informação

Esta foi uma das decisões de projeto mais cuidadas. O que foi feito:

### 7.1 Autenticação e senha

- **Senha nunca é gravada em texto puro.** A coluna `senha` guarda apenas o
  **hash Argon2id** (64 MiB de memória, 2 iterações, salt de 16 bytes), com o
  prefixo `{argon2}` que identifica o algoritmo.
  - *Por que Argon2id e não BCrypt?* A OWASP passou a recomendá-lo como
    primeira opção no *Password Storage Cheat Sheet* de 2023: o Argon2id usa
    **memória** como recurso principal, e cada tentativa de ataque consome
    dezenas de megabytes — o que trava o ataque paralelo em GPU, contra o
    qual o BCrypt (que usa pouca memória) só se defende parcialmente.
  - **E a migração não quebrou ninguém.** O `CodificadorDeSenhaDelegado`
    reconhece o formato do hash já gravado: os hashes antigos em BCrypt
    continuam valendo e são **reescritos sozinhos no primeiro login**. A base
    migra no ritmo do uso — sem script em lote e sem janela de manutenção.
- **A senha nunca sai da API.** O DTO de resposta (`EmpresaResponse`) não tem
  campo de senha — nem em texto puro, nem o hash.
- **Autenticação por configuração, não por código.** No perfil padrão
  (desenvolvimento/avaliação) os endpoints ficam abertos, exatamente como o
  enunciado pede. Ativando `SECURITY_ENABLED=true` (perfil `prod`), **toda**
  rota exige credencial. Mesmo código, sem `if` espalhado pelo projeto.
- **Dois mecanismos pelo mesmo código.** `SECURITY_MODE=basic` (HTTP Basic)
  ou `SECURITY_MODE=jwt` (token JWT). Os dois produzem o **mesmo objeto de
  autenticação** no contexto de segurança, então as regras de acesso são
  idênticas — trocar de modo é mudar **uma propriedade**.
- **JWT assimétrico (RS256)** com rotação de chaves pelo cabeçalho `kid`, e
  **rotação do token de renovação com detecção de reuso**: um token usado de
  novo revoga a família inteira.
- **Falha rápida.** No perfil `prod`, as credenciais do banco **não têm valor
  padrão**: se faltarem, a aplicação não sobe — em vez de subir com uma senha
  fraca em produção.

### 7.2 Validação de entrada (a garantia real)

- **Bean Validation** em **todos** os DTOs de entrada: obrigatoriedade,
  tamanho máximo (idêntico ao da coluna no banco), formato de CNPJ, CEP,
  telefone, e-mail e URL.
- **Validação em duas camadas.** O frontend valida para dar boa experiência
  (erro na hora, sem perder o que foi digitado); o backend valida porque é a
  camada que **não pode ser contornada** — qualquer um pode chamar a API sem
  passar pela tela. As regras são as **mesmas** dos dois lados (as expressões
  regulares do JS espelham as anotações do Java), então o usuário nunca vê
  "a tela aceitou e a API recusou".
- **Normalização de entrada.** CNPJ, CEP e telefone são gravados **sempre**
  só com dígitos, mesmo que o usuário digite `11.422.377/0001-55`. Isso
  garante que a busca por CNPJ funcione com qualquer forma digitada.
- **Lista de permissão (*allow-list*).** A API nunca grava a entidade
  diretamente: usa DTOs de entrada com **apenas** os campos permitidos. O
  cliente não consegue enviar o `id` para trocar a chave de um registro, nem
  o `created_at` para falsificar a trilha de auditoria.

### 7.3 Proteção do banco

- **SQL Injection:** todo o SQL é gerado pelo Hibernate com **parâmetros
  vinculados** (*prepared statements*). Nenhuma concatenação de texto
  digitado pelo usuário. As únicas consultas escritas à mão (a de total dos
  leilões) são **JPQL** — a camada abstrata do Hibernate — e o JPQL também
  usa parâmetros vinculados.
- **Integridade referencial no banco:** PK, FK, UNIQUE e CHECK constraints.
  Mesmo que a aplicação seja contornada, o banco recusa dados inválidos.
- **Princípio do menor privilégio:** o script opcional
  [`sql/seguranca/03_permissoes.sql`](leilao-api/src/main/resources/sql/seguranca/03_permissoes.sql)
  cria um usuário de banco separado, com **apenas** `SELECT/INSERT/UPDATE/DELETE`
  e **sem** permissão de `DROP`/`ALTER`. Se a aplicação for comprometida, o
  dano se limita aos dados — não ao banco.

### 7.4 Proteção da aplicação e do navegador

| Mecanismo | Efeito |
|-----------|--------|
| **CORS com lista explícita** | Só `http://localhost:8080` (ou o domínio real, via `CORS_ORIGINS`) pode chamar a API. Uma origem não autorizada recebe **403**. Nunca `*` combinado com credenciais — que permitiria CSRF cross-origin. |
| **Cabeçalhos de segurança** | `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY` (anti-clickjacking), `Referrer-Policy: no-referrer`, `Cache-Control: no-store`, `X-Powered-By` removido, **`Content-Security-Policy: default-src 'none'`**, **`Permissions-Policy`**, **`Cross-Origin-Opener-Policy`**, **`Cross-Origin-Resource-Policy`**, **`Cross-Origin-Embedder-Policy`**. |
| **Sem vazamento de stack trace** | `server.error.include-stacktrace=never`. Erros 500 devolvem mensagem genérica; o detalhe real fica **só no log do servidor**. |
| **CSRF desabilitado — com justificativa** | A API é *stateless* e não usa cookie de sessão (a autenticação é por cabeçalho). Sem cookie automático, o CSRF não se aplica — é a recomendação da própria documentação do Spring Security para esse cenário. |
| **Sessão stateless** | `SessionCreationPolicy.STATELESS`: sem estado no servidor → mais rápido, escala para várias instâncias e reduz a superfície de ataque. |
| **HSTS** | Força HTTPS nas requisições seguintes. |
| **Actuator restrito** | Só `health` e `info` são expostos. O padrão do Spring expõe `env`, `beans`, `configprops` — que vazam informação interna. |
| **Controle anti-força-bruta** | Limite por **usuário** (5 falhas) e por **IP** (20 falhas), janela de 15 min. Resposta `429` com `Retry-After`. |
| **Rate limiting de volume** | 120 requisições por IP a cada 60 s, em **janela deslizante** — protege o *recurso*, não só a conta. |
| **Trilha de auditoria à prova de adulteração** | Cada registro é selado por **HMAC-SHA256** e encadeado no anterior. Alteração, forja ou remoção são detectadas, com a sequência exata da ruptura. |
| **Identificador de correlação** | `X-Correlation-Id` em toda requisição, presente em todas as linhas de log. |
| **Auditoria** | `created_at`/`updated_at` automáticos em todas as tabelas (base `Auditable`), com log de cada operação de escrita. Base da trilha de auditoria exigida pela LGPD e pela ISO 27001. |
| **SBOM e varredura de CVEs** | Inventário de dependências gerado a cada build; build falha se houver vulnerabilidade de CVSS ≥ 7. |
| **CI com testes e lint** | Nada entra na branch principal sem passar por build, testes, lint e SBOM. |

> O detalhamento completo — mecanismo, limite e o que **não** é protegido —
> está na seção 7 deste README e nas anotações do próprio código, no ponto
> onde cada decisão acontece.

### 7.5 O que **não** há no repositório

- Nenhuma senha real versionada no Git.
- Nenhum token ou chave de API no código do frontend (lembre-se: o
  frontend roda no navegador do usuário — qualquer coisa escrita nele é
  pública).
- Credenciais do banco sempre por **variável de ambiente**
  (`DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`).

> **Nota honesta:** os endpoints estão abertos por padrão porque o
> enunciado do teste exige que `GET /unidades` responda sem login. O
> mecanismo para exigir autenticação existe, está testado e é ativado por
> uma variável de ambiente — a decisão de segurança está explícita e
> documentada, e não é uma omissão.

---

## 8. Decisões tomadas em relação ao *starter* fornecido

Todas as mudanças foram feitas **com motivo**, e o motivo está registrado.

| # | Mudança | Por quê |
|---|---------|---------|
| 1 | **Vue CLI 4 → 5** (webpack 4 → 5) | O webpack 4 usa o hash **MD4**, removido do OpenSSL 3 (padrão no Node 17+). O build quebra com `ERR_OSSL_EVP_UNSUPPORTED`. O `--openssl-legacy-provider` resolveria, mas só existe do Node 17 ao 21 e é descontinuado. O webpack 5 usa SHA-256 e funciona do Node 12 ao 24 **sem nenhuma flag**. |
| 2 | **Vue 2.6 → 2.7** | Versão final do Vue 2, com correções de segurança e compatibilidade total com o Vuetify 2. |
| 3 | **Vuetify 2 adicionado** | Não vinha no starter, mas os exercícios 5–8 exigem `v-data-table`. É a biblioteca de componentes mais usada em Vue 2 no mercado. Importada de forma completa (sem `sass`/`vuetify-loader`), o que reduz dependências e o risco de build. |
| 4 | **`spring-boot-starter-validation` adicionado** | Removido do `spring-starter-web` no Spring Boot 2.3. Sem ele, as anotações `@NotBlank`/`@Size`/`@Pattern` dos DTOs **não são verificadas** — e o exercício 8 depende delas. |
| 5 | **`spring-security-crypto` + `spring-boot-starter-security`** | BCrypt para a senha e cadeia de segurança configurável. Sem o `starter-security`, o Spring Boot geraria uma senha aleatória e bloquearia todos os endpoints. |
| 6 | **`spring-boot-starter-actuator`** | Health check (`/actuator/health`), que é o que um orquestrador consulta para saber se a aplicação está viva. Exposição restrita a `health` e `info`. |
| 7 | **`POSTGRES_DB=az` no `variables.env`** | Sem essa variável, o container do Postgres cria só o banco `postgres` e a API falha com `database "az" does not exist`. |
| 8 | **Colunas em `snake_case`** | Padrão do mercado; a ligação com o `camelCase` do diagrama é explícita no `@Column`. |
| 9 | **IDs `Integer` (não `Long`)** | Bate exatamente com `integer` do diagrama e evita o erro de tipo do PostgreSQL (`integer` × `bigint`). |
| 10 | **URLs em singular e plural** | O enunciado é inconsistente entre os exercícios 3 e 5–7. Aceitar as duas formas é mais robusto do que escolher uma. |
| 11 | **`views/leilao/Leiloes.vue`** (maiúscula) | O exercício 4 escreve `leiloes.vue` e o 7 escreve `Leiloes.vue`. Em Linux o sistema de arquivos diferencia maiúsculas — adotamos a do exercício 7. |
| 12 | **`#/empresa/:id`** (não `#/leilao/:id`) | O próprio enunciado diz "os links para editar **empresa** na tela do exercício 6", e as URLs da seção 8 são `#/empresa` e `#/empresa/:id`. O `#/leilao/:id` é um erro de digitação. |
| 13 | **`Auditable` como base das entidades** | `created_at`/`updated_at` aparecem nas 5 tabelas do diagrama. Uma `@MappedSuperclass` evita repetir o mesmo código 5 vezes. |
| 14 | **Validação do `comprador` com chave composta** | Mapeado com `@EmbeddedId` (`CompradorId`), exatamente como a PK composta do diagrama. |

---

## 9. Testes automatizados

```bash
cd leilao-api && mvn test
```

**63 testes**, todos passando. Os testes **não dependem de banco de dados**:
usam dublês do Mockito ou `@WebMvcTest`, então `mvn test` passa em
qualquer máquina, sem PostgreSQL no ar.

| Classe | O que garante |
|--------|---------------|
| `PasswordEncoderTest` | A senha gravada é um hash, não texto puro; a senha correta é reconhecida; a errada é rejeitada; **duas senhas iguais geram hashes diferentes** (prova do *salt*); e **o hash legado em BCrypt continua funcionando** (a migração não quebrou ninguém). |
| `EmpresaBOTest` | CNPJ/CEP/telefone são normalizados; CNPJ e usuário duplicados são recusados com mensagem clara; a senha é gravada como **Argon2id**; **na alteração sem nova senha, o hash anterior é preservado**; o DTO de resposta não tem campo de senha. |
| `UnidadeBOTest` | Nome é normalizado; nome repetido é recusado; id inexistente devolve “não encontrado”; exclusão remove o registro. |
| `GeradorHashSenhaTest` | Gera os hashes da massa de dados **e verifica** que cada hash do `02_dml.sql` confere com a senha correspondente (`Senha@1`…`Senha@12`). |
| `AuditoriaBOTest` | A cadeia de hashes se forma; a cadeia intacta é verificada como íntegra; e **alterar, forjar ou remover um registro é detectado**, com a sequência exata da ruptura. |
| `TokenJwtBOTest` | Token adulterado, expirado, de tipo errado ou assinado com outra chave é **recusado**; o `kid` viaja no cabeçalho; o hash do token é estável. |
| `LimitadorDeRequisicoesFilterTest` | 429 no estouro do limite; `Retry-After` com tempo **real** (nunca zero); **isolamento por IP** (um IP não bloqueia o outro). |
| `CorrelationIdFilterTest` | ID único por requisição; **não vaza** para a requisição seguinte; valor malicioso do cliente é descartado (log forging). |
| `SegurancaIntegracaoTest` | A **cadeia de filtros de ponta a ponta**: 401 em JSON, 429, 200 com o dado real do controller, `WWW-Authenticate`, health check não barrado. |
| `LoginAttemptServiceTest` | Contadores, janela deslizante, bloqueio por usuário e por IP, limpeza no sucesso. |
| `ClientIpResolverTest` | `X-Forwarded-For` só é confiado quando há proxy confiável. |

---

## 10. Problemas técnicos que precisam ser conhecidos

| Sintoma | Causa | Solução |
|---------|-------|---------|
| `ERR_OSSL_EVP_UNSUPPORTED` ao rodar `npm run serve` | Node 17+ removeu o MD4 do OpenSSL, usado pelo webpack 4 | Resolvido no projeto: Vue CLI 5 / webpack 5 |
| `database "az" does not exist` | Faltou `POSTGRES_DB=az` no `variables.env` | Já corrigido no `variables.env` |
| `No mapping for GET /...` no console do navegador | URL não existe na API | Verifique a porta: a API é a **8081**, o frontend a **8080** |
| Erro de CORS no console | Frontend e API em origens diferentes sem permissão | Já configurado; se mudar a porta do frontend, ajuste `app.cors.origins` |
| `Connection refused` ao iniciar a API | PostgreSQL parado | `docker compose -f docker-database.yml up -d` |
| Senha da empresa com valor do `Senha@N` | A massa de dados guarda **hash**, não a senha | Veja a tabela na seção 11 |

---

## 11. Dados de teste

A massa de dados tem **12 empresas, 12 unidades, 12 leiloes, 18 lotes e
14 compradores**.

| Empresa | CNPJ | Usuário | Senha |
|---------|------|---------|-------|
| Metalurgica Aurora Ltda | 11.422.377/0001-55 | `admin.aurora` | `Senha@1` |
| Transportes Vale Verde S.A. | 11.422.377/0002-36 | `operador.valverde` | `Senha@2` |
| Agroindustrial Boa Vista S.A. | 11.422.377/0003-17 | `admin.boavista` | `Senha@3` |
| … | … | … | … |

> A senha no banco é o **hash** de `Senha@N` (gravado em BCrypt pela massa
> de dados original e migrado para Argon2id no primeiro login). Para conferir:
> `mvn test -Dtest=GeradorHashSenhaTest`.

Exemplo de conferência do total do leilão 1001:
`5 × 12.500,00 + 2 × 8.750,00 = 80.000,00`.

---

## 12. Observações finais

- Todo o código está comentado em **português, em linguagem simples**,
  explicando *por quê* e não apenas *o quê*.
- As decisões não óbvias estão documentadas **no ponto onde elas ocorrem**
  (nos arquivos `.java` e `.vue`) — não só aqui.
- Nenhuma senha, chave ou token real está versionado no Git.

---

## 13. Documentação

| Documento | Para quê |
|---|---|
| [`README.md`](README.md) | **Este arquivo.** Visão geral, arquitetura, modelo de dados, API e decisões. |
| [`IDENTIDADE-VISUAL.md`](IDENTIDADE-VISUAL.md) | A paleta, a tipografia e o logo **extraídos do site institucional da empresa** — com a procedência de cada valor e a checagem de contraste contra a WCAG. |
| [`MANUAL-OPERACAO.md`](MANUAL-OPERACAO.md) | Como rodar do zero, testar, gerar hashes, aplicar permissões, demonstrar e diagnosticar. |
| [`.github/SECURITY.md`](.github/SECURITY.md) | Política de segurança: como reportar uma vulnerabilidade pelo canal privado. |
| [`.github/workflows/ci.yml`](.github/workflows/ci.yml) | O pipeline: build, testes, lint e SBOM a cada push. |

**Ferramentas de apoio** que acompanham o projeto:

| Ferramenta | Para quê |
|---|---|
| `node scripts/verificar-higiene.js` | Procura caracteres de outro alfabeto, palavras em inglês e erros de digitação nos comentários. |
| `python scripts/enriquecer-javadocs.py` | Ferramenta usada durante o desenvolvimento para padronizar a documentação dos controllers. |
| `mvn test` | **63 testes**, sem depender de banco de dados. |
| `mvn package -DskipTests` | Gera o **SBOM** em `target/bom.json` (inventário de dependências). |
| `mvn -Psecurity-scan verify` | Varredura de CVEs (falha o build em CVSS ≥ 7). Requer `NVD_API_KEY`. |
| `npm run lint` | Verificação de estilo do frontend. |
