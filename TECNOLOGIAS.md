# TECNOLOGIAS — Guia de referência

> Documento de referência do projeto **Avaliação AZ Tecnologia**.
>
> Para cada tecnologia: **o que é**, **por que foi escolhida**, **o que
> ela resolve** e **qual alternativa foi descartada**. É o material para
> explicar as escolhas técnicas depois — sem precisar improvisar.

---

## Sumário

1. [Panorama geral](#1-panorama-geral)
2. [Backend](#2-backend)
   - [Java 11](#21-java-11)
   - [Spring Boot 2.7](#22-spring-boot-27)
   - [Spring Web](#23-spring-web)
   - [Spring Data JPA e Hibernate](#24-spring-data-jpa-e-hibernate)
   - [PostgreSQL 16](#25-postgresql-16)
   - [Bean Validation](#26-bean-validation)
   - [Spring Security](#27-spring-security)
   - [BCrypt](#28-bcrypt)
   - [Lombok](#29-lombok)
   - [SLF4J e Logback](#2-10-slf4j-e-logback)
   - [Actuator](#2-11-actuator)
   - [JUnit 5 e Mockito](#2-12-junit-5-e-mockito)
   - [Maven](#2-13-maven)
3. [Frontend](#3-frontend)
   - [Vue 2.7](#31-vue-27)
   - [Vuetify 2](#32-vuetify-2)
   - [Vue Router](#33-vue-router)
   - [Axios](#34-axios)
   - [Material Design Icons](#35-material-design-icons)
   - [Vue CLI](#36-vue-cli)
   - [ESLint](#37-eslint)
4. [Infraestrutura](#4-infraestrutura)
5. [Decisões de modelagem](#5-decisões-de-modelagem)
6. [Alternativas descartadas](#6-alternativas-descartadas)

---

## 1. Panorama geral

```
┌──────────────────────────────────────────────────────────────┐
│  NAVEGADOR                                                    │
│  Vue 2 + Vuetify 2 + Vue Router + Axios                       │
└───────────────────────────┬──────────────────────────────────┘
                            │  HTTP/JSON  (CORS, Basic Auth)
┌───────────────────────────▼──────────────────────────────────┐
│  API — Spring Boot 2.7 (Java 11)                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │ controller (service/)  HTTP, validação, status code     │  │
│  ├────────────────────────────────────────────────────────┤  │
│  │ business (business/)    regras de negócio               │  │
│  ├────────────────────────────────────────────────────────┤  │
│  │ repository (repository/)JPQL do Spring Data             │  │
│  └────────────────────────────────────────────────────────┘  │
│ transversal (config/) Segurança · CORS · Auditoria · Rate limit │
└───────────────────────────┬──────────────────────────────────┘
                            │  JDBC (TLS, usuário de menor privilégio)
┌───────────────────────────▼──────────────────────────────────┐
│  PostgreSQL 16                                                 │
│  5 tabelas · PK composta · FK · CHECK · views                │
└──────────────────────────────────────────────────────────────┘
```

**A stack em uma frase:** um backend REST em Java com Spring Boot,
persistência por JPA no PostgreSQL, e um frontend SPA em Vue 2 com
Vuetify, falados por JSON/HTTP com CORS controlado.

---

## 2. Backend

### 2.1 Java 11

**O que é.** A linguagem e a plataforma. A versão 11 é **LTS** (*Long
Term Support*), com suporte estendido e atualizações de segurança
publicadas por anos.

**Por que a 11 e não a 17.**
A 17 é mais recente e melhor, mas a 11 é a versão que o Spring Boot 2.7
suporta **nativamente**, sem build nativo de imagem (`GraalVM`) e sem
`--add-opens` no Java 17. A 11 funciona em qualquer JDK instalado, o
que reduz o atrito de setup — importante num projeto avaliado por
outra pessoa.

**Onde aparece.** Todo o backend. `pom.xml`:
```xml
<java.version>11</java.version>
```

**Detalhe que vale citar.** `BigDecimal` é o tipo usado para todo
valor monetário. O motivo está detalhado em
[`LoteBO.java`](leilao-api/src/main/java/br/com/selecao/locadora/business/LoteBO.java).

---

### 2.2 Spring Boot 2.7

**O que é.** Um framework que configura a aplicação por **convenção**:
o Spring Boot detecta o que está no classpath e monta a configuração
correspondente, sem XML e sem quase nenhum `@Bean` manual.

**O que resolve.**
- **Servidor web embutido** (Tomcat) — `java -jar` e sobe o servidor.
- **Auto-configuração** — o driver do PostgreSQL está no classpath, e o
  `DataSource`, o `EntityManager` e o repositório JPA aparecem sozinhos.
- **Gerenciamento de configuração** — `application.properties` +
  perfis (`prod`, `local`) + variáveis de ambiente.
- **Verificação de saúde** — Actuator embutido.

**Onde aparece.** `LeilaoApplication` (a classe `main`), `pom.xml`.

**Detalhe importante para a entrevista.** A anotação
`@SpringBootApplication` é, na prática, três anotações:

| Anotação | O que faz |
|---|---|
| `@SpringBootConfiguration` | Marca a classe como fonte de beans |
| `@EnableAutoConfiguration` | Liga as auto-configurações |
| `@ComponentScan` | Varre o pacote em busca de componentes |

E o `@ComponentScan` começa no **pacote da classe principal**. É por
isso que a classe `LeilaoApplication` fica em `br.com.selecao.locadora`
(a raiz do projeto) e não em um subpacote: se ela estivesse acima, o
Spring varreria o computador inteiro e a aplicação não subiria.

---

### 2.3 Spring Web

**O que é.** O módulo do Spring MVC: anotações como `@RestController`,
`@GetMapping`, `@RequestBody`, `@PathVariable`, `@Valid`.

**O que resolve.** O mapeamento entre HTTP e Java.

**Padrão usado no projeto — "controller magro":**

```java
@GetMapping("/{id}")
public ResponseEntity<UnidadeResponse> buscarPorId(@PathVariable Integer id) {
    return ResponseEntity.ok(unidadeBO.buscarPorId(id));
}
```

O controller tem **três responsabilidades** e nenhuma a mais:
1. dizer qual URL e qual método HTTP correspondem a cada ação;
2. receber o JSON do corpo (`@RequestBody`);
3. devolver a resposta com o código HTTP correto.

**O que resolve.**
- A URL acepta **singular e plural** (`/unidades` e `/unidade`) — o
  enunciado é inconsistente entre os exercícios, e a resposta é aceitar
  as duas formas em vez de torcer por uma.
- O `@Valid` no `@RequestBody` faz a validação acontecer **antes** do
  código de negócio rodar.
- Os status codes são escolhidos com intenção: `201 Created` na
  inclusão, `204 No Content` na exclusão, `404` no "não encontrado",
  `400` na falha de validação, `409` na violação de regra de negócio.

---

### 2.4 Spring Data JPA e Hibernate

**O que é.** O Spring Data JPA é uma camada que **gera** as consultas a
partir do nome do método. O Hibernate é o motor que transforma
entidades Java em SQL (e de volta).

**O que resolve.** Repetição. Um repositório inteiro cabe em poucas
linhas:

```java
public interface UnidadeRepository extends JpaRepository<Unidade, Integer> {
    boolean existsByNome(String nome);
    boolean existsByNomeAndIdNot(String nome, Integer id);
    List<Lote> findByLeilaoId(Integer leilaoId);
}
```

O método `findByLeilaoId` vira, automaticamente:

```sql
SELECT * FROM tb_lote WHERE id_leilao = ?
```

**Nenhuma concatenação de valor do usuário em SQL.** É a proteção real
contra SQL Injection (OWASP A03): o driver envia os valores **separados**
do texto da consulta.

**Onde aparece.** `repository/*.java`, `entity/*.java`.

**Detalhe que vale citar — a JPQL do total do leilao.** A única consulta
escrita à mão está em `LeilaoRepository`:

```java
@Query("SELECT COALESCE(SUM(l.quantidade * l.valorInicial), 0) FROM Lote l WHERE l.leilao.id = :leilaoId")
BigDecimal calcularTotal(@Param("leilaoId") Integer leilaoId);
```

É **JPQL**, não SQL: ela consulta **entidades e atributos** (`l.quantidade`),
não tabelas e colunas (`lote.quantidade`). O Hibernate traduz para o
SQL real. Isso significa que a consulta sobrevive a uma renomeação de
coluna no banco — e que ela não depende do PostgreSQL, funcionando em
qualquer banco relacional.

**Por que o total é calculado no banco e não no Java.** Somar em SQL
custa **uma** consulta para todos os leiloes. Somar em Java exigiria
buscar todos os lotes de todos os leiloes e somar em memória — para uma
lista, é desperdício de memória e tempo. O total do **lote**, esse sim,
é calculado em Java (`LoteBO`), porque é o valor de **um** registro.

---

### 2.5 PostgreSQL 16

**O que é.** Banco de dados relacional (o mais usado do mundo em
código aberto).

**Por que ele.**
- **Integridade referencial de verdade.** As chaves estrangeiras
  (`FOREIGN KEY`) são garantidas pelo banco, não só pelo código. Se
  uma regra de negócio for burada, o banco ainda recusa o dado
  inconsistente.
- **Tipos nativos.** `NUMERIC(15,2)` para dinheiro (exato), `TIMESTAMP`
  para data, `INTEGER` para contadores.
- **Integridade de domínio.** As restrições `CHECK` garantem regras que
  nem a aplicação nem o teste precisam conferir:

```sql
quantidade INTEGER NOT NULL CHECK (quantidade > 0),
valor_inicial NUMERIC(15,2) NOT NULL CHECK (valor_inicial >= 0)
```

**Onde aparece.** `resources/sql/01_ddl.sql` (estrutura),
`resources/sql/02_dml.sql` (carga inicial), `application.properties`
(string de conexão).

**Detalhe que vale citar — o `NUMERIC`.** O tipo `NUMERIC(15,2)` é
exato: armazena o número como decimal, não como binário. Para
dinheiro, isso é obrigatório — veja a explicação do `BigDecimal` em
`LoteBO`. Um `FLOAT` no banco perderia centavos.

**Detalhe que vale citar — o usuário de menor privilégio.**
`sql/seguranca/03_permissoes.sql` cria um usuário que pode mexer
apenas nas tabelas do projeto. Sem isso, um invasor que explorasse um
SQL Injection herdaria o poder do superusuário.

---

### 2.6 Bean Validation

**O que é.** O padrão **Jakarta Bean Validation** (implementado pelo
Hibernate Validator): anotações no DTO que declaram as regras.

**O que resolve.** Validação **declarativa**, na borda da API, antes
de qualquer regra de negócio.

```java
@NotNull(message = "O leilao e obrigatorio.")
private Integer leilao;

@Size(max = 80, message = "A descricao deve ter no maximo 80 caracteres.")
private String descricao;

@Min(value = 1, message = "A quantidade deve ser maior que zero.")
private Integer quantidade;
```

**A divisão de responsabilidades no projeto:**

| Onde | O que valida | Papel |
|---|---|---|
| Bean Validation (`@Valid`) | Formato e preenchimento | **Contrato da API** |
| Bean Validation (`@Pattern`, `@Email`) | Formato (e-mail, URL) | **Contrato da API** |
| `business/` | Existência, duplicidade, coerência | **Regra de negócio** |

**Por que no backend e não só no frontend.** Porque qualquer um pode
chamar a API sem passar pela tela:

```bash
curl -X POST http://localhost:8081/unidades \
  -H "Content-Type: application/json" -d '{"nome":""}'
# → 400. O nome vazio nem chega na camada de negócio.
```

**Detalhe importante.** `@NotNull` garante que o campo foi **informado**.
Ele **não** garante que o registro exista — essa é regra de negócio, e
fica na camada `business`. A distinção é o que separa "entrada
malformada" (400) de "regra violada" (409).

---

### 2.7 Spring Security

**O que é.** O framework de segurança do Spring: autenticação,
autorização, proteção contra CSRF, headers de segurança, CORS.

**Por que ele, e não código próprio.** Porque a segurança não deve ser
escrita do zero — quem faz isso erra. O Spring Security já resolve os
casos difíceis (tempo constante na comparação de senha, proteção de
CSRF, headers padrão) e é auditado continuamente.

**O que o projeto usa dele:**

| Recurso | Para quê |
|---|---|
| `BCryptPasswordEncoder` | Codificar e conferir senhas |
| `httpBasic()` / `BasicAuthenticationFilter` | Autenticação HTTP Basic |
| `SecurityFilterChain` | Cadeia de filtros de segurança |
| `headers()` | X-Frame-Options, nosniff, HSTS, Referrer-Policy |
| `CorsConfigurationSource` | CORS restrito |
| `SessionCreationPolicy.STATELESS` | Nenhuma sessão no servidor |
| `AuthenticationEntryPoint` | Resposta 401/429 em JSON |

**Detalhe que vale citar — por que o CSRF está desativado.** CSRF
explora o fato de o navegador anexar cookies automaticamente a
requisições. Esta API **não usa cookie** (é `STATELESS`, autentica por
cabeçalho `Authorization`), então não há nada para o atacante forjar —
o CSRF é inaplicável. **Se um dia a autenticação passar a usar cookie,
o CSRF precisa ser religado** — e há um comentário no código
apontando exatamente esse ponto.

**Detalhe que vale citar — `spring-security-crypto` × `starter`.**
O projeto importa **as duas** bibliotecas, e a distinção é importante:

- `spring-security-crypto` traz **apenas** a codificação de senha. Não
  ativa autenticação em nada. É dela que vem o `BCryptPasswordEncoder`.
- `spring-boot-starter-security` traz a autenticação **completa**. É
  ela que, por padrão, protegeria todas as rotas — e é por isso que o
  projeto a usa **com a autenticação desligada por padrão**, de forma
  explícita e documentada.

**Detalhe que vale citar — o `HttpBasicConfigurer` do Spring 5.7.** Ele
expõe apenas `realmName()`, `authenticationEntryPoint()` e
`authenticationDetailsSource()`. **Não existe `successHandler()` nem
`failureHandler()`** — o handler de falha é definido no construtor do
`BasicAuthenticationFilter` e não pode ser trocado pela configuração. Por
isso o projeto estende o filtro
(`AuditedBasicAuthenticationFilter`) e sobrescreve
`onSuccessfulAuthentication` e `onUnsuccessfulAuthentication`.

---

### 2.8 BCrypt

**O que é.** Um algoritmo de hashing **especificamente feito para senha**.

**Três propriedades que importam:**

1. **Salt.** Um valor aleatório sorteado por hash e **embutido** no
   resultado. Duas empresas com a mesma senha ficam com hashes
   **diferentes**. Sem salt, um vazamento permitiria descobrir quais
   usuários compartilham senha — e testar essa senha em outros
   sistemas (as pessoas reutilizam senha).

2. **Custo configurável.** O custo 10 significa 2^10 ≈ 1024 iterações,
   ~100 ms por verificação. Um ataque de força bruta contra SHA-256
   testaria milhões de senhas por segundo; contra BCrypt, poucas.
   **O custo sai do atacante e entra no servidor**, que faz isso uma
   vez por login.

3. **Comparação em tempo constante.** O `matches()` do BCrypt não
   retorna mais cedo quando os caracteres divergem — impede que o
   atacante descubra a senha medindo o tempo de resposta.

**Onde aparece.** `config/PasswordEncoderConfig.java`,
`business/EmpresaBO.java`.

**Por que não MD5, SHA-1 ou SHA-256.** São rápidos demais para senha
(milhões de hashes por segundo numa GPU) e não têm custo ajustável.
SHA-256 com *PBKDF2* ou *Argon2* seria aceitável, mas o BCrypt já é o
padrão do Spring e resolve o problema sem configuração extra.

**A cadeia completa no projeto:**
`senha em claro → BCrypt (salt + custo 10) → hash no banco`.
A senha em claro **nunca** é gravada, **nunca** volta na resposta
(o DTO de saída não tem o campo) e **nunca** vai para o log.

---

### 2.9 Lombok

**O que é.** Um processador de anotações que **escreve código Java
durante a compilação**.

```java
@Data                       // gera getters, setters, equals, hashCode, toString
@NoArgsConstructor         // gera o construtor vazio (necessário para o Jackson)
@AllArgsConstructor    // gera o construtor com todos os campos
@Builder                   // gera o padrão Builder
```

**Por que.**
- **Menos ruído.** Uma entidade de 8 campos passa de 80 linhas para 25.
- **Consistência.** Todas as entidades têm os mesmos getters.
- **Nada de mágica em tempo de execução.** O Lombok roda no
  *build*; o bytecode gerado é Java comum, e o IDE navega entre os
  métodos gerados normalmente.

**O `@Builder` nas respostas.** O padrão *Builder* deixa o código de
montagem do DTO legível, porque não importa a ordem dos parâmetros:

```java
return LoteResponse.builder()
        .id(lote.getId())
        .numeroLote(lote.getNumeroLote())
        .totalLote(calcularTotalLote(...))
        .build();
```

**Onde aparece.** `entity/*.java`, `dto/*.java`.

---

### 2.10 SLF4J e Logback

**O que é.** SLF4J é a **fachada** de logging (uma interface única); o
Logback é a **implementação** (quem escreve no arquivo).

**Por que a fachada.** Trocar o Logback pelo Log4j sem tocar em uma
única linha do código da aplicação.

**Níveis usados no projeto:**

| Nível | Quando | Exemplo |
|---|---|---|
| `INFO` | Evento normal de negócio | `AUDITORIA \| AUTENTICACAO_CONFIRMADA` |
| `WARN` | Situação anormal, mas a aplicação segue | `AUDITORIA \| LOGIN_FALHOU` |
| `ERROR` | Falha que exige intervenção | erro de regra de negócio no `AuditTrailFilter` |

**O prefixo `AUDITORIA |`.** É um detalhe pequeno e muito prático: com
ele, o log de auditoria pode ser separado do log técnico por um simples
`grep AUDITORIA`, e encaminhado para um SIEM ou para um arquivo
diferente.

**Onde aparece.** `config/AuditTrailFilter.java`,
`config/AuditedBasicAuthenticationFilter.java`, `business/*.java`.

---

### 2.11 Actuator

**O que é.** Módulos de monitoramento que o Spring Boot expõe em
endpoints como `/actuator/health`.

**Por que.** Em um contêiner, o orquestrador (Docker, Kubernetes) lê
`/actuator/health` para decidir se a aplicação está viva. Se ela
responder "UP", o contêiner continua. Se falhar, é reiniciado.

**Detalhe de segurança que vale citar.** Esse endpoint fica **fora** do
controle de força bruta (explicado em `BruteForceProtectionFilter`): se
ele devolvesse 429 durante um ataque, o orquestrador interpretaria
"aplicação doente" e reiniciaria o contêiner — transformando um ataque
de senha em um ataque de negação de serviço.

**Configuração:** só `/actuator/health` e `/actuator/info` ficam
expostos. Os demais (beans, env, threads, heapdump) continuam
inacessíveis — o Actuator expõe endpoints sensíveis por padrão, e
esconder o resto é decisão consciente.

---

### 2.12 JUnit 5 e Mockito

**O que são.** JUnit 5 é o framework de testes unitários. O Mockito
cria **dubles** (objetos falsos) para isolar a classe testada das
suas dependências.

**A regra de ouro do projeto: nenhum teste exige banco de dados.**
Um teste com `@SpringBootTest` sobe o container inteiro e, com isso,
exige o PostgreSQL no ar — o que quebraria `mvn test` numa máquina sem
banco, por um motivo sem relação com a regra testada.

Por isso os testes de regra de negócio usam o Mockito:

```java
UnidadeRepository unidadeRepository = mock(UnidadeRepository.class);
UnidadeBO unidadeBO = new UnidadeBO();
ReflectionTestUtils.setField(unidadeBO, "unidadeRepository", unidadeRepository);
```

**A exceção (proposital):** `SegurancaIntegracaoTest` usa
`@WebMvcTest`, que sobe **apenas** a camada web (filtros, segurança,
controllers) — sem o banco. É por isso que ele consegue testar a cadeia
de autenticação inteira em milissegundos.

**Onde aparece.** `src/test/java/...`.

**Os 19 testes de segurança** (`SegurancaIntegracaoTest`,
`LoginAttemptServiceTest`, `ClientIpResolverTest`) verificam, entre
outras coisas, que a resposta de sucesso traz o **dado do controller** e
não uma mensagem de autenticação — que é o tipo de bug que só aparece
em produção.

---

### 2.13 Maven

**O que é.** O gerenciador de build e dependências do Java.

**Por que.** Padrão na industry, funciona bem com IDEs, e o
`spring-boot-starter-parent` centraliza as versões de todas as
dependências transitivas — o que evita a classe mais comum de problema
em Java: a mesma biblioteca em duas versões diferentes no classpath.

**Comandos do dia a dia:**

```bash
mvn compile              # compila
mvn test                 # roda os testes (não precisa de banco)
mvn spring-boot:run      # sobe a API
mvn package              # gera o .jar executável
```

---

## 3. Frontend

### 3.1 Vue 2.7

**O que é.** Um framework progressivo para interfaces reativas. A
ideia central é o **two-way data binding**: o dado e a tela são a mesma
coisa.

```html
<input v-model="search" />
```

O `v-model` liga o campo ao estado. Digitar atualiza o dado; o dado
mudar redesenha o campo. Sem escrever uma linha de código de
"sincronização".

**A versão 2.7 é específica.** É a **última** versão da série 2.x e
traz as *Composition API* e as *Composition Functions* — o que permite
escrever código no estilo "setup()" dentro de um projeto que, no
resto, usa a *Options API*.

**Por que Vue 2 e não Vue 3.** O *starter* fornecido pela AZ Tecnologia
é Vue 2 com Vuetify 2. Migrar para Vue 3 exigiria migrar também o
Vuetify (que chegou ao 3.x com reescrita de componentes) e reescrever
os componentes de tela. O ganho não compensava o risco dentro do
prazo da avaliação. **A decisão está documentada no código.**

**Onde aparece.** `leilao-app/src/**`.

**Conceito que vale explicar — o ciclo de vida:**

| Gancho | Quando | Uso no projeto |
|---|---|---|
| `created()` | Antes de aparecer na tela | — |
| `mounted()` | Depois que entrou na tela | `this.carregar()` — busca os dados |
| `updated()` | Depois de uma mudança reativa | — |
| `beforeDestroy()` | Antes de ser removido | — |

O `mounted` é o lugar certo para buscar dados: a tela já existe, o
usuário vê o indicador de carregamento.

---

### 3.2 Vuetify 2

**O que é.** Uma biblioteca de componentes de interface que **já vem
com o design system pronto** — botões, campos, tabelas, diálogos,
menus, notificações.

**Por que.** Escrever um sistema de formulários e tabelas do zero
consumiria o projeto inteiro. Com Vuetify, a tela de CRUD sai em
horas.

**O que o projeto usa:**

| Componente | Para quê |
|---|---|
| `v-data-table` | A "grid editável" do exercício 5 |
| `v-edit-field` | Edição **inline** (o campo vira formulário na própria linha) |
| `v-dialog` + `v-card` | O diálogo de confirmação de exclusão |
| `v-snackbar` | As notificações de sucesso/erro |
| `v-form` + `v-text-field` | Formulários com validação |
| `v-alert` | Alertas de erro do servidor |
| `v-tooltip` | Dicas nos botões de ação |
| `v-menu` / `v-navigation-drawer` / `v-app-bar` | A casca da aplicação |

**Conceito que vale explicar — o padrão "grid editável".** É a solução
canônica do Vuetify para o exercício 5, e tem **três estados** na mesma
tabela:

1. **Consulta** — mostra os dados, com os botões de editar e excluir.
2. **Edição** — a linha vira formulário (`v-edit-field`), os botões
   viram salvar e cancelar.
3. **Inclusão** — uma linha vazia aparece no topo, pronta para
   preencher.

A lista exibida é um `computed` que coloca a linha de inclusão na
frente da lista real:

```javascript
linhasExibidas() {
  if (this.novo.nome !== null) {
    return [this.novo].concat(this.unidades);
  }
  return this.unidades;
}
```

**Por que não há uma segunda tabela na tela.** O usuário nunca fica
"desorientado" sobre onde está preenchendo alguma coisa: o que está
sendo editado está sempre visível, na própria linha.

---

### 3.3 Vue Router

**O que é.** O roteador do Vue: mapeia URLs para componentes.

```javascript
{ path: '/unidades', component: Unidades },
{ path: '/empresa/:id', component: Empresa }
```

**O "modo hash".** O projeto usa `mode: 'hash'`, o que faz a URL ficar
`#/unidades` em vez de `/unidades`.

**Por que hash e não History API.** Com hash, **não é preciso configurar
servidor nenhum** para o roteamento funcionar: o servidor sempre
devolve o `index.html`, e o Vue Router lê a parte depois do `#`. Com a
History API, seria preciso configurar o servidor para reescrever
qualquer URL desconhecida para o `index.html` — e um erro nessa
configuração quebra a aplicação inteira em produção.

**O componente `<router-view/>`.** É o "buraco" onde a tela da rota
atual é encaixada. Sem ele, a URL mudaria mas o conteúdo não.

---

### 3.4 Axios

**O que é.** O cliente HTTP do navegador.

**Por que.** `fetch` funciona, mas o Axios traz o que uma aplicação de
tempo real precisa:

- **Interceptors.** É o que permite ter **um único ponto** de tratamento
  de erro. O `services/api.js` do projeto intercepta toda resposta com
  erro e transforma a resposta do backend em um objeto padronizado:

```javascript
// O frontend sempre recebe { status, mensagem, campos }
erro.mensagem   // a mensagem que o backend mandou
erro.campos     // os erros por campo (para o formulário)
```

Sem isso, cada tela precisaria decodificar o erro — e uma tela nova
esqueceria de hacerlo.

- **Timeout.** `timeout: 10000` — sem ele, uma requisição pendurada
  ficaria carregando para sempre.
- **`baseURL`.** A URL da API fica em **um** lugar
  (`VUE_APP_API_BASE_URL`), e não espalhada por 30 chamadas.

**Onde aparece.** `leilao-app/src/services/api.js`.

---

### 3.5 Material Design Icons

**O que é.** A biblioteca de ícones do Material Design (Google). São
tipographic icons: um caractere de fonte com nome semântico
(`mdi-plus`, `mdi-pencil`, `mdi-delete`).

**Por que.** Ícones consistentes, sem depender de arquivos de imagem, e
com uma fonte única — o que evita dezenas de requisições.

---

### 3.6 Vue CLI

**O que é.** A ferramenta de build do Vue.

**O que ela dá.** Servidor de desenvolvimento com *hot reload* (a tela
atualiza sozinha ao salvar o arquivo), compilação de produção,
otimização de bundle e a integração com o ESLint.

**Comandos:**

```bash
npm run serve     # servidor de desenvolvimento (porta 8080)
npm run build     # gera o bundle de produção na pasta dist/
npm run lint      # verificação estática de código
```

---

### 3.7 ESLint

**O que é.** O verificador de estilo e de erros comuns do JavaScript.

**Por que no projeto.** Ele pegou um **bug real** durante o
desenvolvimento: o template chamava `mascararCnpj(...)` e o código
definia `mascaraCnpj(...)` — nomes diferentes. A tela renderizava
vazia e o ESLint apontou.

**Configuração.** O `eslint-plugin-vue` conhece as regras do Vue e
entende a estrutura de um arquivo `.vue` (template, script, style) —
sem ele, o ESLint não saberia o que é código e o que é template.

**Regra configurada:** `plugin:vue/essential` — pega erros de template
(sem voltar a ser o *strongly recommended*, que gera ruído).

---

## 4. Infraestrutura

| Item | Escolha | Motivo |
|---|---|---|
| **Docker** | `postgres:16-alpine` | Banco pronto, sem instalar nada na máquina |
| **Docker Compose** | `docker-compose.yml` | Sobe banco + API + frontend com um comando |
| **Health check** | `pg_isready` no Compose | O Compose espera o banco ficar pronto |
| **Perfil local** | `application-local.properties` | Log detalhado do SQL, para depurar |
| **Usuário do banco** | `app_leilao` (menor privilégio) | Dano limitado em caso de invasão |
| **Processo Java** | Usuário não-root no `Dockerfile` | Reduz o estrago de uma exploração |
| **HTTPS** | Responsabilidade do proxy | TLS no proxy é o padrão (Let's Encrypt) |

**Sobre o Docker Compose:** ele sobe **o banco** (com os scripts `.sql`
da pasta `sql/` já montados em `/docker-entrypoint-initdb.d`). O
container do Postgres executa automaticamente os `.sql` que estiverem
**diretamente** nessa pasta — por isso o script de permissões fica na
subpasta `seguranca/`: ele é aplicado à mão, por opção.

---

## 5. Decisões de modelagem

### 5.1 A chave composta do comprador

```sql
CREATE TABLE tb_comprador (
    id_empresa INTEGER NOT NULL REFERENCES tb_empresa(id),
    id_leilao  INTEGER NOT NULL REFERENCES tb_leilao(id),
    PRIMARY KEY (id_empresa, id_leilao)
);
```

A tabela **não tem id próprio**: o vínculo entre empresa e leilão *é* a
identidade. Isso se reflete em toda a API (as rotas recebem dois ids) e
no mapeamento JPA (`@EmbeddedId` + `@MapsId`).

**Por que é a modelagem certa:** o vínculo "esta empresa comprou este
leilão" é único por natureza. Uma chave simples com um id sintético
permitiria o mesmo vínculo duas vezes — e o banco não impediria.

### 5.2 `NUMERIC` para dinheiro, `INTEGER` para quantidade

`NUMERIC(15,2)` armazena o valor de forma **exata**. Um `FLOAT`
armazenaria uma aproximação — e o erro se acumularia ao somar lotes.

### 5.3 Trilha de auditoria em toda tabela

`created_at` e `updated_at` em todas as tabelas, preenchidos
automaticamente por `@PrePersist` e `@PreUpdate`. Uma linha de log não
é registrada pela aplicação — o próprio banco sabe quando cada registro
nasceu e quando mudou.

### 5.4 `ON DELETE RESTRICT` nas chaves estrangeiras

Apagar um leilão com lotes vinculados é **recusado** pelo banco. É
melhor que um `CASCADE` silencioso, que apagaria dados de uma vez e
deixaria o sistema sem rastro do que aconteceu.

---

## 6. Alternativas descartadas

| Decisão | Alternativa | Por que foi descartada |
|---|---|---|
| **Java 11** | Java 17 | O Spring Boot 2.7 não dá suporte nativo; exigiria build de imagem nativa e `--add-opens`. O ganho não compensava. |
| **Vue 2.7** | Vue 3 | O *starter* é Vue 2 + Vuetify 2. Migrar exigiria reescrever os componentes e migrar o Vuetify. Risco alto dentro do prazo. |
| **Spring Data JPA** | JDBC / JdbcTemplate | Menos código para as consultas simples, mas perde o gerenciamento de transação e de *mapping*. O projeto tem 5 entidades com relacionamentos — o JPA paga o custo. |
| **HTTP Basic** | JWT | JWT exige refresh token, rotação de chaves e blacklist. HTTP Basic é o mecanismo mais simples que atende o enunciado (ligar/desligar por configuração). |
| **Spring Security** | Código próprio | Segurança escrita do zero erra. E o framework já resolve tempo constante, CSRF e headers padrão. |
| **Map em memória** | Redis | Mais simples e suficiente para uma instância. O `LoginAttemptService` está isolado para permitir a troca — está documentado na seção 4.2 do `SEGURANCA.md`. |
| **Filtro por IP no balanceador** | Rate limit na aplicação | O balanceador não conhece o usuário. O limite por usuário precisa ser da aplicação. |
| **`mode: 'hash'`** | History API | Exige configuração de servidor; um erro quebra a aplicação em produção. O modo hash funciona sem configuração nenhuma. |

---

**Documentos relacionados:** [`README.md`](README.md) ·
[`SEGURANCA.md`](SEGURANCA.md) · [`MANUAL-OPERACAO.md`](MANUAL-OPERACAO.md) ·
[`ENTREVISTA.md`](ENTREVISTA.md)
