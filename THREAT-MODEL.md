# Modelo de Ameaças — API de Leilões

Este documento é o mapa de segurança do projeto: o que a API tem de
valioso, quem pode tentar pegar, como cada ataque é bloqueado e o que
ficou em aberto de propósito.

Ele não é uma lista de desejos. Cada item aqui aponta para o código que
implementa o controle, porque um controle de segurança que não existe no
código não existe.

---

## 1. O que a API protege

Antes de falar de ataque, é preciso saber o que está em jogo.

| Ativo | Onde está | Por que importa |
|-------|-----------|-----------------|
| Dados cadastrais das empresas | `tb_empresa` | CNPJ, endereço, telefone, e-mail e nome de usuário de empresas que são concorrentes entre si num mesmo leilão. |
| Credenciais de acesso | `tb_empresa.senha`, configuração | Hash Argon2id. Quem vazar o hash ainda tem uma barreira de custo para quebrar. |
| Leilões e lotes | `tb_leilao`, `tb_lote` | O valor dos lotes define o faturamento da operação. |
| Participação em leilões | `tb_comprador` | Quem está inscrito num leilão é informação de negócio — e a lista completa revela a estratégia de compra dos concorrentes. |
| Trilha de auditoria | `tb_auditoria` | A prova de não-repúdio: quem fez o quê, de onde, com que resultado. É o que permite investigar um incidente. |
| Segredos de assinatura | pasta de chaves JWT, `AUDIT_HMAC_KEY` | Quem tem a chave assina token de acesso e forja registro de auditoria. |
| Disponibilidade do serviço | a API inteira | Uma API fora do ar no dia do leilão custa mais que qualquer dado. |

O dado mais sensível **não** é o cadastro: é a **participação**. A lista
de compradores de um leilão revela a estratégia de compra de cada
concorrente. Por isso a autorização é *modelada por tenant*, e não apenas
"está autenticado?".

---

## 2. Quem pode tentar

| Ator | Habilidade | Objetivo |
|------|-----------|----------|
| **Visitante** | Nenhuma credencial. | Ler dados, enumerar ids, derrubar o serviço. |
| **Empresa concorrente** | Uma credencial válida da própria empresa. | Ver os dados de outra empresa:participações, cadastros, valores. |
| **Atacante externo** | Credenciais vazadas, bot, proxies. | Forçar bruta, roubar sessão, exfiltrar. |
| **Empregado interno** | Acesso ao banco, ao log ou ao servidor. | Ler dados de fora do escopo, apagar rastro. |
| **Fornecedor comprometido** | Dependência com CVE. | Executar código na aplicação. |

O ator mais importante aqui é o **segundo**: ele não é externo, não
usa ferramenta especial, e está autenticado. É o que a maioria das APIs
esquece.

---

## 3. Fronteiras de confiança

```
   Navegador (não confiável)
        │  HTTPS, cookie de sessão não existe
        ▼
   ┌──────────────────────────────────────────────┐
   │ Proxy reverso / balanceador  (confiável)     │
   │  - termina o TLS                            │
   │  - X-Forwarded-For com o IP real             │
   └──────────────────────────────────────────────┘
        │
        ▼
   ┌──────────────────────────────────────────────┐
   │ API Spring Boot                              │
   │  ┌────────────────────────────────────────┐  │
   │  │ Filtros: correlação, limite, JWT,     │  │
   │  │ anti-força-bruta, auditoria             │  │
   │  ├────────────────────────────────────────┤  │
   │  │ Autorização (por empresa / tenant)      │  │
   │  ├────────────────────────────────────────┤  │
   │  │ Regras de negócio (business/)          │  │
   │  └────────────────────────────────────────┘  │
   └──────────────────────────────────────────────┘
        │  JDBC, usuário com privilégio reduzido
        ▼
   ┌──────────────────────────────────────────────┐
   │ PostgreSQL                                   │
   │  - chave HMAC da auditoria fora do banco     │
   │  - chave RSA de assinatura fora do banco     │
   └──────────────────────────────────────────────┘
```

Regras que valem para toda a fronteira:

