-- =============================================================================
--  01_ddl.sql  |  ESTRUTURA DO BANCO (DDL - Data Definition Language)
--  Projeto: Avaliação AZ Tecnologia - Leilões
--  Banco....: PostgreSQL 10+ (testado no 16)
--
--  PARA QUE SERVE ESTE ARQUIVO?
--  O diagrama que o avaliador entregou (database.png) mostra 5 tabelas:
--  empresa, leilao, lote, unidade e comprador.
--  Este script transforma aquele desenho em tabelas de verdade no banco:
--  cria as tabelas, as chaves primárias (PK), as chaves estrangeiras (FK),
--  as restrições de unicidade (UNIQUE), as sequências (SEQUENCE) e as
--  validações de conteúdo (CHECK).
--
--  CONVENÇÃO DE NOMENCLATURA (importante para ler o código depois)
--  O diagrama usa "camelCase" (razaoSocial, inicioPrevisto, valorInicial).
--  No banco, que roda em outro SO e em outra linguagem, adotamos o padrão
--  mais usado do mercado: "snake_case" em minusculas.
--      Diagrama (logico)  ->  Banco (fisico)
--           razaoSocial   ->      razao_social
--           inicioPrevisto->      inicio_previsto
--           valorInicial  ->      valor_inicial
--           numeroLote    ->      numero_lote
--           createdAt     ->      created_at
--           updatedAt     ->      updated_at
--  O mesmo nome logico aparece em camelCase no codigo Java e em snake_case
--  no banco, e a ligacao entre os dois fica EXPLICITA na anotacao
--  @Column(name = "razao_social") de cada entidade.
--
--  REGRA DE OURO USADA AQUI:
--  1) Todo "id" e INTEGER e nasce de uma SEQUENCE (nextval). Isso evita
--     erro de "duplicate key" quando duas pessoas cadastram ao mesmo tempo.
--  2) Respeitei os asteriscos (*) do diagrama: TODA coluna com * e NOT NULL.
--  3) Respeitei os sublinhados do diagrama: cnpj e usuario sao UNIQUE.
--  4) VALORES monetarios usam NUMERIC(14,2) e nunca FLOAT/DOUBLE, porque
--     ponto flutuante "perde" centavos (0.1 + 0.2 != 0.3). Dinheiro e
--     exato por definicao.
--  5) Todo comando esta dentro de uma TRANSACTION (BEGIN/COMMIT): se algo
--     falhar no meio, o banco volta ao estado anterior em vez de ficar
--     pela metade. Isso e "transacionalidade" e evita sujeira no banco.
--
--  COMO RODAR ESTE ARQUIVO:
--  - Via Docker (o repo ja traz docker-database.yml):
--      docker compose -f docker-database.yml up -d
--    O container do Postgres roda automaticamente TODOS os arquivos .sql
--    que estiverem na pasta /docker-entrypoint-initdb.d (o compose.yml ja
--    monta essa pasta). Por isso os arquivos comecam com 01_, 02_...
--    (a ordem numerica garante a execucao correta).
--
--  - Via psql (Postgres ja instalado na maquina):
--      psql -U postgres -d az -f 01_ddl.sql
-- =============================================================================

BEGIN;

-- -----------------------------------------------------------------------------
-- PASSO 1 - Limpeza (idempotencia)
-- -----------------------------------------------------------------------------
-- Por que apagar antes de criar? Para este script poder ser executado
-- varias vezes sem erro (com DROP IF EXISTS nao da erro se nao existir).
-- O CASCADE apaga tambem as chaves estrangeiras que apontam para as tabelas.
-- ATENCAO: em producao isso NAO deve ser executado, porque apaga dados reais.
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS tb_comprador CASCADE;
DROP TABLE IF EXISTS tb_lote      CASCADE;
DROP TABLE IF EXISTS tb_leilao    CASCADE;
DROP TABLE IF EXISTS tb_unidade   CASCADE;
DROP TABLE IF EXISTS tb_empresa   CASCADE;

