# Manual de Operação do Sistema de Leilões

> Documento operacional do projeto **Avaliação AZ Tecnologia**.
>
> Tudo o que é preciso para rodar, testar, demonstrar e diagnosticar o
> projeto numa máquina limpa.

Para o que o projeto **é** e **por que** foi feito assim, veja o
[`README.md`](README.md). Aqui é só o lado prático.

## 1. Pré-requisitos

| Ferramenta | Versão | Para quê |
|---|---|---|
| Java | 11 (LTS) | Rodar a API |
| Maven | 3.8+ | Build e dependências |
| Node.js | 16+ | Rodar o frontend |
| PostgreSQL | 16 | Banco de dados |
| Docker | qualquer | Alternativa para subir o banco sem instalar |

```bash
java -version        # deve mostrar 11.x
mvn -version         # deve mostrar 3.8+ e o Java 11
node -v              # deve mostrar 16+
psql --version       # deve mostrar 16+
```

A API precisa do **Java 11**. Com o Java 17, o Spring Boot 2.7 exige
`--add-opens` no `MAVEN_OPTS` para funcionar.

## 2. Subir o banco de dados

### Opção A: Docker (recomendado)

```bash
cd Selecao-Dev-AZ
docker compose -f docker-database.yml up -d
```

Na primeira subida o container do PostgreSQL executa `01_ddl.sql` (estrutura) e
`02_dml.sql` (dados). O banco `az` já fica criado e populado.

Conferir:

```bash
docker compose -f docker-database.yml ps
docker exec -it $(docker ps -q --filter "publish=5432") psql -U postgres -d az -c "\dt"
```

Recomeçar do zero (apaga os dados):

```bash
docker compose -f docker-database.yml down -v
docker compose -f docker-database.yml up -d
```

### Opção B: PostgreSQL na máquina

```bash
createdb -U postgres az
psql -U postgres -d az -f leilao-api/src/main/resources/sql/01_ddl.sql
psql -U postgres -d az -f leilao-api/src/main/resources/sql/02_dml.sql
```

Os dois scripts são idempotentes: podem rodar de novo sem quebrar.

Conferir os dados:

```bash
psql -U postgres -d az -c "SELECT COUNT(*) FROM tb_unidade;"
psql -U postgres -d az -c "SELECT COUNT(*) FROM tb_empresa;"
psql -U postgres -d az -c "SELECT COUNT(*) FROM tb_leilao;"
psql -U postgres -d az -c "SELECT COUNT(*) FROM tb_lote;"
psql -U postgres -d az -c "SELECT COUNT(*) FROM tb_comprador;"
```

## 3. Subir a API

```bash
cd leilao-api
mvn spring-boot:run
```

Sobe na porta **8081**. Para ver o SQL no console:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Para gerar o `.jar` executável:

```bash
mvn package
java -jar target/leilao-api-1.0.0-SNAPSHOT.jar
```

Conferir que subiu:

```bash
curl http://localhost:8081/actuator/health
# {"status":"UP"}
```

## 4. Subir o frontend

```bash
cd leilao-app
npm install
npm run serve
```

Sobe na porta **8080**. Acesse <http://localhost:8080>.

```bash
npm run build      # gera a pasta dist/
npm run lint       # verificação de estilo e erros comuns
```

O `.env` da pasta `leilao-app` aponta a API para `http://localhost:8081`. Para
mudar o endereço, edite a variável `VUE_APP_API_BASE_URL`.

## 5. Rodar os testes

```bash
cd leilao-api
mvn test
```

90 testes, todos sem precisar de banco de dados: rodam em qualquer máquina,
em menos de 30 segundos.

