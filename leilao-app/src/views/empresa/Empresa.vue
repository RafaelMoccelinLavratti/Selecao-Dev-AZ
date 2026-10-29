<template>
  <!--
    ==========================================================================
     VIEW: CADASTRO DE EMPRESA   (views/empresa/Empresa.vue)
     EXERCICIO 8 - "Crie a tela de inclusao e alteracao do cadastro de
     empresas. Observe que esta tela deve validar os campos obrigatorios
     especificados no modelo de banco de dados. Deve validar tambem as
     mascaras e os formatos de numero, email, url e tamanho maximo do
     texto. Quanto mais completo melhor."

     URL da pagina INCLUIR: http://server/#/empresa
     URL da pagina EDITAR.: http://server/#/empresa/:id
     URL dos servicos....: http://server/empresa (GET | POST | PUT | DELETE)

     ------------------------------------------------------
     A MESMA TELA FAZ INCLUSAO E ALTERACAO
     ------------------------------------------------------
     A diferenca entre os dois modos e UMA COISA: o presence de um id na
     URL (#/empresa/5). Se houver id, a tela carrega os dados e o botao
     diz "Alterar"; se nao houver, o formulario comeca vazio e o botao
     diz "Cadastrar".

     E assim que se evita duplicar 200 linhas de formulario em duas telas
     - e qualquer correcao de layout vale para as duas.
    ==========================================================================
  -->
  <div>
    <h1 class="titulo-tela">
      {{ emEdicao ? 'Alterar empresa' : 'Nova empresa' }}
    </h1>
    <p class="subtitulo-tela">
      <v-icon small color="grey">mdi-information</v-icon>
      Os campos marcados com <span style="color: #c62828;">*</span> sao obrigatorios.
    </p>

    <v-card elevation="2">
      <!--
        FORMULARIO.
        "v-form" (do Vuetify) cuida da validacao: cada campo tem suas
        "rules" e o componente tem o "ref" que usamos para disparar a
        validacao de todos de uma vez ("this.$refs.formulario.validate()").
      -->
      <v-form ref="formulario" v-model="formularioValido" @submit.prevent="salvar">
        <v-card-text>
          <!-- ============ DADOS DA EMPRESA ============ -->
          <div class="text-subtitle-1 font-weight-medium mb-3" style="color: #0f4c75;">
            <v-icon small left color="primary">mdi-domain</v-icon>
            Dados da empresa
          </div>

          <v-row dense>
            <!-- RAZAO SOCIAL (obrigatoria) -->
            <v-col cols="12" md="8">
              <v-text-field
                v-model="formulario.razaoSocial"
                label="Razao social *"
                :rules="[regras.razaoSocial]"
                :counter="84"
                maxlength="84"
                outlined
                dense
                required
              />
            </v-col>

            <!-- CNPJ (obrigatorio e unico) -->
            <v-col cols="12" md="4">
              <v-text-field
                v-model="formulario.cnpj"
                label="CNPJ *"
                placeholder="00.000.000/0000-00"
                :rules="[regras.cnpj]"
                :counter="18"
                maxlength="18"
                outlined
                dense
                required
                @input="formulario.cnpj = mascaraCnpj(formulario.cnpj)"
              />
            </v-col>
          </v-row>

          <v-row dense>
            <!-- LOGADOURO -->
            <v-col cols="12" md="6">
              <v-text-field
                v-model="formulario.logadouro"
                label="Logradouro"
                :counter="84"
                maxlength="84"
                outlined
                dense
              />
            </v-col>

            <!-- NUMERO -->
            <v-col cols="6" md="2">
              <v-text-field
                v-model="formulario.numero"
                label="Numero"
                :counter="10"
                maxlength="10"
                outlined
                dense
              />
            </v-col>

            <!-- COMPLEMENTO -->
            <v-col cols="6" md="4">
              <v-text-field
                v-model="formulario.complemento"
                label="Complemento"
                :counter="84"
                maxlength="84"
                outlined
                dense
              />
            </v-col>
          </v-row>

          <v-row dense>
            <!-- MUNICIPIO -->
            <v-col cols="12" md="5">
              <v-text-field
                v-model="formulario.municipio"
                label="Municipio"
                :counter="64"
                maxlength="64"
                outlined
                dense
              />
            </v-col>

            <!-- CEP (com mascara) -->
            <v-col cols="6" md="3">
              <v-text-field
                v-model="formulario.cep"
                label="CEP"
                placeholder="00000-000"
                :rules="[regras.cep]"
                :counter="9"
                maxlength="9"
                outlined
                dense
                @input="formulario.cep = mascaraCep(formulario.cep)"
              />
            </v-col>

            <!-- QUANTIDADE -->
            <v-col cols="6" md="2">
              <v-text-field
                v-model="formulario.quantidade"
                label="Quantidade"
                type="number"
                min="0"
                :rules="[regras.quantidade]"
                outlined
                dense
              />
            </v-col>

            <!-- ESTADO (campo auxiliar, nao existe no banco) -->
            <v-col cols="6" md="2">
              <v-select
                v-model="formulario.estado"
                label="UF"
                :items="ufs"
                outlined
                dense
                clearable
              />
            </v-col>
          </v-row>

          <!-- ============ CONTATO ============ -->
          <div class="text-subtitle-1 font-weight-medium mt-4 mb-3" style="color: #0f4c75;">
            <v-icon small left color="primary">mdi-card-text</v-icon>
            Contato
          </div>

          <v-row dense>
            <!-- TELEFONE (com mascara e validacao de formato) -->
            <v-col cols="12" md="4">
              <v-text-field
                v-model="formulario.telefone"
                label="Telefone"
                placeholder="(00) 0000-0000"
                :rules="[regras.telefone]"
                :counter="16"
                maxlength="16"
                outlined
                dense
                @input="formulario.telefone = mascaraTelefone(formulario.telefone)"
              />
            </v-col>

            <!-- EMAIL (validacao de formato) -->
            <v-col cols="12" md="4">
              <v-text-field
                v-model="formulario.email"
                label="E-mail"
                type="email"
                placeholder="empresa@dominio.com.br"
                :rules="[regras.email]"
                :counter="254"
                maxlength="254"
                outlined
                dense
              />
            </v-col>

            <!-- SITE (validacao de URL) -->
            <v-col cols="12" md="4">
              <v-text-field
                v-model="formulario.site"
                label="Site"
                placeholder="https://www.empresa.com.br"
                :rules="[regras.site]"
                :counter="254"
                maxlength="254"
                outlined
                dense
              />
            </v-col>
          </v-row>

          <!-- ============ ACESSO ============ -->
          <div class="text-subtitle-1 font-weight-medium mt-4 mb-3" style="color: #0f4c75;">
            <v-icon small left color="primary">mdi-lock</v-icon>
            Acesso
          </div>

          <v-row dense>
            <!-- USUARIO (unico no banco) -->
            <v-col cols="12" md="4">
              <v-text-field
                v-model="formulario.usuario"
                label="Usuario"
                :rules="[regras.usuario]"
                :counter="20"
                maxlength="20"
                outlined
                dense
                prepend-inner-icon="mdi-account"
              />
            </v-col>

            <!-- SENHA -->
            <v-col cols="12" md="4">
              <v-text-field
                v-model="formulario.senha"
                label="Senha"
                :type="mostrarSenha ? 'text' : 'password'"
                :rules="[regras.senha]"
                :hint="emEdicao ? 'Deixe em branco para manter a senha atual.' : 'Minimo de 8 caracteres.'"
                persistent-hint
                outlined
                dense
                prepend-inner-icon="mdi-lock"
                :append-icon="mostrarSenha ? 'mdi-eye-off' : 'mdi-eye'"
                @click:append="mostrarSenha = !mostrarSenha"
              />
            </v-col>

            <!-- CONFIRMAR SENHA -->
            <v-col cols="12" md="4">
              <v-text-field
                v-model="formulario.confirmarSenha"
                label="Confirmar senha"
                :type="mostrarSenha ? 'text' : 'password'"
                :rules="[regras.confirmarSenha]"
                outlined
                dense
                prepend-inner-icon="mdi-lock-check"
              />
            </v-col>
          </v-row>

          <!--
            ALERTA DE ERROS DO SERVIDOR.
            Quando o backend rejeita a gravacao (por exemplo, CNPJ ja
            cadastrado), a mensagem dele aparece aqui, perto do botao de
            salvar - e nao perdida no console.
          -->
          <v-alert
            v-if="erroServidor"
            type="error"
            dense
            text
            class="mt-3"
            dismissible
            @input="erroServidor = ''"
          >
            {{ erroServidor }}
          </v-alert>

          <!-- ERROS DE CAMPO RETORNADOS PELO BACKEND -->
          <v-alert
            v-if="errosCampo && Object.keys(errosCampo).length > 0"
            type="error"
            dense
            text
            class="mt-3"
          >
            <ul style="margin: 0; padding-left: 18px;">
              <li v-for="(mensagem, campo) in errosCampo" :key="campo">
                {{ mensagem }}
              </li>
            </ul>
          </v-alert>
        </v-card-text>

        <!-- ============ BOTOES ============ -->
        <v-card-actions class="px-4 pb-4">
          <v-btn
            color="primary"
            type="submit"
            depressed
            :loading="salvando"
            :disabled="salvando"
          >
            <v-icon left>mdi-check</v-icon>
            {{ emEdicao ? 'Alterar' : 'Cadastrar' }}
          </v-btn>

          <v-btn text :to="'/empresas'">
            Cancelar
          </v-btn>

          <v-spacer></v-spacer>

          <span v-if="emEdicao" class="text-caption grey--text">
            Editando o registro de id {{ id }}
          </span>
        </v-card-actions>
      </v-form>
    </v-card>
  </div>
</template>

<script>
import api from '../../services/api';
import {
  mascaraCnpj,
  mascaraCep,
  mascaraTelefone,
  obrigatorio,
  emailValido,
  urlValida,
  cnpjValido,
  cepValido,
  telefoneValido
} from '../../utils/mascaras';

export default {
  name: 'TelaEmpresa',

  data() {
    return {
      // O formulario e um objeto com todos os campos.
      formulario: this.formularioVazio(),

      // Resultado da validacao do v-form.
      formularioValido: false,

      // Controla se a senha aparece em texto puro.
      mostrarSenha: false,

      // Mensagem de erro vinda do servidor.
      erroServidor: '',

      // Erros por campo vindos do servidor (chave = nome do campo).
      errosCampo: {},

      // Sinaliza que o salvamento esta em andamento (desabilita o botao).
      salvando: false,

      // Lista de estados (UF) para o campo auxiliar.
      ufs: [
        'AC', 'AL', 'AM', 'AP', 'BA', 'CE', 'DF', 'ES', 'GO', 'MA', 'MG',
        'MS', 'MT', 'PA', 'PB', 'PE', 'PI', 'PR', 'RJ', 'RN', 'RO', 'RR',
        'RS', 'SC', 'SE', 'SP', 'TO'
      ]
    };
  },

  computed: {
    /**
     * O id veio da URL? (#/empresa/5)
     */
    id() {
      return this.$route.params.id;
    },

    /**
     * Estamos editando ou incluindo?
     */
    emEdicao() {
      return !!this.id;
    },

    /**
     * Regras de validacao do formulario.
     *
     * ESTAS REGRAS ESPELHAM AS DO BACKEND (EmpresaRequest.java).
     * A mesma regra nos dois lados garante que o usuario nunca veja
     * "a tela aceitou e a API recusou".
     */
    regras() {
      return {
        razaoSocial: value => obrigatorio(value) || 'A razao social e obrigatoria.',
        cnpj: value => obrigatorio(value) || 'O CNPJ e obrigatorio.'
          || (cnpjValido(value) || 'O CNPJ deve ter 14 digitos.'),
        cep: value => cepValido(value) || 'O CEP deve ter 8 digitos (00000-000).',
        telefone: value => telefoneValido(value) || 'Informe um telefone valido com DDD.',
        email: value => emailValido(value) || 'Informe um e-mail valido.',
        site: value => urlValida(value) || 'O site deve comecar com http:// ou https://',
        quantidade: value => (value === '' || value === null || Number(value) >= 0)
          || 'A quantidade nao pode ser negativa.',
        usuario: value => (value === '' || value === null || value.length <= 20)
          || 'O usuario deve ter no maximo 20 caracteres.',
        // "!value" cobre vazio, null e indefinido: na alteracao, deixar a
        // senha em branco significa "manter a senha atual".
        senha: value => (!value || value.length >= 8)
          || 'A senha deve ter no minimo 8 caracteres.',
        // A confirmacao so e exigida quando ha senha nova para confirmar.
        confirmarSenha: value => (!this.formulario.senha || value === this.formulario.senha)
          || 'As senhas nao conferem.'
      };
    }
  },

  /**
   * Ao entrar na tela, se for edicao, carrega os dados da empresa.
   */
  mounted() {
    if (this.emEdicao) {
      this.carregar();
    }
  },

  methods: {
    /**
     * Objeto de formulario vazio (usado na criacao e ao limpar).
     */
    formularioVazio() {
      return {
        razaoSocial: '',
        cnpj: '',
        logadouro: '',
        numero: '',
        complemento: '',
        municipio: '',
        quantidade: '',
        cep: '',
        telefone: '',
        email: '',
        site: '',
        usuario: '',
        senha: '',
        confirmarSenha: '',
        estado: ''
      };
    },

    // Mascaradores (importados do arquivo de utilitarios).
    mascaraCnpj,
    mascaraCep,
    mascaraTelefone,

    /**
     * Carrega a empresa que esta sendo editada.
     */
    async carregar() {
      try {
        const empresa = await api.get('/empresas/' + this.id);

        // Copia os campos da API para o formulario.
        // A senha NAO vem da API (por seguranca) - por isso fica vazia.
        this.formulario = Object.assign(this.formularioVazio(), {
          razaoSocial: empresa.razaoSocial || '',
          cnpj: mascaraCnpj(empresa.cnpj || ''),
          logadouro: empresa.logadouro || '',
          numero: empresa.numero || '',
          complemento: empresa.complemento || '',
          municipio: empresa.municipio || '',
          quantidade: empresa.quantidade !== null && empresa.quantidade !== undefined ? empresa.quantidade : '',
          cep: mascaraCep(empresa.cep || ''),
          telefone: mascaraTelefone(empresa.telefone || ''),
          email: empresa.email || '',
          site: empresa.site || '',
          usuario: empresa.usuario || '',
          senha: '',
          confirmarSenha: ''
        });
      } catch (erro) {
        this.erroServidor = erro.mensagem || 'Erro ao carregar a empresa.';
      }
    },

    /**
     * Valida e envia o formulario.
     */
    async salvar() {
      this.erroServidor = '';
      this.errosCampo = {};

      // 1) Valida no navegador (a mensagem aparece campo a campo).
      if (!this.$refs.formulario.validate()) {
        this.notificar('error', 'Verifique os campos destacados do formulario.');
        return;
      }

      // 2) Monta o corpo da requisicao (sem os campos que so existem
      //    na tela, como "estado" e "confirmarSenha").
      //    A senha vazia vira null: assim o backend entende que o usuario
      //    NAO quer trocar a senha (na alteracao), em vez de tentar
      //    validar uma string vazia.
      const corpo = {
        razaoSocial: this.formulario.razaoSocial.trim(),
        cnpj: this.formulario.cnpj,
        logadouro: this.formulario.logadouro,
        numero: this.formulario.numero,
        complemento: this.formulario.complemento,
        municipio: this.formulario.municipio,
        quantidade: this.formulario.quantidade === '' ? null : Number(this.formulario.quantidade),
        cep: this.formulario.cep,
        telefone: this.formulario.telefone,
        email: this.formulario.email,
        site: this.formulario.site,
        usuario: this.formulario.usuario,
        senha: this.formulario.senha ? this.formulario.senha : null
      };

      // 3) Envia para a API (POST ou PUT, conforme o modo).
      this.salvando = true;
      try {
        if (this.emEdicao) {
          await api.put('/empresas/' + this.id, corpo);
          this.notificar('success', 'Empresa alterada com sucesso.');
        } else {
          await api.post('/empresas', corpo);
          this.notificar('success', 'Empresa cadastrada com sucesso.');
        }

        // 4) Volta para a tela de listagem.
        this.$router.push('/empresas');
      } catch (erro) {
        // Erro do servidor: mostra a mensagem e recarrega o formulario
        // para o usuario corrigir sem perder o que digitou.
        this.erroServidor = erro.mensagem || 'Erro ao salvar a empresa.';
        if (erro.campos) {
          this.errosCampo = erro.campos;
        }
        this.notificar('error', this.erroServidor);
      } finally {
        this.salvando = false;
      }
    },

    /**
     * Atalho para exibir a notificacao global.
     */
    notificar(tipo, mensagem) {
      if (this.$root.$refs.notificacoes) {
        this.$root.$refs.notificacoes.exibir(tipo, mensagem);
      }
    }
  }
};
</script>
