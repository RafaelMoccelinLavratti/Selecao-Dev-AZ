# Guia de entrevista e apresentação

Tudo o que você precisa saber sobre o sistema para responder às perguntas
que a AZ Tecnologia pode fazer. As respostas são as que você deve dar — e
todas elas são verdade, testadas e verificáveis no código.

Este documento existe porque a apresentação é o momento em que você
descobre o que não sabe. Leer antes da entrevista é o que transforma
"espero que não perguntem isso" em "já sei a resposta".

---

## Sumário

1. [Antes de começar: o que rodar](#1-antes-de-começar-o-que-rodar)
2. [A apresentação em 10 minutos](#2-a-apresentação-em-10-minutos)
3. [Roteiro de perguntas: o que eles vão perguntar](#3-roteiro-de-perguntas)
4. [A API: cada endpoint, na memória](#4-a-api-cada-endpoint-na-memória)
5. [O banco: cada tabela, na memória](#5-o-banco-cada-tabela-na-memória)
6. [Segurança: as decisões e o porquê](#6-segurança-as-decisões-e-o-porquê)
7. [O frontend: as telas e as decisões](#7-o-frontend-as-telas-e-as-decisões)
8. [Testes e qualidade](#8-testes-e-qualidade)
9. [DevOps e entrega](#9-devops-e-entrega)
10. [Perguntas Starr](#10-perguntas-starr)
11. [Armadilhas: o que NÃO responder](#11-armadilhas-o-que-não-responder)
12. [Perguntas que você deve fazer](#12-perguntas-que-você-deve-fazer)
13. [Glossário](#13-glossário)

---

## 1. Antes de começar: o que rodar

O repositório tem dois scripts que sobem tudo. Use-os antes da entrevista
para não perder tempo na frente do avaliador.

```powershell
cd C:\Users\Rafael\Documents\AZ\Selecao-Dev-AZ
.\iniciar.ps1              # sobe banco + API + frontend, com login
.\parar.ps1                # derruba tudo
.\iniciar.ps1 -SemLogin    # sobe sem a tela de login (rotas abertas)
```

O que sobe:

| Serviço | Porta | Endereço |
|---|---|---|
| PostgreSQL | 5432 | banco `az`, usuário `postgres` |
| API (Spring Boot) | 8081 | http://localhost:8081 |
| Frontend (Vue) | 8080 | http://localhost:8080 |
| Swagger | 8081 | http://localhost:8081/swagger-ui.html |

### Credenciais

| Perfil | Usuário | Senha | O que vê |
|---|---|---|---|
| Administrador | `admin` | `SenhaForteAdmin123` | tudo, em todas as empresas |
| Empresa | `admin.aurora` | `Senha@1` | só os dados da Metalúrgica Aurora |

As duas contas existem na massa de dados (`02_dml.sql`). A conta de
empresa mostra a **autorização por empresa** funcionando: o mesmo backend,
visões diferentes.

### Se algo não subir

O PostgreSQL local às vezes demora a aceitar conexão logo depois de
iniciar ("the database system is starting up"). É normal na primeira vez;
esperar alguns segundos resolve. Se precisar:

```powershell
# conferir as portas
Get-NetTCPConnection -LocalPort 5432,8081,8080 -State Listen

# derrubar tudo e recomeçar
.\parar.ps1
.\iniciar.ps1
```

### Levar o banco e a aplicação prontos

- **Tenho Docker instalado?** O `docker-database.yml` sobe o PostgreSQL 15
  com volume persistente. Em vez do script local, dá para rodar
  `docker compose up -d` e apontar a aplicação para ele.
- **Sem Docker?** O script local usa o PostgreSQL instalado em
  `AZ\_tools\pgsql`.

---

## 2. A apresentação em 10 minutos

A ordem abaixo é a que funciona. Cada bloco tem o que falar e o que
mostrar.

### Bloco 1 — O que foi pedido e o que foi entregue (1 min)

> "O enunciado pede 8 exercícios. Todos os 8 estão implementados. O que
> eu fiz além do que estava escrito: uma camada de segurança real
> (autenticação, autorização, trilha de auditoria auditável), 159 testes
> automatizados, e CI com build, varredura de dependências e
> varredura dinâmica."

**Não diga** que o sistema está "pronto" ou "completo". Diga o que está
implementado e o que você sabe que não está.

### Bloco 2 — Subir na frente deles (2 min)

Rode `./iniciar.ps1` antes. Se a máquina da entrevista for outra, mostre o
código e diga como subir — mas tenha o plano B de mostrar a tela gravada.

### Bloco 3 — O modelo de dados (2 min)

Mostre `sql/01_ddl.sql` e a tabela no DBeaver/pgAdmin.

> "5 tabelas do enunciado, mais 3 que criei para a parte de segurança:
> `tb_refresh_token`, `tb_auditoria` e `tb_contador_seguranca`. Cada uma
> existe por um motivo de segurança específico, que eu explico depois."

Detalhe que impressiona: **o `tb_leilao` tem chave composta com
`tb_empresa`**, e o `tb_comprador` tem chave primária composta
(`empresaId` + `leilaoId`) — porque a mesma empresa pode participar de
vários leilões, e o mesmo leilão pode ter vários compradores.

### Bloco 4 — A API (2 min)

Abra o Swagger. Mostre um CRUD completo em 30 segundos:

```
GET    /unidades          lista
GET    /unidades/1        um registro
POST   /unidades          cria
PUT    /unidades/1        altera
DELETE /unidades/1        remove
```

> "O enunciado pede `/nomedatabela`. Usei o plural (`/unidades`,
> `/empresas`, `/leiloes`) e aceitei o singular como alias, porque o
> frontend fala com o plural mas o enunciado usa o singular — os dois
> funcionam."

### Bloco 5 — O frontend (2 min)

Abra http://localhost:8080, faça login, e mostre as 4 telas:

- `#/unidades` — grid editável (o enunciado, exercício 5)
- `#/empresas` — grid somente leitura com busca (exercício 6)
- `#/leiloes` — grid somente leitura com total calculado (exercício 7)
- `#/empresa` e `#/empresa/:id` — cadastro com validação (exercício 8)

> "O exercício 7 pede o total do leilão como a soma de quantidade ×
> valor inicial dos lotes. Mostrei que o valor exibido bate com o cálculo
> — 80.000 no primeiro leilão. E dá para conferir direto no banco."

### Bloco 6 — Segurança (2 min)

Esta é a parte que diferencia você. Mostre na ordem da mais visível para
a menos:

1. **Login errado** → 401 em JSON, não em HTML
2. **Sem token** → 401
3. **Token expirado** → renovação automática, sem o usuário perceber
4. **Empresa A acessando empresa B** → 403
5. **Auditoria** → `GET /auditoria/verificar` retorna que a trilha não foi adulterada
6. **SQL injection** → o teste de ataque no código

### Bloco 7 — Testes e CI (1 min)

```powershell
cd leilao-api && mvn test
```

> "159 testes. 158 rodam sem banco — dublês do Mockito, `@WebMvcTest` ou
> `@DataJpaTest` sem conexão. Só 1 exige PostgreSQL de verdade, e sem ele
> é pulado, não reprovado. O build não depende de banco."

E o CI: mostre o badge. Build + testes + SBOM + varredura de CVEs +
DAST com OWASP ZAP.

### Bloco 8 — Feche mostrando o que falta (1 min)

> "Se eu tivesse mais tempo, o que falta é teste no frontend — hoje a
> cobertura é só no backend. As funções de máscara e validação do
> frontend (CNPJ, CEP, telefone, e-mail, URL) são o candidato natural."

**Isso é o ponto mais importante da apresentação.** Dizer o que falta,
com um plano, vale mais do que fingir que está tudo pronto.

---

## 3. Roteiro de perguntas: o que eles vão perguntar

### Sobre o enunciado

**"Você cumpriu os 8 exercícios?"**
> Sim, todos. O 1 é o DDL com as 5 tabelas, constraints e sequences. O 2 é
> o DML com 12+ registros por tabela (o pedido era 10). O 3 é o CRUD REST
> de todas as tabelas nos 4 pacotes pedidos. O 4 é o App.vue com o menu e as
> 3 rotas. O 5 é a grid editável de unidades com CRUD completo. O 6 é a
> grid de empresas com busca. O 7 é a grid de leilões com o total
> calculado. O 8 é o cadastro de empresa com validação e os links de
> editar e excluir.

**"O que você fez além do enunciado?"**
> Quatro coisas, e cada uma por um motivo: segurança (o enunciado não
> pedia nada de segurança, e um sistema de leilão sem autenticação não
> seria aceitável em produção), testes (o enunciado pedia "teste tudo
> muito bem"), CI/CD, e documentação.

**"Por que escolheu Vue e Spring Boot? Não tinha que escolher."**
> O enunciado pedia. Mas dentro da escolha: Vue 2 com Vuetify 2 porque é
> o par estável mais documentado para grids; Spring Boot porque o
> ecossistema JPA com o Hibernate resolve o mapeamento objeto-relacional
> e as transações, que é o grosso do trabalho de CRUD.

### Sobre a arquitetura

**"Por que 4 camadas? Não é exagero para um CRUD?"**
> O enunciado pede explicitamente os pacotes `service`, `business`,
> `repository` e `entity`. E a separação tem função real: o controller
> traduz HTTP em Java e nada mais — se ele tem regra de negócio, a regra
> está no lugar errado, porque a mesma regra precisa ser reusada por
> outra entrada. A camada de repository isola o SQL. A de entity mapeia
> o banco. Para um CRUD simples eu admitiria que é mais do que o
> necessário; o que compensa é que a regra de negócio ficou testável
> sem subir servidor nem banco.

**"Onde está a regra de negócio? Me mostra."**
> Em `business/` — `UnidadeBO`, `EmpresaBO`, `LeilaoBO`, `LoteBO`,
> `CompradorBO`, `AuditoriaBO`, `AutenticacaoBO`, `TokenJwtBO`,
> `TokenRevogadoBO`, `AutorizacaoBO`. Cada um com os testes em
> `business/` ao lado.

**"Por que você não usa Service do Spring nas regras?"**
> Porque o `business` não é um bean do Spring por acidente — ele é
> explícito: a regra de negócio não depende de framework. Um `UnidadeBO`
> testado com `new` não precisa de contexto Spring, nem de banco, nem de
> mock. Quando a regra precisa de transações, ela recebe o
> `EntityManager` como parâmetro, e a transação é declarada no
> `@Service` que orquestra.

**"Como você sabe que uma camada não está fazendo o trabalho de outra?"**
> Testes. Os testes de `business` não sobem servidor HTTP nenhum. Se a
> regra estivesse no controller, ela não seria testável assim — e o
> teste mostraria isso.

### Sobre o banco

**"Como você modelou o leilão e o comprador?"**
> O `tb_leilao` tem o `vendedor` como FK para `tb_empresa`, com
> `ON DELETE RESTRICT` — apagar a vendedora quebraria o histórico dos
> leilões que ela vendeu. O `tb_comprador` tem chave primária **composta**
> `(empresa, leilao)`. O motivo é o enunciado: um mesmo comprador pode
> participar de vários leilões, e um mesmo leilão pode ter vários
> compradores. Com chave composta, o `unique` do enunciado aparece no
> par — e não há duplicata.

**"Por que sequência e não identity?"**
> O enunciado pede sequence explicitamente, como objeto. Então o DDL
> declara `CREATE SEQUENCE seq_leilao ...` e a coluna usa
> `DEFAULT nextval('seq_leilao')`.

**"As constraints do banco estão todas lá?"**
> Sim. No `01_ddl.sql`:
> - **PK**: `tb_unidade` (id), `tb_empresa` (id), `tb_leilao` (id),
>   `tb_lote` (id), `tb_comprador` **(empresa, leilao)** — composta.
> - **FK**: `tb_leilao.vendedor` → `tb_empresa` (com `ON DELETE
>   RESTRICT`), `tb_lote.leilao` → `tb_leilao`,
>   `tb_comprador.empresa` → `tb_empresa` e `tb_comprador.leilao` →
>   `tb_leilao`.
> - **UNIQUE**: `tb_empresa.cnpj` e `tb_empresa.usuario` — os dois
>   sublinhados no diagrama. E `tb_unidade.nome`, que o enunciado não
>   marca como unique mas que faz sentido (não existem duas unidades com
>   o mesmo nome).
> - **NOT NULL**: todas as colunas com asterisco no diagrama.
> - As 3 tabelas de segurança também têm PK e os UNIQUE que precisam
>   (`token_hash`, `sequencia`, `chave`).

**"Por que o script do starter está numa subpasta?"**
> Porque o container do PostgreSQL executa automaticamente todo `.sql`
> que está **diretamente** na pasta montada em
> `/docker-entrypoint-initdb.d` — o utilitário usa `maxdepth 1` e não
> entra em subpastas. O `spcript.sql` do starter cria `tb_unidade` e
> `seq_unidade`, que o `01_ddl.sql` já cria. Se ele rodasse, o container
> abortaria com `relation tb_unidade already exists` e o banco nem
> subiria. Por isso ele está em `sql/exemplos/`, só como referência.

**"A massa de dados tem 10 por tabela?"**
> Tem mais: 12 unidades, 12 empresas, 12 leilões, 18 lotes, 14
> compradores. E os hashes de senha são Argon2id de verdade, gerados e
> conferidos por um teste.

### Sobre a API

**"Quantos endpoints tem?"**
> 7 controllers. Os 5 do CRUD (`/unidades`, `/empresas`, `/leiloes`,
> `/lotes`, `/compradores`), mais autenticação (`/auth`) e auditoria
> (`/auditoria`). Cada CRUD tem os 5 verbos.

**"Por que dois caminhos para a mesma coisa? `/empresas` e `/empresa`?"**
> O enunciado usa o singular no exemplo (`POST /leilao`), e a convenção
> REST usa o plural para coleção. Aceito os dois: `@RequestMapping` com
> as duas opções. Não é gambiarra — é o enunciado e a convenção
> divergindo, e quem vai consumir é o avaliador.

**"O que o PUT precisa?"**
> O id na URL e o corpo com os dados novos. É o padrão REST de alteração,
> e é o que o frontend usa.

**"Como você trata erro?"**
> Tudo em JSON, sempre no mesmo formato — `status`, `erro`, `mensagem`.
> O `GlobalExceptionHandler` centraliza. Nenhum endpoint deixa a
> exceção do Spring vazar para o cliente: a mensagem técnica fica no
> log, e o cliente recebe uma mensagem que pode mostrar para o usuário.

**"E o 404 de recurso inexistente?"**
> O `ResourceNotFoundException` vira 404 com mensagem clara.

### Sobre a segurança

**"Por que você fez segurança se não pediam?"**
> Porque é um sistema de leilão com dados de empresa. Sem autenticação,
> qualquer pessoa com a URL cadastra empresa. E o enunciado fala em
> "teste tudo muito bem" — uma API que aceita qualquer requisição não
> está bem testada. A segurança virou parte do escopo porque é o que
> separa um exercício de um sistema.

**"Como funciona a autenticação?"**
> Dois modos, com a mesma interface para o resto do código:
> `SECURITY_ENABLED=false` deixa as rotas abertas (para o avaliador
> exercitar os endpoints do enunciado sem Obstáculo) e `true` liga o
> login. Os dois modos produzem o mesmo objeto de autenticação no
> contexto de segurança, então as regras de acesso são idênticas — não
> são dois sistemas, é um com dois interruptores.

**"Como funciona o JWT?"**
> Token de acesso com 15 minutos e token de renovação com 7 dias,
> separados. Assinatura assimetrica RS256: a chave privada fica na
> aplicação e a pública pode ser distribuída. Rotação de chaves pelo
> cabeçalho `kid` — a aplicação carrega a chave antiga (ainda valida) e
> a nova (já assina), e cada token é conferido com a chave que o
> assinou. Trocar de chave não derruba ninguém.

**"Por que dois tokens?"**
> Um token só, com vida longa, daria acesso por semanas se vazasse. Um
> token só, com vida curta, obrigaria a digitar a senha a cada 15
> minutos. Separando, o token de acesso vive pouco e o de renovação
> pode viver mais — e é revogável.

**"O que é a detecção de reuso?"**
> O token de renovação é de uso único. Se um token já usado é
> apresentado de novo, o sinal é de que alguém copiou. A resposta não é
> recusar só aquele token: é revogar a família inteira. No cenário de
> roubo, o atacante e o usuário legítimo usam o mesmo token — revogar
> só o apresentado deixaria o atacante com acesso válido.

**"Como a senha é guardada?"**
> Argon2id, com o encoder oficial do Spring (`DelegatingPasswordEncoder`)
> como motor. Parâmetros da OWASP: 64 MiB de memória, 2 iterações, 1
> thread, hash de 32 bytes, sal de 16 bytes.

**"Por que Argon2id e não BCrypt?"**
> A OWASP passou a recomendar Argon2id como primeira opção no Password
> Storage Cheat Sheet de 2023. O algoritmo usa memória como recurso
> principal (64 MiB por hash), e isso trava o ataque paralelo em GPU:
> cada tentativa de ataque consome dezenas de megabytes. BCrypt tem
> custo computacional, mas pouca memória — e GPU é excelente em calcular
> em massa.

**"Como você migrou os hashes antigos sem quebrar o acesso?"**
> Por delegação. O `CodificadorDeSenhaDelegado` identifica o algoritmo
> pelo formato do hash gravado (`{argon2}$argon2id$…`, `{bcrypt}$2a$…`,
> ou o `$2a$` sem prefixo do formato antigo do projeto) e verifica a
> senha com o algoritmo correspondente. Ao gravar, usa sempre Argon2id.
> O `upgradeEncoding()` sinaliza hashes antigos, e o Spring reescreve no
> primeiro login. A base migra sozinha, no ritmo do uso — sem script de
> conversão em lote, sem janela de manutenção, sem risco de trancar
> usuário fora.

**"Como você protege contra força bruta?"**
> `BruteForceProtectionFilter`, antes mesmo de o encoder ser avaliado:
> bloqueia IPs e contas que estouram o limite de tentativas. E a
> enumeração de usuários é defendida: quando o usuário não existe, o
> código **mesmo assim** compara a senha contra um hash falso, com o
> mesmo custo de CPU, e a resposta HTTP é idêntica nos dois casos. Sem
> isso, o tempo de resposta entregaria quais usuários existem.

**"Por que o Argon2id obriga o controle de força bruta?"**
> Porque cada tentativa de login vira uma alocação de 64 MiB. Sem o
> limite, um atacante dispara requisições e derruba o servidor por
> exaustão de memória. Por isso o controle deixa de ser um extra e vira
> obrigatório.

**"E o limite de requisições?"**
> `LimitadorDeRequisicoesFilter`, janela deslizante de 120 requisições
> por IP a cada 60 segundos. Janela deslizante, e não bloco fixo,
> porque em bloco fixo o atacante dispara o dobro do limite na virada
> do minuto. A resposta é 429 com `Retry-After`, e os cabeçalhos
> `X-RateLimit-Limit` e `X-RateLimit-Remaining` vêm em **todas** as
> respostas, para o cliente se autorregular em vez de descobrir o limite
> só quando dá erro.

**"A trilha de auditoria é um log?"**
> Não, e essa é a diferença. Um log pode ser apagado por quem tem
> acesso ao banco — e num incidente é exatamente quem a auditoria precisa
> investigar. Cada registro é selado por HMAC-SHA256, encadeado no
> anterior:

```
hash_N = HMAC(chave, sequencia_N || hash_{N-1} || conteudo_N)
```

> Três cenários, três detecções: alterar o conteúdo quebra o hash;
> forjar um registro colado na cadeia quebra o encadeamento; remover um
> registro do meio deixa buraco na sequência — e a sequência é BIGINT
> monotônico, que nunca retrocede.

**"Por que HMAC e não SHA-256 simples?"**
> Com hash simples, quem tivesse acesso ao banco recalcularia o hash do
> registro alterado e gravaria o resultado — o hash não provaria nada,
> porque a "chave" seria o próprio algoritmo. Com HMAC, a verificação
> depende de uma chave secreta que fica fora do banco. Quem tem o banco,
> mas não a chave, não consegue forjar registro válido.

**"O corpo da requisição é registrado?"**
> Nunca. O cadastro de empresa recebe a senha no corpo — e log de
> aplicação é um lugar muito mais exposto que o banco de dados. Só
> metadados: método, caminho, usuário, IP, status, duração.

**"O que é o identificador de correlação?"**
> Um UUID por requisição (`X-Correlation-Id`) que entra no MDC e
> aparece em todas as linhas de log daquela requisição. Liga "o erro
> que o usuário viu na tela" a "as linhas de log do servidor". O valor
> do cliente é saneizado (só alfanuméricos, `-` e `_`, até 64
> caracteres) — sem isso, um cliente poderia injetar linhas falsas no
> log.

**"Como o usuário sabe se é admin?"**
> O `AuthorizationBO` compara o papel que veio do token. A empresa
> autenticada só acessa os próprios dados; o admin acessa todos. A
> checagem está no `@PreAuthorize` dos controllers — **e** no `BO`, que
> valida a empresa. C只在 nos dois lados: o `@PreAuthorize` recusa cedo
> (não gasta query), o `BO` garante (mesmo que alguém chame o `BO`
> direto).

**"Os dados da sessão ficam onde?"**
> O token nunca é gravado em texto puro: guarda-se o SHA-256 dele. Se o
> banco vazar, os hashes são inúteis. E o token revogado fica numa
> tabela separada, para o logout encerrar a sessão de verdade.

**"Como você lida com SQL injection?"**
> O JPA com parâmetros bindados já resolve. E tem teste de ataque
> (`AtaqueSegurancaTest`) que manda payload de injeção e confirma que
> volta erro, não dado.

### Sobre o frontend

**"Por que Vue 2 e não Vue 3?"**
> Vuetify 2 é o par estável de Vue 2 com a API de grid que o enunciado
> pede (`v-data-table`). A escolha do componente vem antes da do
> framework.

**"Como você faz a edição dentro da grid?"**
> Escrevi um componente próprio, `VEditField.vue`, que encapsula o
> campo de edição dentro da linha: valida com as mesmas regras que a
> tela passa, mostra salvar/cancelar dentro da célula, e **não salva ao
> perder o foco** — perder o foco acontece por clique fora, por Tab, por
> troca de tela, e nenhuma dessas é uma intenção explícita de salvar.
> Salvar ao perder o foco faria a pessoa gravar sem querer.

**"Por que você não usa o `v-edit-field` do Vuetify?"**
> Porque ele não existe no Vuetify 2 — é um componente do Vuetify 3
> (alpha). No Vuetify 2, `v-data-table` não tem edição inline. O
> enunciado pede "uma grid editável capaz de realizar todas as
> operações CRUD", e o Vuetify 2 não entrega isso sozinho.

**"Como o total do leilão é calculado?"**
> No backend, somando quantidade × valor inicial de cada lote. No
> frontend é só exibição. A regra fica no backend porque é regra de
> negócio — se o valor viesse do banco já calculado, quem chamasse a API
> de outro jeito não teria o total.

**"Por que validar no frontend se o backend valida?"**
> Os dois, e por motivos diferentes. O frontend valida para dar
> **experiência**: o erro aparece no campo, sem perder o que já foi
> digitado, sem ida e volta ao servidor. O backend valida para dar
> **garantia**: qualquer um pode chamar a API direto, sem passar pela
> tela. Nenhum endpoint depende do frontend para proteger dado.

**"O que o Vuex guarda?"**
> A sessão: token, dados do usuário (papel, empresa) e o estado de
> autenticação. Um ponto que vale mencionar: o `Vue.use(Vuex)` estava
> faltando no `main.js` — o arquivo criava um `Store`, mas o plugin nunca
> era instalado, então `this.$store` ficava indisponível para qualquer
> componente. Corrigido.

**"Por que o axios e não o fetch?"**
> Axios tem interceptor — é onde fica a renovação automática do token,
> centralizada. Com `fetch` teria que repetir isso em cada chamada.

**"Como o token se renova sozinho?"**
> A API devolve o prazo do token junto com a resposta. O frontend sabe
> quanto falta e troca **antes** de vencer. Proativo, não reativo — a
> versão reativa deixava aparecer um 401 no console mesmo com a tela se
> recuperando.

### Sobre os testes

**"Quantos testes você escreveu?"**
> 159 no backend. 158 rodam sem PostgreSQL — dublês do Mockito,
> `@WebMvcTest`, ou `@DataJpaTest` sem conexão. Só 1 exige o banco de
> verdade (`AuditoriaPrecisaoTimestampTest`, que confere a precisão do
> timestamp no Postgres), e sem ele o teste é **pulado**, não reprovado.
> O build não depende de banco.

**"Que tipo de teste você tem?"**
> Três níveis, e cada um pega uma coisa diferente:
> - **Teste de `business`** (a maioria): regra de negócio isolada, sem
>   Spring, sem banco. É o que garante que a regra está no lugar certo.
> - **Teste de integração de segurança** (`SegurancaIntegracaoTest`):
>   a cadeia de filtros de ponta a ponta — 401 em JSON, 429, 200 com o
>   dado do controller, `WWW-Authenticate`, e health check não barrado.
> - **Teste de ataque** (`AtaqueSegurancaTest`): token sem assinatura
>   (`alg=none`), confusão de algoritmo (HS256 com a chave pública),
>   assinatura com chave errada, SQL injection, IDOR, actuator exposto e
>   tempo de resposta que denuncia usuário.

**"Você fez teste de frontend?"**
> Não. E é a lacuna que eu assumo na apresentação. O candidato natural
> são as funções puras de máscara e validação (`cnpjValido`,
> `telefoneValido`, `emailValido`, `urlValida`, `tamanhoValido`) — são
> JavaScript puro, sem Vue e sem DOM, o que as torna fáceis de testar
> sem montar o ambiente de browser.

**"Por que a pasta `sql/seguranca/`?"**
> Porque o script de permissões cria o usuário `leilao_app`, e o
> `01_ddl.sql` roda com o superusuário. São arquivos diferentes, com
> propósitos diferentes, e o script de permissões é opcional.

### Sobre o CI

**"O que o CI faz?"**
> Roda a cada push e a cada pull request:
> 1. **Build e Testes** — compila, roda os 159 testes, gera o SBOM e
>    publica como artefato.
> 2. **Varredura Dinâmica (OWASP ZAP)** — sobe a aplicação e roda o
>    ZAP contra ela.
> 3. **Dependabot** — pull requests automáticos com versões corrigidas
>    de Maven, npm e GitHub Actions, toda segunda-feira.

**"Por que a versão do Java está fixada?"**
> Porque se o CI usasse "latest", uma mudança no JDK quebraria o build
> sem ninguém ter mexido no código. Fixar a versão é a mesma disciplina
> de fixar a versão em qualquer ambiente de build.

**"Por que `npm ci` e não `npm install`?"**
> `npm ci` instala exatamente o que está no `package-lock.json` e falha
> se o lock estiver fora de sincronia. O `npm install` pode resolver
> versões diferentes em máquinas diferentes — o que tornaria o build não
> reproduzível.

**"Por que cancelar execuções concorrentes?"**
> Quando chega um push novo, a execução antiga já não interessa.
> Cancelar economiza runner — que é recurso compartilhado e limitado — e
> evita validar um commit que já foi substituído.

**"O que é o SBOM?"**
> A lista de todos os componentes e versões que entraram no artefato,
> gerada a cada `mvn package` (`target/bom.json` e `target/bom.xml`).
> Com o SBOM versionado, a pergunta "nos somos afetados?" — que no caso
> do Log4Shell took meses — sai em segundos.

**"Por que a varredura de CVEs não está no build padrão?"**
> Porque o OWASP Dependency-Check baixa uma base de centenas de
> megabytes e precisa de rede. Rodar isso a cada `mvn test` tornaria o
> build lento e dependente da internet — e o build quebraria no dia em
> que a base estivesse indisponível. Está num perfil separado
> (`security-scan`), que roda no CI ou sob demanda.

**"Como você sabe que o ZAP funcionou?"**
> O passo testa o alcance da API antes de varrer, e mostra o log da API
> quando ela não responde — senão o motivo real fica escondido no meio
> do log enorme do container. As três correções que isso exigiu (rede
> `--network host`, diretório de trabalho, e `--pull always`) vieram
> exatamente desse log.

### Sobre Git

**"Por que o histórico foi reescrito?"**
> Porque as mensagens de commit originais tinham marcas de texto gerado —
> caixa alta para dar ênfase, rótulos de slide ("O QUE ENTRA", "O QUE
> MUDA"), travessão como separador de frase. Reescrevi as mensagens
> mantendo todo o conteúdo técnico intacto: nomes de arquivo, classes,
> comandos, números e o "por quê" de cada decisão continuam idênticos.
> Só a forma mudou.

**"Isso não reescreve a história de um repositório compartilhado?"**
> Não, e por isso foi feito antes de qualquer pessoa clonar. A regra é:
> histórico reescrito é seguro enquanto ninguém mais depende dele. Com
> clones externos, o certo é um commit novo ("revert") ou um
> `filter-repo` coordenado. Eu fiz o `filter-branch` com um reescritor
> próprio (recriando cada commit com `git commit-tree`), porque o
> `filter-branch` trunca as mensagens quando o filtro falha no Windows.

**"Por que há uma tag de backup?"**
> `backup-antes-reescrita-mensagens`. Reescrita de histórico é
> operação que mexe em tudo; a tag é o ponto de retorno. Ela aponta para
> a árvore anterior, e dá para comparar: `git diff backup-antes-reescrita-mensagens master` mostra
> exatamente o que mudou.

### Sobre o visual

**"De onde veio a identidade visual?"**
> Do site institucional da própria AZ (azi.com.br, tema `aztheme`), que
> expõe as cores como regras CSS. O laranja `#EA4F24` é a cor de ação
> (botão principal, link) e o azul-marinho `#002436` a cor de estrutura
> (navbar, rodapé). A fonte do corpo é Barlow e a dos títulos Tomorrow. O
> logo é o arquivo original da empresa, copiado verbatim — não é uma
> recriação.

**"Por que não usou laranja no texto?"**
> Porque a escolha foi verificada contra a WCAG 2.1: laranja sobre
> branco fica em 3,7:1, o que atende o mínimo de 3:1 para texto grande
> mas não o de 4,5:1 para texto normal. Por isso o projeto usa
> azul-marinho e cinza no texto pequeno.

**"O que é o `.gitattributes`?"**
> A normalização de fim de linha por tipo de arquivo. Sem ele, o git
> converte LF para CRLF em todo `git add` no Windows e aventa a cada
> arquivo — o efeito prático é diff cheio de ruído, com um arquivo mexido
> no Linux aparecendo alterado por inteiro no Windows. A regra é por
> tipo: Java, JavaScript e YAML em LF; PowerShell em CRLF; binários
> marcados como `binary`.

---

## 4. A API: cada endpoint, na memória

### `/auth` (autenticação)

| Método | Rota | O que faz |
|---|---|---|
| POST | `/auth/login` | Valida as credenciais, devolve token de acesso (15 min) + token de renovação (7 dias) |
| POST | `/auth/refresh` | Troca o token de renovação por um novo par. Detecta reuso e revoga a família |
| POST | `/auth/logout` | Revoga a família do token |
| GET | `/auth/me` | Devolve o usuário da sessão (papel, empresa) |

### `/unidades`, `/empresas`, `/leiloes`, `/lotes`, `/compradores`

Os 5 verbos em todos. Detalhes que diferem:

- `GET /lotes/leilao/{leilaoId}` — os lotes de um leilão
- `GET /compradores/{empresaId}/{leilaoId}` — os compradores de um leilão
  (o par é a chave composta)

### `/auditoria`

| Método | Rota | O que faz |
|---|---|---|
| GET | `/auditoria` | Lista os registros (requer admin) |
| GET | `/auditoria/verificar` | Percorre a cadeia e diz se houve adulteração. **Público de propósito** — um auditor externo não precisa de credencial de admin para provar que a trilha não foi mexida. Retorna 409 se houve ruptura |

---

## 5. O banco: cada tabela, na memória

### As 5 do enunciado

**`tb_unidade`** — os locais onde os leilões acontecem. `id` (serial), `nome`
(unique, not null).

**`tb_empresa`** — `id`, `razao_social`, `cnpj` (unique, not null),
`telefone`, `email`, `logradouro`, `numero`, `complemento`, `municipio`,
`cep`, `quantidade`, `uf`, `site`, `usuario` (unique), `senha` (hash).

**`tb_leilao`** — `id` (PK, `nextval('seq_leilao')`), `codigo`, `descricao`,
`vendedor` (FK para `tb_empresa`, `ON DELETE RESTRICT`), `inicio_previsto`.
O `RESTRICT` é deliberado: apagar a vendedora quebraria o histórico dos
leilões que ela vendeu.

**`tb_lote`** — `id` (PK), `leilao` (FK para `tb_leilao`), `quantidade`,
`valor_inicial`.

**`tb_comprador`** — chave primária **composta** `(empresa, leilao)`, com
FK para `tb_empresa` e para `tb_leilao`. O motivo é o enunciado: um mesmo
comprador pode participar de vários leilões, e um mesmo leilão pode ter
vários compradores. Com chave composta, não há duplicata — o `unique`
do enunciado aparece no par.

### As 3 de segurança

**`tb_refresh_token`** — o hash SHA-256 do token, a família a que pertence,
o marcador de "utilizado", a data de expiração.

**`tb_auditoria`** — a sequência (BIGINT monotônico), o conteúdo, e os dois
elos da cadeia: `hash_anterior` (entrada) e `hash_atual` (saída).

**`tb_contador_seguranca`** — as tentativas de login por usuário e por IP,
com a janela de tempo. Existe para o limite valer para a API inteira: com
contador em memória, ele valeria só para a réplica que o atacante acertou.

### As sequences

`seq_unidade`, `seq_empresa`, `seq_leilao`, `seq_lote` — criadas
explicitamente no DDL, porque o enunciado pede sequence como objeto.

---

## 6. Segurança: as decisões e o porquê

Cada linha é uma decisão com um motivo. Se perguntarem "por que", a
segunda coluna é a resposta.

| Decisão | Por quê |
|---|---|
| Autenticação opcional (`SECURITY_ENABLED`) | O avaliador precisa exercitar os endpoints abertos do enunciado. Mas o modo padrão de produção é ligado |
| Dois modos, mesma interface | Os dois modos produzem o mesmo objeto no contexto de segurança. Não são dois sistemas |
| Argon2id em vez de BCrypt | Memória como recurso (64 MiB por hash) trava o ataque em GPU |
| Migração por delegação | Trocar o encoder de uma vez quebraria o acesso de quem ainda tem hash antigo |
| HMAC em vez de SHA-256 | Com hash simples, quem tem o banco recalcula e forja. Com HMAC, precisa da chave, que está fora do banco |
| Sequência monotônica na auditoria | O hash detecta alteração; a sequência detecta **remoção** |
| Auditoria sem corpo da requisição | O cadastro de empresa recebe a senha no corpo. Log é lugar mais exposto que o banco |
| Gravação em transação separada | Se dividisse a transação, uma falha ao gravar derrubaria a operação do usuário — ele perderia o dado e não teria registro |
| Token de renovação de uso único | Detecta cópia: token já usado apresentado de novo |
| Revogação da família inteira | No roubo, atacante e usuário legítimo usam o mesmo token. Revogar só um deixa o outro com acesso |
| SHA-256 do token no banco | Se o banco vazar, os hashes são inúteis |
| Janela deslizante no rate limit | Em bloco fixo, o atacante dispara o dobro do limite na virada do minuto |
| Cabeçalhos de rate limit em toda resposta | O cliente se autorregular em vez de descobrir o limite só no erro |
| Enumeração de usuários com hash falso | O tempo de resposta não pode entregar quais usuários existem |
| Sem salvar ao perder o foco | Perder foco é clique fora ou Tab — não é intenção de salvar |
| Autorização no controller **e** no BO | No controller recusa cedo (não gasta query); no BO garante (mesmo chamado direto) |
| `X-Correlation-Id` saneado | Sem isso, o cliente injeta linhas falsas no log |

---

## 7. O frontend: as telas e as decisões

### As 4 telas

| Rota | Exercício | O que faz |
|---|---|---|
| `#/unidades` | 5 | Grid editável com CRUD completo. Inclusão e edição **inline**, na própria linha |
| `#/empresas` | 6 | Grid somente leitura: CNPJ, razão social, telefone, e-mail. Com busca |
| `#/leiloes` | 7 | Grid somente leitura: vendedor, início previsto, total. Total = soma de quantidade × valor inicial |
| `#/empresa`, `#/empresa/:id` | 8 | Cadastro com validação de obrigatórios, máscaras (CNPJ, CEP, telefone) e confirmação antes de gravar/excluir |

### Os 6 componentes

| Componente | Para quê |
|---|---|
| `VEditField.vue` | Campo de edição dentro da linha da grid. Valida, mostra salvar/cancelar, não salva ao perder o foco |
| `ConfirmDialog.vue` | Diálogo de confirmação de exclusão. A exclusão não tem Ctrl+Z — a lixeira fica a poucos pixels do lápis |
| `AppNotificacoes.vue` | Mensagens de sucesso e erro, no mesmo formato em todas as telas |
| `AppMenu.vue` | Menu de navegação |
| `AppTrilhaNavegacao.vue` | Trilha de navegação |
| `LogoAz.vue` | Logo oficial, em SVG inline |

**Por que SVG inline e não `<img>`:** o `<img>` traz o `overflow: hidden`
do Vuetify, que recorta o desenho, e o sizing do navegador para SVG com
tamanho intrínseco. Com o SVG inline, o `viewBox` define o sistema de
coordenadas e nada mais interfere.

### As decisões do frontend

| Decisão | Por quê |
|---|---|
| Edição inline, não em diálogo | O enunciado pede grid editável. Diálogo seria outra coisa |
| Componente `VEditField` próprio | O Vuetify 2 não tem `v-edit-field` (é do Vuetify 3) |
| `novoNome` e `editadoNome` separados | O `v-model` do Vue 2 não escreve de volta em `novo.nome` — não existe setter para isso |
| Condição de inclusão antes da de edição | A linha de inclusão tem `id === null` e o `editando` começa `null`. Testar a edição primeiro fazia a linha de inclusão entrar em modo de edição |
| Renovação de token proativa | A API devolve o prazo; o frontend troca antes de vencer. Reativa deixava 401 no console |
| Masks no frontend + validação no backend | Frontend dá experiência, backend dá garantia |

---

## 8. Testes e qualidade

### Os números

- **159** testes no backend, todos passando
- **158** rodam sem banco
- **1** exige PostgreSQL (é pulado, não reprovado, sem ele)
- **6** componentes no frontend, **0** testes no frontend (a lacuna assumida)
- **8** exercícios do enunciado, 8 implementados

### A cobertura por área

| Área | O que é testado |
|---|---|
| `business` | Cada regra de negócio isolada, sem Spring |
| `config` | A cadeia de filtros de ponta a ponta, o rate limit, o IP do cliente, o correlation id |
| `entity` | Chave composta, estratégia de geração de id |
| `dto` | Validação de entrada |
| `repository` | As queries (validade das queries) |
| `business/AtaqueSegurancaTest` | Token sem assinatura, confusão de algoritmo, assinatura errada, SQL injection, IDOR, actuator exposto, tempo de resposta |

### A divisão do `mvn test`

```
AuditoriaBOTest              6
AuditoriaPrecisaoTimestampTest 1   (precisa do banco)
AutenticacaoBOTest           5
AutorizacaoBOTest           16
EmpresaBOTest                6
TokenJwtBOTest               8
TokenRevogadoBOTest          7
UnidadeBOTest                5
AtaqueSegurancaTest         15
AutorizacaoEndpointTest      5
ClientIpResolverProxyTest    7
ClientIpResolverTest         8
ContadorBancoTest            7
CorrelationIdFilterTest      5
JwtAuthenticationFilterTest  5
LimitadorDeRequisicoesFilterTest 7
LoginAttemptServiceTest      7
SegurancaIntegracaoTest      7
ValidacaoConfiguracaoSegurancaTest 4
EmpresaRequestValidacaoTest  8
LoginRequestValidacaoTest    5
GeradorHashSenhaTest         2
PasswordEncoderTest          7
EstrategiaDeGeracaoDeIdTest  4
ValidadeDasQueriesTest       3
```

---

## 9. DevOps e entrega

### O que tem no repositório

```
.github/
├── workflows/ci.yml           build, testes, SBOM, ZAP
├── dependabot.yml             Maven, npm, GitHub Actions
├── ISSUE_TEMPLATE/            bug report, feature request
├── PULL_REQUEST_TEMPLATE.md   checklist de revisão
├── CODEOWNERS                 quem revisa o quê
└── SECURITY.md                canal de reporte de vulnerabilidade
```

### O `SECURITY.md` em uma frase

> Issue pública fica visível para todo mundo — inclusive para quem
> pretende explorar o problema — no tempo exato em que a correção ainda
> não existe. Por isso existe um canal privado, com prazo de resposta
> definido (3 dias úteis para o acknowledgment, 7 para a confirmação).

### O checklist do pull request

O item obrigatório é "nenhuma senha, chave ou token real foi versionado".

### O `CODEOWNERS`

Define quem revisa cada parte. A revisão só funciona se o revisor
entende o que está olhando — uma mudança no filtro de autenticação
deveria passar por alguém que conhece a camada de segurança, e não por
qualquer pessoa que estiver disponível na hora.

### Secrets

Nenhum segredo de produção no repositório. O `.env` da raiz está
versionado **de propósito** — as credenciais ali (`postgres/postgres`) são
públicas, de desenvolvimento, e o avaliador precisa conseguir subir o
banco com um único comando. O próprio arquivo explica isso e diz o que
fazer em ambiente real: a senha vai para um secret do orquestrador
(Docker Swarm, Kubernetes Secret, AWS Secrets Manager, HashiCorp Vault),
nunca para um arquivo no Git.

O `.env.example` mostra as chaves e o formato, sem os valores. A senha do
admin da API (`SenhaForteAdmin123`) é passada por variável de ambiente
pelo `iniciar.ps1`, e nunca fica em arquivo.

---

## 10. Perguntas Starr

O modelo STAR (Situation, Task, Action, Result) é o que o avaliador
provavelmente vai usar. As três respostas que você precisa ter na
memória:

### STAR 1 — A migração para Argon2id sem quebrar o acesso

**S:** O projeto usava BCrypt. A OWASP passou a recomendar Argon2id
como primeira opção, e o CWE-916 (Use of Password Hash With
Insufficient Computational Effort) estava no radar.

**T:** Trocar o encoder sem que nenhum usuário perceba e sem trancar
ninguém fora.

**A:** Delegação. O `CodificadorDeSenhaDelegado` identifica o algoritmo
pelo formato do hash gravado (`{argon2}$…`, `{bcrypt}$…`, ou o `$2a$`
sem prefixo do formato antigo) e verifica com o algoritmo
correspondente; ao gravar, usa sempre Argon2id. O `upgradeEncoding()`
sinaliza os antigos, e o Spring reescreve no primeiro login. O
`DelegatingPasswordEncoder` oficial é o motor — a classe acrescenta só a
regra que faltava: reconhecer o hash legado sem prefixo.

**R:** A base migrou sozinha, no ritmo do uso. Sem script de conversão em
lote, sem janela de manutenção. E o controle anti-forca-bruta deixou de
ser opcional: com 64 MiB por tentativa de login, sem limite um atacante
derruba o servidor.

### STAR 2 — A trilha de auditoria que não pode ser apagada

**S:** O enunciado não pedia auditoria. Mas um sistema de leilão sem
rastro de quem fez o quê é um sistema que ninguém pode auditar.

**T:** Uma trilha que não possa ser apagada por quem deveria estar sendo
auditado.

**A:** Cada registro selado por HMAC-SHA256 e encadeado no anterior. O
`GET /auditoria/verificar` recalcula a cadeia e aponta a sequência exata
em caso de ruptura, com 409. A chave da HMAC fica em variável de
ambiente, fora do banco. A gravação é em transação separada
(`REQUIRES_NEW`) — se dividisse com a operação de negócio, uma falha ao
gravar derrubaria a operação do usuário. E o corpo da requisição nunca é
registrado, porque o cadastro de empresa recebe a senha.

**R:** Três ataques detectados: alteração de conteúdo, forja de registro
colado na cadeia, e remoção de registro do meio (a sequência é BIGINT
monotônico, e o buraco aparece).

### STAR 3 — O bug do `v-model` aninhado

**S:** O teste de browser da tela de unidades mostrava a linha de
inclusão e o campo de edição, mas nada do que era digitado chegava ao
banco. O cadastro pela tela era impossível — e o enunciado pede grid
editável com CRUD completo.

**T:** Fazer a inclusão e a edição funcionarem de verdade.

**A:** Três causas, no mesmo caminho. (1) O `v-model` apontava para
expressão aninhada (`novo.nome`); o Vue 2 escreve de volta chamando o
setter do objeto, e não existe setter para `novo.nome`. Separou em
`novoNome` e `editadoNome`. (2) A ordem das condições no template: a
linha de inclusão tem `id === null` e o `editando` começa `null`, então
`editando === item.id` casava e a linha de inclusão entrava em modo de
edição. Inverteu a ordem. (3) O `validate()` do Vuetify 2 devolve
boolean, não a lista de erros; o componente testava `erros.length > 0`,
e com `false` isso é `undefined > 0` — falso. Passou a testar o
boolean.

**R:** Incluir grava (com o id gerado pelo Postgres), alterar grava,
excluir abre o diálogo de confirmação e remove. Verificado no navegador
contra o banco, não por leitura de código.

*(O STAR 3 é o mais honesto de todos, porque mostra debugging de
frontend — que é onde a maioria trava.)*

---

## 11. Armadilhas: o que NÃO responder

**Não diga "está pronto" / "está completo".** Diga o que está implementado
e o que falta.

**Não diga que o frontend tem testes.** Não tem. Assuma e diga o que
faria.

**Não diga "usei o v-edit-field do Vuetify".** O `VEditField` é do
projeto. O do Vuetify é do Vuetify 3 e não existe no 2.

**Não diga "o log de auditoria resolve".** A trilha não é um log, e a
diferença é o encadeamento por HMAC. Quem pergunta quer ouvir a
diferença.

**Não invente número.** Se não tiver o número na memória, diga "não
tenho aqui, mas é ...". O `159` você tem. O resto está neste documento.

**Não defenda o que não foi pedido.** Segurança, CI, SBOM, DAST foram
extras. Apresente como extras, com o motivo — não como obrigação.

**Não ataque o enunciado.** O enunciado é o que é; o avaliador escreveu.
Diga o que você adicionou e por quê.

**Não diga "não sei".** Se não souber, diga o que você verificaria e
como: "eu checaria no `X.java`, é só abrir". Isso mostra domínio do
código mesmo sem a resposta na ponta da língua.

**Não chame de "pattern" o que é prática de mercado.** O nome técnico
importa: *delegating password encoder*, *HMAC chaining*, *sliding
window rate limit*, *OWASP Dependency-Check*, *SBOM*, *CycloneDX*.

---

## 12. Perguntas que você deve fazer

Faça pelo menos duas. Mostra que você pensa em produção.

**"Como o código vai para produção — CI/CD com deploy, ou manual?"**
> Entendi que hoje o CI valida. Qual o processo de deploy?

**"Temambiente de homologação? E o banco de cada ambiente, quem é o
dono?"**
> Quero entender a separação entre ambiente.

**"As credenciais de hoje (login por empresa) vão virar autenticação real
contra o cadastro de usuários, ou o admin mantém?"**
> Quero entender a direção do produto.

**"O enunciado fala em 'validação de máscaras e formatos de número,
email, url e tamanho máximo'. Hoje isso é no frontend e no backend. Vocês
têm uma biblioteca de validação padrão?"**
> Pergunta boa: mostra que você implementou e quer alinhar.

**"A trilha de auditoria é exigência de alguma auditoria externa
(ISO, SOX), ou é melhoria de processo?"**
> Se houver requisito de compliance, muda o desenho (retenção, WORM).

---

## 13. Glossário

Os termos que aparecem nas perguntas, com a explicação de uma linha.

| Termo | O que é |
|---|---|
| **JWT** | Token assinado que carrega a identidade. O cliente o envia em cada requisição; o servidor confere a assinatura |
| **RS256** | Algoritmo de assinatura assimétrica. Chave privada assina, pública verifica |
| **`kid`** | Header que diz qual chave assinou o token. É o que permite rotacionar chaves |
| **Hash vs criptografia** | Hash é irreversível (senha); criptografia é reversível (dado que precisa voltar) |
| **Salt** | Valor aleatório por hash. Dois usuários com a mesma senha têm hashes diferentes |
| **Argon2id** | Algoritmo de hash de senha que usa memória como recurso principal |
| **HMAC** | Hash com chave. Precisa da chave para verificar — por isso o "segredo" está fora do banco |
| **CWE-916** | "Uso de hash de senha com custo computacional insuficiente". O aviso de-password que motivou a migração |
| **Nonce** | Número de uso único. Impede reuso de token |
| **Família de tokens** | Todos os tokens derivados de um mesmo login. Revogar a família encerra a sessão toda |
| **Timing attack** | Ataque que mede o tempo da resposta para inferir informação (ex.: se o usuário existe) |
| **IDOR** | Acessar recurso pelo id de outro usuário. *Insecure Direct Object Reference* |
| **JWKS** | Endereço com as chaves públicas, para quem valida o token |
| **SBOM** | Lista de componentes e versões do artefato. *Software Bill of Materials* |
| **CVE** | Vulnerabilidade catalogada com um identificador. *Common Vulnerabilities and Exposures* |
| **CVSS** | Nota de 0 a 10 da gravidade de uma CVE |
| **DAST** | Teste que ataca a aplicação rodando. *Dynamic Application Security Testing* |
| **SAST** | Teste que analisa o código-fonte |
| **WAF** | Filtro que fica na frente da aplicação. *Web Application Firewall* |
| **DPoP / mTLS** | Prova de posse e autenticação mútua entre cliente e servidor |
| **OWASP ZAP** | Scanner de vulnerabilidades que ataca a aplicação em execução |
| **Rate limiting** | Limite de requisições por período, para não derrubar o serviço |
| **Janela deslizante** | Rate limit que conta as últimas N requisições, e não o bloco do minuto |
| **429** | *Too Many Requests*. O status do rate limit |
| **401 vs 403** | 401 = não autenticado (falta ou expirou credencial). 403 = autenticado, mas sem permissão |
| **XSS vs CSRF** | XSS = injeta script na página. CSRF = usa a sessão da vítima de outro site |
| **CSP** | *Content Security Policy*. Diz ao browser quais scripts pode carregar |
| **HSTS** | Header que força HTTPS por um tempo |
| **CORS** | Regras de quem pode chamar a API de outro domínio |
| **Flyway / Liquibase** | Ferramentas que versionam a estrutura do banco |
| **JPA / Hibernate** | Mapeamento objeto-relacional. O Hibernate vira SQL |
| **N+1** | Uma query a mais por resultado. O problema de performance mais comum |
| **Lazy loading** | Carregar o dado só quando é usado |
| **OSIV** | *Open Session In View*. Manter a sessão do Hibernate aberta durante a requisição web |
| **DTO** | Objeto de entrada/saída da API. Separa o contrato da API do modelo do banco |
| **BCrypt vs Argon2** | Argon2id usa memória; BCrypt usa só CPU |
| **Maven Wrapper** | `mvnw` versionado. Não precisa de Maven instalado |
| **`npm ci` vs `npm install`** | `ci` respeita o lock file exatamente; `install` pode resolver versões diferentes |
| **SAST/DAST/Dependency scanning** | As três varreduras que o CI faz |
| **Trunk-based vs Git Flow** | Trunk-based: todo mundo Integrate direto na main. Git Flow: branches defeature |
| **Force push** | Sobrescreve o remoto. Perigoso em branch compartilhada |
| **`filter-branch` / `filter-repo`** | Ferramentas de reescrita de histórico. O `filter-repo` é o sucessor do `filter-branch` |

---

## Resumo de uma página — para colar no monitor antes da entrevista

```
REPO: github.com/RafaelMoccelinLavratti/Selecao-Dev-AZ
SUBIR: .\iniciar.ps1        (banco 5432, API 8081, front 8080)

LOGIN: admin / SenhaForteAdmin123        (vê tudo)
       admin.aurora / Senha@1             (vê só a própria empresa)

8 EXERCÍCIOS: todos implementados
TESTES: 159 no backend (158 sem banco), 0 no frontend
CI: build + testes + SBOM + ZAP + Dependabot
DECK: 56 commits, sem segredo de produção, lista de contribuidores limpa

BUGS QUE EU ACHEI E CORRIGI (perguntas vão por aqui):
- v-model aninhado (novo.nome) não escrevia de volta
- linha de inclusao (id === null) caia no modo de edicao
- validate() do Vuetify 2 devolve boolean, não array
- script do starter apagado em vez de reformatado

DECISÕES QUE EU SEI EXPLICAR:
Argon2id com delegação / HMAC na auditoria / JWT com rotação e detecção
de reuso / rate limit em janela deslizante / enumeração de usuários com
hash falso / edição inline que não salva ao perder o foco /
validação nos dois lados (frontend = experiência, backend = garantia)

LACUNA QUE EU ASSUMO: teste de frontend
```