| Arquivo | Testes | O que verifica |
|---|---|---|
| `AtaqueSegurancaTest` | 15 | Ataques de verdade: token sem assinatura, confusão de algoritmo, SQL injection, IDOR, actuator exposto, tempo de resposta |
| `AutenticacaoBOTest` | 5 | O login por JSON conta a falha no mesmo controle de tentativas do login por HTTP Basic |
| `SegurancaIntegracaoTest` | 7 | A cadeia de segurança inteira (401, 429, bloqueio, dado do controller) |
| `TokenJwtBOTest` | 8 | Emissão, adulteração, expiração e tipo dos tokens |
| `LoginAttemptServiceTest` | 7 | As regras do controle de tentativas de login |
| `PasswordEncoderTest` | 7 | O codificador de senha (Argon2id, salt e hash legado) |
| `AuditoriaBOTest` | 6 | A corrente de hashes da trilha de auditoria |
| `EmpresaBOTest` | 6 | Regras de negócio da empresa (CNPJ único, senha hasheada) |
| `UnidadeBOTest` | 5 | Regras de negócio da unidade (normalização, duplicidade) |
| `ClientIpResolverTest` | 5 | A descoberta do IP real e a proteção contra `X-Forwarded-For` forjado |
| `ClientIpResolverProxyTest` | 7 | O IP forjado pelo cliente não vira o IP usado (o limite por IP não pode ser burlado) |
| `LimitadorDeRequisicoesFilterTest` | 5 | O limite por IP e o `Retry-After` |
| `CorrelationIdFilterTest` | 5 | O identificador de correlação e o log forging |
| `GeradorHashSenhaTest` | 2 | O gerador de hash usado para montar a massa de dados |

Rodar só um arquivo, ou só um método:

```bash
mvn test -Dtest=SegurancaIntegracaoTest
mvn test -Dtest=SegurancaIntegracaoTest#credencialCorretaDevolveODadoDoController
```

O relatório HTML fica em `target/surefire-reports/`.

## 6. Gerar senhas com hash

A senha nunca é gravada em texto puro: o banco guarda um hash. A massa de
dados (`02_dml.sql`) vem com hashes em BCrypt, o formato que existia no banco
quando o projeto começou e que o codificador delegado continua reconhecendo.

Para gerar hashes novos (no formato atual, Argon2id):

```bash
cd leilao-api
mvn test -Dtest=GeradorHashSenhaTest
```

O teste imprime os hashes gerados no console. O mesmo teste confere que cada
hash gravado no `02_dml.sql` corresponde à senha `Senha@N` correspondente. Ou
seja, ele é também a prova de que a migração para o Argon2id não trancou
ninguém fora do sistema.

Alternativamente, pelo JShell do Java 11:

```bash
cd leilao-api
mvn -q dependency:build-classpath -Dmdep.outputFile=cp.txt
jshell --class-path "target/classes;$(cat cp.txt)"
```

```java
var encoder = new br.com.selecao.locadora.config.PasswordEncoderConfig().passwordEncoder();
System.out.println(encoder.encode("MinhaSenhaForte123"));
```

A senha original nunca é gravada, só o hash. E o hash gerado não serve para
outra senha: como o salt é sorteado a cada vez, cada hash é único.

## 7. Aplicar o menor privilégio no banco

Opcional, mas recomendado para qualquer ambiente que não seja a máquina do
avaliador.

```bash
psql -U postgres -d az -f leilao-api/src/main/resources/sql/seguranca/03_permissoes.sql
```

Isso cria o usuário `leilao_app`, que pode ler e gravar apenas nas tabelas do
projeto. Ele não pode derrubar o banco, alterar a estrutura, criar usuários
nem ler outros bancos.

Depois, aponte a aplicação para ele:

```bash
export DATABASE_URL="jdbc:postgresql://localhost:5432/az"
export DATABASE_USER="leilao_app"
export DATABASE_PASSWORD="<senha definida no script>"
```

Por que isso importa: sem isso a aplicação conecta como superusuário. Se ela
for invadida (um SQL Injection, uma dependência maliciosa), o atacante herda
todo esse poder. Com isso, o dano fica limitado às tabelas do projeto.

## 8. Roteiro de demonstração

Para uma apresentação de uns 10 minutos.

**Abrir.** Subir banco, API e frontend. Em <http://localhost:8080> abre a
tela de Unidades.

**Exercício 5 - Unidades (CRUD completo).** Clicar em "Nova unidade" e
digitar o nome: a linha entra no topo, com o campo já focado. Salvar: a
linha vira registro, com id gerado pelo banco. O lápis edita, a lixeira
exclui (com diálogo de confirmação, porque exclusão não tem Ctrl+Z).