- **O cliente nunca é confiável.** Tudo que vem do navegador é
  revalidado no servidor, inclusive o que o frontend já validou.
- **O proxy é confiável, e só ele.** O `X-Forwarded-For` só é considerado
  quando `app.security.trust-proxy=true` — e a documentação avisa que,
  nesse caso, a porta da aplicação não pode estar exposta publicamente.
  Sem o proxy na frente, o cabeçalho é ignorado.
- **O banco não sabe o segredo.** A chave do HMAC da auditoria e a chave
  RSA do JWT vivem no ambiente, não no banco. Quem tiver acesso total ao
  banco ainda não consegue forjar registro de auditoria nem assinar token.
- **O banco não está exposto na rede.** A porta do PostgreSQL é publicada
  em `127.0.0.1`, e não em `0.0.0.0`. A diferença não é cosmética: um
  `5432:5432` sem endereço faz o Docker escutar em **todas** as interfaces
  da máquina, e o PostgreSQL (com a senha de desenvolvimento, pública)
  fica acessível para qualquer pessoa da mesma rede. Com o `127.0.0.1`
  antes dos dois pontos, a porta só escuta na própria máquina — e a API,
  que roda na máquina, continua conectando em `localhost:5432` sem
  nenhuma mudança.

---

## 4. Análise STRIDE

### 4.1 Spoofing (falsificação de identidade)

| Ameaça | Controle | Onde está |
|--------|----------|-----------|
| Roubo de senha por força bruta | Argon2id (64 MiB) + limite por usuário e por IP + janela deslizante | `PasswordEncoderConfig`, `LoginAttemptService`, `BruteForceProtectionFilter` |
| Enumeração de usuários | Resposta e **tempo de resposta idênticos** para usuário inexistente e senha errada; o hash falso é comparado no caso inexistente | `AutenticacaoBO.buscarUsuarioComDefesa()` |
| Token JWT forjado | Assinatura RS256 verificada com a chave pública; o `kid` precisa ser de uma chave conhecida | `TokenJwtBO.validar()` |
| Confusão de algoritmo (`alg=none`, HS256 com chave pública) | O parser do JJWT fica restrito à família RSA e valida a compatibilidade entre algoritmo e tipo de chave | `TokenJwtBO` |
| Token de renovação usado como token de acesso | Claim `typ` conferido na validação | `TokenJwtBO.validar()` |
| Sessão revogada continuando válida | Lista de bloqueio por família de sessão: logout e detecção de reuso invalidam os tokens de acesso | `TokenRevogadoBO`, `JwtAuthenticationFilter` |
| Reuso de token de renovação | Detecção de reuso revoga a família inteira (tokens de acesso e de renovação) | `AutenticacaoBO.renovar()` |

### 4.2 Tampering (adulteração de dados)

| Ameaça | Controle | Onde está |
|--------|----------|-----------|
| Cliente alterar campo que não deve | DTO de entrada com lista fechada de campos; a entidade nunca é deserializada | `dto/`, `business/` |
| Cliente forjar `created_at` / `id` | Os DTOs não têm esses campos | `dto/` |
| Alteração da trilha de auditoria | Cadeia de hashes HMAC encadeados; a chave está fora do banco; a verificação detecta a quebra | `AuditoriaBO`, `tb_auditoria` |
| SQL injection | Todo SQL é JPQL ou nativo com parâmetros vinculados; não há concatenação de texto do usuário | `repository/` |
| Path traversal | A única leitura de arquivo é sobre a pasta de chaves, definida por variável de ambiente | `ChaveJwtProvider` |

### 4.3 Repudiation (negação de ação)

| Ameaça | Controle | Onde está |
|--------|----------|-----------|
| "Não fui eu que apaguei aquilo" | Trilha selada com hash encadeado, sequência monotônica e HMAC | `tb_auditoria`, `AuditoriaBO` |
| Auditoria com dado falsificado | `ip_origem`, `usuario`, método e caminho vêm do servidor, não do corpo da requisição | `AuditTrailFilter` |
| Usuário que não pode ser rastreado | `X-Correlation-Id` sanitizado contra CR/LF, para não haver log forging | `CorrelationIdFilter` |
| IP de origem malicioso na auditoria | O IP é truncado e normalizado antes de gravar | `ClientIpResolver` |

