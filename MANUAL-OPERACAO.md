# MANUAL DE OPERAÇÃO — Sistema de Leilões

> Documento operacional do projeto **Avaliação AZ Tecnologia**.
>
> Tudo o que é preciso para **rodar**, **testar**, **demonstrar** e
> **diagnosticar** o projeto — do zero, em máquina limpa.

---

## Sumário

1. [Pré-requisitos](#1-pré-requisitos)
2. [Subir o banco de dados](#2-subir-o-banco-de-dados)
3. [Subir a API](#3-subir-a-api)
4. [Subir o frontend](#4-subir-o-frontend)
5. [Rodar os testes](#5-rodar-os-testes)
6. [Gerar senhas com hash BCrypt](#6-gerar-senhas-com-hash-bcrypt)
7. [Aplicar o menor privilégio no banco](#7-aplicar-o-menor-privilégio-no-banco)
8. [Roteiro de demonstração](#8-roteiro-de-demonstração)
9. [Verificação completa de saúde](#9-verificação-completa-de-saúde)
10. [Diagnóstico de problemas](#10-diagnóstico-de-problemas)
11. [Variáveis de ambiente](#11-variáveis-de-ambiente)

---

## 1. Pré-requisitos

| Ferramenta | Versão | Para quê |
|---|---|---|
| **Java** | 11 (LTS) | Rodar a API |
| **Maven** | 3.8+ | Build e dependências da API |
| **Node.js** | 16+ | Rodar o frontend |
| **PostgreSQL** | 16 | Banco de dados |
| **Docker** (opcional) | qualquer | Alternativa para subir o banco sem instalar |

**Verificando o que já está instalado:**

```bash
java -version        # deve mostrar 11.x
mvn -version         # deve mostrar 3.8+ e o Java 11
node -v              # deve mostrar 16+
psql --version       # deve mostrar 16+
docker --version     # opcional
```

> **Atenção:** a API precisa do **Java 11**. Com o Java 17, o Spring
> Boot 2.7 exige `--add-opens` no `MAVEN_OPTS` para funcionar.

---

## 2. Subir o banco de dados

### Opção A — Docker (recomendado)

```bash
cd Selecao-Dev-AZ
docker compose -f docker-database.yml up -d
```

Na **primeira** subida, o container do PostgreSQL executa
automaticamente `01_ddl.sql` (estrutura) e `02_dml.sql` (dados). O banco
`az` já fica criado **e populado**.

Conferir:

```bash
docker compose -f docker-database.yml ps
docker exec -it $(docker ps -q --filter "publish=5432") psql -U postgres -d az -c "\dt"
```

**Recomeçar do zero** (apaga os dados):

```bash
docker compose -f docker-database.yml down -v
docker compose -f docker-database.yml up -d
```

### Opção B — PostgreSQL instalado na máquina

```bash
createdb -U postgres az
psql -U postgres -d az -f leilao-api/src/main/resources/sql/01_ddl.sql
psql -U postgres -d az -f leilao-api/src/main/resources/sql/02_dml.sql
```

Os scripts são **idempotentes**: podem ser rodados de novo sem quebrar.

**Verificar os dados:**

```bash
psql -U postgres -d az -c "SELECT COUNT(*) FROM tb_unidade;"
psql -U postgres -d az -c "SELECT COUNT(*) FROM tb_empresa;"
psql -U postgres -d az -c "SELECT COUNT(*) FROM tb_leilao;"
psql -U postgres -d az -c "SELECT COUNT(*) FROM tb_lote;"
psql -U postgres -d az -c "SELECT COUNT(*) FROM tb_comprador;"
```

---

## 3. Subir a API

```bash
cd leilao-api
mvn spring-boot:run
```

Sobe na **porta 8081**.

**Para ver o SQL no console** (útil para depurar):

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

**Para gerar o `.jar` executável:**

```bash
mvn package
java -jar target/leilao-api-1.0.0-SNAPSHOT.jar
```

**Conferir que subiu:**

```bash
curl http://localhost:8081/actuator/health
# {"status":"UP"}
```

---

## 4. Subir o frontend

```bash
cd leilao-app
npm install
npm run serve
```

Sobe na **porta 8080**. Acesse <http://localhost:8080>.

**Para gerar o bundle de produção:**

```bash
npm run build      # gera a pasta dist/
npm run lint       # verificação de estilo e erros comuns
```

> O `.env` da pasta `leilao-app` aponta a API para
> `http://localhost:8081`. Para apontar para outro endereço, edite a
> variável `VUE_APP_API_BASE_URL`.

---

## 5. Rodar os testes

```bash
cd leilao-api
mvn test
```

**37 testes**, todos automatizados e **sem precisar de banco de dados** —
rodam em qualquer máquina, em menos de 5 segundos.

| Arquivo de teste | Testes | O que verifica |
|---|---|---|
| `SegurancaIntegracaoTest` | 7 | A cadeia de segurança inteira (401, 429, bloqueio, dado do controller) |
| `LoginAttemptServiceTest` | 7 | As regras do controle de tentativas de login |
| `ClientIpResolverTest` | 5 | A descoberta do IP real e a proteção contra `X-Forwarded-For` forjado |
| `EmpresaBOTest` | 6 | Regras de negócio da empresa (CNPJ único, senha hasheada) |
| `UnidadeBOTest` | 5 | Regras de negócio da unidade (normalização, duplicidade) |
| `PasswordEncoderTest` | 5 | O codificador BCrypt |
| `GeradorHashSenhaTest` | 2 | O gerador de hash usado para montar a massa de dados |

**Rodar só um arquivo:**

```bash
mvn test -Dtest=SegurancaIntegracaoTest
```

**Rodar um método específico:**

```bash
mvn test -Dtest=SegurancaIntegracaoTest#credencialCorretaDevolveODadoDoController
```

**Relatório HTML** (o Surefire gera em `target/surefire-reports/`):

```bash
mvn test && open leilao-api/target/surefire-reports/index.html
```

---

## 6. Gerar senhas com hash BCrypt

A massa de dados (`02_dml.sql`) já vem com senhas em hash BCrypt
prontas. Mas, ao cadastrar uma empresa nova pela tela, a senha é
hasheada automaticamente pela aplicação.

Para gerar um hash manualmente (por exemplo, para inserir direto no
banco), existe um utilitário dentro do próprio projeto:

```bash
cd leilao-api
mvn test -Dtest=GeradorHashSenhaTest
```

O teste imprime os hashes gerados no console. Alternativamente, use o
JShell do Java 11 com o classpath do projeto:

```bash
cd leilao-api
mvn -q dependency:build-classpath -Dmdep.outputFile=cp.txt
jshell --class-path "target/classes;$(cat cp.txt)"
```

```java
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
var encoder = new BCryptPasswordEncoder(10);
System.out.println(encoder.encode("MinhaSenhaForte123"));
```

**Regra importante:** a senha original **nunca** é gravada — só o hash.
E o hash gerado **não pode ser reaproveitado** para outra senha: como
o salt é sorteado a cada vez, cada hash é único.

---

## 7. Aplicar o menor privilégio no banco

**Opcional**, mas recomendado para qualquer ambiente que não seja a
máquina do avaliador.

```bash
psql -U postgres -d az -f leilao-api/src/main/resources/sql/seguranca/03_permissoes.sql
```

Isso cria o usuário `app_leilao`, que pode ler e gravar **apenas** nas
tabelas do projeto — e **não** pode derrubar o banco, alterar a
estrutura, criar usuários nem ler outros bancos.

Depois, aponte a aplicação para ele:

```bash
export DATABASE_URL="jdbc:postgresql://localhost:5432/az"
export DATABASE_USER="app_leilao"
export DATABASE_PASSWORD="<senha definida no script>"
```

> **Por que isso importa.** Sem isso, a aplicação conecta como
> superusuário. Se ela for invadida (um SQL Injection, uma dependência
> maliciosa), o atacante herda todo esse poder. Com isso, o dano fica
> limitado às tabelas do projeto.

---

## 8. Roteiro de demonstração

O roteiro para uma apresentação de ~10 minutos.

### 8.1 Abrir a aplicação

1. Subir o banco, a API e o frontend.
2. Abrir <http://localhost:8080> — a tela de **Unidades**.

### 8.2 Exercício 5 — Unidades (CRUD completo)

1. **Clicar em "Nova unidade"** → uma linha em branco aparece no topo,
   com o campo já focado.
2. Digitar "Novo Galpão.Teste" e salvar → a linha vira registro, com
   **id gerado pelo banco**.
3. **Clicar no lápis** → a linha vira formulário; digitar e salvar.
4. **Clicar na lixeira** → aparece o **diálogo de confirmação**
   (exclusão não tem Ctrl+Z).
5. **Explicar a técnica:** *grid editável do Vuetify — uma única
   tabela com três estados (consulta, edição, inclusão), e a linha de
   inclusão entra no topo via um `computed`.*

### 8.3 Exercício 6 — Empresas (consulta + máscaras)

1. Menu → **Empresas**.
2. Digitar na busca → o filtro acontece **no navegador**.
3. **Explicar as máscaras:** a API devolve **só os dígitos**
   (`11422377000155`); a tela formata para exibição
   (`11.422.377/0001-55`). A canonicalização é feita no **backend** —
   é o que garante que a busca por CNPJ funcione.

### 8.4 Exercício 8 — Cadastro de empresa

1. Menu → **Empresas** → "Nova empresa".
2. **Explicar a URL:** `#/empresa` é inclusão; `#/empresa/5` é edição.
   **Uma tela, dois modos** — o componente descobre o modo lendo o
   parâmetro `id` da rota.
3. Preencher e salvar → a tela volta para a listagem.
4. **Explicar a validação em duas camadas:** o frontend valida para
   *experiência*, o backend para *garantia*.

### 8.5 Exercício 7 — Leilões (total calculado no banco)

1. Menu → **Leilões**.
2. **Apontar os cards de resumo**: total geral e próximo leilão.
3. **Explicar:** o total do leilão é uma **JPQL agregada no banco**
   (`SUM(quantidade * valorInicial)`) — uma consulta só, em vez de
   buscar todos os lotes e somar em memória.

### 8.6 Segurança (a parte que impressiona)

```bash
# Em outro terminal, com a API no perfil de produção:
curl -i http://localhost:8081/unidades
# → 401 em JSON

curl -i -u admin:senha-errada http://localhost:8081/unidades
# → 401 em JSON

curl -i -u admin:$SENHA_CORRETA http://localhost:8081/unidades
# → 200 com os DADOS do controller
```

Depois, o ataque de força bruta:

```bash
for i in $(seq 1 6); do
  curl -s -o /dev/null -w "tentativa $i -> HTTP %{http_code}\n" \
       -u admin:senha-errada http://localhost:8081/unidades
done
# cinco 401 e depois 429
```

**Explicar:** o limite é de **5 falhas por usuário** e **20 por IP**; a
conta bloqueada responde **429** com o cabeçalho `Retry-After`; e a
resposta para "usuário inexistente" é **byte a byte igual** à de "senha
errada" — isso impede a enumeração de usuários.

### 8.7 O que está no repositório

Mostrar os documentos e dizer que a segurança está mapeada contra a
**OWASP Top 10**, com a seção explícita do que **não** está protegido.

---

## 9. Verificação completa de saúde

O checklist para rodar **antes** de apresentar, garantindo que está tudo
100%.

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

# 5) Higiene do codigo (caracteres estranhos, palavras em ingles, erros de digitacao)
cd .. && node scripts/verificar-higiene.js
```

**Todos devem responder:** API `200`, suíte `BUILD SUCCESS`, lint
`No lint errors found!` e higiene `Higiene OK`.

---

## 10. Diagnóstico de problemas

### A API não sobe

| Sintoma | Causa provável | Solução |
|---|---|---|
| `Port 8081 was already in use` | outra instância rodando | `netstat -ano \| findstr :8081` e encerre o PID |
| `Connection to localhost:5432 refused` | banco parado | `docker compose -f docker-database.yml up -d` |
| `relation "tb_unidade" does not exist` | scripts SQL não rodaram | rode `01_ddl.sql` e `02_dml.sql` |
| `Unsupported class file major version` | Java errado | use o JDK 11 |
| `Porta 8081 já está em uso` no frontend | frontend e API na mesma porta | o frontend é 8080, a API é 8081 |

### O frontend abre em branco

| Sintoma | Causa provável | Solução |
|---|---|---|
| Tela branca | a API não está rodando | `curl http://localhost:8081/actuator/health` |
| "Network Error" no console | CORS bloqueando | verifique `app.cors.origins` (padrão: `http://localhost:8080`) |
| "vue-cli-service: command not found` | `npm install` não rodou | `cd leilao-app && npm install` |

### Um teste falha

| Sintoma | Causa provável | Solução |
|---|---|---|
| `Port 5432 ... connection refused` num teste | algum teste exige banco | os 37 testes foram escritos para **não** exigir banco; se aparecer isso, é regressão |
| `SegurancaIntegracaoTest` falha | contexto do Spring não subiu | rode `mvn test -Dtest=SegurancaIntegracaoTest` e leia a stack trace |

### A autenticação não está sendo exigida

A autenticação é **opt-in** por perfil. Verifique:

```bash
echo $SECURITY_ENABLED        # deve ser "true"
# ou, ao iniciar:
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

E confira o `application-prod.properties`, que exige `SECURITY_USERNAME`
e `SECURITY_PASSWORD` — sem elas, a aplicação **não sobe** (por opção:
falhar na inicialização é melhor do que subir com senha padrão).

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

---

## 11. Variáveis de ambiente

Todas podem ser definidas por variável de ambiente (o Spring Boot dá
precedência à variável sobre o `application.properties`).

### Backend

| Variável | Padrão | Para quê |
|---|---|---|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/az` | String de conexão |
| `DATABASE_USER` | `postgres` | Usuário do banco |
| `DATABASE_PASSWORD` | `postgres` | Senha do banco |
| `SECURITY_ENABLED` | `false` | `true` liga a autenticação |
| `SECURITY_USERNAME` | `admin` | Usuário do HTTP Basic |
| `SECURITY_PASSWORD` | `troque-esta-senha` | Senha do HTTP Basic |
| `RATE_LIMIT_ENABLED` | `true` | Liga o controle de força bruta |
| `RATE_LIMIT_MAX_ATTEMPTS` | `5` | Falhas por usuário antes do bloqueio |
| `RATE_LIMIT_MAX_ATTEMPTS_PER_IP` | `20` | Falhas por IP antes do bloqueio |
| `RATE_LIMIT_WINDOW_MINUTES` | `15` | Duração da janela de contagem |
| `TRUST_PROXY` | `false` | Confiar no `X-Forwarded-For` (só atrás de proxy) |
| `CORS_ORIGINS` | `http://localhost:8080,...` | Origens permitidas |

### Exemplo: subir em modo produção

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

> **ATENÇÃO:** o `.env` do frontend vira **código que chega ao
> navegador**. Nunca coloque ali uma senha ou um token — qualquer
> pessoa que abrir o "ver código-fonte" da página veria.

---

**Documentos relacionados:** [`README.md`](README.md) ·
[`IDENTIDADE-VISUAL.md`](IDENTIDADE-VISUAL.md) ·
[`.github/SECURITY.md`](.github/SECURITY.md)
