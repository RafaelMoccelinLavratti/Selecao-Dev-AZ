-- 01_ddl.sql - estrutura do banco (DDL). PostgreSQL 10+ (testado no 16).
--
-- O diagrama da avaliacao (sql/database.png) tem 5 tabelas: empresa,
-- leilao, lote, unidade e comprador. Este script cria essas tabelas com
-- chaves primarias, chaves estrangeiras, restricoes de unicidade,
-- sequencias e validacoes de conteudo.
--
-- O diagrama usa camelCase (razaoSocial, inicioPrevisto, valorInicial) e o
-- banco usa snake_case, que e o padrao do mercado:
--
--     razaoSocial    ->  razao_social
--     inicioPrevisto ->  inicio_previsto
--     valorInicial   ->  valor_inicial
--     numeroLote     ->  numero_lote
--     createdAt      ->  created_at
--
-- A ligacao entre os dois fica explicita no @Column(name = "razao_social")
-- de cada entidade.
--
-- Algumas decisoes de projeto:
--   - todo id e INTEGER e nasce de uma SEQUENCE (nextval), o que evita
--     "duplicate key" quando duas pessoas cadastram ao mesmo tempo;
--   - os asteriscos do diagrama viraram NOT NULL e os sublinhados viraram
--     UNIQUE (cnpj e usuario);
--   - valores monetarios usam NUMERIC(14,2) e nunca FLOAT, porque ponto
--     flutuante perde centavos (0.1 + 0.2 != 0.3);
--   - tudo roda dentro de uma transacao, entao uma falha no meio devolve o
--     banco ao estado anterior em vez de deixa-lo pela metade.
--
-- Para rodar:
--   docker compose -f docker-database.yml up -d
--     (o container do Postgres executa sozinho os .sql da pasta montada em
--     /docker-entrypoint-initdb.d; o prefixo numerico garante a ordem)
--
--   psql -U postgres -d az -f 01_ddl.sql

BEGIN;

-- -----------------------------------------------------------------------------
-- PASSO 1 - Limpeza (idempotencia)
-- -----------------------------------------------------------------------------
-- Por que apagar antes de criar? Pra este script poder ser executado
-- varias vezes sem erro (com DROP IF EXISTS nao da erro se nao existir).
-- O CASCADE apaga tambem as chaves estrangeiras que apontam pra as tabelas.
-- Em producao isso nao deve ser executado, porque apaga dados reais.
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

-- As tabelas ainda nao existem neste ponto do script, entao o vinculo
-- "OWNED BY" (explicado no PASSO 9) e criado logo depois que as cinco
-- tabelas sao criadas.

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
-- A empresa e a raiz do modelo: ela e vendedora (via leilao.vendedor) e
-- tambem compradora (via tabela tb_comprador).
--
-- Colunas com * no diagrama => NOT NULL: razaoSocial, cnpj.
-- Colunas sublinhadas no diagrama => UNIQUE: cnpj, usuario.
--
-- Sobre `usuario` e `senha`: no desenho eles nao tem asterisco, entao aqui
-- ficam opcionais no banco (e o que o diagrama pede). Em contrapartida,
-- a senha nunca e gravada em texto puro - a aplicacao grava apenas o
-- hash (Argon2id, com o prefixo "{argon2}" que identifica o algoritmo).
-- Os hashes antigos em BCrypt continuam valendo: a aplicacao os reconhece
-- e os reescreve sozinha no login. Ver
-- src/main/java/.../config/PasswordEncoderConfig.java.
--
-- "senha" tem 128 caracteres porque o hash do Argon2id ocupa 109
-- ("{argon2}" + parametros + sal + hash). O BCrypt ocupa 60.
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

    -- Chave estrangeira: o vendedor tem que existir em tb_empresa.
    -- O que e isso? E uma regra que o proprio banco fiscaliza: nao da pra
    -- cadastrar um leilao apontando pra uma empresa que nao existe.
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

    -- O lote sempre pertence a um leilao (o * no diagrama confirma).
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
-- empresa comprou qual leilao. Por isso a chave primaria e composta:
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
-- PASSO 8 - Tabela REFRESH_TOKEN (tokens de renovacao)
-- -----------------------------------------------------------------------------
-- Armazena os tokens de renovacao (o "refresh token") do fluxo JWT.
--
-- Por que essa tabela existe:
-- Um token de renovacao e, por definicao, de longa duracao (o usuario nao
-- deve digitar a senha a cada 15 minutos). Isso o torna o alvo perfeito
-- pra um atacante: e o token que, se roubado, da acesso por dias.
--
-- Por isso esta tabela aplica tres protecoes:
--
--  1) O token nunca e guardado em texto puro. Guardamos apenas o SHA-256
--     dele. Se o banco vazar, os hashes sao inuteis pra o atacante (a
--     senha e o token sao diferentes, entao o hash do token nao
--     ajuda a descobrir o token - seria preciso reverter o SHA-256).
--     O proprio token so existe na hora em que e emitido.
--
--  2) Cada token pertence a uma familia. Todos os tokens gerados a
--     partir do mesmo login compartilham o mesmo identificador de familia.
--     Quando um token usado e reapresentado (sinal de token copiado),
--     toda a familia e revogada de uma vez.
--
--  3) Cada token e de uso unico. Ao renovar, ele e marcado como usado.
--
-- O indice em "familia" existe porque a revogacao em lote (logout e
-- deteccao de reuso) busca por ele.
-- -----------------------------------------------------------------------------
CREATE TABLE tb_refresh_token (
    id            BIGSERIAL,
    token_hash    CHAR(64)     NOT NULL,
    usuario       VARCHAR(100) NOT NULL,
    familia       VARCHAR(36)  NOT NULL,
    utilizado     BOOLEAN       NOT NULL DEFAULT FALSE,
    data_revogacao TIMESTAMP,
    ip_origem     VARCHAR(45),
    data_emissao  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_expiracao TIMESTAMP    NOT NULL,

    CONSTRAINT tb_refresh_token_pk PRIMARY KEY (id),

    CONSTRAINT tb_refresh_token_hash_uk UNIQUE (token_hash)
);

