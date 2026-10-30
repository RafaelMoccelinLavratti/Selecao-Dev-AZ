# Sistema de Leilões (Avaliação AZ Tecnologia)

Aplicação full stack para consulta e gestão de leilões, construída para a
avaliação técnica da **AZ Tecnologia em Gestão**.

Sobre o *starter* fornecido, cobre os 8 exercícios do enunciado e mantém a
arquitetura em camadas que ele pede.

## Os 8 exercícios

| # | Exercício | Onde está |
|---|-----------|-----------|
| 1 | Script DDL (tabelas, constraints, sequências) | [`sql/01_ddl.sql`](leilao-api/src/main/resources/sql/01_ddl.sql) |
| 2 | Script DML (massa de dados, 10+ por tabela) | [`sql/02_dml.sql`](leilao-api/src/main/resources/sql/02_dml.sql) |
| 3 | CRUD REST de todas as tabelas | pacotes `service`, `business`, `repository` |
| 4 | Tela principal, menu e rotas `#/nomedatela` | [`App.vue`](leilao-app/src/App.vue), [`router/index.js`](leilao-app/src/router/index.js) |
| 5 | Unidades com grid editável (CRUD) | [`Unidades.vue`](leilao-app/src/views/unidade/Unidades.vue) |
| 6 | Empresas em consulta | [`Empresas.vue`](leilao-app/src/views/empresa/Empresas.vue) |
| 7 | Leilões com o total calculado | [`Leiloes.vue`](leilao-app/src/views/leilao/Leiloes.vue) |
| 8 | Cadastro/edição de empresa com validações | [`Empresa.vue`](leilao-app/src/views/empresa/Empresa.vue) |

## Stack

| Camada | Tecnologia |
|--------|-----------|
| Banco | PostgreSQL 16 (funciona do 10 em diante) |
| API | Spring Boot 2.7, Java 11, Spring Data JPA, Bean Validation |
| Segurança | Spring Security 5.7, Argon2id, JWT RS256 (JJWT + BouncyCastle) |
| Frontend | Vue 2.7, Vue Router, Vuetify 2.7, Axios, Vue CLI 5 |
| Tipografia | Barlow (texto) e Tomorrow (títulos), as oficiais da AZ |

A paleta, a fonte e o logo saíram do site institucional da empresa. A
procedência de cada valor está em [`IDENTIDADE-VISUAL.md`](IDENTIDADE-VISUAL.md).

## Como rodar

Resumido. O passo a passo completo, com diagnóstico, está em
[`MANUAL-OPERACAO.md`](MANUAL-OPERACAO.md).

```bash
# 1) Banco, já criado e populado na primeira subida
docker compose -f docker-database.yml up -d

# 2) API na porta 8081
cd leilao-api && mvn spring-boot:run

# 3) Frontend na porta 8080
cd leilao-app && npm install && npm run serve
```

Acesse <http://localhost:8080>.

Sem Docker? Crie o banco e rode os dois scripts SQL:

```bash
createdb -U postgres az
psql -U postgres -d az -f leilao-api/src/main/resources/sql/01_ddl.sql
psql -U postgres -d az -f leilao-api/src/main/resources/sql/02_dml.sql
```

Os testes não precisam de banco:

```bash
cd leilao-api && mvn test
```

São 90 testes, e `mvn test` passa em qualquer máquina, sem PostgreSQL no ar.

## Arquitetura

O enunciado pede 4 camadas, e cada uma tem uma responsabilidade só:

```
   Navegador
      │  HTTP/JSON
      ▼
┌────────────────────────────────────┐
│ service     controllers REST       │  traduzem HTTP ↔ Java
│             (sem regra de negócio) │
└────────────────┬───────────────────┘
                 ▼
┌────────────────────────────────────┐
│ business    UnidadeBO, EmpresaBO   │  regra de negócio
│             valida e calcula       │
└────────────────┬───────────────────┘
                 ▼
┌────────────────────────────────────┐
│ repository  Spring Data JPA        │  acesso a dados
│             (gera o SQL)            │
└────────────────┬───────────────────┘
                 ▼
┌────────────────────────────────────┐
│ entity      mapeamento do banco    │
└────────────────────────────────────┘
```

