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

### O jeito mais rápido (Windows)

Dois scripts cuidam de tudo: banco, API e frontend.

```powershell
# Sobe tudo (e abre o navegador)
.\iniciar.ps1

# Sobe sem exigir login na tela (como o enunciado do teste pede)
.\iniciar.ps1 -SemLogin

# Derruba a API e o frontend (o banco continua no ar, com os dados)
.\parar.ps1
```

O `iniciar.ps1` resolve dois detalhes que costumam travar a primeira
execução:

- **Aponta o `JAVA_HOME` para o JDK 11.** O projeto é Spring Boot 2.7 /
  Java 11, e o Lombok que vem com ele não compila no JDK 21 (erro
  `NoSuchFieldError: JCImport.qualid`). O ajuste vale só para a sessão do
  script.
- **Sobe o PostgreSQL local**, se o Docker não estiver instalado — usando
  o cluster que está em `_tools/pgsql`. O `docker-database.yml` continua
  sendo o caminho oficial; o script é o atalho para esta máquina.

Depois:

| Endereço | O que é |
|---|---|
| <http://localhost:8080> | A aplicação |
| <http://localhost:8081/swagger-ui.html> | A documentação da API |
| <http://localhost:8081/actuator/health> | O health check |

### O caminho oficial (Docker)

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

São 159 testes. 158 rodam sem PostgreSQL (dublês do Mockito, `@WebMvcTest` ou
`@DataJpaTest` sem conexão) e **1 exige o banco no ar** — o
`AuditoriaPrecisaoTimestampTest`, que grava num PostgreSQL de verdade para
conferir a precisão do timestamp (é o único lugar onde o arredondamento do
banco aparece). Sem o banco, esse teste é **pulado**, não reprovado: o build
passa igual.

### A pasta `sql/`

```
leilao-api/src/main/resources/sql/
├── 01_ddl.sql              estrutura das 5 tabelas
├── 02_dml.sql              massa de dados inicial
├── database.png            o diagrama do enunciado
├── seguranca/
│   └── 03_permissoes.sql   usuário de banco com menor privilégio (opcional)
└── exemplos/
    └── spcript.sql         script original do starter, só como referência
```

A subpasta `exemplos/` não é opcional, e o motivo é prático: o container do
PostgreSQL executa automaticamente todo `.sql` que está **diretamente** na
pasta montada em `/docker-entrypoint-initdb.d` (o utilitário do container usa
`maxdepth 1` e não entra em subpastas). O `spcript.sql` do starter cria
`tb_unidade` e `seq_unidade`, que o `01_ddl.sql` já cria — se ele rodasse
automatically, o container abortaria com `relation tb_unidade already
exists` e o banco nem subiria.

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
| Documentação | GET `/swagger-ui.html` (interface), GET `/v3/api-docs` (OpenAPI) |

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
  fraca em produção. O mesmo vale para a pasta de chaves do JWT e para a
  chave do HMAC da auditoria: sem elas, a aplicação não sobe.
- **O logout encerra a sessão de verdade.** O token de acesso é um JWT
  stateless, então valeria até expirar (15 minutos) depois do logout. Para
  fechar isso, o token carrega o identificador da família de sessão
  (`fam`): no logout e na detecção de reuso, a família entra numa lista de
  bloqueio, e o filtro de JWT recusa qualquer token dela. O registro expira
  sozinho, quando nenhum token da família puder mais valer.
- **A documentação da API é gerada do código.** O `springdoc` monta a
  especificação OpenAPI a partir das anotações, e a interface fica em
  `/swagger-ui.html`. A documentação não pode ficar desatualizada em
  relação ao código.

### Autorização: cada empresa vê só o que é dela

O modelo de dados já era multi-empresa — o leilão tem uma empresa
vendedora, e a tabela `tb_comprador` liga empresa e leilão. O que faltava
era o controle de acesso que usa essa informação: bastava estar
autenticado para ver a lista de empresas com CNPJ, telefone, e-mail e nome
de usuário de todos os concorrentes.

Agora há dois papéis:

| Papel | Quem é | O que alcança |
|-------|--------|---------------|
| `ROLE_ADMIN` | Administrador da API (da configuração) | Tudo, inclusive a trilha de auditoria. |
| `ROLE_EMPRESA` | Usuário cadastrado na `tb_empresa` | Só os próprios dados, os leilões que vende e os em que está inscrita como compradora. |

As regras, em uma frase: **o administrador vê tudo; a empresa vê o que tem
relação com ela.** O filtro das listagens vem do banco (a query já vem
com a condição), e o filtro dos recursos individuais é uma verificação de
posse — a mesma mensagem de negação para recurso existente e inexistente,
para não revelar quais ids são válidos.

