# Política de Segurança

Este documento explica **como reportar uma vulnerabilidade** e **o que não
é uma vulnerabilidade**.

## Como reportar

**Não abra uma issue pública para reportar uma vulnerabilidade.** Uma
issue pública fica visível para todo mundo — inclusive para quem pretende
explorar o problema — no tempo exato em que a correção ainda não existe.

Use o canal privado do GitHub:

1. Abra a aba **Security** do repositório
2. Clique em **Report a vulnerability**
3. Descreva o problema com o máximo de detalhe

### O que incluir no relato

| Pergunta | Por que importa |
|---|---|
| **O que acontece?** | Qual é o impacto observável |
| **Como reproduzir?** | Os passos exatos, do início ao fim |
| **Qual é o ambiente?** | Versão do Java, perfil de segurança, banco |
| **Qual é a correção sugerida?** (opcional) | Se você já sabe a correção, economiza tempo |

### O que acontece depois

1. Você recebe a confirmação de recebimento em até **3 dias úteis**.
2. A vulnerabilidade é confirmada (ou não) em até **7 dias úteis**.
3. A correção é preparada e testada.
4. A correção é publicada, e você é creditado(a) no changelog — a menos
   que prefira não ser creditado(a).
5. A issue pública é aberta depois que a correção estiver disponível.

---

## O que **não** é uma vulnerabilidade

- **Erros de configuração que exigem acesso prévio.** Rodar a aplicação
  com `app.security.enabled=false` em produção é uma escolha de
  implantação, não uma vulnerabilidade — o perfil `prod` liga a
  autenticação.
- **Ataques de negação de serviço com recursos infinitos.** O projeto já
  tem controle anti-força-bruta e limitador de requisições por IP.
- **Ausência de HTTPS na configuração local.** O TLS é responsabilidade da
  infraestrutura (proxy reverso, balanceador). O projeto já envia HSTS em
  produção.
- **Segredos de ambiente.** As variáveis de ambiente não fazem parte do
  repositório. Se você encontrar uma senha real versionada no Git, isso
  **é** uma vulnerabilidade — reporte.
- **Relatos sem passos de reprodução.** Um relato teórico precisa de ao
  menos uma indicação de como chegar até lá.

## Escopo

**Em escopo:**
- Falhas de autenticação ou autorização
- Injections (SQL, XSS, comando, LDAP)
- Vazamento de dado sensível
- Bypass de controles de segurança
- Fraquezas criptográficas na implementação

**Fora de escopo:**
- Vulnerabilidades do próprio Spring Boot, Vue, PostgreSQL ou do JDK
  (reportadas aos respectivos mantenedores — o Dependabot deste
  repositório já sinaliza as atualizações)
- Problemas de configuração de infraestrutura
- Falta de cabeçalhos de segurança em ambientes que desativam a API

## Práticas já adotadas

| Controle | Onde |
|---|---|
| Hash de senha Argon2id com migração automática de BCrypt | `config/CodificadorDeSenhaDelegado.java` |
| JWT RS256 com rotação de chaves (`kid`) | `config/ChaveJwtProvider.java` |
| Renovação de token com detecção de reuso | `business/AutenticacaoBO.java` |
| Trilha de auditoria a prova de adulteração (cadeia HMAC) | `business/AuditoriaBO.java` |
| Controle anti-força-bruta e limitador por IP | `config/LoginAttemptService.java`, `config/LimitadorDeRequisicoesFilter.java` |
| Identificador de correlação em todas as requisições | `config/CorrelationIdFilter.java` |
| Cabeçalhos de segurança (CSP, COOP, CORP, Permissions-Policy) | `config/SecurityHeadersFilter.java` |
| Sem exposição de stack trace ao cliente | `exception/GlobalExceptionHandler.java` |
| SBOM gerado a cada build | `pom.xml` (cyclonedx-maven-plugin) |
| Análise de dependências automatizada | `.github/dependabot.yml` |
| Varredura de vulnerabilidades (CVSS ≥ 7) | `pom.xml` (perfil `security-scan`) |