Separar assim deixa as regras testáveis sem subir servidor nem banco, e
impede que mexer no SQL afete o controller (ou vice-versa).

### Onde fica cada coisa

```
leilao-api/src/main/java/br/com/selecao/locadora/
├── business/       regras de negócio (UnidadeBO, TokenJwtBO, ...)
├── config/         segurança, CORS, filtros, codificador de senha
├── service/        controllers REST
├── dto/            objetos de entrada e saída
├── entity/         mapeamento do banco
├── exception/      tratamento global de erro
└── repository/     acesso a dados

leilao-app/src/
├── App.vue         tela principal e menu (exercício 4)
├── components/     AppMenu, AppNotificacoes, ConfirmDialog
├── services/api.js único ponto de contato com a API
├── store/          estado global (Vuex)
├── utils/          máscaras de CNPJ, CEP e telefone
├── assets/brand/   logo e tokens de cor e tipografia da AZ
└── views/          uma pasta por exercício
```

## Modelo de dados

```
empresa ──< leilao ──< lote
   │           │
   └───────────┴──< comprador >── empresa
```

| Tabela | Chave primária | Observações |
|--------|-----------------|-------------|
| `tb_empresa` | `id` (sequência) | `cnpj` e `usuario` são UNIQUE, `razao_social` e `cnpj` são NOT NULL |
| `tb_leilao` | `id` (sequência) | `vendedor` → FK `tb_empresa` com ON DELETE RESTRICT |
| `tb_lote` | `id` (sequência) | `leilao` → FK `tb_leilao` com ON DELETE CASCADE, `valor_inicial` é NUMERIC(14,2) |
| `tb_unidade` | `id` (sequência) | `nome` NOT NULL e UNIQUE |
| `tb_comprador` | `(empresa, leilao)`, composta | junção, mapeada no JPA com `@EmbeddedId` |

Mais `tb_refresh_token` (tokens de renovação) e `tb_auditoria` (trilha de
auditoria).

Algumas decisões que valem explicar:

- **Dinheiro em NUMERIC(14,2).** Ponto flutuante perde centavos (em `double`,
  `0.1 + 0.2` dá `0.3` com sobra de ruído).
- **Sequência para todo id.** Evita "duplicate key" quando duas pessoas
  cadastram ao mesmo tempo, coisa que um `MAX(id)+1` não resolve.
- **`OWNED BY` ligando a sequência à tabela.** Sem esse vínculo o
  `TRUNCATE ... RESTART IDENTITY` não reinicia os contadores e a massa de
  dados quebra (a empresa de id 1 deixa de existir).
- **`ON DELETE RESTRICT` em `leilao.vendedor`.** Apagar a vendedora
  quebraria o histórico dos leilões que ela vendeu.
- **`ON DELETE CASCADE` em `lote.leilao`.** Lote sem leilão não faz sentido.
  A aplicação avisa antes de excluir um leilão que ainda tenha lotes.
- **`snake_case` no banco.** O diagrama usa `camelCase` (`razaoSocial`), o
  mercado usa `snake_case` (`razao_social`). A ligação fica explícita no
  `@Column` de cada entidade.
- **`CHECK` constraints.** O banco garante CNPJ com 14 dígitos, e-mail e site
  com formato válido, telefone só com dígitos e `quantidade`/`valor_inicial`
  não negativos. Valida nos dois lados: quem buscar o dado direto no banco
  também fica protegido.

## API

Todas as respostas de erro saem no mesmo formato:

```json
{
  "timestamp": "2026-10-29T12:37:42.577",
  "status": 400,
  "erro": "Dados invalidos",
  "mensagem": "Verifique os campos destacados.",
  "campos": { "cnpj": "CNPJ invalido. Use 14 digitos ou o formato 00.000.000/0000-00." }
}
```