Os usuários das empresas entram com o `usuario` e a `senha` da própria
`tb_empresa` — o mesmo cadastro que já existia no banco, e que até então
não servia para ninguém entrar.

### A brecha que a verificação de dono não fechava

Vale registrar aqui, porque é o tipo de detalhe que separa um sistema
"funcionando" de um sistema "pensado".

A verificação de posse responde a uma pergunta: **esta pessoa pode mexer
neste recurso, agora?** A resposta é "sim, se ela é a dona". E essa
resposta está certa — para a leitura e para a maior parte da escrita.

Mas a alteração de um leilão também pode trocar **quem é o vendedor**. E
essa troca não passa pela verificação de dono: ela olha o leilão como ele
está, e não como ele vai ficar.

O que a brecha permitia, na prática:

```
PUT /leiloes/1   { "codigo": 1001, "descricao": "...", "vendedor": 5 }
```

A empresa 1 é a vendedora do leilão 1. A verificação de posse diz "ok,
ela é a dona". A alteração acontece... e o leilão passa a ter a empresa 5
como vendedora. A empresa 1 perdeu o controle — mas o mal maior é o
caminho inverso: a empresa 5 ganhou, e pode repetir a operação com o
leilão de outra empresa. O controle de acesso virava uma roleta: quem
gira mais vezes leva.

O mesmo valia para o lote, que podia ser movido para o leilão de outra
empresa — ou trazido para o próprio, dando à empresa o controle do valor
inicial de um item que não é dela.

A correção são duas verificações, e as duas importam:

| Verificação | Pergunta que ela responde |
|-------------|---------------------------|
| `exigirEscritaLeilao(id)` | Esta pessoa pode mexer neste leilão? |
| `exigirProprioOuAdministrador(vendedor)` | O novo vendedor é a própria pessoa (ou o administrador)? |

O mesmo para o lote, trocando "vendedor" por "leilão".

O que impede a regressão são dois níveis de teste, porque cada um pega
uma coisa diferente:

- **`AutorizacaoBOTest`** — a regra de negócio: uma empresa não
  transfere o próprio leilão para outra;
- **`AutorizacaoEndpointTest`** — a regra *no endpoint*: o PUT de verdade
  volta **403** e **não grava nada**. Uma regra testada na unidade pode
  não estar ligada ao controller; este teste fecha essa porta.

### A autenticação no frontend

O backend já falava JWT, papéis e revogação de sessão — mas o frontend
ignorava tudo isso. Era uma feature backend-only: com a segurança
ligada, o frontend simplesmente quebrava com 401.

O que existe agora:

| Peça | Onde | O que faz |
|------|------|-----------|
| Tela de login | `src/views/Login.vue` | Modal que pede usuário e senha e chama `POST /auth/login`. |
| Sessão | `src/store/index.js` | Guarda os tokens e os dados do usuário (papel, empresa), em memória e no `localStorage`. |
| Token na requisição | `src/services/api.js` | *Interceptor* do axios que anexa o `Authorization: Bearer` em **toda** chamada. |
| Guarda de rota | `src/router/index.js` | Abre a tela de login quando a API exige credencial e não há sessão. |
| Fim de sessão | `src/App.vue` | O botão "Sair" revoga a sessão na API e limpa o estado local. |

Duas decisões que valem explicar:

**O token entra por interceptor, e não por tela.** A sessão é uma
propriedade da aplicação, não de uma tela. Se cada tela lembrasse de
anexar o token, a esqueceria seria certa — e o resultado seria um 401
que ninguém entenderia. O interceptor garante que nenhuma requisição sai
sem o token, sem depender de quem a disparou.

**A tela recarrega quando a sessão muda.** As telas carregam os dados
quando nascem, e nesse instante ainda não há token: o login acontece
depois. A primeira carga voltaria 401 e a tela ficaria vazia — mesmo
depois do login, porque ninguém pediu para recarregar. A store mantém um
contador de geração da sessão, e as telas o observam para recarregar
quando o login termina.