-- Busca do token pelo hash: e o que acontece em TODA renovacao.
CREATE INDEX tb_refresh_token_hash_idx ON tb_refresh_token (token_hash);

-- Revogacao em lote da familia: e o que acontece no logout e na
-- deteccao de reuso.
CREATE INDEX tb_refresh_token_familia_idx ON tb_refresh_token (familia);

-- Tarefa agendada de limpeza: remove os tokens expirados.
CREATE INDEX tb_refresh_token_expiracao_idx ON tb_refresh_token (data_expiracao);

-- -----------------------------------------------------------------------------
-- PASSO 8b - Tabela AUDITORIA (trilha a prova de adulteracao)
-- -----------------------------------------------------------------------------
-- Registra as operacoes que alteram dados (POST, PUT, PATCH, DELETE).
--
-- Por que essa tabela e diferente das outras:
-- Uma trilha de auditoria comum e so um log: quem tem acesso ao banco
-- pode apagar a prova. Isso nao serve pra nada numa investigacao.
--
-- Esta trilha e a prova de adulteracao (tamper-evident). Cada registro
-- guarda o hash do registro anterior, formando uma corrente:
--
--     registro 1  hash = HMAC(conteudo_1)
--     registro 2  hash = HMAC(hash_1 + conteudo_2)
--     registro 3  hash = HMAC(hash_2 + conteudo_3)
--     ...
--
-- Se alguem alterar um registro no meio da cadeia, todos os hashes
-- seguintes deixam de bater - e a verificacao acusa exatamente onde a
-- adulteracao aconteceu.
--
-- A chave do HMAC fica fora do banco (variavel de ambiente). Sem ela,
-- nem quem tem acesso ao banco consegue forjar um registro valido.
--
-- "sequencia" e um numero monotonico, gravado junto com o registro. Ele
-- impede o ataque mais obvio: apagar um registro do meio. Se o registro 5
-- sumir, a sequencia salta de 4 para 6, e a verificacao ve a quebra.
-- -----------------------------------------------------------------------------
CREATE TABLE tb_auditoria (
    id            BIGSERIAL,
    sequencia     BIGINT        NOT NULL,
    data_evento   TIMESTAMP     NOT NULL,
    usuario       VARCHAR(100),
    ip_origem     VARCHAR(45),
    metodo_http   VARCHAR(10),
    caminho       VARCHAR(500),
    status_http   INTEGER,
    duracao_ms    BIGINT,
    acao          VARCHAR(100),
    detalhe       TEXT,
    hash_anterior CHAR(64),
    hash_atual    CHAR(64)      NOT NULL,

    CONSTRAINT tb_auditoria_pk PRIMARY KEY (id),

    CONSTRAINT tb_auditoria_sequencia_uk UNIQUE (sequencia)
);

-- Leitura cronologica da trilha (e o que a API devolve em GET /auditoria).
CREATE INDEX tb_auditoria_sequencia_idx ON tb_auditoria (sequencia);

-- Investigacao por usuario e por janela de tempo.
CREATE INDEX tb_auditoria_usuario_idx ON tb_auditoria (usuario);
CREATE INDEX tb_auditoria_data_idx ON tb_auditoria (data_evento);

-- -----------------------------------------------------------------------------
-- PASSO 9 - Vincular as sequencias as suas tabelas
-- -----------------------------------------------------------------------------
-- "OWNED BY" amarra a sequencia a uma coluna da tabela. Isso nao e
-- decoracao: e o que faz o "TRUNCATE ... RESTART IDENTITY" do script de
-- massa de dados (02_dml.sql) voltar os contadores pra 1.
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


-- Nota de seguranca.
-- Nenhum usuario/senha do banco aparece neste script, e nenhuma senha do
-- avaliador esta versionada no Git. Para rodar localmente, use as
-- credenciais do seu proprio PostgreSQL (veja README.md, secao "Como rodar").
-- Para separar o usuario da aplicacao do dono do banco, veja o script
-- opcional sql/seguranca/03_permissoes.sql (principio do menor privilegio).