| Recurso | Rotas |
|---------|-------|
| Unidades | GET `/unidades`, GET `/unidades/{id}`, POST, PUT, DELETE |
| Empresas | GET `/empresas`, GET `/empresas/{id}`, POST, PUT, DELETE |
| Leilões | GET `/leiloes` (com vendedor e total), GET `/leiloes/{id}`, POST, PUT, DELETE |
| Lotes | GET `/lotes`, GET `/lotes/leilao/{leilaoId}`, GET `/lotes/{id}`, POST, PUT, DELETE |
| Compradores | GET `/compradores`, GET `/compradores/{empresaId}/{leilaoId}`, POST, PUT, DELETE |
| Autenticação | POST `/auth/login`, POST `/auth/refresh`, POST `/auth/logout`, GET `/auth/me` |
| Auditoria | GET `/auditoria`, GET `/auditoria/verificar` |
| Monitoramento | GET `/actuator/health` |

Cada recurso responde no singular e no plural (`/unidade/1` e `/unidades/1`
são o mesmo código). O enunciado mistura as duas formas, e aceitar as duas
custa uma linha. O mesmo vale para `/auth/*` e `/autenticacao/*`.

## Segurança

### Senha e autenticação

- **A senha nunca é gravada em texto puro.** O banco guarda o hash Argon2id
  (64 MiB de memória, 2 iterações, salt de 16 bytes), com o prefixo
  `{argon2}` que identifica o algoritmo.
  - *Por que Argon2id e não BCrypt?* A OWASP passou a recomendá-lo como
    primeira opção no *Password Storage Cheat Sheet* de 2023: o Argon2id usa
    **memória** como recurso principal, e cada tentativa de ataque consome
    dezenas de megabytes. Isso trava o ataque paralelo em GPU, contra o
    qual o BCrypt (que usa pouca memória) só se defende em parte.
  - **A migração não quebrou ninguém.** O `CodificadorDeSenhaDelegado`
    reconhece o formato do hash já gravado: os hashes antigos em BCrypt
    continuam valendo e são reescritos sozinhos no primeiro login. A base
    migra no ritmo do uso, sem script em lote e sem janela de manutenção.
- **A senha nunca sai da API.** O DTO de resposta (`EmpresaResponse`) não
  tem campo de senha: nem em texto puro, nem o hash.
- **A autenticação liga por configuração, não por código.** No perfil padrão
  os endpoints ficam abertos, como o enunciado pede. Com
  `SECURITY_ENABLED=true` (perfil `prod`), toda rota exige credencial. O
  mesmo código serve pros dois casos, sem `if` espalhado pelo projeto.
- **Dois mecanismos, o mesmo código.** `SECURITY_MODE=basic` (HTTP Basic) ou
  `SECURITY_MODE=jwt` (token JWT). Os dois produzem o mesmo objeto de
  autenticação no contexto de segurança, então as regras de acesso não
  mudam. Trocar de modo é mudar uma propriedade.
- **JWT assimétrico (RS256)** com rotação de chaves pelo cabeçalho `kid`, e
  detecção de reuso na renovação: um token usado de novo revoga a família
  inteira.
- **Falha rápida.** No perfil `prod` as credenciais do banco não têm valor
  padrão. Se faltarem, a aplicação não sobe, em vez de subir com uma senha
  fraca em produção.

### Validação de entrada

- **Bean Validation em todos os DTOs de entrada**: obrigatoriedade, tamanho
  máximo (igual ao da coluna no banco), formato de CNPJ, CEP, telefone,
  e-mail e URL.
- **Validação nos dois lados.** O frontend valida para dar boa experiência
  (erro na hora, sem perder o que foi digitado); o backend valida porque é
  a camada que não pode ser contornada. As regras são as mesmas dos dois
  lados, então ninguém vê "a tela aceitou e a API recusou".