### 4.4 Information Disclosure (vazamento de informação)

| Ameaça | Controle | Onde está |
|--------|----------|-----------|
| Empresa A ver dados da empresa B | Autorização por tenant: cada empresa só vê os próprios dados, os leilões que vende e os em que é compradora | `AutorizacaoBO`, `UsuarioAtual` |
| Resposta de erro com stack trace / SQL | Handler global devolve mensagem genérica no 500; `include-stacktrace=never` | `GlobalExceptionHandler` |
| Enumeração por mensagem de erro | "Acesso negado" é a mesma mensagem para recurso existente e inexistente | `AutorizacaoBO` |
| Hash de senha exposto | `EmpresaResponse` não tem campo de senha; o hash não sai na resposta | `EmpresaBO.paraResponse()` |
| Detalhe do actuator vazando | Só `health` e `info` expostos, sem detalhes nem componentes | `application.properties` |
| Configuração interna exposta | `env`, `beans` e `configprops` não estão expostos | `management.endpoints.web.exposure.include` |

### 4.5 Denial of Service (negação de serviço)

| Ameaça | Controle | Onde está |
|--------|----------|-----------|
| Rajada de requisições | Limitador por IP com janela deslizante e cabeçalhos `X-RateLimit-*` | `LimitadorDeRequisicoesFilter` |
| Rajada de tentativas de login | Limite por usuário e por IP, com bloqueio antes da validação da senha (o hash é caro de propósito) | `BruteForceProtectionFilter` |
| Custo do hash como vetor de exaustão | Argon2id com 64 MiB: o atacante gasta memória do servidor para cada palpite | `PasswordEncoderConfig` |
| Login por JSON sem proteção | O login por JSON alimenta o mesmo contador de tentativas que o HTTP Basic | `AutenticacaoBO.autenticar()` |
| Inchaço de memória pelos contadores | Contadores com janela e limpeza periódica | `LoginAttemptService`, `ContadorStore` |
| Inchaço de memória pelo mapa de IP forjado | O `X-Forwarded-For` só é aceito atrás de proxy confiável; e o mapa tem teto | `ClientIpResolver`, `LimitadorDeRequisicoesFilter` |

### 4.6 Elevation of Privilege (elevação de privilégio)

| Ameaça | Controle | Onde está |
|--------|----------|-----------|
| Empresa acessar dado de outra | Autorização por empresa em todas as rotas de negócio | `AutorizacaoBO` + os `service/` |
| Usuário comum virar administrador | Papel no token (`ROLE_ADMIN` / `ROLE_EMPRESA`); token sem papel é tratado como empresa, nunca como admin | `JwtAuthenticationFilter`, `UsuarioAutenticado` |
| Criar empresa sem ser administrador | `exigirAdministrador("cadastrar empresas")` | `EmpresaService` |
| Ler a trilha de auditoria | Reservada ao administrador | `AuditoriaService` |
| Escalada via alteração de empresa | Alterar empresa é do administrador; o usuário de empresa só altera a própria | `EmpresaService` |
| Criar leilão em nome de outra empresa | O usuário de empresa só cria leilão do qual é o vendedor | `LeilaoService` |
| **Transferir um leilão para outra empresa** | A verificação de posse olha o recurso como ele **está**; a transferência mudaria isso. Por isso a alteração verifica **duas** coisas: que a pessoa pode mexer no leilão, e que o **novo vendedor** é a própria pessoa (ou o administrador) | `AutorizacaoBO.exigirEscritaLeilaoComVendedor()` |
| **Mover um lote para o leilão de outra empresa** | Mesma regra: quem pode mexer no lote, **e** o novo leilão precisa ser de quem pede. Sem a segunda, a empresa ficaria com o controle do valor inicial de um item alheio | `AutorizacaoBO.exigirEscritaLoteComLeilao()` |
| Alterar a trilha | A chave do HMAC está fora do banco e é obrigatória no perfil `prod` | `AuditoriaBO`, `application-prod.properties` |

