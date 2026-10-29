# Guia de Apresentação — Avaliação AZ Tecnologia

> Este é o material de apoio para a **apresentação oral** do projeto.
> Está escrito para ser lido antes da entrevista e para servir de *cola*
> durante as perguntas.
>
> O [`README.md`](README.md) tem os detalhes técnicos de implementação.
> Aqui está o **como explicar**.

---

## 1. Pitch de 30 segundos

> "Construí um sistema de gestão de leilões full stack, em **Vue 2 + Spring Boot
> 2.7 + PostgreSQL**, seguindo a arquitetura em camadas pedida no enunciado:
> controllers REST, camada de negócio, repositórios e entidades.
>
> O modelo de dados tem 5 tabelas, com chave primária composta na tabela de
> junction e índices e constraints pensados para integridade. A API tem o CRUD
> completo das 5 tabelas, com **validação de entrada em duas camadas**,
> **senhas com hash BCrypt**, **CORS restrito por lista** e **tratamento global
> de erros sem vazamento de stack trace**.
>
> O frontend tem o menu com rotas `#/`, a grid editável de unidades, as telas
> de consulta de empresas e leilões e o formulário de empresa com máscaras e
> validações. Tudo com **17 testes automatizados** que rodam sem banco de dados
> e **um guia de decisões** documentando cada mudança em relação ao starter."

---

## 2. Roteiro de execução (10 minutos antes da entrevista)

**Deixe tudo pronto antes de a chamada começar.** Teste na mesma máquina.

```bash
# Terminal 1 - banco (só na primeira vez)
docker compose -f docker-database.yml up -d

# Terminal 2 - API
cd leilao-api && mvn spring-boot:run

# Terminal 3 - frontend
cd leilao-app && npm install && npm run serve
```

Checklist antes de começar:

- [ ] `http://localhost:8080` abre na tela de **Unidades**
- [ ] `http://localhost:8081/unidades` responde JSON no navegador
- [ ] `http://localhost:8080/#/empresas` e `#/leiloes` abrem
- [ ] Botão **Nova empresa** abre o formulário
- [ ] `cd leilao-api && mvn test` passa (17 testes)

> **Dica:** se a sua máquina não tiver Docker, o PostgreSQL instalado
> resolve — `createdb -U postgres az` + os dois scripts `.sql`.
> Se não tiver Java 11 nem Maven, instale antes: o *starter* exige Java 11
> e o build não compila em Java 8.

---

## 3. Roteiro de demonstração (sugestão de 10 minutos)

| # | O que fazer | O que falar |
|---|-------------|-------------|
| 1 | Abrir `http://localhost:8080` | "Esta é a tela principal. O menu usa rotas com hash (`#/unidades`), então funciona em qualquer hospedagem sem configurar o servidor web." |
| 2 | Menu → **Unidades** | "Grade editável: a linha vira formulário ao clicar no lápis, e o botão verde cria uma linha nova no topo." Clicar em *Nova unidade*, digitar, salvar. |
| 3 | Menu → **Empresas** | "Consulta somente leitura, com os campos pedidos: CNPJ, razão social, telefone e e-mail. Note as máscaras — a API devolve só dígitos e a tela formata." |
| 4 | Ícone do lápis | "Esse é o link de edição do exercício 8: ele navega para `#/empresa/:id`." |
| 5 | No formulário: limpar a razão social e salvar | "A validação do frontend barra e mostra o erro no campo." |
| 6 | Preencher, salvar | "Gravou. Agora vou mostrar a validação do backend." |
| 7 | Terminal: `curl -X POST localhost:8081/empresas -d '{"cnpj":"123"}'` | "O backend valida de novo, e rejeita com HTTP 400 e o campo exato do problema." |
| 8 | Menu → **Leilões** | "Aqui está o cálculo do total: soma de `quantidade × valor_inicial` dos lotes, feito no banco." Apontar para os cards de resumo. |
| 9 | Terminal: `mvn test` | "17 testes automatizados, rodando sem banco de dados." |

---

