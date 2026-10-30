<template>
  <!--
     View: cadastro de empresa (views/empresa/Empresa.vue).

     Exercicio 8 - inclusao e alteracao do cadastro de empresas, validando
     os campos obrigatorios do modelo, as mascaras e os formatos de numero,
     email, url e tamanho maximo do texto.

     URL de inclusao: http://server/#/empresa
     URL de edicao..: http://server/#/empresa/:id
     Servicos........: http://server/empresa (GET | POST | PUT | DELETE)

     A MESMA TELA FAZ INCLUSAO E ALTERACAO

     O que define o modo e a presenca de um id na URL (#/empresa/5). Com id,
     a tela carrega os dados e o botao diz "Alterar"; sem id, o formulario
     comeca vazio e o botao diz "Cadastrar". E assim que se evita duplicar
     o mesmo formulario em duas telas - e qualquer correcao de layout vale
     para as duas.
  -->
  <div>
    <h1 class="titulo-tela">
      {{ emEdicao ? 'Alterar empresa' : 'Nova empresa' }}
    </h1>
    <p class="subtitulo-tela">
      <v-icon small color="grey">mdi-information</v-icon>
      Os campos marcados com <span style="color: var(--az-vermelho-600);">*</span> sao obrigatorios.
    </p>

    <v-card elevation="2">
      <!--
        Formulario.
        "v-form" (do Vuetify) cuida da validacao: cada campo tem suas
        "rules" e o componente tem o "ref" que usamos para disparar a
        validacao de todos de uma vez ("this.$refs.formulario.validate()").
      -->
      <v-form ref="formulario" v-model="formularioValido" @submit.prevent="salvar">
        <v-card-text>
          <!-- ============ dados da empresa ============ -->
          <div class="text-subtitle-1 font-weight-medium mb-3" style="color: var(--az-azul-900);">
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
            <!-- logadouro -->
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

            <!-- numero -->
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

            <!-- complemento -->
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
            <!-- municipio -->
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

            <!-- quantidade -->
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

          <!-- ============ contato ============ -->
          <div class="text-subtitle-1 font-weight-medium mt-4 mb-3" style="color: var(--az-azul-900);">
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

          <!-- ============ acesso ============ -->
          <div class="text-subtitle-1 font-weight-medium mt-4 mb-3" style="color: var(--az-azul-900);">
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

            <!-- senha -->
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

            <!-- confirmar senha -->
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
            Alerta de erros do servidor.
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

          <!-- erros de campo retornados pelo backend -->
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

        <!-- ============ botoes ============ -->
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
/**
 * Script da tela de cadastro de empresa.
 *
 * A mesma tela atende inclusao e edicao: o que define o modo e a URL
 * (#/empresa para incluir, #/empresa/{id} para editar).
 *
 * As regras do formulario espelham exatamente as do backend
 * (EmpresaRequest.java), para que o usuario nunca veja "a tela aceitou e a
 * API recusou".
 *
 * A senha nunca volta da API, entao, ao carregar um registro para edicao,
 * o campo fica vazio e a senha so e exigida quando o usuario troca.
 */

/**
 * Importacao do servico centralizado de comunicacao com a API.
 */
import api from '../../services/api';

/**
 * Importacao dos mascaradores e das funcoes de validacao.
 *
 * Todas as funcoes de validacao do projeto ficam em UM arquivo
 * (utils/mascaras.js). Isso garante que a mesma mascara e a mesma
 * regra aparecam identicas em todas as telas - e que exista UM lugar
 * para corrigir quando o formato mudar.
 */
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
  /**
   * Nome do componente (tela).
   */
  name: 'TelaEmpresa',

  /**
   *  DADOS REATIVOS (data)
   */
  data() {
    return {
      /**
       * O formulario: um objeto com todos os campos da empresa.
       *
       * Comeca chamando "formularioVazio()". Chamar um metodo dentro
       * do "data" e permitido - o Vue executa o "data" uma unica vez,
       * na criacao do componente, entao o metodo e chamado uma vez
       * tambem. (Nao daria para usar "this" aqui: ele ainda nao
       * existiria.)
       */
      formulario: this.formularioVazio(),

      /**
       * Resultado da validacao do v-form.
       *
       * O Vuetify preenche esta variavel (via o "v-model") com o
       * resultado da validacao de todos os campos. E o que permite
       * desabilitar o botao "Salvar" enquanto o formulario estiver
       * invalido.
       */
      formularioValido: false,

      /**
       * Controla se a senha aparece em texto puro (o "olho" do campo).
       *
       * Comeca em false - por padrao,senha nunca e mostrada.
       */
      mostrarSenha: false,

      /**
       * Mensagem de erro vinda do servidor (ex.: "CNPJ ja cadastrado").
       */
      erroServidor: '',

      /**
       * Erros por campo vindos do servidor.
       *
       * A chave e o nome do campo, o valor e a mensagem. Exemplo:
       *
       *      { "cnpj": "O CNPJ deve ter 14 digitos." }
       *
       * O backend sempre responde nesse formato quando a validacao
       * falha (ver o GlobalExceptionHandler do lado Java).
       */
      errosCampo: {},

      /**
       * Sinaliza que o salvamento esta em andamento.
       *
       * Desabilita o botao de salvar e mostra o indicador de
       * carregamento - impedindo o usuario de clicar duas vezes e criar
       * dois registros.
       */
      salvando: false,

      /**
       * Lista de estados (UF) para o campo auxiliar.
       *
       * Este campo existe APENAS no formulario da tela: ele ajuda o
       * usuario a digitar o municipio, mas NAO faz parte do modelo de
       * dados (o banco guarda o municipio e o CEP). Por isso, ele nao
       * e enviado na requisicao - veja o metodo "salvar".
       */
      ufs: [
        'AC', 'AL', 'AM', 'AP', 'BA', 'CE', 'DF', 'ES', 'GO', 'MA', 'MG',
        'MS', 'MT', 'PA', 'PB', 'PE', 'PI', 'PR', 'RJ', 'RN', 'RO', 'RR',
        'RS', 'SC', 'SE', 'SP', 'TO'
      ]
    };
  },

  /**
   *  VALORES CALCULADOS (computed)
   */
  computed: {
    /**
     * O id veio da URL? (#/empresa/5)
     *
     * O vue-router guarda os parametros dinamicos da rota em
     * "$route.params". Como a rota foi declarada como "/empresa/:id",
     * o "id" e o pedaco final da URL.
     *
     * Quando o usuario esta na rota "/empresa" (sem id), este valor
     * e undefined - e por isso que o "emEdicao" abaixo verifica se
     * ele existe.
     */
    id() {
      return this.$route.params.id;
    },

    /**
     * Estamos editando ou incluindo?
     *
     * Basta verificar se existe um id na URL. Este computado e usado
     * em varios lugares do template (titulo do botao, texto do alerta,
     * metodo "salvar"), e ter o conceito em UM lugar evita a
     * condicao duplicada e inconsistente.
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
     *
     *  Como uma regra do vuetify funciona
     *  Cada regra e uma funcao que recebe o valor do campo e devolve:
     *      - TRUE  -> o valor esta valido;
     *      - FALSE -> invalido, sem mensagem;
     *      - STRING-> invalido, e a string e a mensagem exibida.
     *
     *  O encadeamento com "||" e o que faz a mensagem aparecer: a
     *  primeira regra que falhar "devolve" a mensagem e as seguintes
     *  nem sao avaliadas.
     *
     *  Exemplo do CNPJ:
     *      obrigatorio(value) || 'O CNPJ e obrigatorio.'
     *          || (cnpjValido(value) || 'O CNPJ deve ter 14 digitos.')
     *
     *  Se o campo estiver vazio, a primeira parte retorna a mensagem
     *  "obrigatorio" e o resto nem e avaliado. Se estiver preenchido
     *  mas invalido, a segunda parte retorna a mensagem de formato.
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
   *  CICLO DE VIDA: mounted
   *
   *  Ao entrar na tela, se for EDICAO, carrega os dados da empresa.
   *
   *  Na inclusao nao ha nada a carregar - o formulario ja comeca
   *  vazio. E por isso que a verificacao "emEdicao" e necessaria
   *  aqui: sem ela, a tela tentaria buscar a empresa de id undefined.
   */
  mounted() {
    if (this.emEdicao) {
      this.carregar();
    }
  },

  /**
   *  METODOS (methods)
   */
  methods: {
    /**
     * Objeto de formulario vazio (usado na criacao e ao limpar).
     *
     * Concentrar a lista de campos em UM metodo evita o erro classico
     * de esquecer um campo: se um campo novo for adicionado aqui, ele
     * nasce vazio nos dois lugares que usam este metodo (a criacao e o
     * reset apos o carregamento na edicao).
     *
     * @returns {object} um objeto novo, com todos os campos vazios
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

    /**
     * Mascaradores (importados do arquivo de utilitarios).
     *
     * Sao atribuidos como metodos do componente para poderem ser
     * chamados no template, por exemplo "mascaraCnpj(valor)".
     */
    mascaraCnpj,
    mascaraCep,
    mascaraTelefone,

    /**
     * Carrega a empresa que esta sendo editada.
     *
     * Repare em tres detalhes importantes:
     *
     *  1) O "Object.assign" parte do formulario VAZIO e sobrescreve
     *     apenas os campos que vieram da API. Assim, o formulario
     *     fica sempre com a estrutura completa, mesmo que a API
     *     devolva um objeto com menos campos.
     *
     *  2) O CNPJ, o CEP e o telefone sao mascarados NA EXIBICAO
     *     (o banco guarda so os digitos).
     *
     *  3) A SENHA fica vazia de proposito - a API nunca devolve a
     *     senha (nem o hash). O usuario so digita uma senha se quiser
     *     trocar a atual.
     */
    async carregar() {
      try {
        const empresa = await api.get('/empresas/' + this.id);

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
     *
     * O metodo tem QUATRO passos bem separados, e a ordem importa:
     */
    async salvar() {
      // ---- PASSO 0: limpa os erros da tentativa anterior ----
      // Sem isso, a mensagem de erro antiga ficaria na tela mesmo
      // depois de o usuario corrigir o campo.
      this.erroServidor = '';
      this.errosCampo = {};

      // ---- PASSO 1: valida no navegador ----
      // O "validate()" do v-form percorre todos os campos e devolve
      // true/false. As mensagens aparecem campo a campo, embaixo de
      // cada input.
      if (!this.$refs.formulario.validate()) {
        this.notificar('error', 'Verifique os campos destacados do formulario.');
        return;
      }

      // ---- PASSO 2: monta o corpo da requisicao ----
      // Montamos um objeto NOVO, com apenas o que a API espera. Os
      // campos que so existem na tela ("estado" e "confirmarSenha")
      // ficam de fora - e um espelho do que o backend aceitaria.
      //
      // Detalhes:
      //  - o "trim()" remove espacos das pontas;
      //  - a quantidade vazia vira null (e nao 0), para distinguir
      //    "nao informado" de "zero";
      //  - a senha vazia vira null: assim o backend entende que o
      //    usuario NAO quer trocar a senha (na alteracao), em vez de
      //    tentar validar uma string vazia.
      const corpo = {
        razaoSocial: this.formulario.razaoSocial.trim(),
        cnpj: this.formulario.cnpj,
        logadouro: this.formulario.ladouro,
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

      // ---- PASSO 3: envia para a API (POST ou PUT, conforme o modo) ----
      this.salvando = true;
      try {
        if (this.emEdicao) {
          await api.put('/empresas/' + this.id, corpo);
          this.notificar('success', 'Empresa alterada com sucesso.');
        } else {
          await api.post('/empresas', corpo);
          this.notificar('success', 'Empresa cadastrada com sucesso.');
        }

        // ---- PASSO 4: volta para a tela de listagem ----
        this.$router.push('/empresas');
      } catch (erro) {
        // Erro do servidor: mostra a mensagem e MANTEM o formulario
        // preenchido, para o usuario corrigir sem perder o que digitou.
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