- **Entrada normalizada.** CNPJ, CEP e telefone são gravados sempre só com
  dígitos, mesmo que o usuário digite `11.422.377/0001-55`. Aí a busca por
  CNPJ funciona com qualquer forma digitada.
- **Lista de campos permitidos.** A API nunca grava a entidade direto: usa
  DTOs de entrada só com o que pode ser alterado. O cliente não manda o `id`
  para trocar a chave de um registro, nem o `created_at` para falsificar a
  trilha de auditoria.

### Proteção do banco

- **SQL Injection:** todo SQL é gerado pelo Hibernate com parâmetros
  vinculados. A única consulta escrita à mão (o total dos leilões) é JPQL,
  e JPQL também usa parâmetros vinculados.
- **Integridade referencial no banco:** PK, FK, UNIQUE e CHECK. Mesmo que a
  aplicação seja contornada, o banco recusa dados inválidos.
- **Menor privilégio (opcional):** o script
  [`sql/seguranca/03_permissoes.sql`](leilao-api/src/main/resources/sql/seguranca/03_permissoes.sql)
  cria um usuário de banco separado, com apenas SELECT/INSERT/UPDATE/DELETE e
  sem permissão de DROP/ALTER. Se a aplicação for comprometida, o dano fica
  nos dados, não no banco.

### Aplicação e navegador

| Mecanismo | O que faz |
|-----------|-----------|
| CORS com lista explícita | Só as origem em `CORS_ORIGINS` chamam a API. Nunca `*` junto com credenciais, que permitiria CSRF entre origens. |
| Cabeçalhos de segurança | `X-Content-Type-Options`, `X-Frame-Options: DENY` (anti-clickjacking), `Referrer-Policy`, `Cache-Control: no-store`, `Content-Security-Policy: default-src 'none'`, `Permissions-Policy`, COOP/CORP/COEP. |
| Sem vazar stack trace | Erro 500 devolve mensagem genérica; o detalhe real fica só no log do servidor. |
| CSRF desligado, com motivo | A API é stateless e não usa cookie de sessão (a autenticação é por cabeçalho). Sem cookie automático, CSRF não se aplica. |
| Sessão stateless | Sem estado no servidor: mais rápido e escala para várias instâncias. |
| HSTS | Força HTTPS nas requisições seguintes. |
| Actuator restrito | Só `health` e `info`. O padrão do Spring também expõe `env`, `beans` e `configprops`, que vazam informação interna. |
| Anti-força-bruta | 5 falhas por usuário e 20 por IP, janela de 15 min. Responde 429 com `Retry-After`. |
| Limite de volume | 120 requisições por IP a cada 60 s, em janela deslizante. Protege o recurso, não só a conta. |
| Trilha de auditoria | Cada registro é selado por HMAC-SHA256 e encadeado no anterior. Alteração, forja ou remoção aparecem na verificação, com a sequência exata da ruptura. |
| Identificador de correlação | `X-Correlation-Id` em toda requisição, presente em todas as linhas de log. |
| Datas de controle | `created_at`/`updated_at` automáticos em todas as tabelas (base `Auditable`). |
| SBOM | Inventário das dependências gerado a cada build e guardado como artefato do CI. |
| Varredura de CVEs | Perfil Maven opcional (`-Psecurity-scan`) que cruza as dependências com a base de vulnerabilidades. Precisa da chave `NVD_API_KEY`, então roda sob demanda, não a cada push. |
| CI | Build, testes, lint e SBOM a cada push. |

**O que não está no repositório:** nenhuma senha real versionada, nenhum
token ou chave no código do frontend (tudo ali vira código que chega ao
navegador) e credenciais de banco sempre por variável de ambiente.

Um ponto que vale deixar claro: os endpoints estão abertos por padrão porque
o enunciado pede que `GET /unidades` responda sem login. O mecanismo para
exigir autenticação existe, está testado e liga por variável de ambiente.

## Decisões em relação ao starter

