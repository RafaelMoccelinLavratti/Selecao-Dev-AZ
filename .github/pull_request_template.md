name: Contexto do Pull Request

about: Descreva a mudanca de forma que quem revisa consiga entender sem precisar perguntar

title: "[tipo] descricao curta da mudanca"

labels: []

body:
  - type: markdown
    attributes:
      value: |
        Obrigado por contribuir! Este formulario existe para poupar tempo de
        quem revisa. Preencha o que se aplica.

  - type: input
    id: tipo
    attributes:
      label: Qual o tipo desta mudanca?
      placeholder: "feat, fix, docs, refactor, chore, perf, test"
    validations:
      required: true

  - type: dropdown
    id: modulo
    attributes:
      label: Qual modulo foi alterado?
      options:
        - API - Java / Spring Boot
        - Frontend - Vue
        - Banco de dados / scripts SQL
        - Documentacao
        - Infraestrutura / CI
        - varios (ou outro)
    validations:
      required: true

  - type: textarea
    id: descricao
    attributes:
      label: O que esta mudanca faz?
      description: |
        Descreva o COMPORTAMENTO, nao o codigo. Diga o que passou a ser
        possivel fazer (ou deixou de ser possivel) depois desta mudanca.
      placeholder: |
        Antes: o usuario nao conseguia renovar o token sem digitar a senha.
        Depois: a renovacao e automatica, com deteccao de reuso.
    validations:
      required: true

  - type: textarea
    id: seguranca
    attributes:
      label: Esta mudanca tem impacto em seguranca?
      description: |
        Marque se a mudanca toca em autenticacao, autorizacao, dados sensiveis,
        validacao de entrada ou dependencias. Se sim, explique o porque.
      placeholder: |
        Ex.: a senha agora e gravada com Argon2id; os hashes antigos em BCrypt
        continuam valendo e sao migrados no proximo login.
    validations:
      required: false

  - type: textarea
    id: comoTestar
    attributes:
      label: Como testar
      description: |
        Passos objetivos para reproduzir. Se for um bug, inclua como
        reproduzir o comportamento errado ANTES da mudanca.
      placeholder: |
        mvn test
        curl -H "Authorization: Bearer <token>" http://localhost:8081/unidades
    validations:
      required: true

  - type: checkboxes
    id: checklist
    attributes:
      label: Checklist
      options:
        - label: Rodei "mvn test" e "npm run lint" localmente e passaram
          required: true
        - label: A documentacao foi atualizada (README, MANUAL-OPERACAO ou IDENTIDADE-VISUAL)
          required: false
        - label: Nenhuma senha, chave ou token real foi versionado
          required: true
        - label: A migracao do banco (se houver) e' idempotente
          required: false