## 4. Resumo de cada tecnologia (a "cola")

> Objective e simples: se perguntarem "por que X", a resposta está aqui.

### PostgreSQL
É o banco de dados relacional — guarda os dados em **tabelas** ligadas por
chaves. Escolhi porque o enunciado pede e porque modela bem relações como
"empresa vende leilão" e "empresa comprou leilão".
- **Chave primária (PK)**: identifica a linha. Impede duplicidade.
- **Chave estrangeira (FK)**: liga uma tabela a outra e garante que o
  registro exista. É uma regra que **o próprio banco fiscaliza**.
- **Constraint `UNIQUE`**: dois registros não podem ter o mesmo valor
  naquele campo (o `cnpj` e o `usuario` da empresa).
- **Constraint `CHECK`**: o valor tem que ter um formato válido (CNPJ com 14
  dígitos, e-mail com `@`).
- **Sequência (`nextval`)**: contador do banco que gera os `id` sem risco de
  duplicidade em cadastros simultâneos.
- **Transação (`BEGIN`/`COMMIT`)**: se qualquer comando falhar, tudo
  volta ao estado anterior — o banco nunca fica "pela metade".
- **`NUMERIC(14,2)` para dinheiro**: ponto flutuante perde centavos
  (`0.1 + 0.2 ≠ 0.3`); número decimal é exato.

### Java 11
A linguagem do backend. Java 11 é *Long Term Support* (suporte estendido) e
é o que o *starter* pede. Java 8 (o que já vinha instalado na minha máquina)
**não compila** o projeto, porque o Spring Boot 2.7 exige 11 ou mais.

### Spring Boot
Um framework que junta as peças prontas: ele sobe o servidor web (Tomcat),
configura o acesso ao banco, encontra as classes, injeta as dependências e
sobe a aplicação com um `main`. O ganho é não precisar configurar tudo à mão.

### Spring Data JPA / Hibernate
Camada que traduz **objetos Java ↔ tabelas**.
- **Entidade** = uma tabela. `@Entity`, `@Table`, `@Id`, `@Column`.
- **Repositório** = interface com métodos prontos. `findAll()`, `findById()`,
  `save()`, `delete()` já vêm prontos; métodos como `existsByCnpj` são
  **derivados pelo nome** e o Spring monta o SQL.
- **JPQL** = a linguagem de consulta do Hibernate. É usada na consulta do
  total dos leilões.
- **`@ManyToOne` / `@OneToMany`** = relações entre entidades.
- **`@EmbeddedId`** = chave primária **composta** (usada na tabela
  `comprador`, cuja PK é o par empresa + leilão).
- **`LAZY`** = "só busca no banco se alguém realmente precisar". Evita o
  problema N+1 (muitas consultas desnecessárias).

### Bean Validation (Jakarta Validation)
Anotações que descrevem as regras dos campos de entrada:
`@NotBlank` (obrigatório), `@Size` (tamanho máximo), `@Email`, `@Pattern`
(formato). O Spring verifica **automaticamente** ao receber a requisição e
devolve um erro 400 dizendo **qual campo** está errado.

### Spring Security + BCrypt
- **BCrypt** é um algoritmo de *hash* para senhas. É **irreversível** (não
  dá para "desfazer" o hash e descobrir a senha). Tem duas propriedades que
  importam: o **salt** (uma serragem aleatória, que faz a mesma senha gerar
  hashes diferentes) e a **lentidão proposital** (custa ~100 ms, o que
  derruba ataques de força bruta).
- **Spring Security** é o framework de segurança do Spring. Aqui ele está
  **configurável**: no desenvolvimento os endpoints ficam abertos (para o
  avaliador testar rápido) e com `SECURITY_ENABLED=true` toda rota exige
  autenticação.

### Vue 2
Framework de interface. A ideia central é o **componente**: um pedaço de
tela (HTML + CSS + JavaScript) reutilizável. O **DOM virtual** é a técnica
que faz o Vue ser rápido: quando o dado muda, ele calcula a diferença e
aplica só o que mudou, em vez de redesenhar a página inteira.