A técnica: uma única tabela do Vuetify com três estados (consulta, edição,
inclusão), e a linha de inclusão entra no topo por um `computed`.

**Exercício 6 - Empresas (consulta e máscaras).** No menu, Empresas. Digitar
na busca filtra no navegador.

As máscaras: a API devolve só os dígitos (`11422377000155`) e a tela
formata para exibição (`11.422.377/0001-55`). A canonicalização é feita no
backend, e é o que garante que a busca por CNPJ funcione.

**Exercício 8 - Cadastro de empresa.** Menu → Empresas → "Nova empresa".

A URL `#/empresa` é inclusão e `#/empresa/5` é edição: uma tela, dois
modos. O componente descobre o modo lendo o `id` da rota.

A validação está em duas camadas: o frontend valida para a experiência, o
backend para a garantia.

**Exercício 7 - Leilões (total calculado no banco).** Menu → Leilões. Os
cards de resumo mostram o total geral e o próximo leilão.

O total do leilão é uma JPQL agregada no banco (`SUM(quantidade *
valorInicial)`): uma consulta só, em vez de buscar todos os lotes e somar em
memória.

**Segurança.** Com a API no perfil de produção:

```bash
curl -i http://localhost:8081/unidades
# → 401 em JSON

curl -i -u admin:senha-errada http://localhost:8081/unidades
# → 401 em JSON

curl -i -u admin:$SENHA_CORRETA http://localhost:8081/unidades
# → 200 com os DADOS do controller
```

E o ataque de força bruta:

```bash
for i in $(seq 1 6); do
  curl -s -o /dev/null -w "tentativa $i -> HTTP %{http_code}\n" \
       -u admin:senha-errada http://localhost:8081/unidades
done
# cinco 401 e depois 429
```

O limite é de 5 falhas por usuário e 20 por IP. A conta bloqueada responde
429 com o cabeçalho `Retry-After`. E a resposta para "usuário inexistente" é
byte a byte igual à de "senha errada", o que impede a enumeração de usuários.

## 9. Verificação de saúde

O que rodar antes de apresentar:

```bash
# 1) Banco no ar
docker compose -f docker-database.yml ps
psql -U postgres -d az -c "SELECT COUNT(*) FROM tb_unidade;"

# 2) API no ar
curl -s http://localhost:8081/actuator/health
curl -s -o /dev/null -w "GET /unidades -> HTTP %{http_code}\n" http://localhost:8081/unidades
curl -s -o /dev/null -w "GET /empresas -> HTTP %{http_code}\n" http://localhost:8081/empresas
curl -s -o /dev/null -w "GET /leiloes  -> HTTP %{http_code}\n" http://localhost:8081/leiloes

# 3) Testes
cd leilao-api && mvn test

# 4) Frontend
cd ../leilao-app && npm run lint && npm run build
```

Tudo deve responder: API em 200, suíte com `BUILD SUCCESS` e lint com
`No lint errors found!`.

## 10. Diagnóstico

### A API não sobe

| Sintoma | Causa provável | Solução |
|---|---|---|
| `Port 8081 was already in use` | outra instância rodando | `netstat -ano \| findstr :8081` e encerre o PID |
| `Connection to localhost:5432 refused` | banco parado | `docker compose -f docker-database.yml up -d` |
| `relation "tb_unidade" does not exist` | scripts SQL não rodaram | rode `01_ddl.sql` e `02_dml.sql` |
| `Unsupported class file major version` | Java errado | use o JDK 11 |

### O frontend abre em branco

| Sintoma | Causa provável | Solução |
|---|---|---|
| Tela branca | a API não está rodando | `curl http://localhost:8081/actuator/health` |
| "Network Error" no console | CORS bloqueando | verifique `app.cors.origins` (padrão: `http://localhost:8080`) |
| `vue-cli-service: command not found` | `npm install` não rodou | `cd leilao-app && npm install` |

### Um teste falha