DROP SEQUENCE IF EXISTS seq_empresa CASCADE;
DROP SEQUENCE IF EXISTS seq_leilao  CASCADE;
DROP SEQUENCE IF EXISTS seq_lote    CASCADE;
DROP SEQUENCE IF EXISTS seq_unidade CASCADE;

-- -----------------------------------------------------------------------------
-- PASSO 2 - Sequencias (geradores de numero automatico)
-- -----------------------------------------------------------------------------
-- Uma SEQUENCE e um "contador" do banco. Cada vez que pedimos um id novo,
-- ela entrega o proximo numero (1, 2, 3...). E isso que garante que dois
-- cadastros simultaneos nunca recebam o mesmo id.
--   INCREMENT 1  -> soma 1 a cada uso
--   MINVALUE 1   -> comeca no 1
--   MAXVALUE 9999999 -> limite de 7 digitos (bate com INTEGER)
--   START 1      -> o primeiro numero e o 1
--   CACHE 1      -> guarda 1 valor em memoria por vez (seguranca: se o
--                   servidor cair, o numero nao "pula" em bloco)
-- -----------------------------------------------------------------------------
CREATE SEQUENCE seq_empresa INCREMENT 1 MINVALUE 1 MAXVALUE 9999999 START 1 CACHE 1;
CREATE SEQUENCE seq_leilao  INCREMENT 1 MINVALUE 1 MAXVALUE 9999999 START 1 CACHE 1;
CREATE SEQUENCE seq_lote    INCREMENT 1 MINVALUE 1 MAXVALUE 9999999 START 1 CACHE 1;
CREATE SEQUENCE seq_unidade INCREMENT 1 MINVALUE 1 MAXVALUE 9999999 START 1 CACHE 1;

-- NOTA: as tabelas ainda nao existem neste ponto do script, entao o
-- vinculo "OWNED BY" (explicado no PASSO 9) e criado logo depois que as
-- cinco tabelas sao criadas.

-- -----------------------------------------------------------------------------
-- PASSO 3 - Tabela UNIDADE
-- -----------------------------------------------------------------------------
-- A "unidade" e o local fisico onde o leilao acontece (ex.: galpao A,
-- filial SP). O diagrama pede: id* (PK), nome* e as datas de controle.
-- -----------------------------------------------------------------------------
CREATE TABLE tb_unidade (
    id         INTEGER      NOT NULL DEFAULT nextval('seq_unidade'),
    nome       VARCHAR(128) NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT tb_unidade_pk      PRIMARY KEY (id),
    -- Impede duas unidades com o mesmo nome (nao e UNIQUE no desenho,
    -- mas "Leilao SP" duplicado e erro de digitacao na origem).
    CONSTRAINT tb_unidade_nome_uk UNIQUE (nome),
    -- Garante que o nome nao seja so espacos em branco.
    CONSTRAINT tb_unidade_nome_chk CHECK (btrim(nome) <> '')
);