Uma observação honesta sobre o tradeoff: o token fica no `localStorage`,
que sobrevive ao fechamento do navegador e é legível por qualquer script
da página — um XSS rouba o token. A alternativa mais forte seria o
cookie `HttpOnly`, que o JavaScript não consegue ler, mas ela exige que
a API funcione com credencial de origem e com a proteção de CSRF ligada —
o que não cabe no modelo stateless por cabeçalho que este projeto usa. A
mitigação que cabe aqui: o token de acesso expira em 15 minutos, e o
logout revoga a sessão inteira — o token deixa de valer na hora, sem
esperar expirar.

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
- **O banco não fica exposto na rede.** O `docker-database.yml` publica a
  porta 5432 em `127.0.0.1`, e não em `0.0.0.0`. A diferença não é
  cosmética: um `5432:5432` sem endereço faz o Docker escutar em **todas**
  as interfaces da máquina — num notebook ligado na rede do escritório, o
  PostgreSQL fica acessível para qualquer pessoa da mesma rede, com a senha
  do `.env` (que, em desenvolvimento, é pública). Com o `127.0.0.1`
  antes dos dois pontos, a porta só escuta na própria máquina, e a API
  continua conectando em `localhost:5432` sem nenhuma mudança.

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
| **`POSTGRES_DB=az` no `.env`** | Sem isso o container cria só o banco `postgres` e a API falha com `database "az" does not exist`. |
| **Colunas em `snake_case`** | Padrão do mercado, com a ligação para o `camelCase` do diagrama explícita no `@Column`. |
| **IDs `Integer`** | Bate com o `integer` do diagrama e evita a incompatibilidade de tipo do PostgreSQL (`integer` × `bigint`). |
| **URLs em singular e plural** | O enunciado é inconsistente entre os exercícios 3 e 5 a 7. Aceitar as duas formas é mais robusto do que escolher uma. |
| **`views/leilao/Leiloes.vue`** (maiúscula) | O exercício 4 escreve `leiloes.vue` e o 7 escreve `Leiloes.vue`. Em Linux a diferença de maiúsculas importa. |
| **`#/empresa/:id`** (não `#/leilao/:id`) | O próprio enunciado manda o link de edição de empresa na tela do exercício 6. |
| **`Auditable` como base das entidades** | `created_at`/`updated_at` aparecem nas 5 tabelas. Uma `@MappedSuperclass` evita repetir o mesmo código 5 vezes. |
| **Chave composta no `comprador`** | Mapeado com `@EmbeddedId` (`CompradorId`), como a PK composta do diagrama. |

## Testes

159 testes. 158 rodam sem banco de dados (dublês do Mockito, `@WebMvcTest` ou
`@DataJpaTest` sem conexão). O `AuditoriaPrecisaoTimestampTest` é a
exceção: ele grava num PostgreSQL de verdade e relê, porque é o único lugar
onde a perda de precisão do timestamp aparece — o `LocalDateTime` do Java
tem 9 casas (nanossegundos) e a coluna `TIMESTAMP` do PostgreSQL guarda 6
(microsegundos). Sem o banco, ele é pulado.

