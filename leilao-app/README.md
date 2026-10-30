# leilao-app

Frontend do sistema de gestão de leilões, em Vue 2 + Vuetify.

Ele consome a API Spring Boot que fica na pasta `../leilao-api`. A API precisa
estar no ar antes: o endereço vem da variável `VUE_APP_API_BASE_URL` (definida
em `.env`), e o padrão é `http://localhost:8081`.

## Rodando

```bash
npm install
npm run serve      # http://localhost:8080
```

Outros comandos:

```bash
npm run build      # gera o bundle de produção em dist/
npm run lint       # roda o ESLint com --fix
```

## Telas

| Rota          | Arquivo                            | O que faz                                    |
| ------------- | ---------------------------------- | -------------------------------------------- |
| `/unidades`   | `views/unidade/Unidades.vue`       | Consulta e edição de unidades, em grid       |
| `/empresas`   | `views/empresa/Empresas.vue`       | Lista de empresas com busca                  |
| `/empresa`    | `views/empresa/Empresa.vue`        | Cadastro de empresa                          |
| `/empresa/:id`| `views/empresa/Empresa.vue`        | Alteração de empresa                         |
| `/leiloes`    | `views/leilao/Leiloes.vue`         | Resumo dos leilões com o total de lotes      |

A rota `/` redireciona para `/unidades`.

`/empresa` e `/empresa/:id` usam o mesmo componente: o que define o modo é a
presença do `id` na URL. Com `id`, a tela carrega os dados e o botão vira
"Alterar"; sem `id`, o formulário começa vazio e o botão diz "Cadastrar".

## Organização

```
src/
├── components/     AppMenu, AppNotificacoes, ConfirmDialog
├── plugins/        Vuetify
├── router/         rotas da aplicação
├── services/       api.js - cliente HTTP e interceptors
├── store/          store do Vuex
├── utils/          mascaras.js - CNPJ, CEP, telefone
├── views/          uma pasta por exercício
└── assets/brand/   logo, tokens de cor e tipografia da AZ
```

O `services/api.js` centraliza a base URL e normaliza o erro da API para um
formato único (`mensagem` e `campos`), que as telas exibem na notificação e
no formulário.

## Identidade visual

As cores, a tipografia (Poppins) e o logo estão em `src/assets/brand/`. A
referência completa está em `../IDENTIDADE-VISUAL.md`.