-- -----------------------------------------------------------------------------
-- PASSO 4 - Tabela EMPRESA
-- -----------------------------------------------------------------------------
-- A empresa e a RAIZ do modelo: ela e VENDEDORA (via leilao.vendedor) e
-- tambem COMPRADORA (via tabela tb_comprador).
--
-- Colunas com * no diagrama => NOT NULL: razaoSocial, cnpj.
-- Colunas sublinhadas no diagrama => UNIQUE: cnpj, usuario.
--
-- Sobre `usuario` e `senha`: no desenho eles NAO tem asterisco, entao aqui
-- ficam opcionais no banco (e o que o diagrama pede). Em contrapartida,
-- a senha NUNCA e gravada em texto puro - a aplicacao grava apenas o
-- hash (BCrypt). Ver src/main/java/.../config/PasswordEncoderConfig.java.
-- -----------------------------------------------------------------------------
CREATE TABLE tb_empresa (
    id           INTEGER       NOT NULL DEFAULT nextval('seq_empresa'),
    razao_social VARCHAR(84)  NOT NULL,
    cnpj         VARCHAR(32)  NOT NULL,
    logadouro    VARCHAR(84),
    municipio    VARCHAR(64),
    numero       VARCHAR(10),
    complemento  VARCHAR(84),
    quantidade   INTEGER,
    cep          VARCHAR(16),
    telefone     VARCHAR(32),
    email        VARCHAR(254),
    site         VARCHAR(254),
    usuario      VARCHAR(20),
    senha        VARCHAR(128),
    created_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT tb_empresa_pk             PRIMARY KEY (id),
    CONSTRAINT tb_empresa_cnpj_uk        UNIQUE (cnpj),
    CONSTRAINT tb_empresa_usuario_uk     UNIQUE (usuario),

    -- CNPJ com 14 digitos. O diagrama nao impoe isso, mas um CNPJ valido
    -- e o que permite cruzamento de dados com a Receita Federal.
    CONSTRAINT tb_empresa_cnpj_chk       CHECK (cnpj ~ '^[0-9]{14}$'),
    -- E-mail com formato minimo (algo@algo.algo).
    CONSTRAINT tb_empresa_email_chk      CHECK (email IS NULL OR email ~* '^[^@\s]+@[^@\s]+\.[^@\s]+$'),
    -- URL comecando com http:// ou https://
    CONSTRAINT tb_empresa_site_chk       CHECK (site IS NULL OR site ~* '^https?://'),
    -- Telefone: so digitos, espaco, + e traco.
    CONSTRAINT tb_empresa_telefone_chk   CHECK (telefone IS NULL OR telefone ~ '^[0-9+() -]{8,32}$'),
    -- Quantidade (campo do diagrama) nao pode ser negativa.
    CONSTRAINT tb_empresa_quantidade_chk CHECK (quantidade IS NULL OR quantidade >= 0)
);

-- -----------------------------------------------------------------------------
-- PASSO 5 - Tabela LEILAO
-- -----------------------------------------------------------------------------
-- O leilao e o "evento" que junta um vendedor (empresa), um horario previsto
-- e varios lotes. Colunas com * no diagrama: id, codigo, vendedor.
-- -----------------------------------------------------------------------------
CREATE TABLE tb_leilao (
    id             INTEGER     NOT NULL DEFAULT nextval('seq_leilao'),
    codigo         INTEGER     NOT NULL,
    descricao      VARCHAR(80),
    vendedor       INTEGER     NOT NULL,
    inicio_previsto TIMESTAMP,
    created_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT tb_leilao_pk PRIMARY KEY (id),

    -- CHAVE ESTRANGEIRA: o vendedor tem que existir em tb_empresa.
    -- O que e isso? E uma regra que o proprio banco fiscaliza: e impossivel
    -- cadastrar um leilao apontando para uma empresa inexistente.
    -- "ON DELETE RESTRICT" = nao deixa apagar a empresa se ela tiver
    -- leilao. Escolha deliberada: apagar a vendedora quebraria o
    -- historico dos leiloes que ela vendeu.
    CONSTRAINT tb_leilao_vendedor_fk FOREIGN KEY (vendedor)
        REFERENCES tb_empresa (id) ON DELETE RESTRICT ON UPDATE CASCADE,

    CONSTRAINT tb_leilao_codigo_chk CHECK (codigo > 0)
);

-- Indice no vendedor: toda consulta de "leiloes da empresa X" usa
-- este indice e nao precisa varrer a tabela inteira. Performance.
CREATE INDEX tb_leilao_vendedor_idx ON tb_leilao (vendedor);

