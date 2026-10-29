# Politica de Seguranca

Obrigado por se importar com a seguranca deste projeto. Este documento
explica **como reportar uma vulnerabilidade** e **o que NAO e' uma
vulnerabilidade**.

---

## Como reportar uma vulnerabilidade

**Nao abra uma issue publica para reportar uma vulnerabilidade.**

Uma issue publica fica visivel para todo mundo - inclusive para quem
pretende explorar o problema - no tempo exato em que a correcao ainda nao
existe. Isso daria ao atacante uma vantagem real.

Em vez disso, use o canal privado do GitHub:

1. Abra a aba **Security** do repositorio
2. Clique em **Report a vulnerability**
3. Descreva o problema com o maximo de detalhe

Ou envie um e-mail direto para o mantenedor do projeto.

### O que incluir no relato

Um bom relato de vulnerabilidade responde a quatro perguntas:

| Pergunta | Por que importa |
|---|---|
| **O que acontece?** | Qual e' o impacto observavel |
| **Como reproduzir?** | Os passos exatos, do inicio ao fim |
| **Qual e' o ambiente?** | Versao do Java, perfil de seguranca, banco |
| **Qual e' a correcao sugerida?** (opcional) | Se voce ja sabe a correcao, economiza tempo |

### O que acontece depois

1. Voce recebe um acknowledgment em ate **3 dias uteis**.
2. A vulnerabilidade e' confirmada (ou nao) em ate **7 dias uteis**.
3. Uma correcao e' preparada e testada.
4. A correcao e' publicada, e voce e' creditado(a) no changelog - a menos
   que voce prefira nao ser creditado(a).
5. A issue publica e' aberta depois que a correcao estiver disponivel.

**Nunca ha retaliação.** Quem reporta uma vulnerabilidade de boa fe esta
ajudando o projeto.

---

## O que NAO e' uma vulnerabilidade

Estas situacoes **nao** sao tratadas como vulnerabilidades:

- **Erros de configuracao que exigem acesso previo.** Por exemplo, rodar
  a aplicacao com `app.security.enabled=false` em producao e' uma escolha
  de implantacao, nao uma vulnerabilidade. O perfil `prod` ja liga a
  autenticacao.
- **Ataques de negacao de servico com recursos infinitos.** O projeto ja
  tem controle anti-forca-bruta e limitador de requisicoes por IP.
- **Ausencia de HTTPS na configuracao local.** O TLS e' responsabilidade
  da infraestrutura (proxy reverso, balanceador). O projeto ja envia HSTS
  em producao.
- **Segredos de ambiente.** As variaveis de ambiente nao fazem parte do
  repositorio. Se voce encontrar uma senha real versionada no Git, isso
  **e'** uma vulnerabilidade - reporte.
- **Relatos sem passos de reproducao.** Um relato teorico ("acho que da
  para fazer X") precisa de pelo menos uma indicacao de como chegar ate
  la.

---

## Escopo

Este projeto e' uma API de leilao (leilao-api) e seu frontend (leilao-app).

**Em escopo:**
- Falhas de autenticacao ou autorizacao
- Injections (SQL, XSS, comando, LDAP)
- Vazamento de dado sensivel
- Bypass de controles de seguranca
- Fraquezas criptograficas na implementacao

**Fora de escopo:**
- Vulnerabilidades do proprio Spring Boot, Vue, PostgreSQL ou do JDK
  (essas sao reportadas aos seus respectivos mantenedores - o Dependabot
  deste repositorio ja sinaliza as atualizacoes)
- Problemas de configuracao de infraestrutura do avaliador
- Falta de cabecalhos de seguranca em ambientes que desativam a API

---

## Praticas ja adotadas pelo projeto

Para que voce nao reporte algo que o projeto ja faz, e para que saiba o
que esperar de um sistema maduro:

| Controle | Onde |
|---|---|
| Hash de senha Argon2id com migracao automatica de BCrypt | `config/CodificadorDeSenhaDelegado.java` |
| JWT RS256 com rotacao de chaves (`kid`) | `config/ChaveJwtProvider.java` |
| Renovacao de token com deteccao de reuso | `business/AutenticacaoBO.java` |
| Trilha de auditoria a prova de adulteracao (cadeia HMAC) | `business/AuditoriaBO.java` |
| Controle anti-forca-bruta e limitador por IP | `config/LoginAttemptService.java`, `config/LimitadorDeRequisicoesFilter.java` |
| Identificador de correlacao em todas as requisicoes | `config/CorrelationIdFilter.java` |
| Cabecalhos de seguranca (CSP, COOP, CORP, Permissions-Policy) | `config/SecurityHeadersFilter.java` |
| Sem exposicao de stack trace ao cliente | `exception/GlobalExceptionHandler.java` |
| SBOM gerado a cada build | `pom.xml` (cyclonedx-maven-plugin) |
| Analise de dependencias automatizada | `.github/dependabot.yml` |
| Varredura de vulnerabilidades (CVSS >= 7) | `pom.xml` (perfil `security-scan`) |
