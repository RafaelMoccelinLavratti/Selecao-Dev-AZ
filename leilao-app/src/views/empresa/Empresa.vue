<template>
  <!--
     Cadastro de empresa (views/empresa/Empresa.vue).

     Exercicio 8 - inclusao e alteracao, validando os campos obrigatorios,
     as mascaras e os formatos de numero, email, url e tamanho maximo.

     Inclusao: #/empresa
     Edicao..: #/empresa/:id
     Servicos: /empresas (GET | POST | PUT | DELETE)

     A mesma tela faz inclusao e alteracao. O que define o modo e a
     presenca do id na URL: com id a tela carrega os dados e o botao diz
     "Alterar"; sem id o formulario comeca vazio e o botao diz "Cadastrar".
     Assim nao duplicamos o mesmo formulario em duas telas, e qualquer
     correcao de layout vale pras duas.
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
        Formulario. O "v-form" cuida da validacao: cada campo tem suas
        "rules", e o "ref" dispara a validacao de todos de uma vez
        ("this.$refs.formulario.validate()").
      -->
      <v-form ref="formulario" v-model="formularioValido" @submit.prevent="salvar">
        <v-card-text>
          <!-- dados da empresa -->
          <div class="text-subtitle-1 font-weight-medium mb-3" style="color: var(--az-azul-900);">
            <v-icon small left color="primary">mdi-domain</v-icon>
            Dados da empresa
          </div>

          <v-row dense>
            <!-- razao social (obrigatoria) -->
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

            <!-- cnpj (obrigatorio e unico) -->
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
            <!-- logradouro -->
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

            <!-- cep (com mascara) -->
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

            <!-- uf (campo auxiliar, nao existe no banco) -->
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

          <!-- contato -->
          <div class="text-subtitle-1 font-weight-medium mt-4 mb-3" style="color: var(--az-azul-900);">
            <v-icon small left color="primary">mdi-card-text</v-icon>
            Contato
          </div>

          <v-row dense>
            <!-- telefone (com mascara e validacao de formato) -->
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

            <!-- email (validacao de formato) -->
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

            <!-- site (validacao de URL) -->
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

          <!-- acesso -->
          <div class="text-subtitle-1 font-weight-medium mt-4 mb-3" style="color: var(--az-azul-900);">
            <v-icon small left color="primary">mdi-lock</v-icon>
            Acesso
          </div>

          <v-row dense>
            <!-- usuario (unico no banco) -->
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
            Erro do servidor (ex.: "CNPJ ja cadastrado"). Aparece aqui, perto
            do botao de salvar, e nao perdido no console.
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

        <!-- botoes -->
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
 * A mesma tela atende inclusao e edicao: quem define o modo e a URL
 * (#/empresa inclui, #/empresa/{id} edita).
 *
 * As regras do formulario espelham as do backend (EmpresaRequest.java),
 * pro usuario nunca ver "a tela aceitou e a API recusou".
 *
 * A senha nunca volta da API, entao ao carregar um registro pra edicao o
 * campo fica vazio e a senha so e exigida quando o usuario troca.
 */

import api from '../../services/api';

// Todas as mascaras e funcoes de validacao ficam num arquivo so
// (utils/mascaras.js), pra mesma mascara e mesma regra aparecerem iguais
// em todas as telas, e pra existir um lugar unico de correcao.
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
      // O formulario: um objeto com todos os campos da empresa. Chamar um
      // metodo aqui dentro funciona porque o "data" roda uma unica vez, na
      // criacao do componente (nao da pra usar "this" ainda).
      formulario: this.formularioVazio(),

      // Resultado da validacao do v-form. E o que permite desabilitar o
      // botao "Salvar" enquanto o formulario estiver invalido.
      formularioValido: false,

      // Se a senha aparece em texto puro (o "olho" do campo).
      mostrarSenha: false,

      // Mensagem de erro vinda do servidor.
      erroServidor: '',

      // Erros por campo vindos do servidor. A chave e o nome do campo e o
      // valor e a mensagem:
      //
      //      { "cnpj": "O CNPJ deve ter 14 digitos." }
      //
      // O backend sempre responde nesse formato quando a validacao falha.
      errosCampo: {},

      // Salvamento em andamento: desabilita o botao e mostra o indicador,
      // impedindo o usuario de clicar duas vezes e criar dois registros.
      salvando: false,

      // Lista de estados (UF) pro campo auxiliar. Ele existe so na tela,
      // pra ajudar a digitar o municipio: o banco guarda o municipio e o
      // CEP. Por isso nao vai na requisicao (veja o metodo "salvar").
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
     *
     * O vue-router guarda os parametros dinamicos da rota em
     * "$route.params". Como a rota foi declarada como "/empresa/:id", o
     * "id" e o pedaco final da URL. Na rota "/empresa" ele vem undefined.
     */
    id() {
      return this.$route.params.id;
    },

    /**
     * Estamos editando ou incluindo? Basta ver se existe id na URL. Este
     * computado e usado em varios lugares do template, e ter o conceito
     * num lugar so evita condicao duplicada e inconsistente.
     */
    emEdicao() {
      return !!this.id;
    },

    /**
     * Regras de validacao do formulario. Espelham as do backend
     * (EmpresaRequest.java), pra o usuario nunca ver "a tela aceitou e a
     * API recusou".
     *
     * Cada regra do Vuetify e uma funcao que recebe o valor do campo e
     * devolve true (valido), false (invalido, sem mensagem) ou uma string
     * (invalido, e a string e a mensagem exibida). O encadeamento com "||"
     * e o que faz a mensagem aparecer: a primeira regra que falha devolve
     * a mensagem e as seguintes nem sao avaliadas. Exemplo do CNPJ:
     *
     *      obrigatorio(value) || 'O CNPJ e obrigatorio.'
     *          || (cnpjValido(value) || 'O CNPJ deve ter 14 digitos.')
     *
     * Se o campo estiver vazio, a primeira parte devolve a mensagem de
     * "obrigatorio" e o resto nem e avaliado. Se estiver preenchido mas
     * invalido, entra a mensagem de formato.
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
        // A confirmacao so e exigida quando ha senha nova pra confirmar.
        confirmarSenha: value => (!this.formulario.senha || value === this.formulario.senha)
          || 'As senhas nao conferem.'
      };
    }
  },

  /**
   * Na edicao, carrega os dados da empresa. Na inclusao nao ha nada a
   * carregar - o formulario ja comeca vazio. Sem a verificacao "emEdicao",
   * a tela tentaria buscar a empresa de id undefined.
   */
  mounted() {
    if (this.emEdicao) {
      this.carregar();
    }
  },

  methods: {
    /**
     * Objeto de formulario vazio (usado na criacao e ao limpar).
     *
     * Concentrar a lista de campos num metodo so evita o erro classico de
     * esquecer um campo: um campo novo nasce vazio nos dois lugares que
     * usam este metodo.
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

    // Mascaradores importados do arquivo de utilitarios, atribuidos como
    // metodos pra poderem ser chamados no template.
    mascaraCnpj,
    mascaraCep,
    mascaraTelefone,

    /**
     * Carrega a empresa que esta sendo editada. Tres detalhes:
     *
     *  1) o "Object.assign" parte do formulario vazio e sobrescreve so os
     *     campos que vieram da API, pra estrutura ficar sempre completa;
     *
     *  2) CNPJ, CEP e telefone sao mascarados na exibicao (o banco guarda
     *     so os digitos);
     *
     *  3) a senha fica vazia de proposito: a API nunca devolve a senha nem
     *     o hash, e o usuario so digita uma senha se quiser trocar a atual.
     */
    async carregar() {
      try {
        const empresa = await api.get('/empresas/' + this.id);

        this.formulario = Object.assign(this.formularioVazio(), {
          razaoSocial: empresa.razaoSocial || '',
          cnpj: mascaraCnpj(empresa.cnpj || ''),
          logadouro: empresa.logradouro || '',
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
     * Valida e envia o formulario. A ordem dos passos importa.
     */
    async salvar() {
      // limpa os erros da tentativa anterior, senao a mensagem antiga
      // ficaria na tela depois do usuario corrigir o campo
      this.erroServidor = '';
      this.errosCampo = {};

      // valida no navegador: o "validate()" percorre todos os campos e as
      // mensagens aparecem abaixo de cada input
      if (!this.$refs.formulario.validate()) {
        this.notificar('error', 'Verifique os campos destacados do formulario.');
        return;
      }

      // monta um corpo novo, com so o que a API espera. Os campos que so
      // existem na tela ("estado" e "confirmarSenha") ficam de fora.
      //   - o "trim()" remove espacos das pontas;
      //   - quantidade vazia vira null (e nao 0), pra distinguir "nao
      //     informado" de "zero";
      //   - senha vazia vira null, e assim o backend entende que o usuario
      //     nao quer trocar a senha.
      const corpo = {
        razaoSocial: this.formulario.razaoSocial.trim(),
        cnpj: this.formulario.cnpj,
        logadouro: this.formulario.logradouro,
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

      // envia (POST ou PUT, conforme o modo)
      this.salvando = true;
      try {
        if (this.emEdicao) {
          await api.put('/empresas/' + this.id, corpo);
          this.notificar('success', 'Empresa alterada com sucesso.');
        } else {
          await api.post('/empresas', corpo);
          this.notificar('success', 'Empresa cadastrada com sucesso.');
        }

        // volta pra tela de listagem
        this.$router.push('/empresas');
      } catch (erro) {
        // Mantem o formulario preenchido, pro usuario corrigir sem perder
        // o que digitou.
        this.erroServidor = erro.mensagem || 'Erro ao salvar a empresa.';
        if (erro.campos) {
          this.errosCampo = erro.campos;
        }
        this.notificar('error', this.erroServidor);
      } finally {
        this.salvando = false;
      }
    },

    // Atalho pra exibir a notificacao global.
    notificar(tipo, mensagem) {
      if (this.$root.$refs.notificacoes) {
        this.$root.$refs.notificacoes.exibir(tipo, mensagem);
      }
    }
  }
};
</script>
