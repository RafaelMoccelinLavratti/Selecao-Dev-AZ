name: Contexto do Pull Request

about: Descreva a mudança de forma que quem revisa entenda sem precisar perguntar

title: "[tipo] descrição curta da mudança"

labels: []

body:
  - type: markdown
    attributes:
      value: |
        Preencha o que se aplica.

  - type: input
    id: tipo
    attributes:
      label: Qual o tipo desta mudança?
      placeholder: "feat, fix, docs, refactor, chore, perf, test"
    validations:
      required: true

  - type: dropdown
    id: modulo
    attributes:
      label: Qual módulo foi alterado?
      options:
        - API - Java / Spring Boot
        - Frontend - Vue
        - Banco de dados / scripts SQL
        - Documentação
        - Infraestrutura / CI
        - varios (ou outro)
    validations:
      required: true

  - type: textarea
    id: descricao
    attributes:
      label: O que esta mudança faz?
      description: |
        Descreva o COMPORTAMENTO, não o código: o que passou a ser possível
        fazer (ou deixou de ser) depois desta mudança.
      placeholder: |
        Antes: o usuário não conseguia renovar o token sem digitar a senha.
        Depois: a renovação é automática, com detecção de reuso.
    validations:
      required: true

  - type: textarea
    id: seguranca
    attributes:
      label: Esta mudança tem impacto em segurança?
      description: |
        Marque se toca em autenticação, autorização, dados sensíveis,
        validação de entrada ou dependências.
      placeholder: |
        Ex.: a senha agora é gravada com Argon2id; os hashes antigos em BCrypt
        continuam valendo e são migrados no próximo login.
    validations:
      required: false

  - type: textarea
    id: comoTestar
    attributes:
      label: Como testar
      description: |
        Passos objetivos para reproduzir. Se for um bug, inclua como
        reproduzir o comportamento errado ANTES da mudança.
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
        - label: A documentação foi atualizada (README ou MANUAL-OPERACAO)
          required: false
        - label: Nenhuma senha, chave ou token real foi versionado
          required: true
        - label: A migração do banco (se houver) é idempotente
          required: false