---

## 5. Riscos aceitos de propósito

Não existe projeto sem risco residual. Estes são os que **sabidamente**
existem, com o motivo.

| Risco | Motivo | Mitigação disponível |
|-------|--------|----------------------|
| O perfil padrão abre as rotas | O enunciado pede que o avaliador rode as rotas sem login | `SECURITY_ENABLED=true` ou perfil `prod` fecha tudo. O banner de aviso na subida deixa claro que a API está aberta. |
| O limitador é por IP, não por usuário | O IP é o único identificador confiável antes da autenticação | Há também o limite por par (usuário, IP). |
| Os contadores em memória se perdem no restart | Requisição autenticada não deve pagar uma ida ao banco | `RATE_LIMIT_STORE=banco` compartilha o contador entre réplicas e sobrevive ao restart. |
| Uma consulta ao banco por requisição autenticada (modo JWT) | A lista de bloqueio precisa ser consultada para o logout ter efeito | É o custo de um logout que realmente encerra a sessão. O registro expira sozinho e é limpo. |
| A documentação da API é pública | Ajuda quem integra e não expõe dado nenhum | Se preferir fechar, é uma linha em `SecurityConfig`. |
| O `X-Forwarded-For` é confiável no perfil `prod` | Em produção a aplicação fica atrás do proxy | Documentado: a porta da aplicação não pode estar exposta publicamente. |

---

## 6. Fora do escopo

Coisas que um modelo de ameaça completo cobriria e que **não** estão
implementadas aqui — declaradas para que ninguém as ache depois:

- **Segurança do infraestrutura**: rede, VPN, hardening do host, gestão
  de segredos (Vault, KMS). O projeto assume que o ambiente já é
  confiável nesse nível.
- **WAF / protecao na borda**: rate limiting de CDN, bloqueio por
  geolocalização, desafio de navegador. Existe antes da API.
- **Segurança da cadeia de suprimento**: SBOM e varredura de CVE estão
  no build ([`pom.xml`](leilao-api/pom.xml), perfil `security-scan`), mas
  não há assinatura de artefato nem verificação de procedência.
- **Auditoria de acessos do banco**: quem connectou no PostgreSQL, e
  quando. O projeto controla as permissões do usuário da aplicação
  ([`03_permissoes.sql`](leilao-api/src/main/resources/sql/seguranca/03_permissoes.sql)),
  mas não o acesso administrativo.
- **Teste de intrusão por terceiro** e **pentest**: o que existe aqui
  são testes automatizados de ataque, que cobrem os vetores conhecidos —
  não substituem um teste feito por alguém de fora.

---

## 7. Como este documento é conferido

O modelo só vale se a proteção existir no código. Por isso cada
controle listado acima tem teste automatizado:

| Controle | Teste |
|----------|-------|
| Força bruta, IP e por usuário | `AtaqueSegurancaTest` |
| IDOR entre empresas | `AutorizacaoBOTest` |
| **Transferência de leilão/lote para outra empresa** | `AutorizacaoBOTest` (regra) e `AutorizacaoEndpointTest` (no endpoint, com verificação de que nada foi gravado) |
| Logout encerra a sessão | `TokenRevogadoBOTest` |
| Reuso de token revoga a família | `AutenticacaoBOTest` |
| Resposta de erro sem vazamento | `SegurancaIntegracaoTest` |
| Injection e confusão de algoritmo | `SegurancaIntegracaoTest` |
| JPQL válido (falha no build, não em produção) | `ValidadeDasQueriesTest` |
| Configuração insegura não sobe | `ValidacaoConfiguracaoSegurancaTest` |
| Login aceita as credenciais que existem (regra de senha é do cadastro) | `LoginRequestValidacaoTest` |
| Geração de id da entidade concorda com o DDL | `EstrategiaDeGeracaoDeIdTest` |

E a qualidade dessa suite é medida por mutação (perfil `mutacao` do
Maven): o PIT altera o código de propósito e verifica se algum teste
percebe. Cobertura de linha diz quanto código rodou; a mutation score
diz quanto código está de fato protegido.