| Classe | O que garante |
|--------|---------------|
| `PasswordEncoderTest` | A senha gravada é hash, não texto puro; duas senhas iguais geram hashes diferentes; o hash legado em BCrypt continua funcionando. |
| `EmpresaBOTest` | CNPJ/CEP/telefone são normalizados; duplicados são recusados com mensagem clara; o hash anterior é preservado quando a alteração não traz senha nova; o DTO de resposta não tem campo de senha. |
| `UnidadeBOTest` | Nome é normalizado; nome repetido é recusado; id inexistente devolve "não encontrado". |
| `GeradorHashSenhaTest` | Gera os hashes da massa de dados e confere que cada hash do `02_dml.sql` bate com a senha correspondente. |
| `AuditoriaBOTest` | A corrente de hashes se forma e alterar, forjar ou remover um registro é detectado. |
| `AuditoriaPrecisaoTimestampTest` | O instante gravado no banco tem a mesma precisão do selo — o Java tem 9 casas, o PostgreSQL guarda 6. Sem o banco, o teste é pulado. |
| `TokenJwtBOTest` | Token adulterado, expirado, de tipo errado ou assinado com outra chave é recusado. |
| `LimitadorDeRequisicoesFilterTest` | 429 no estouro do limite, `Retry-After` com tempo real e isolamento por IP. |
| `CorrelationIdFilterTest` | ID único por requisição, sem vazar para a seguinte, e valor malicioso do cliente é descartado (log forging). |
| `SegurancaIntegracaoTest` | A cadeia de filtros de ponta a ponta: 401 em JSON, 429, 200 com o dado do controller, `WWW-Authenticate` e health check não barrado. |
| `LoginAttemptServiceTest` | Contadores, janela deslizante, bloqueio por usuário e por IP, limpeza no sucesso. |
| `ClientIpResolverTest` | `X-Forwarded-For` só é confiado quando há proxy confiável. |
| `ClientIpResolverProxyTest` | O IP forjado pelo cliente não vira o IP usado: o limitador por IP não pode ser burlado. |
| `AtaqueSegurancaTest` | Ataques de verdade: token sem assinatura, confusão de algoritmo, SQL injection, IDOR, actuator exposto e tempo de resposta que denuncia usuário. |
| `AutenticacaoBOTest` | O login por JSON conta a falha no mesmo controle de tentativas que o login por HTTP Basic. |
| `AutorizacaoBOTest` | **O IDOR está fechado:** uma empresa autenticada não alcança o leilão, o lote nem o vínculo de comprador de outra. |
| `TokenRevogadoBOTest` | O logout encerra a sessão: a família revogada bloqueia o token de acesso, e o bloqueio expira sozinho. |
| `ContadorBancoTest` | O contador compartilhado entre réplicas renova a janela, reseta quando vence e apaga por prefixo. |
| `ValidacaoConfiguracaoSegurancaTest` | A aplicação não sobe com a senha padrão do administrador. |
| `ValidadeDasQueriesTest` | O JPQL das consultas delicatezas é analisado pelo Hibernate **no build**: query errada quebra o build, não a produção. |
| `AutorizacaoEndpointTest` | A autorização é verificada **no endpoint**, não só na regra: o PUT que tentaria transferir um leilão para outra empresa volta 403 e não grava nada. |
| `EstrategiaDeGeracaoDeIdTest` | A geração do id da entidade concorda com o DDL. Existe porque o `tb_refresh_token` era `BIGSERIAL` (que cria `tb_refresh_token_id_seq`) enquanto a entidade pedia `seq_refresh_token` — o primeiro login quebrava com HTTP 500. |
| `EmpresaRequestValidacaoTest` | As regras de entrada: telefone aceito em todas as formas que o usuário digita (inclusive só com dígitos, como a massa de dados usa), CNPJ e CEP com e sem pontuação, senha vazia vira `null`. |
| `LoginRequestValidacaoTest` | O login **não** aplica a regra de senha mínima: essa é do cadastro. Sem isso, a senha `Senha@1` da massa de dados era recusada com HTTP 400 e 9 das 12 empresas ficavam inacessíveis. |

A qualidade da suite também é **medida**, não só contada: o perfil
`mutacao` do Maven roda o [PIT](https://pitest.org/), que altera o código
de propósito (troca um `>=` por `>`, apaga uma validação) e verifica se
algum teste percebe. Cobertura de linha diz quanto código rodou; a
*mutation score* diz quanto código está de fato protegido — e é ela que
vira regra, com um mínimo que faz o build falhar.

## Problemas conhecidos

| Sintoma | Causa | Solução |
|---------|-------|---------|
| `ERR_OSSL_EVP_UNSUPPORTED` no `npm run serve` | Node 17+ removeu o MD4 do OpenSSL, usado pelo webpack 4 | Resolvido no projeto: Vue CLI 5 / webpack 5 |
| `database "az" does not exist` | Faltou `POSTGRES_DB=az` no `.env` | Já corrigido no `.env` |
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
| [`THREAT-MODEL.md`](THREAT-MODEL.md) | Modelo de ameaças: o que a API protege, quem tenta pegar e como cada ataque é bloqueado. |
| [`IDENTIDADE-VISUAL.md`](IDENTIDADE-VISUAL.md) | De onde saíram as cores, a fonte e o logo, e a checagem de contraste. |
| [`leilao-app/README.md`](leilao-app/README.md) | Detalhes do frontend. |
| [`.github/SECURITY.md`](.github/SECURITY.md) | Como reportar uma vulnerabilidade pelo canal privado. |

### Comandos

| Comando | Para quê |
|---|---|
| `mvn test` | 159 testes. 158 sem depender de banco; 1 exige PostgreSQL (é pulado se não houver). |
| `mvn package -DskipTests` | Gera o SBOM em `target/bom.json`. |
| `mvn -Psecurity-scan verify` | Varredura de CVEs. Requer `NVD_API_KEY`. |
| `mvn -Pmutacao test` | Mede a qualidade dos testes por mutação (PIT). |
| `npm run lint` | Estilo do frontend. |

O CI (`.github/workflows/`) roda quatro camadas de segurança:

| Workflow | O que faz |
|----------|-----------|
| `ci.yml` | Compila, roda os 159 testes e gera o SBOM. |
| `dast.yml` | **Varredura dinâmica** com o OWASP ZAP: sobe a API e a varre como um atacante faria, olhando as respostas. |
| `security-scan` (perfil Maven) | Varredura de CVEs das dependências (OWASP Dependency-Check). |
| `dependabot.yml` | Acompanha as atualizações de segurança das dependências. |