-- -----------------------------------------------------------------------------
-- PASSO 6 - Tabela LOTE
-- -----------------------------------------------------------------------------
-- O lote e a "casa" dentro do leilao: o que esta sendo vendido, quanto e
-- por qual valor inicial. E a partir do lote que se calcula o total do
-- leilao (soma de quantidade * valor_inicial).
-- -----------------------------------------------------------------------------
CREATE TABLE tb_lote (
    id             INTEGER        NOT NULL DEFAULT nextval('seq_lote'),
    numero_lote    INTEGER,
    descricao      VARCHAR(80),
    quantidade     INTEGER,
    valor_inicial  NUMERIC(14,2),
    unidade        VARCHAR(128),
    leilao         INTEGER        NOT NULL,
    created_at     TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT tb_lote_pk PRIMARY KEY (id),

    -- O lote SEMPRE pertence a um leilao (o * no diagrama confirma).
    CONSTRAINT tb_lote_leilao_fk FOREIGN KEY (leilao)
        REFERENCES tb_leilao (id) ON DELETE CASCADE ON UPDATE CASCADE,

    -- Quantidade e valor precisam ser coerentes para o total fazer sentido.
    CONSTRAINT tb_lote_quantidade_chk   CHECK (quantidade IS NULL OR quantidade > 0),
    CONSTRAINT tb_lote_valor_inicial_chk CHECK (valor_inicial IS NULL OR valor_inicial >= 0),
    CONSTRAINT tb_lote_numero_lote_chk   CHECK (numero_lote IS NULL OR numero_lote > 0)
);

-- Indice do FK leilao: toda listagem de lotes por leilao usa este indice.
CREATE INDEX tb_lote_leilao_idx ON tb_lote (leilao);

-- -----------------------------------------------------------------------------
-- PASSO 7 - Tabela COMPRADOR
-- -----------------------------------------------------------------------------
-- Tabela de juncao: liga "empresa" e "leilao" - ou seja, registra qual
-- empresa comprou qual leilao. Por isso a CHAVE PRIMARIA E COMPOSTA:
-- (empresa, leilao). A mesma empresa nao pode comprar o mesmo leilao
-- duas vezes, e o mesmo par nao pode se repetir.
-- No JPA isso e mapeado com @EmbeddedId (ver entity/CompradorId.java).
-- -----------------------------------------------------------------------------
CREATE TABLE tb_comprador (
    empresa   INTEGER NOT NULL,
    leilao    INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT tb_comprador_pk PRIMARY KEY (empresa, leilao),

    CONSTRAINT tb_comprador_empresa_fk FOREIGN KEY (empresa)
        REFERENCES tb_empresa (id) ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT tb_comprador_leilao_fk FOREIGN KEY (leilao)
        REFERENCES tb_leilao (id) ON DELETE CASCADE ON UPDATE CASCADE
);

-- -----------------------------------------------------------------------------
-- PASSO 9 - Vincular as sequencias as suas tabelas
-- -----------------------------------------------------------------------------
-- "OWNED BY" amarra a sequencia a uma coluna da tabela. Isso nao e
-- decoracao: e o que faz o "TRUNCATE ... RESTART IDENTITY" do script de
-- massa de dados (02_dml.sql) voltar os contadores para 1.
-- Sem esse vinculo, o PostgreSQL nao sabe que a sequencia pertence a
-- tabela e o TRUNCATE deixa os ids continuarem de onde pararam - o que
-- quebraria a massa de dados (a empresa de id 1 deixaria de existir).
-- Alem disso, com o vinculo feito, o DROP TABLE apaga a sequencia junto.
-- -----------------------------------------------------------------------------
ALTER SEQUENCE seq_empresa OWNED BY tb_empresa.id;
ALTER SEQUENCE seq_leilao  OWNED BY tb_leilao.id;
ALTER SEQUENCE seq_lote    OWNED BY tb_lote.id;
ALTER SEQUENCE seq_unidade OWNED BY tb_unidade.id;

-- -----------------------------------------------------------------------------
-- PASSO 10 - Fim da transacao
-- -----------------------------------------------------------------------------
-- COMMIT confirma tudo de uma vez. Se qualquer comando acima tivesse
-- falhado, o ROLLBACK automatico devolveria o banco ao estado inicial.
-- -----------------------------------------------------------------------------
COMMIT;

-- =============================================================================
--  NOTA DE SEGURANCA
--  Nenhum usuario/senha do banco aparece neste script, e nenhuma senha do
--  avaliador esta versionada no Git. Para rodar localmente, use as
--  credenciais do seu proprio PostgreSQL (veja README.md, secao "Como rodar").
--  Para separar o usuario da aplicacao do dono do banco, veja o script
--  opcional sql/seguranca/03_permissoes.sql (principio do menor privilegio).
-- =============================================================================
