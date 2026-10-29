# BANCO DE PERGUNTAS PARA ENTREVISTA

> Projeto **Avaliação AZ Tecnologia — Sistema de Leilões**.
>
> Este documento é um **roteiro de estudo**, não um arquivo para decorar.
> Cada resposta tem a estrutura de uma resposta **boa**: o que é, por que
> foi feito assim, e qual é o limite da solução.
>
> **A regra de ouro:** se você não sabe a resposta, diga "não sei, mas
> eu descobriria assim". Isso vale mais que uma resposta decorada e
> errada — e a segunda parte ("eu descobriria assim") mostra raciocínio.

---

## Sumário

- [Bloco 1 — Arquitetura](#bloco-1--arquitetura)
- [Bloco 2 — Segurança](#bloco-2--segurança)
- [Bloco 3 — Banco de dados](#bloco-3--banco-de-dados)
- [Bloco 4 — Frontend](#bloco-4--frontend)
- [Bloco 5 — Java e Spring](#bloco-5--java-e-spring)
- [Bloco 6 — Problemas reais encontrados](#bloco-6--problemas-reais-encontrados)
- [Bloco 7 — Evolução do projeto](#bloco-7--evolução-do-projeto)
- [Checklist final antes da entrevista](#checklist-final-antes-da-entrevista)

---

## Bloco 1 — Arquitetura

### 1. Me apresente o projeto.

> Sistema REST de gestão de leilões, com cinco entidades — unidade,
> empresa, leilão, lote e comprador — e um CRUD completo em cada uma,
> mais telas web.
>
> **Backend** em Java 11 com Spring Boot 2.7, persistência por JPA no
> PostgreSQL 16, e **frontend** em Vue 2 com Vuetify, falados por JSON
> via HTTP. São 37 testes automatizados, que rodam sem banco de dados.
>
> O ponto que eu destaco é a **segurança**: existe autenticação
> HTTP Basic, controle de força bruta e trilha de auditoria — mas
> **ligados por configuração**, desligados por padrão, porque o
> enunciado pede endpoints abertos para a avaliação.

### 2. Quais as camadas e por que?

> Três camadas, com uma responsabilidade cada:
>
> - **`service/`** (controller): HTTP. Qual URL, qual método, receber o
>   JSON, devolver o status code. Nenhuma regra de negócio.
> - **`business/`**: as regras. "O CNPJ não pode se repetir", "o
>  rollable do lote não pode ser negativo", "a empresa do comprador
>   precisa existir".
> - **`repository/`**: os dados, via Spring Data JPA.
>
> E há os **DTOs**, que são a fronteira entre o mundo HTTP e o mundo do
> banco.
>
> **Por que separar?** É o que permite trocar o banco sem mexer nas
> regras, ou expor a API em GraphQL sem mexer nas regras. E o que
> impede uma regra de negócio de ficar espalhada por 40 controllers — o
> que aconteceu no *starter* original, e foi corrigido.

### 3. Por que DTOs separados da entidade?

> Três motivos, e o primeiro é de segurança:
>
> **1. Controle do que entra.** A entidade tem `id`, `createdAt`,
> `updatedAt`. Se o controller usasse a entidade, o cliente poderia
> mandar um `id` no corpo do `POST` e o servidor aceitaria — ou seja,
> seria possível escrever em uma tabela arbitrária com um id escolhido
> pelo atacante. Com o DTO, só entram os campos declarados nele.
>
> **2. Controle do que sai.** O `EmpresaResponse` **não tem** o campo
> `senha` — nem em texto, nem o hash. A entidade inteira nunca vaza.
>
> **3. Desacoplamento.** Se o banco ganhar uma coluna interna, a
> tabela e a API não precisam mudar junto.
>
> O único ponto em que o DTO de entrada e o de saída **coincidem** é o
> `Comprador`, que é literalmente só um par de ids. Ainda assim, são
> duas classes — porque um dia o de saída pode ganhar um campo
> (a razão social, por exemplo) sem quebrar ninguém.

### 4. Qual o fluxo de uma requisição de criação?

> `POST /empresas` com o JSON no corpo:
>
> 1. **Tomcat** recebe a requisição.
> 2. **CORS** verifica a origem (se for cross-origin) e responde ao
>    *preflight* se necessário.
> 3. **Spring Security** aplica os headers de segurança e, no perfil de
>    produção, autentica.
> 4. **DispatcherServlet** do Spring MVC localiza o método do
>    controller pela URL e pelo método HTTP.
> 5. **Jackson** desserializa o JSON no `EmpresaRequest`.
> 6. **`@Valid`** dispara o Bean Validation. Se algo estiver errado,
>    para aqui: HTTP 400, com a lista de erros por campo.
> 7. O **controller** chama `empresaBO.salvar(request, null)`.
> 8. A **camada de negócio** normaliza os dados (tira a pontuação do
>    CNPJ), verifica se o CNPJ já existe, **hasheia a senha** e grava.
> 9. A **transação** commita.
> 10. O controller devolve **HTTP 201** com o `EmpresaResponse`.

### 5. Por que o controller não acessa o repositório direto?

> Porque a regra de negócio ficaria na camada HTTP. Pense no que
> acontece com a **normalização do CNPJ**: se ela estivesse no
> controller, uma futura rota (um importador, uma integração) poderia
> gravar o CNPJ sem normalizar — e o banco passaria a ter o mesmo CNPJ
> escrito de dois jeitos, quebrando a restrição de unicidade.

### 6. Tem testes? Como eles são feitos?

> Sim, 36 — e nenhum exige banco de dados. Esse é um requisito
> deliberado: se os testes precisassem do PostgreSQL, `mvn test`
> quebraria numa máquina sem banco, e o avaliador poderia achar que o
> projeto está quebrado.
>
> As regras de negócio são testadas com **Mockito**: crio um *duble*
> do repositório, monto o BO na mão e verifico o comportamento. Os
> testes de segurança usam `@WebMvcTest`, que sobe só a camada web
> (filtros, segurança, controllers) — sem o banco.
>
> Um teste que eu achei especialmente importante é o que verifica que
> **a resposta de sucesso traz o dado do controller** e não uma
> mensagem de autenticação. Explico o problema no bloco 6.

---

## Bloco 2 — Segurança

### 7. O que você fez em segurança?

> Vou organized em camadas, do dado para a aplicação:
>
> **1. O dado.** Senha nunca em texto puro: BCrypt com salt e custo 10.
> O hash tem o salt embutido, então duas empresas com a mesma senha
> ficam com hashes diferentes.
>
> **2. A entrada.** Validação no servidor com Bean Validation. O
> frontend valida para dar experiência; o backend valida para dar
> **garantia**, porque qualquer um pode chamar a API sem passar pela
> tela.
>
> **3. A consulta.** Nenhuma concatenação de SQL. Tudo via Spring Data
> JPA, que gera consultas parametrizadas.
>
> **4. O acesso.** Autenticação HTTP Basic, habilitada por perfil de
> produção.
>
> **5. O ataque.** Controle de força bruta: 5 falhas por usuário e 20
> por IP, com janela de 15 minutos. Bloqueio por IP acontece **antes**
> de validar a senha.
>
> **6. O rastreio.** Trilha de auditoria das autenticações e de todas
> as operações que alteram dados.
>
> **7. A superfície.** CORS fechado, headers de segurança, Actuator
> expondo só o health check, segredos por variável de ambiente, e o
> usuário de banco com menor privilégio.
>
> E, tão importante quanto o que **tem**: documentei explicitamente o
> que **não** tem. Está no `SEGURANCA.md`, seção 4.

### 8. Explique o controle de força bruta.

> Tem **duas dimensões**, e as duas são necessárias.
>
> O limite por **usuário** (5 falhas) protege a conta: impede que
> alguém descubra a senha de um usuário específico.
>
> O limite por **IP** (20 falhas, somando todos os usuários) protege a
> **aplicação**. Sem ele, um atacante poderia tentar 5 senhas para
> 1.000 usuários diferentes a partir do mesmo IP — e o limite
> individual nunca seria estourado. O servidor pagaria mil e poucas
> verificações de BCrypt (cada uma com ~100 ms).
>
> Um detalhe que dá qualidade à implementação: o filtro de bloqueio
> roda **antes** do filtro de autenticação. Se rodasse depois, o
> atacante continuaria gastando CPU com o BCrypt mesmo com a conta
> bloqueada — a defesa existiria, mas não serviria para nada.
>
> E: a conta bloqueada responde **429**, com o cabeçalho `Retry-After`
> dizendo quantos segundos faltam. Não é só um "erro" — é uma
> resposta que respeita o cliente.

### 9. Como você impede enumeração de usuários?

> A API devolve **exatamente a mesma resposta** — byte a byte — para
> "usuário não encontrado" e "senha incorreta":
>
> ```json
> { "status": 401, "erro": "Nao autorizado", "mensagem": "Credenciais invalidas." }
> ```
>
> Se as mensagens fossem diferentes, o atacante montaria uma lista de
> usuários válidos e só precisaria adivinhar as senhas. É o item
> A07 da OWASP.
>
> O motivo real da falha continua no log do servidor, onde só a TI
> chega. E isso está coberto por um teste automatizado, que compara o
> corpo das duas respostas.

### 10. Por que HTTP Basic e não JWT?

> Foi uma decisão guiada pelo **enunciado**: a avaliação pede
> endpoints abertos por padrão e a possibilidade de ligar a segurança
> sem reescrever o código.
>
> HTTP Basic é o mecanismo mais simples que atende a isso — liga e
> desliga com uma variável de ambiente. JWT exigiria refresh token,
> rotação de chaves e uma blacklist de tokens revogados — três peças
> que só fariam sentido com mais de um cliente e mais de uma
> expiração.
>
> **As desvantagens do HTTP Basic, que eu sei e assumo:** a senha é
> reenviada a cada requisição (por isso **exige TLS**, sem exceção),
> e não existe revogação — trocar a senha é o jeito de "deslogar
> todo mundo".
>
> Se eu fosse evoluir, migraria para **OAuth2 com OpenID Connect**,
> usando um provedor de identidade. Aí o token tem expiração curta, o
> *refresh token* permite revogação, e eu não guardo senha do usuário
> no meu banco.

### 11. Por que o CSRF está desativado? Não é uma falha?

> Não, porque a API **não usa cookie** — ela é `STATELESS` e autentica
> por cabeçalho `Authorization`.
>
> CSRF explora o fato de o **navegador** anexar cookies automaticamente
> a requisições feitas por uma página de terceiro. Sem cookie, não há
> nada para o atacante forjar.
>
> **A condição para religar:** se um dia a autenticação passar a usar
> cookie, o CSRF **precisa** voltar — e há um comentário no código,
> exatamente no lugar, apontando isso. É o tipo de decisão que precisa
> estar documentada, senão o próximo devolve um cookie e esquece do
> CSRF.

### 12. Como você protege o banco de dados?

> Em três níveis.
>
> **1. Credencial.** A aplicação não usa o superusuário. O script
> `sql/seguranca/03_permissoes.sql` cria um usuário que pode ler e
> gravar **apenas** nas tabelas do projeto — não pode derrubar o
> banco, alterar a estrutura, criar usuários nem ler outros bancos.
> Sem isso, um invasor que explorasse um SQL Injection herdaria todo
> esse poder.
>
> **2. SQL parametrizado.** Nenhuma concatenação. O driver envia os
> valores separados do texto da consulta.
>
> **3. Integridade no próprio banco.** As foreign keys, os `CHECK` e a
> chave composta garantem as regras que nem a aplicação nem os testes
> precisam conferir.

### 13. O que você **não** protege?

> Essa é a pergunta que mais importa responder bem. O `SEGURANCA.md`
> tem uma seção inteira para isso. As lacunas que eu assumo:
>
> - **Não há RBAC.** Com a autenticação ligada, qualquer usuário
>   autenticado acessa todas as rotas. Não existe "administrador".
> - **O rate limit vive na memória do processo.** Com 3 instâncias, o
>   atacante ganha 3× o limite; e um restart zera. A correção seria
>   Redis — e o `LoginAttemptService` está isolado para permitir a
>   troca sem mexer em mais nenhuma classe.
> - **Não há TLS na aplicação.** É responsabilidade do proxy. Com HTTP
>   Basic sem TLS, a senha viaja em Base64 — que não é criptografia.
> - **Não há verificação automática de CVEs** no pipeline.
> - **Não há alerta automático** de incidente: o log registra, mas
>   ninguém observa.
> - **Não há rate limiting nas rotas de negócio**, só na autenticação.

### 14. Se um usuáriodigita a senha 5 vezes errada e a 6ª vez certa?

> A 6ª resposta é **429**, com `Retry-After`. Ele tem de esperar a
> janela expirar (15 minutos por padrão).
>
> É uma decisão de projeto que vale explicar: eu **poderia** deixar o
> login correto passar, dizendo "ok, você errou, mas agora acertou".
> Escolhi não fazer, por dois motivos. O primeiro é que a resposta
> seria ambígua para o cliente. O segundo é que um atacante que
> descobriu a senha exata no 6º palpite receberia **exatamente a mesma
> resposta** de um usuário legítimo — a diferença de comportamento
> entregaria a senha. Mantendo o bloqueio, o atacante
> recebe 429, e um usuário legítimo recebe 429. Não há diferença
> observável.
>
> Depois da janela expirar, o contador zera e o login funciona
> normalmente.

### 15. Como você audita?

> Duas trilhas, com **metadados apenas** — nunca o corpo da requisição.
>
> A de **autenticação**: sucesso, falha e bloqueio, com usuário, IP e
> motivo.
>
> A de **dados**: um filtro que registra `POST`, `PUT`, `PATCH` e
> `DELETE` com método, caminho, usuário, IP, status e duração.
>
> **O detalhe mais importante:** o corpo da requisição **não** é
> registrado. Seria fácil registrar — e seria um erro grave, porque o
> cadastro de empresa recebe o campo `senha` no corpo. Se o log
> guardasse o corpo, a senha em texto claro passaria a estar gravada
> num arquivo de log: um lugar muito mais acessível e muito menos
> protegido que o banco de dados.
>
> As linhas de log têm prefixo `AUDITORIA |`, o que permite separá-las
> do log técnico com um `grep` e encaminhá-las para um SIEM.

### 16. E o "menor privilégio" no banco, como você aplicou?

> Um script SQL opcional, em `sql/seguranca/03_permissoes.sql`. Ele
> fica em uma **subpasta** de propósito: o container do Postgres
> executa automaticamente apenas os `.sql` que estão **diretamente** em
> `/docker-entrypoint-initdb.d` — ele não entra em subpastas. Se o
> script fosse executado automaticamente, ele criaria um usuário a cada
> `docker compose up`, e a segunda vez falharia.
>
> Então ele é aplicado à mão, por opção, e está documentado no
> `MANUAL-OPERACAO.md`.

---

## Bloco 3 — Banco de dados

### 17. Quais as cinco entidades e como elas se relacionam?

> - **Unidade** — o local físico onde o leilão acontece. Não tem
>   relacionamento.
> - **Empresa** — vendedora ou compradora. Também não tem
>   relacionamento direto.
> - **Leilão** — tem um vendedor (Empresa) e vários **Lotes**.
> - **Lote** — o item leiloado: quantidade, valor inicial, unidade de
>   medida. Pertencece a um Leilão.
> - **Comprador** — tabela de **relacionamento** entre Empresa e
>   Leilão. É o papel de compradora.
>
> E o total do leilão é derivado: `SUM(lote.quantidade ×
> lote.valor_inicial)` dos lotes daquele leilão.

### 18. Por que o comprador não tem id próprio?

> Porque o vínculo **é** a identidade. A chave primária é composta:
> `(id_empresa, id_leilao)`.
>
> Isso é a modelagem correta, porque o vínculo "esta empresa comprou
> este leilão" é único **por natureza**. Com uma chave simples e um id
> sintético, o mesmo vínculo poderia ser inserido duas vezes — e o banco
> não impediria.
>
> O reflexo disso em toda a API: as rotas de comprador recebem **dois**
> ids, e o mapeamento JPA usa `@EmbeddedId` com `@MapsId`.

### 19. Por que `NUMERIC` para dinheiro e não `DOUBLE`?

> Porque `DOUBLE` não representa 0,1 de forma exata — é binário, e
> 0,1 em binário é uma dízima infinita. O computer guarda o valor mais
> próximo, e o erro **se acumula** ao somar.
>
> O caso clássico:
>
> ```java
> System.out.println(0.1 + 0.2);   // 0.30000000000000004
> ```
>
> Em dinheiro isso não é "imprecisão aceitável" — é dinheiro a mais ou
> a menos. Por isso o projeto usa `BigDecimal` no Java e `NUMERIC(15,2)`
> no Postgres, e os dois são exatos.
>
> Um detalhe do `BigDecimal` que eu sempre aponto: **não se usa o
> operador `*`**. Se usa `.multiply()`, que exige que os dois lados
> sejam `BigDecimal`. Isso é proposital — se um dos lados for um
> `double`, o Java impede a operação, obrigando a conversão
> explícita e impedindo que o erro entre sorrateiramente.
>
> E tem a segunda regra: sempre `BigDecimal.valueOf(x)`, nunca
> `new BigDecimal(x)`. Com `double`, o `new BigDecimal` guardaria o
> **mesmo erro**, só que escondido dentro de um `BigDecimal`.

### 20. Onde o total do leilão é calculado? Por quê?

> No **banco**, por uma JPQL agregada:
>
> ```java
> @Query("SELECT COALESCE(SUM(l.quantidade * l.valorInicial), 0) FROM Lote l WHERE l.leilao.id = :leilaoId")
> ```
>
> Por performance: somar em SQL custa **uma** consulta para todos os
> leiloes; somar em Java exigiria buscar todos os lotes de todos os
> leiloes e somar em memória.
>
> E por coerência: é uma **JPQL**, não SQL — ela consulta entidades e
> atributos (`l.quantidade`), não tabelas e colunas
> (`lote.quantidade`). O Hibernate traduz. Isso significa que a
> consulta sobrevive a uma renomeação de coluna e não depende do
> PostgreSQL.
>
> Já o total do **lote** é calculado em Java, porque é o valor de **um**
> registro — não há ganho em ir ao banco para isso.

### 21. O que são as constraints `CHECK`?

> Regras de integridade garantidas **pelo banco**, e não só pelo
> código:
>
> ```sql
> quantidade INTEGER NOT NULL CHECK (quantidade > 0),
> valor_inicial NUMERIC(15,2) NOT NULL CHECK (valor_inicial >= 0)
> ```
>
> São uma **terceira** camada de defesa, depois do frontend e do
> backend. Se um bug passar pelas duas primeiras, ou se alguém inserir
> direto no banco, o dado inválido ainda é recusado.
>
> É a ideia de "defesa em profundidade": nenhuma proteção depende
> exclusivamente da outra.

### 22. E as views do banco?

> O projeto cria views de consulta que previsibilizam o total do
>ossary. Vale a pena saber o motivo: o `SUM` do valor inicial ×
> quantidade é uma regra de negócio, e materializá-lo numa view deixa
> a regra **em um único lugar** — e consultá-la é mais barato do que
> recalcular o agregado a cada listagem.

### 23. Como você garante que os dados iniciais batem?

> A massa de dados está em `02_dml.sql`, e é **idempotente** — pode
> rodar de novo sem duplicar nada.
>
> E há um detalhe que me agrada: as senhas da massa de dados são
> **hashes BCrypt reais**, gerados por um teste automatizado
> (`GeradorHashSenhaTest`), e não valores inventados. Ou seja, dá para
> **entrar com a senha real** em qualquer tela que exija login — e a
> validação funciona de verdade.

---

## Bloco 4 — Frontend

### 24. Me explica a tela de unidades. Por que ela é interessante?

> Porque é o **exercício 5**, e porque resolve um problema de UX de um
> jeito elegante: **uma única tabela com três estados**.
>
> 1. **Consulta** — mostra os dados, com os botões de editar e
>    excluir.
> 2. **Edição** — ao clicar no lápis, a linha vira formulário
>    (com `v-edit-field`), e os botões viram salvar e cancelar.
> 3. **Inclusão** — ao clicar em "Nova unidade", uma linha em branco
>    aparece **no topo**, pronta para preencher.
>
> A implementação é um `computed` que coloca a linha de inclusão na
> frente da lista real:
>
> ```javascript
> linhasExibidas() {
>   if (this.novo.nome !== null) {
>     return [this.novo].concat(this.unidades);
>   }
>   return this.unidades;
> }
> ```
>
> **Por que isso é melhor do que duas tabelas:** o usuário nunca fica
> desorientado sobre onde está preenchendo alguma coisa. O que está
> sendo editado está sempre visível, na própria linha.

### 25. Por que a edição acontece numa cópia do objeto?

> Porque, se eu editasse o objeto original, o texto mudaria na tela
> ao digitar. Se o usuário clicasse em "cancelar", o valor original já
> teria sumido — e não haveria como voltar atrás sem recarregar a
> página.
>
> Com a cópia, o cancelamento é "esquecer a cópia": o estado original
> nunca foi tocado. É o mesmo padrão de qualquer formulário.

### 26. Como você centraliza as mensagens de sucesso e erro?

> Num componente único (`AppNotificacoes`), que usa o `v-snackbar` do
> Vuetify, e que vive no `App.vue` — a raiz da aplicação.
>
> Três motivos:
>
> 1. **Consistência** — toda mensagem do sistema tem o mesmo formato,
>    a mesma posição e a mesma duração. O usuário aprende a ler em uma
>    tela e aplica o mesmo nas outras.
> 2. **Um só elemento na tela** — se cada tela tivesse o seu, existiriam
>    vários snackbars empilhados, e qual apareceria por cima seria
>    imprevisível.
> 3. **Separação de responsabilidades** — quem mostra a mensagem não é
>    quem decide o que aconteceu.
>
> As telas acessam por `$root.$refs.notificacoes`. Reconheço que o
> ideal seria **provide/inject** (que já está configurado no `App.vue`),
> e em um projeto maior eu migraria. Para este tamanho, os dois
> convivem, e a transição está documentada.

### 27. Como o frontend trata os erros da API?

> Num **interceptor** do Axios, em `services/api.js`. Ele intercepta
> toda resposta com erro e transforma no **mesmo formato** que o
> `GlobalExceptionHandler` do backend produz:
>
> ```javascript
> erro.status    // 400, 404, 409, 429...
> erro.mensagem  // a mensagem que o backend mandou
> erro.campos    // os erros por campo, para o formulário
> ```
>
> Assim, o frontend tem **uma única regra** de leitura de erro, e uma
> tela nova não tem como "esquecer" de tratar o erro. Também evitamos
> o CORS no caso de erro, porque a resposta é gerada no servidor.

### 28. E o CORS? Como está configurado?

> Em `CorsConfig`, com uma **lista explícita** de origens
> permitidas, vindas de `app.cors.origins`. Em desenvolvimento são
> `http://localhost:8080` e `http://127.0.0.1:8080`.
>
> **Não é `*`**, e não é `*` com `allowCredentials(true)` — essa
> combinação é recusada pelo navegador de propósito, e forçá-la seria
> um erro clássico.
>
> E há uma decisão importante: **o CORS não é mecanismo de
> segurança**. Ele só diz ao navegador se a página de outra origem pode
> ler a resposta. Um atacante com `curl` não é barrado por ele. A
> proteção real é a autenticação. Por isso, no perfil de produção, o
> CORS **não** é a barreira — a autenticação é.

### 29. Por que hash routing e não History API?

> Porque **não exige configuração de servidor**. Com hash, a URL fica
> `#/unidades` e o servidor sempre devolve o `index.html` — qualquer
> endereço funciona.
>
> Com a History API, seria preciso configurar o servidor para
> reescrever toda URL desconhecida para o `index.html`. Um erro nessa
> configuração — e ela é chata de acertar em Nginx, Apache e
> balanceadores — **quebra a aplicação inteira em produção**, enquanto
> em desenvolvimento tudo funciona. Eu prefiro o modo que não tem
> essa armadilha.

### 30. Como o frontend valida, e por que o backend valida de novo?

> São **duas camadas com papéis diferentes**, e essa é uma das
> coisas que eu mais explico.
>
> O **frontend** valida para dar **experiência**: o usuário vê o erro
> no campo, na hora, sem perder o que já digitou e sem gastar uma ida
> ao servidor.
>
> O **backend** valida para dar **garantia**: é o único lugar que pode
> garantir a regra, porque qualquer um pode chamar a API direto, sem
> passar pela tela:
>
> ```bash
> curl -X POST http://localhost:8081/unidades \
>   -H "Content-Type: application/json" -d '{"nome":""}'
> # → 400
> ```
>
> O frontend é conveniência. O backend é garantia. **As duas regras
> precisam espelhar** — e é por isso que o `EmpresaRequest.java` e o
> `utils/mascaras.js` do frontend precisam ser lidos juntos.

### 31. O que é o Vuex aqui? Por que está vazio?

> O Vuex é a biblioteca de estado global do Vue. Ele existe para
> resolver o caso em que **duas telas precisam do mesmo dado** — sem
> ele, uma tela altera o dado e a outra continua mostrando a versão
> antiga, e alguém precisa avisar.
>
> Aqui o store está **vazio, de propósito**. Nenhuma tela precisa do
> dado de outra: cada uma carrega os seus da API e trabalha localmente.
> Não existe "usuário logado" global (a API é aberta por padrão), nem
> carrinho, nem preferência global.
>
> Manter o store vazio é uma **decisão**, não uma esquecimento. Criar
> um store "caso someday" é o primeiro passo para uma aplicação com
> estado inconsistente e bugs impossíveis de reproduzir. O arquivo
> está documentado com os dois cenários em que ele ganharia conteúdo
> (autenticação no frontend e filtros globais).

---

## Bloco 5 — Java e Spring

### 32. O que é inversão de dependência, na prática?

> Na prática, é isso:
>
> ```java
> @Service
> public class EmpresaBO {
>     @Autowired
>     private EmpresaRepository empresaRepository;
> }
> ```
>
> A classe **não** sabe de onde veio o repositório. Quem entrega é o
> container do Spring, que varre o pacote, vê o `@Service`, vê o
> `@Repository`, e monta o grafo.
>
> O ganho concreto: `EmpresaBO` pode ser testada **sem** o Spring e
> **sem** o banco, recebendo um duble pelo `ReflectionTestUtils`. É
> exatamente o que os 37 testes fazem.

### 33. O que é o `Optional` e por que `orElseThrow`?

> O `Optional` representa um valor que **pode ou não existir** — o
> `findById` do Spring Data devolve um `Optional`, não `null`.
>
> A alternativa ao `orElseThrow` seria devolver `null`, e o problema
> do `null` é bem conhecido: ele se espalha. Cada chamador precisaria
> checar `if (x == null)`, e um `null` esquecido vira
> `NullPointerException` em algum lugar distante.
>
> Com `orElseThrow`, eu **transformo a ausência em uma exceção de
> negócio** (`RegistroNaoEncontradoException`), que o
> `GlobalExceptionHandler` já sabe converter em HTTP 404. A
> informação de "não encontrado" vira um **caminho explícito**,
>e nao um `null` silencioso.

### 34. O que é o `@Transactional` e quando ele importa?

> O `@Transactional` marca o método como uma **unidade de trabalho**:
> tudo que acontece dentro dele é confirmado junto, ou desfeito junto.
>
> O caso concreto do projeto é o **comprador**. Alterar o vínculo
> significa trocar a chave primária — tecnicamente, apagar o antigo e
> criar o novo. **Sem transação**, se a criação falhasse, o vínculo
> antigo já teria sumido e o novo não existiria: perda de dados sem
> possibilidade de recuperação. Com transação, é tudo ou nada.
>
> E há o detalhe do **`saveAndFlush`**: o `save` normal pode apenas
> *agendar* a gravação para o fim da transação. O `flush` força o SQL
> agora — e é o que garante que o `@PreUpdate` (que preenche o
> `updated_at`) já tenha rodado antes de montarmos a resposta, para
> ela não vir com a data de alteração vazia.

### 35. Fale do tratamento de erros global.

> Existe uma classe só (`GlobalExceptionHandler`) com `@RestControllerAdvice`,
> que trata **todas** as exceções da aplicação e devolve sempre o
> **mesmo formato** de JSON:
>
> ```json
> { "status": 409, "erro": "Conflito de negocio", "mensagem": "...", "campos": {} }
> ```
>
> O ganho é que o frontend tem **uma regra** de leitura de erro. E o
> ganho de segurança é que **nunca** vaza stack trace: o `message` do
> erro interno nunca chega ao cliente — o log do servidor registra o
> detalhe, e a resposta devolve uma mensagem genérica.
>
> As exceções de negócio (`RegraNegocioException`,
> `RegistroNaoEncontradoException`) viram **409** e **404**. As de
> validação do Bean Validation viram **400**, com a lista por campo.

### 36. Por que você não usa Java 17?

> Porque o Spring Boot 2.7 **não dá suporte nativo** ao Java 17. Para
> funcionar, exige `--add-opens` no `MAVEN_OPTS` para abrir módulos do
> JDK a reflexão, e o build nativo de imagem exigiria configuração extra.
>
> O Java 11 é LTS, funciona em qualquer JDK instalado, e **não exige
> nenhuma configuração**. Num projeto avaliado por outra pessoa, em
> máquina desconhecida, reduzir atrito de setup vale mais que a versão
> mais nova da linguagem.

### 37. E Vue 3, por que não?

> Porque o *starter* fornecido é **Vue 2 com Vuetify 2**. Migrar para
> o Vue 3 exigiria migrar também o Vuetify (que só chegou ao 3.x com
> uma reescrita de componentes) e reescrever as telas. O **ganho não
> compensava o risco** dentro do prazo da avaliação.
>
> Detalhe interessante: a versão **2.7** é a última da série 2.x e já
> traz a Composition API — dá para escrever no estilo `setup()` dentro
> de um projeto que, no resto, usa a Options API. É uma ponte útil.

---

## Bloco 6 — Problemas reais encontrados

> Esta é a parte que mais valoriza uma entrevista. Quem só responde
> "não tive dificuldade" não demonstra nada. Quem **conta o problema, a
> investigação e a correção** demonstra método.

### 38. Qual foi o problema mais difícil que você encontrou?

> **A resposta de autenticação estava sendo sobrescrita.**
>
> O sintoma: no perfil de produção, uma requisição com credenciais
> erradas devolvia **401**, mesmo deveria devolver **429** quando a
> conta já estava bloqueada por excesso de tentativas. E o corpo da
> resposta tinha **dois JSONs colados**.
>
> **A investigação.** Fui instrumentando por etapas: imprimi o contador
> de tentativas depois de cada requisição (ele estava correto — 1, 2,
> 3), e o "usuarioBloqueado" devolvia `true` na quarta tentativa (também
> correto). Ou seja: a **regra de negócio** estava certa. O problema era
> na camada HTTP.
>
> Aí veio a pista decisiva: o corpo tinha `{...429...}{...401...}`. Dois
> escritores na mesma resposta. Marquei temporariamente o JSON do
> *entry point* com um texto diferente e o corpo passou a mostrar
> `{...429...}{"erro":"ENTRADA-POINT-JSON"...}` — o que identificou o
> segundo escritor.
>
> **A causa raiz.** No Spring Security 5.7, o `BasicAuthenticationFilter`
> **não interrompe** a cadeia de filtros quando recusa uma credencial.
> Ele chama `onUnsuccessfulAuthentication` e a requisição **continua**.
> Mais adiante, o `FilterSecurityInterceptor` recusa (não há usuário
> autenticado) e o `ExceptionTranslationFilter` chama o
> `AuthenticationEntryPoint` — que escreve **401 por cima** do 429 que o
> handler de falha já tinha escrito.
>
> **A correção.** inverted the responsibility: o handler de falha
> passou a **não escrever na resposta**. Ele apenas registra a
> tentativa, conta a falha e **marca a requisição** com o resultado
> (`request.setAttribute(...)`). O `AuthenticationEntryPoint` lê essa
> marca e escreve **401 ou 429** — ficando com **um único escritor** de
> resposta.
>
> **O que aprendi.** É um bug que **só aparece em produção**: em
> desenvolvimento a autenticação está desligada e o código nem roda.
> E é o tipo de bug que nenhum teste de unidade pega — porque o
> problema não está em nenhuma classe isolada, está na **emenda** entre
> os filtros. Por isso escrevi um teste de integração que verifica
> justamente esse cenário.

### 39. Teve algum outro problema?

> Sim, dois menores, mas que valem porque mostram um método diferente:
>
> **O `Retry-After` saía zerado.** O código que montava o cabeçalho
> fazia `(segundos instanceof Integer)`, mas o valor era um `long` — que
> o Java empacota como `Long`, não `Integer`. O `instanceof` falhava,
> e o cabeçalho saía `Retry-After: 0`, dizendo ao cliente "tente de
> novo agora" — o oposto do que queríamos.
>
> **O limite de tentativas contava uma tentativa a menos.** Eu verificava
> se a conta estava bloqueada **depois** de registrar a falha. Com
> limite 5, a quinta tentativa já recebia 429 — ou seja, o usuário
> tinha só **4 palpites**, não 5. A correção foi inverter a ordem:
> verificar **antes** de registrar. Agora a quinta tentativa recebe
> 401 e a sexta recebe 429 — exatamente cinco tentativas.
>
> Os dois foram encontrados **escrevendo o teste antes de confiar no
> código**. No caso do `Retry-After`, o teste live com `curl` foi o que
> mostrou o `0`.

### 40. O que o ESLint pegou no seu projeto?

> Um bug real de nome: o template chamava `mascararCnpj(...)` e o
> código definia `mascaraCnpj(...)`. A tela renderizava vazia, e o
> ESLint — que conhece a estrutura do `.vue` — apontou antes de eu
> subir a tela.

---

## Bloco 7 — Evolução do projeto

### 41. O que você faria com mais tempo?

> Em ordem de prioridade:
>
> **1. Autorização por perfil (RBAC).** É a lacuna mais visível. Com
> `@PreAuthorize("hasRole('ADMIN')")` nos métodos e um
> `GrantedAuthority` no `UserDetailsService`, a mudança seria pequena.
>
> **2. Rate limit no Redis.** O `LoginAttemptService` está isolado —
> trocar o `Map` por um `RedisTemplate` com TTL resolveria o problema de
> múltiplas instâncias, e **nenhuma outra classe mudaria**.
>
> **3. Testes de integração com banco de verdade**, com Testcontainers
> — o container sobe um Postgres descartável, e os testes validam a
> JPQL do total e as constraints do banco.
>
> **4. OpenAPI/Swagger**, para documentar o contrato da API de forma
> executável.
>
> **5. Alertas de segurança** — contagem de falhas de login no log,
> com notificação.

### 42. E se o sistema crescesse?

> A primeira coisa seria **extrair o estado compartilhado para o
> Vuex** — que já está configurado, esperando o momento em que
> duas telas precisarem do mesmo dado (por exemplo, o usuário
> autenticado).
>
> Depois, **paginação server-side**. Hoje o filtro e a paginação são
> client side, o que é ótimo para uma lista de 20 registros e
> inviável para 20.000.
>
> E **cache** para as consultas de leitura, com o Spring Cache e o
> Redis.

### 43. Por que o projeto não usa microserviços?

> Porque seria **over-engineering** para o escopo. O enunciado pede
> cinco tabelas e um CRUD. Um microsserviço por tabela custaria: 5
> processos para subir, 5 bancos ou 1 banco com 5 schemas, comunicação
> entre eles com serialização e tratamento de falha, e um
> *orquestrador* para subir tudo.
>
> Em troca, o que a entrega realmente pede — **um sistema que funcione
> e seja fácil de avaliar** — seria muito mais difícil. A arquitetura
> em camadas que eu usei **não impede** a evolução para microserviços
> depois: as fronteiras já estãoimplícitas na separação
> service/business/repository. Se um dia um serviço precisar virar
> independente, ele já está isolado.

### 44. O que você faria diferente, olhando para trás?

> **Teria escrito os testes de integração de segurança antes.** O bug do
> 401 sobrescrevendo o 429 só apareceu porque escrevi o teste depois.
> Se eu tivesse escrito o teste **antes** de implementar o controle de
> força bruta, ele teria falhado desde o primeiro dia — e eu teria
> economizado a investigação.
>
> E teria usado **uma exceção de domínio própria** ("conta bloqueada")
> em vez de reaproveitar "credenciais inválidas" e differentiate
> pelo contador. A exceção própria deixa a intenção explícita no código
> e na API, e é mais fácil de evoluir.

---

## Checklist final antes da entrevista

### O que revisar na véspera

- [ ] Rodar `mvn test` e `npm run build` — chegar com tudo verde.
- [ ] Rodar `node scripts/verificar-higiene.js` — nada de sujeira no texto.
- [ ] Reler [`SEGURANCA.md`](SEGURANCA.md) seção 4 (o que **não** é
      protegido) e a seção 3 (os controles).
- [ ] Reler [`TECNOLOGIAS.md`](TECNOLOGIAS.md) seção 6 (alternativas
      descartadas).
- [ ] Reler as respostas dos **blocos 6** (problemas reais) — são as
      mais importantes para a sua avaliação.

### As 5 respostas que você TEM que saber de cor

1. **Como o projeto está dividido?** — service / business / repository +
   DTOs, e o porquê de cada separação.
2. **Como a senha é protegida?** — BCrypt com salt e custo 10, e o porquê
   de cada uma dessas propriedades.
3. **O que o controle de força bruta faz?** — dois limites (usuário e
   IP), por que dois, e por que o de IP roda antes da autenticação.
4. **O que você **não** protege?** — RBAC, Redis, TLS, CVEs, alertas.
5. **Qual foi o problema mais difícil?** — a resposta sobrescrita no 401
   vs 429, e o que isso ensinou sobre testes de integração.

### Frases que ajudam a soar seguro (e são verdadeiras)

- "O frontend valida para **experiência**; o backend valida para
  **garantia**."
- "A resposta é sempre a mesma, para não permitir **enumeração de
  usuários**."
- "O total do **lote** é calculado em Java; o do **leilão**, no banco —
  porque a granularidade é diferente."
- "CORS não é segurança — é uma restrição do **navegador**. A
  autenticação é a barreira de verdade."
- "Não registro o **corpo** da requisição no log, porque ele carrega a
  senha."
- "O CSRF está desligado **justamente** porque a API não usa cookie —
  e há um comentário no código para lembrar de religá-lo se isso mudar."

### Frases que DEVEM ser evitadas

- ❌ "Está tudo pronto e funcionando." → não é verdade, e a seção 4 do
  `SEGURANCA.md` prova o contrário.
- ❌ "Usei o bcrypt porque é mais seguro." → o *porquê* (salt, custo,
  comparação em tempo constante) é o que vale.
- ❌ "Usei microservices." → o projeto usa uma arquitetura em camadas.
- ❌ "Não tive dificuldade nenhuma." → todo projeto tem dificuldade; e a
  honestidade aqui é um diferencial.

---

**Documentos relacionados:** [`README.md`](README.md) ·
[`SEGURANCA.md`](SEGURANCA.md) · [`TECNOLOGIAS.md`](TECNOLOGIAS.md) ·
[`MANUAL-OPERACAO.md`](MANUAL-OPERACAO.md)