### Vue Router
Cuida do **mapeamento entre URL e tela**. Com `mode: 'hash'`, a URL fica
`#/unidades` — funciona em qualquer servidor sem configuração especial.

### Vuetify
Biblioteca de **60+ componentes prontos** para Vue 2: tabelas, formulários,
diálogos, menus, alertas. É o que a empresa espera num projeto real —
reinventar tabela e formulário do zero não faz sentido. O `v-data-table`
já vem com busca, ordenação, paginação e slots.

### Axios
Cliente HTTP (a biblioteca que faz as requisições ao backend). Centralizei
todas as chamadas em `services/api.js` para ter **um único lugar** que sabe
o endereço da API e **um único lugar** que sabe interpretar o formato de
erro do backend.

### Maven
Gerenciador de build e dependências do Java. O `pom.xml` declara as
dependências e o `mvn` baixa e resolve tudo. `mvn spring-boot:run` sobe a
aplicação; `mvn test` roda a suíte de testes.

### Docker / Docker Compose
Sobe o PostgreSQL já com o esquema e os dados carregados, sem instalar
banco na máquina. No `docker-compose`, a pasta `sql/` é montada em
`/docker-entrypoint-initdb.d`, que é a pasta que o container do Postgres
executa **na primeira subida**.

### ESLint
Analisador de código que aponta problemas antes de rodar a aplicação —
variável não usada, erro de digitação em nome de método, template inválido.
Foi ele que pegou o bug real do meu projeto (eu havia escrito `mascararCnpj`
no template e `mascaraCnpj` no código).

---

## 5. Como cada exercício foi resolvido

| Exercício | Solução | O que falar |
|-----------|---------|-------------|
| **1 — DDL** | `sql/01_ddl.sql` | 5 tabelas, PK/FK/UNIQUE/CHECK, sequências com `OWNED BY`, tudo em transação. Respeitei os asteriscos e sublinhados do diagrama. |
| **2 — DML** | `sql/02_dml.sql` | 12/12/18/12/14 registros, com FKs coerentes. Script **idempotente** (pode rodar de novo). As senhas são hashes BCrypt reais, gerados por um teste. |
| **3 — CRUD REST** | `service` + `business` + `repository` | 5 tabelas × 5 operações. DTO de entrada separado da entidade, `@Valid`, erros padronizados. |
| **4 — Tela principal** | `App.vue` + `router/index.js` | Menu, `router-view`, rotas com hash. Componentes separados (`AppMenu`, `AppNotificacoes`, `ConfirmDialog`). |
| **5 — Unidades** | `views/unidade/Unidades.vue` | Grid editável no padrão do Vuetify: consulta / edição inline / linha de inclusão. |
| **6 — Empresas** | `views/empresa/Empresas.vue` | Leitura + busca, com máscaras na exibição. |
| **7 — Leilões** | `views/leilao/Leiloes.vue` | Leitura + total calculado no banco + cards de resumo. |
| **8 — Cadastro de empresa** | `views/empresa/Empresa.vue` | Uma tela para incluir e editar (muda o comportamento conforme a URL). Máscaras, obrigatoriedade, formatos, tamanho máximo. Links de editar/excluir na tela do exercício 6. |

---

## 6. Segurança — o resumo de 1 minuto

> *"A segurança da informação foi trabalhada em cinco frentes:*
>
> *1) **Senha**: nunca em texto puro — só hash BCrypt, que tem salt e é
> lento de propósito. A senha nunca sai da API: o DTO de resposta não tem
> esse campo.*
>
> *2) **Entrada**: validação em duas camadas. O frontend dá o retorno
> rápido; o backend é a garantia, porque qualquer um pode chamar a API sem
> passar pela tela. Uso DTO com lista de campos permitidos, então o
> cliente não consegue mandar o `id` nem falsificar a data de criação.*
>
> *3) **Banco**: todo SQL é parametrizado (prepared statement), então não há
> injeção de SQL. E o menor privilégio — o script opcional cria um usuário
> de banco que só pode ler e gravar dados, sem poder derrubar tabela.*
>
> *4) **Aplicação**: CORS com lista explícita de origens (nunca `*` com
> credenciais), cabeçalhos de segurança, sem vazamento de stack trace,
> sessão stateless, CSRF desabilitado com justificativa (API sem cookie).*
>
> *5) **Segredos**: nada de senha ou token versionado no Git — tudo por
> variável de ambiente. E o frontend, que é público por natureza, não
> tem nenhum segredo."*