| Mudança | Por quê |
|---------|---------|
| **Vue CLI 4 → 5** (webpack 4 → 5) | O webpack 4 usa o hash MD4, que o OpenSSL 3 (padrão no Node 17+) removeu. O build quebrava com `ERR_OSSL_EVP_UNSUPPORTED`. O webpack 5 usa SHA-256 e funciona do Node 12 ao 24 sem nenhuma flag. |
| **Vue 2.6 → 2.7** | Versão final do Vue 2, com correções de segurança e compatibilidade total com o Vuetify 2. |
| **Vuetify 2 adicionado** | Não vinha no starter, mas os exercícios 5 a 8 exigem `v-data-table`. Importado sem `sass`/`vuetify-loader`, o que reduz dependências e risco de build. |
| **`spring-boot-starter-validation` adicionado** | Saiu do `spring-starter-web` no Spring Boot 2.3. Sem ele as anotações `@NotBlank`/`@Size`/`@Pattern` dos DTOs não são verificadas, e o exercício 8 depende delas. |
| **`spring-security-crypto` + `spring-boot-starter-security`** | Hash de senha e cadeia de segurança configurável. Sem o starter, o Spring Boot geraria uma senha aleatória e bloquearia todos os endpoints. |
| **`spring-boot-starter-actuator`** | Health check, que é o que um orquestrador consulta para saber se a aplicação está viva. |
| **`POSTGRES_DB=az` no `variables.env`** | Sem isso o container cria só o banco `postgres` e a API falha com `database "az" does not exist`. |
| **Colunas em `snake_case`** | Padrão do mercado, com a ligação para o `camelCase` do diagrama explícita no `@Column`. |
| **IDs `Integer`** | Bate com o `integer` do diagrama e evita a incompatibilidade de tipo do PostgreSQL (`integer` × `bigint`). |
| **URLs em singular e plural** | O enunciado é inconsistente entre os exercícios 3 e 5 a 7. Aceitar as duas formas é mais robusto do que escolher uma. |
| **`views/leilao/Leiloes.vue`** (maiúscula) | O exercício 4 escreve `leiloes.vue` e o 7 escreve `Leiloes.vue`. Em Linux a diferença de maiúsculas importa. |
| **`#/empresa/:id`** (não `#/leilao/:id`) | O próprio enunciado manda o link de edição de empresa na tela do exercício 6. |
| **`Auditable` como base das entidades** | `created_at`/`updated_at` aparecem nas 5 tabelas. Uma `@MappedSuperclass` evita repetir o mesmo código 5 vezes. |
| **Chave composta no `comprador`** | Mapeado com `@EmbeddedId` (`CompradorId`), como a PK composta do diagrama. |

## Testes

90 testes, todos sem banco de dados (dublês do Mockito ou `@WebMvcTest`).

| Classe | O que garante |
|--------|---------------|
| `PasswordEncoderTest` | A senha gravada é hash, não texto puro; duas senhas iguais geram hashes diferentes; o hash legado em BCrypt continua funcionando. |
| `EmpresaBOTest` | CNPJ/CEP/telefone são normalizados; duplicados são recusados com mensagem clara; o hash anterior é preservado quando a alteração não traz senha nova; o DTO de resposta não tem campo de senha. |
| `UnidadeBOTest` | Nome é normalizado; nome repetido é recusado; id inexistente devolve "não encontrado". |
| `GeradorHashSenhaTest` | Gera os hashes da massa de dados e confere que cada hash do `02_dml.sql` bate com a senha correspondente. |
| `AuditoriaBOTest` | A corrente de hashes se forma e alterar, forjar ou remover um registro é detectado. |
| `TokenJwtBOTest` | Token adulterado, expirado, de tipo errado ou assinado com outra chave é recusado. |
| `LimitadorDeRequisicoesFilterTest` | 429 no estouro do limite, `Retry-After` com tempo real e isolamento por IP. |
| `CorrelationIdFilterTest` | ID único por requisição, sem vazar para a seguinte, e valor malicioso do cliente é descartado (log forging). |
| `SegurancaIntegracaoTest` | A cadeia de filtros de ponta a ponta: 401 em JSON, 429, 200 com o dado do controller, `WWW-Authenticate` e health check não barrado. |
| `LoginAttemptServiceTest` | Contadores, janela deslizante, bloqueio por usuário e por IP, limpeza no sucesso. |
| `ClientIpResolverTest` | `X-Forwarded-For` só é confiado quando há proxy confiável. |
| `ClientIpResolverProxyTest` | O IP forjado pelo cliente não vira o IP usado: o limitador por IP não pode ser burlado. |
| `AtaqueSegurancaTest` | Ataques de verdade: token sem assinatura, confusão de algoritmo, SQL injection, IDOR, actuator exposto e tempo de resposta que denuncia usuário. |
| `AutenticacaoBOTest` | O login por JSON conta a falha no mesmo controle de tentativas que o login por HTTP Basic. |

