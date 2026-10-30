name: Relato de Bug

about: Reporta um comportamento inesperado

title: "[módulo] resumo do problema"

labels: ["bug"]

body:
  - type: dropdown
    id: modulo
    attributes:
      label: Onde o problema acontece?
      options:
        - API - Java / Spring Boot
        - Frontend - Vue
        - Banco de dados
        - Infraestrutura / Docker
        - Documentação
    validations:
      required: true

  - type: textarea
    id: descricao
    attributes:
      label: O que aconteceu?
      description: |
        Descreva o que você ESPERAVA e o que aconteceu de verdade.
      placeholder: |
        Esperava: a API devolve 401 em JSON quando o token expira.
        Aconteceu: devolveu uma página HTML em branco.
    validations:
      required: true

  - type: textarea
    id: reproducao
    attributes:
      label: Passos para reproduzir
      description: |
        Numerados, do início ao fim. Inclua o comando curl, a tela, ou o que
        quer que tenha sido feito.
      placeholder: |
        1. Fazer login em POST /auth/login
        2. Usar o token em GET /unidades
        3. Esperar 16 minutos
        4. Repetir o passo 2
    validations:
      required: true

  - type: textarea
    id: ambiente
    attributes:
      label: Ambiente
      description: |
        Versão do Java, do Node, do PostgreSQL; sistema operacional; perfil de
        segurança (app.security.enabled).
      placeholder: |
        Java 11.0.32, Node 20, PostgreSQL 16, Windows 11, security.enabled=false
    validations:
      required: true

  - type: textarea
    id: logs
    attributes:
      label: Saída de log ou mensagem de erro
      description: |
        Cole o trecho relevante. Se houver um correlation id (o código que
        aparece no cabeçalho X-Correlation-Id), inclua - ele localiza a
        requisição no servidor.
      render: shell
    validations:
      required: false
