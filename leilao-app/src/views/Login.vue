<template>
  <!--
     Tela de login (views/Login.vue).

     E' um "v-dialog" modal: enquanto ela estiver aberta, o conteudo da
     aplicacao fica bloqueado. E' o que impede que o usuario navegue
     pelas telas sem sessao - mas a garantia de verdade esta no
     backend, que recusa qualquer requisicao sem credencial. Esta tela
     e' a experiencia, nao a barreira.

     O formulario tem dois campos: usuario e senha. O mesmo credencial
     serve para o administrador da API (que vem da configuracao) e
     para o usuario de uma empresa (que vem da tabela tb_empresa).
  -->
  <v-dialog
    v-model="aberto"
    max-width="420"
    persistent
    scrollable
  >
    <v-card>
      <v-card-title class="text-h6">
        <v-icon left color="primary">mdi-lock</v-icon>
        Entrar no sistema
      </v-card-title>

      <v-card-text>
        <p class="text-body-2 text--secondary mb-4">
          Informe suas credenciais para acessar os dados do sistema.
        </p>

        <!--
          O "v-form" cuida da validacao dos campos antes de enviar.
          As regras sao as mesmas do backend: quem chama a API direto
          (sem passar pela tela) continua sendo validado pela API.
        -->
        <v-form ref="formulario" v-model="formularioValido" @submit.prevent="entrar">
          <v-text-field
            v-model="usuario"
            label="Usuário"
            prepend-inner-icon="mdi-account"
            :rules="[regras.obrigatorio]"
            autocomplete="username"
            outlined
            dense
            autofocus
          />

          <v-text-field
            v-model="senha"
            label="Senha"
            prepend-inner-icon="mdi-lock"
            :type="mostrarSenha ? 'text' : 'password'"
            :append-icon="mostrarSenha ? 'mdi-eye-off' : 'mdi-eye'"
            :rules="[regras.obrigatorio]"
            autocomplete="current-password"
            outlined
            dense
            @click:append="mostrarSenha = !mostrarSenha"
          />

          <!--
            Mensagem de erro do servidor (credencial errada, conta
            bloqueada, API fora do ar).
          -->
          <v-alert
            v-if="erroServidor"
            type="error"
            dense
            text
            class="mt-4 mb-0"
          >
            {{ erroServidor }}
          </v-alert>
        </v-form>
      </v-card-text>

      <v-card-actions>
        <v-spacer></v-spacer>
        <v-btn
          color="primary"
          depressed
          :loading="entrando"
          :disabled="!formularioValido"
          @click="entrar"
        >
          Entrar
        </v-btn>
      </v-card-actions>

      <!--
        Ajuda para quem esta avaliando o projeto: as credenciais da
        massa de teste ficam visiveis, porque elas sao publicas e de
        desenvolvimento.

        Atencao ao detalhe: o administrador da API e' um usuario
        separado, definido na configuracao ("SECURITY_PASSWORD"). A
        senha "Senha@1" pertence a empresa "admin.aurora" - nao ao
        administrador.
      -->
      <v-card-text class="pt-0">
        <v-alert type="info" dense text class="text-caption">
          <div class="font-weight-medium mb-1">Credenciais de teste</div>
          <div>
            Empresa: <code>admin.aurora</code> / <code>Senha@1</code>
          </div>
          <div class="mt-1">
            Administrador: usuário <code>admin</code> e a senha definida em
            <code>SECURITY_PASSWORD</code>
          </div>
        </v-alert>
      </v-card-text>
    </v-card>
  </v-dialog>
</template>

<script>
/**
 * Script da tela de login.
 *
 * O fluxo completo:
 *
 *   1. o usuario preenche usuario e senha;
 *   2. a tela chama POST /auth/login;
 *   3. a API devolve o par de tokens;
 *   4. a store guarda os tokens e os dados do usuario;
 *   5. o dialogo fecha e o menu passa a mostrar quem esta logado.
 *
 * A tela nao guarda token nenhum: quem guarda e' a store, e o token
 * volta a tela so na resposta da API, ja no mesmo instante.
 */