| Sintoma | Causa provável | Solução |
|---|---|---|
| `Port 5432 ... connection refused` num teste | algum teste exige banco | os 90 testes foram escritos para não exigir banco; se aparecer isso, é regressão |
| `SegurancaIntegracaoTest` falha | contexto do Spring não subiu | rode `mvn test -Dtest=SegurancaIntegracaoTest` e leia a stack trace |

### A autenticação não está sendo exigida

Ela liga por perfil. Verifique:

```bash
echo $SECURITY_ENABLED        # deve ser "true"
# ou, ao iniciar:
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

O `application-prod.properties` exige `SECURITY_USERNAME` e
`SECURITY_PASSWORD`. Sem elas a aplicação não sobe, por opção: falhar na
inicialização é melhor do que subir com senha padrão.

### Limpar tudo e recomeçar

```bash
# banco
docker compose -f docker-database.yml down -v
docker compose -f docker-database.yml up -d

# build
cd leilao-api && mvn clean
cd ../leilao-app && rm -rf node_modules dist && npm install

# processos travados (Windows)
netstat -ano | findstr :8081
netstat -ano | findstr :8080
```

## 11. Variáveis de ambiente

Todas podem ser definidas por variável de ambiente: o Spring Boot dá
precedência à variável sobre o `application.properties`.

### Backend

| Variável | Padrão | Para quê |
|---|---|---|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/az` | String de conexão |
| `DATABASE_USER` | `postgres` | Usuário do banco |
| `DATABASE_PASSWORD` | `postgres` | Senha do banco |
| `SECURITY_ENABLED` | `false` | `true` liga a autenticação |
| `SECURITY_MODE` | `basic` | `basic` (HTTP Basic) ou `jwt` (token JWT) |
| `SECURITY_USERNAME` | `admin` | Usuário do HTTP Basic |
| `SECURITY_PASSWORD` | `troque-esta-senha` | Senha do HTTP Basic |
| `JWT_ISSUER` | `az-leilao-api` | Emissor dos tokens JWT |
| `JWT_KEYS_DIR` | (vazio) | Pasta das chaves RSA; vazio gera chaves de desenvolvimento |
| `AUDIT_HMAC_KEY` | (vazio) | Chave que sela a trilha de auditoria |
| `RATE_LIMIT_ENABLED` | `true` | Liga o controle de força bruta |
| `RATE_LIMIT_MAX_ATTEMPTS` | `5` | Falhas por usuário antes do bloqueio |
| `RATE_LIMIT_MAX_ATTEMPTS_PER_IP` | `20` | Falhas por IP antes do bloqueio |
| `RATE_LIMIT_WINDOW_MINUTES` | `15` | Duração da janela de contagem |
| `API_RATE_LIMIT_ENABLED` | `true` | Liga o limite de volume |
| `API_RATE_LIMIT_MAX_REQUESTS` | `120` | Requisições por IP na janela |
| `API_RATE_LIMIT_WINDOW_SECONDS` | `60` | Tamanho da janela, em segundos |
| `TRUST_PROXY` | `false` | Confiar no `X-Forwarded-For` (só atrás de proxy) |
| `CORS_ORIGINS` | `http://localhost:8080,...` | Origens permitidas |

Subindo em modo produção:

```bash
export DATABASE_URL="jdbc:postgresql://localhost:5432/az"
export DATABASE_USER="postgres"
export DATABASE_PASSWORD="postgres"
export SECURITY_ENABLED="true"
export SECURITY_USERNAME="admin"
export SECURITY_PASSWORD="SenhaForte@2026"
export CORS_ORIGINS="http://localhost:8080"

cd leilao-api && mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

### Frontend

| Variável | Padrão | Para quê |
|---|---|---|
| `VUE_APP_API_BASE_URL` | `http://localhost:8081` | Endereço da API |

Atenção: o `.env` do frontend vira código que chega ao navegador. Nunca
coloque ali uma senha ou um token, porque qualquer pessoa que abrir o "ver
código-fonte" da página veria.

---

**Documentos relacionados:** [`README.md`](README.md) ·
[`IDENTIDADE-VISUAL.md`](IDENTIDADE-VISUAL.md) ·
[`.github/SECURITY.md`](.github/SECURITY.md)