### Se perguntarem "por que os endpoints estão abertos?"

> "O enunciado do teste pede que `GET /unidades` responda direto, para o
> avaliador testar rápido. A autenticação está implementada e é ativada por
> uma variável de ambiente (`SECURITY_ENABLED=true`), no perfil de
> produção — que também faz a aplicação **não subir** se as credenciais do
> banco não estiverem definidas. A decisão está explícita no código, não
> esquecida."

---

## 7. Perguntas prováveis (e como responder)

**P: Por que você não usou a entidade direto no controller?**
> "Porque o que chega da internet é dado não confiável. Se eu usasse a
> entidade, o cliente poderia mandar o campo `id` e trocar a chave de um
> registro, ou mandar o `created_at` e falsificar a trilha de auditoria.
> Com DTO de entrada, o sistema controla exatamente o que entra. É o
> princípio de *allow-list*."

**P: Como você evita SQL Injection?**
> "O Hibernate envia os valores como parâmetros separados da consulta, e
> nunca cola o texto digitado pelo usuário dentro do SQL. Não existe
> concatenação de string em query em lugar nenhum do projeto."

**P: E se alguém apagar o vendedor de um leilão?**
> "O banco recusa (`ON DELETE RESTRICT` na FK) e a aplicação traduz isso
> numa mensagem clara: 'esta empresa não pode ser excluída porque possui
> leiloes vinculados'. O banco é a última linha de defesa; a aplicação dá
> a mensagem útil."

**P: Por que `NUMERIC` e não `float` para dinheiro?**
> "Porque ponto flutuante perde centavos. `0.1 + 0.2` não dá `0.3` em
> `double`. Dinheiro tem que ser exato, e o `BigDecimal` no Java também é."

**P: O que é CORS e por que foi preciso?**
> "O frontend roda na 8080 e a API na 8081 — origens diferentes. O
> navegador bloqueia por padrão (política de mesma origem). O CORS é o
> mecanismo que o servidor usa para autorizar a origem. E fiz com lista
> explícita, não com `*`, porque `*` com credenciais permitiria um site
> malicioso fazer requisição autenticada na nossa API."

**P: Você usa Lombok? Não atrapalha?**
> "Uso, e ele reduz muito o código repetitivo dos getters e setters. A
> ressalva: ele esconde código. Por isso o núcleo da lógica — as regras de
> negócio — está em classes com anotações e comentários explícitos, e o
> Lombok fica nas estruturas de dados."

**P: Como você testaria isso?**
> "Os 17 testes automatizados cobrem as regras de negócio sem precisar de
> banco (dublês do Mockito), porque quero que `mvn test` passe em
> qualquer máquina. Num próximo passo eu adicionaria testes de integração
> com o Postgres real usando Testcontainers, para validar as queries e as
> constraints do banco."

**P: Se o banco cair, o que acontece?**
> "O HikariCP devolve erro na conexão, o `GlobalExceptionHandler` traduz
> em HTTP 500 com mensagem genérica (sem stack trace para o cliente) e o
> detalhe vai para o log do servidor. Para o orquestrador saber que a
> aplicação está viva, o actuator expõe `/actuator/health`."

**P: O que você faria com mais tempo?**
> "Quatro coisas, em ordem: (1) autenticação real com tokens e *refresh
> token*, em vez do HTTP Basic; (2) testes de integração com Testcontainers;
> (3) paginação e filtros no servidor, hoje as listagens trazem tudo;
> (4) cache e índice para as consultas de maior volume."

