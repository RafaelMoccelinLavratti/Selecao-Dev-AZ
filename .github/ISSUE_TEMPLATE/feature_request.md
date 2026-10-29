name: Sugestao de Melhoria

about: Propõe uma funcionalidade ou melhoria

title: "[modulo] sugestao curta"

labels: ["enhancement"]

body:
  - type: markdown
    attributes:
      value: |
        Sugestoes sao bem-vindas. Ajuda muito saber QUAL PROBLEMA a
        sugestao resolve - uma sugestao sem problema conhecido e' dificil
        de avaliar.

  - type: dropdown
    id: modulo
    attributes:
      label: Qual modulo?
      options:
        - API - Java / Spring Boot
        - Frontend - Vue
        - Banco de dados
        - Documentacao
        - Infraestrutura / CI
    validations:
      required: true

  - type: textarea
    id: problema
    attributes:
      label: Qual problema isso resolve?
      description: |
        Quem sofre com o problema hoje? Em que situacao?
      placeholder: |
        Hoje, para descobrir por que uma requisicao falhou, e preciso
        procurar em varios arquivos de log sem criterio.
    validations:
      required: true

  - type: textarea
    id: solucao
    attributes:
      label: Como voce imaginaria a solucao?
      description: |
        Pode ser um desenho, um texto, um print. Nao precisa estar pronto.
    validations:
      required: true

  - type: checkboxes
    id: alternativas
    attributes:
      label: Alternativas que voce ja considera
      options:
        - label: Nenhuma - e' a primeira vez que penso nisso
        - label: Pensei em outras solucoes, e vou descrever nos comentarios
    validations:
      required: false
