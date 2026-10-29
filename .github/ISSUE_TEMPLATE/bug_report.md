name: Relato de Bug

about: Reporta um comportamento inesperado

title: "[modulo] resumo do problema"

labels: ["bug"]

body:
  - type: markdown
    attributes:
      value: |
        Obrigado por relatar! Um bom relato ajuda a corrigir mais rapido.

  - type: dropdown
    id: modulo
    attributes:
      label: Onde o problema acontece?
      options:
        - API - Java / Spring Boot
        - Frontend - Vue
        - Banco de dados
        - Infraestrutura / Docker
        - Documentacao
    validations:
      required: true

  - type: textarea
    id: descricao
    attributes:
      label: O que aconteceu?
      description: |
        Descreva o que voce ESPERAVA e o que aconteceu de verdade.
      placeholder: |
        Esperava: a API devolve 401 em JSON quando o token expira.
        Aconteceu: devolveu uma pagina HTML em branco.
    validations:
      required: true

  - type: textarea
    id: reproducao
    attributes:
      label: Passos para reproduzir
      description: |
        Numerados, do inicio ao fim. Inclua o comando curl, a tela, ou o
        que quer que tenha sido feito.
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
        Versao do Java, do Node, do PostgreSQL; sistema operacional; perfil
        de seguranca (app.security.enabled).
      placeholder: |
        Java 11.0.32, Node 20, PostgreSQL 16, Windows 11, security.enabled=false
    validations:
      required: true

  - type: textarea
    id: logs
    attributes:
      label: Saida de log ou mensagem de erro
      description: |
        Cole o trecho relevante. Se houver um correlation id (o codigo que
        aparece no cabecalho X-Correlation-Id), inclua - ele localiza a
        requisicao no servidor.
      render: shell
    validations:
      required: false