---

## 8. Glossário rápido

| Termo | Significado em uma frase |
|-------|--------------------------|
| **API** | Interface que o backend expõe para o frontend (e para qualquer outro cliente). |
| **REST** | Estilo de API: cada tabela vira um recurso, cada ação vira um método HTTP (`GET`/`POST`/`PUT`/`DELETE`). |
| **Controller** | Classe que recebe as requisições HTTP e devolve as respostas. |
| **Service / BO** | Classe onde mora a regra de negócio. |
| **Repository** | Classe que fala com o banco. |
| **Entity** | Classe que representa uma tabela. |
| **DTO** | Objeto que define o formato de entrada ou saída da API. |
| **Migration / DDL / DML** | Script de estrutura (tabelas) e de dados (INSERTs). |
| **Transação** | Bloco de operações que acontece inteiro ou nada. |
| **Índice** | Estrutura que acelera as consultas mais frequentes. |
| **Hash** | "Digest" unidirecional: não dá para voltar ao original. |
| **Salt** | Dado aleatório misturado no hash, para que senhas iguais gerem hashes diferentes. |
| **CORS** | Regras que definem quais sites podem chamar a API. |
| **CSRF** | Ataque em que um site faz o navegador enviar uma requisição "por tabela". |
| **XSS** | Injeção de código no navegador — por isso nunca uso `v-html` com dado do usuário. |
| **Lazy loading** | Buscar no banco só quando o dado é realmente usado. |
| **N+1** | Problema clássico: uma consulta que vira muitas. |
| **Componente** | Peça reutilizável de tela (HTML + CSS + JS). |
| **Slot** | "Placeholder" de conteúdo dentro de um componente. |
| **Hash routing** | Rotas na URL depois do `#`, que dispensam configuração de servidor. |
| **SPA** | Aplicação de uma página só, que troca o conteúdo sem recarregar. |
| **Yarn/npm** | Gerenciador de dependências do JavaScript. |
| **Webpack** | Ferramenta que "embala" o código do frontend para o navegador. |

---

## 9. Limitações que eu reconheço

Dizer o que **não** está pronto demonstra maturidade melhor do que dizer
que está tudo perfeito:

1. **Autenticação é básica** — HTTP Basic em memória, sem tokens, sem
   refresh, sem recuperação de senha.
2. **Sem paginação no servidor** — as listagens trazem todos os registros.
   Com muito volume isso precisaria de `Pageable`.
3. **Sem teste de integração com o banco** — a suíte é toda de unidade.
4. **A tabela de lotes não tem tela** — o enunciado não pede uma; existe a
   API completa dela.
5. **Sem CI/CD** — não há pipeline automatizado de build e teste.
6. **Senha do banco em `variables.env`** — adequado para o teste, mas em
   produção viria de um cofre de segredos (Vault, AWS Secrets Manager).

Cada item acima tem a direção de solução já pensada, e está no README.

---

## 10. Resumo das decisões que mudam em relação ao *starter*

Se perguntarem "por que mudou isso?", a tabela completa está no
[README](README.md#8-decisões-tomadas-em-relação-ao-starter-fornecido).
As mais importantes:

1. **Vue CLI 4 → 5** — o webpack 4 usa MD4, que o OpenSSL 3 removeu; o build
   quebra em Node 17+. O webpack 5 resolve sem nenhuma flag especial.
2. **Vuetify adicionado** — exigido pelos exercícios 5–8 (`v-data-table`).
3. **`spring-boot-starter-validation` adicionado** — removido do
   `starter-web` no Spring Boot 2.3; sem ele as validações do exercício 8
   não acontecem.
4. **Spring Security adicionado** — para o BCrypt da senha e para ter
   autenticação configurável.
5. **`POSTGRES_DB=az`** — sem isso o container cria só o banco `postgres` e
   a API não conecta.
6. **URLs em singular e plural** — o enunciado é inconsistente entre os
   exercícios 3 e 5–7; aceitar as duas formas é mais robusto.