import { entrar } from '../services/autenticacao';

export default {
  name: 'TelaLogin',

  data() {
    return {
      usuario: '',
      senha: '',
      mostrarSenha: false,
      entrando: false,
      erroServidor: '',

      /**
       * O formulario esta' valido?
       *
       * Este e' o valor que o "v-form" mantem atualizado: ele vira
       * "true" quando todos os campos passam nas regras, e "false"
       * enquanto algum falha.
       *
       * Ele precisa existir no "data" para o Vue saber que e' reativo.
       * Sem ele, o template referencia uma propriedade inexistente, e o
       * Vue avisa no console - e, pior, o botao "Entrar" fica sempre
       * desabilitado, porque ":disabled='!formularioValido'" com
       * "formularioValido" indefinido da sempre "true".
       */
      formularioValido: false,

      /**
       * Regras de validacao do formulario.
       *
       * Sao o minimo para a tela: campo obrigatorio. O formato da senha
       * e o do CNPJ ficam com o backend, que e' a camada que nao pode ser
       * contornada.
       */
      regras: {
        obrigatorio: value => (value === '' || value === null || value === undefined)
          ? 'Campo obrigatório.'
          : true
      }
    };
  },

  computed: {
    /**
     * O dialogo abre e fecha pelo estado da store.
     *
     * Ligar os dois e' o que permite que o interceptor do axios abra a
     * tela de login quando a API recusar uma requisicao por token
     * expirado.
     */
    aberto: {
      get() {
        return this.$store.state.loginAberto;
      },
      set(valor) {
        this.$store.commit('ABRIR_LOGIN', valor);
      }
    }
  },

  methods: {
    /**
     * Tenta entrar.
     */
    async entrar() {
      this.erroServidor = '';

      // A validacao do formulario: sem isso, a chamada sai com campo
      // vazio e a API recusa com 400 - e a mensagem do backend e mais
      // seca do que a da tela.
      if (!this.$refs.formulario.validate()) {
        return;
      }

      this.entrando = true;

      try {
        const resposta = await entrar(this.usuario, this.senha);

        // A store guarda os tokens e os dados do usuario, e avisa as
        // telas que a sessao mudou - e' o que faz elas recarregarem os
        // dados (a primeira carga delas acontece sem token, e volta
        // vazia por causa do 401).
        this.$store.dispatch('entrar', {
          tokenAcesso: resposta.tokenAcesso,
          tokenRenovacao: resposta.tokenRenovacao,
          // O prazo que a API devolve. E' o que permite renovar o token
          // ANTES de ele vencer - e nao so' depois que a API recusou
          // uma requisicao com 401.
          expiraEmSegundos: resposta.expiraEmSegundos,
          usuario: resposta.usuario || this.usuario,
          papel: resposta.papel || null,
          empresaId: resposta.empresaId || null,
          administrador: resposta.administrador === true
        });

        this.aberto = false;

        this.notificar('success', 'Bem-vindo(a)!');

        // Limpa o formulario, para a proxima sessao nao vir com a senha
        // da anterior na tela.
        this.usuario = '';
        this.senha = '';
        this.mostrarSenha = false;

      } catch (erro) {
        this.erroServidor = erro.mensagem || 'Não foi possível entrar.';
      } finally {
        this.entrando = false;
      }
    },

    /**
     * Mostra a notificacao global.
     *
     * A verificacao do "ref" e' obrigatoria: ela impede que uma
     * excecao aqui dentro vire "não foi possível entrar" na tela.
     *
     * Sem ela, o login dava certo (o token era guardado, a sessao
     * começava, as telas carregavam) e mesmo assim aparecia o erro
     * "Credenciais inválidas" - porque a falha estava na exibicao da
     * mensagem de sucesso, e nao no login.
     */
    notificar(tipo, mensagem) {
      if (this.$root.$refs.notificacoes) {
        this.$root.$refs.notificacoes.exibir(tipo, mensagem);
      }
    }
  }
};
</script>
