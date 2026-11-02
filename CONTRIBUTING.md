# Como contribuir

Este repositório é uma avaliação técnica. O código está aberto para
leitura — e as correções são bem-vindas.

## O que é o projeto

Um sistema de gestão de leilões, em três camadas:

| Camada | Onde | O que é |
|---|---|---|
| Banco | `leilao-api/src/main/resources/sql/` | PostgreSQL 16. O `01_ddl.sql` cria as 8 tabelas; o `02_dml.sql` popula. |
| API | `leilao-api/src/main/java/` | Spring Boot 2.7, em camadas: `service` → `business` → `repository` → `entity`. |
| Frontend | `leilao-app/src/` | Vue 2.7 + Vuetify 2.7. |

## Antes de mexer

```powershell
# Sobe o banco
docker compose -f docker-database.yml up -d

# Roda os testes
cd leilao-api
mvn test

# Estilo do frontend
cd ..\leilao-app
npm run lint
```

O `mvn test` roda **sem precisar de banco**: quase todos os testes usam
dublês do Mockito, `@WebMvcTest` ou `@DataJpaTest` sem conexão. Só um
exige PostgreSQL de verdade, e sem ele é **pulado**, não reprovado.

## O que o CI exige

O `.github/workflows/ci.yml` roda a cada push e a cada pull request:

1. **Build e Testes** — compila, roda a suíte, gera o SBOM
   (`target/bom.json`).
2. **Varredura Dinâmica (OWASP ZAP)** — sobe a aplicação e ataca ela.

O pull request só passa se os dois ficarem verdes. Um PR que quebra o
build não é mergeável — e o badge mostra isso antes de qualquer
revisão humana.

## As regras do projeto

### A regra de negócio fica no `business`

O `business` não é um bean do Spring por acidente: é explícito. A regra
de negócio não depende de framework, e por isso é testável sem subir
servidor, sem banco e sem mock de contexto.

O `service` (controller) traduz HTTP em Java e nada mais. Se ele tem
regra de negócio, a regra está no lugar errado — porque a mesma regra
precisa ser reusada por outra entrada.

O `repository` isola o SQL. O `entity` mapeia o banco.

### O banco tem as constraints; o Java valida de novo

As duas camadas validam, e por motivos diferentes:

- O **frontend** valida para dar *experiência*: o erro aparece no
  campo, sem perder o que já foi digitado.
- O **backend** valida para dar *garantia*: qualquer um pode chamar a
  API direto, sem passar pela tela.

Nenhum endpoint depende do frontend para proteger dado.

### A segurança é padrão, não opcional

- **Senha** com Argon2id (`CodificadorDeSenhaDelegado` + o
  `DelegatingPasswordEncoder` do Spring). O encoder legado continua
  funcionando, e reescreve sozinho no primeiro login.
- **Token de acesso** JWT RS256, 15 minutos, com rotação de chave pelo
  cabeçalho `kid`. O token de renovação é de uso único: reuso revoga a
  família inteira.
- **Toda escrita vai para a trilha de auditoria**, selada por HMAC-SHA256
  e encadeada no registro anterior. A chave vem de `AUDIT_HMAC_KEY`.
- **Erro de cliente é 400**, nunca 500. A mensagem diz qual campo
  falhou, sem o stack trace.

### O que não entra

- **Nenhum segredo real.** Senha e chave vêm de variável de ambiente.
  O `.env.example` versionado tem só a senha de desenvolvimento do
  PostgreSQL (`postgres`), que é pública e está documentada no próprio
  arquivo. Em qualquer ambiente real, a senha vai para um secret do
  orquestrador — nunca para um arquivo no Git.
- **Nenhum script com caminho absoluto da minha máquina.** O que sobe o
  ambiente local fica fora do repositório: quem clona precisa de um
  caminho que funcione na máquina dele.
- **Nenhum `node_modules`, `target` ou `dist` versionado.**
- **Nenhuma alteração no `.gitattributes`** sem atualizar as regras de
  fim de linha junto — é o que mantém o diff limpo entre Windows e
  Linux.

## O padrão de commit

```
[tipo] descrição curta no imperativo

O por quê em parágrafos curtos. Nomes de arquivo, classes e comandos
começam com maiúscula. O que mudou tecnicamente fica no texto; o que
mudou de forma pode ir no corpo.
```

Tipos: `feat`, `fix`, `docs`, `refactor`, `chore`, `perf`, `test`.

## O checklist do pull request

1. `mvn test` passa.
2. `npm run lint` passa.
3. O SQL novo está nos scripts `01_ddl.sql` / `02_dml.sql`, e a massa
   de dados tem 10+ registros por tabela.
4. Nenhuma senha, chave ou token real foi versionado.
5. Se a mudança é de segurança, o README e o `SECURITY.md` continuam
   coerentes com o código.

## Vulnerabilidade

Não abra issue pública. O canal privado está em
[`.github/SECURITY.md`](.github/SECURITY.md), com prazo de resposta
definido.