## Problemas conhecidos

| Sintoma | Causa | Solução |
|---------|-------|---------|
| `ERR_OSSL_EVP_UNSUPPORTED` no `npm run serve` | Node 17+ removeu o MD4 do OpenSSL, usado pelo webpack 4 | Resolvido no projeto: Vue CLI 5 / webpack 5 |
| `database "az" does not exist` | Faltou `POSTGRES_DB=az` no `variables.env` | Já corrigido no `variables.env` |
| `No mapping for GET /...` no console | URL que não existe na API | Confira a porta: a API é a 8081, o frontend é a 8080 |
| Erro de CORS no console | Frontend e API em origens diferentes sem permissão | Ajuste `app.cors.origins` se mudar a porta do frontend |
| `Connection refused` ao iniciar a API | PostgreSQL parado | `docker compose -f docker-database.yml up -d` |

O diagnóstico completo está na seção 10 do
[`MANUAL-OPERACAO.md`](MANUAL-OPERACAO.md).

## Dados de teste

A massa de dados tem 12 empresas, 12 unidades, 12 leilões, 18 lotes e 14
compradores.

| Empresa | CNPJ | Usuário | Senha |
|---------|------|---------|-------|
| Metalurgica Aurora Ltda | 11.422.377/0001-55 | `admin.aurora` | `Senha@1` |
| Transportes Vale Verde S.A. | 11.422.377/0002-36 | `operador.valverde` | `Senha@2` |
| Agroindustrial Boa Vista S.A. | 11.422.377/0003-17 | `admin.boavista` | `Senha@3` |
| … | … | … | … |

O banco guarda o hash de `Senha@N`, não a senha. Para conferir:
`mvn test -Dtest=GeradorHashSenhaTest`.

O total do leilão 1001, por exemplo: `5 × 12.500,00 + 2 × 8.750,00 = 80.000,00`.

## Documentos

| Documento | Para quê |
|---|---|
| [`README.md`](README.md) | Este arquivo: visão geral, arquitetura, modelo, API e segurança. |
| [`MANUAL-OPERACAO.md`](MANUAL-OPERACAO.md) | Rodar do zero, diagnosticar, demonstrar. |
| [`IDENTIDADE-VISUAL.md`](IDENTIDADE-VISUAL.md) | De onde saíram as cores, a fonte e o logo, e a checagem de contraste. |
| [`leilao-app/README.md`](leilao-app/README.md) | Detalhes do frontend. |
| [`.github/SECURITY.md`](.github/SECURITY.md) | Como reportar uma vulnerabilidade pelo canal privado. |

### Comandos

| Comando | Para quê |
|---|---|
| `mvn test` | 90 testes, sem depender de banco. |
| `mvn package -DskipTests` | Gera o SBOM em `target/bom.json`. |
| `mvn -Psecurity-scan verify` | Varredura de CVEs. Requer `NVD_API_KEY`. |
| `npm run lint` | Estilo do frontend. |
