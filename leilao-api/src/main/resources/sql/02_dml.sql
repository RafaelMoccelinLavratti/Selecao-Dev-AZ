-- 02_dml.sql - massa de dados inicial (DML).
--
-- A avaliacao pede "uma massa de dados inicial para permitir que a aplicacao
-- seja testada, com pelo menos 10 registros em cada tabela". Este script
-- cria 12 empresas, 12 unidades, 12 leiloes, 18 lotes e 14 compradores, com
-- as chaves estrangeiras apontando para registros que existem. E o que
-- permite abrir a aplicacao e ver telas com conteudo real sem cadastrar
-- nada na mao.
--
-- Sobre as senhas: a coluna "senha" da tb_empresa guarda um hash, nunca a
-- senha em texto puro. Se o banco vazar, o atacante leva hashes e nao as
-- senhas. Cada empresa da massa de teste tem uma senha diferente, no padrao
-- "Senha@<id>", mas o que fica gravado e so o hash.
--
-- Os hashes gravados aqui ainda estao no formato BCrypt legado ("$2a$10$...").
-- Isso e de proposito: o codificador delegado reconhece esse formato e
-- reescreve cada hash no Argon2id no primeiro login. E o que permite a
-- migracao acontecer sozinha, sem script e sem janela de manutencao.
--
-- Para gerar os hashes:
--   mvn test -Dtest=GeradorHashSenhaTest
--
-- Para conferir os hashes que ja estao no script:
--   mvn test -Dtest=GeradorHashSenhaTest#verificarHashesDoScriptDeDados
--
-- Para rodar:
--   docker compose -f docker-database.yml up -d   (ja roda sozinho)
--   psql -U postgres -d az -f 02_dml.sql
--
-- O script e idempotente: pode rodar quantas vezes quiser, porque primeiro
-- limpa a massa de teste (TRUNCATE ... CASCADE) e so depois insere.

BEGIN;

-- -----------------------------------------------------------------------------
-- PASSO 1 - Limpeza da massa de teste
-- -----------------------------------------------------------------------------
-- TRUNCATE apaga os dados de forma rápida e, com CASCADE, apaga também
-- os registros das tabelas que referenciam estas. A ordem não importa
-- por causa do CASCADE.
-- RESTART IDENTITY reinicia as sequências (o próximo id volta a ser 1),
-- o que deixa o banco sempre igual ao estado "de fábrica" do teste.
-- -----------------------------------------------------------------------------
TRUNCATE TABLE tb_comprador, tb_lote, tb_leilao, tb_unidade, tb_empresa
    RESTART IDENTITY CASCADE;

-- -----------------------------------------------------------------------------
-- PASSO 2 - UNIDADES (12 registros)
-- -----------------------------------------------------------------------------
-- São os locais onde os leiloes acontecem. O nome tem restrição UNIQUE,
-- entao todos os 12 nomes sao diferentes.
-- -----------------------------------------------------------------------------
INSERT INTO tb_unidade (nome) VALUES
    ('Unidade Matriz - Sao Paulo/SP'),
    ('Galpao Industrial - Barueri/SP'),
    ('Centro de Distribuicao - Campinas/SP'),
    ('Armazem Juridica - Osasco/SP'),
    ('Unidade Norte - Sao Jose dos Campos/SP'),
    ('Deposito Sul - Curitiba/PR'),
    ('Galpao_logistico - Joinville/SC'),
    ('Unidade Centro-Oeste - Goiania/GO'),
    ('Porto de Carga - Santos/SP'),
    ('Centro de Distribuicao - Rio de Janeiro/RJ'),
    ('Unidade Nordeste - Recife/PE'),
    ('Armazem Norte - Manaus/AM');

-- -----------------------------------------------------------------------------
-- PASSO 3 - EMPRESAS (12 registros)
-- -----------------------------------------------------------------------------
-- Cada empresa e vendedora e/ou compradora de leiloes.
--   cnpj    -> 14 digitos, todos validos (digitos verificadores conferidos)
--   usuario -> unico (restricao UNIQUE do banco)
--   senha   -> hash (nunca texto puro)
-- -----------------------------------------------------------------------------
INSERT INTO tb_empresa
    (razao_social, cnpj, logadouro, municipio, numero, complemento,
     quantidade, cep, telefone, email, site, usuario, senha) VALUES
    ('Metalurgica Aurora Ltda',              '11422377000155', 'Rua das Industrias',   'Sao Paulo',        '1200', 'Bloco A', 120, '01310-100', '1130001100', 'contato@metalurgicaaurora.com.br',  'https://www.metalurgicaaurora.com.br',  'admin.aurora',   '$2a$10$U5nhn1Beb0lJjcfhGYPYbeyv6m6LI0za9cntAUZ0EYJMuft7y17JO'),
    ('Transportes Vale Verde S.A.',           '11422377000236', 'Avenida das Palmeiras', 'Campinas',         '450',  'Galpao 3', 85,  '13045-220', '1932040500', 'vendas@valeverde.com.br',            'https://valeverde.com.br',              'operador.valverde','$2a$10$fE0RlbYIvPdHAbYQHqFcHObPN0tpr6hJEzElFMho7sIMbecsQvnDC'),
    ('Agroindustrial Boa Vista S.A.',         '11422377000317', 'Rodovia BR-153 km 42',   'Ribeirão Preto',   's/n',  NULL,        300, '14400-000', '1632048800', 'sac@boavista.agr.br',              'https://boavista.agr.br',               'admin.boavista',  '$2a$10$opvcsHxqIzc5/1/vQmh7o.yabqdznx6YkADVlrTHTl8EeBow.Vyzy'),
    ('Distribuidora Central do Brasil Ltda',  '11422377000406', 'Rua Bela Vista',         'Rio de Janeiro',   '230',  'Sala 1201', 500, '20040-002', '2130002200', 'contato@distcentral.com.br',         'https://distcentral.com.br',            'comercial.central','$2a$10$Mkn0bb8jbf4sW1kqIMqjseApmC8VKAZcZADJLC3qcSJBe7KJCzdea'),
    ('Comercio de Eletrodomésticos Norte',    '11422377000589', 'Avenida Beira Mar',      'Recife',          '780',  'Loja 4',    40,  '54400-120', '8130003300', 'sac@eletronorte.com.br',             'https://eletronorte.com.br',            'gerencia.eletronorte','$2a$10$wndd/XwQRvWYL/kNFwgGOeI6pDs7i6AIw/8jVJiXEUBBMGXLntbd2'),
    ('Petroquimica Nova Era Ltda',            '11422377000660', 'Rua das Industrias',     'Duque de Caxias',  '95',   'Galpao 2', 150, '25000-000', '2130004400', 'comercial@novaera.com.br',           'https://novaera.com.br',                'root.novaera',   '$2a$10$K/pUaWvixVzm3Pjhc/LYKesCi/oHfH0DLCZ7tZnrml2su9yiO.RPa'),
    ('Frigorifico Serra Azul S.A.',           '11422377000740', 'Rodovia BR-116 km 9',    'Juiz de Fora',     's/n',  NULL,        80,  '36000-000', '3232045500', 'exportacao@serraazul.com.br',         'https://serraazul.com.br',              'comercial.serraazul','$2a$10$LusBN868fkFPBJvt2bC0Te0VQF55Ti96y4S05ooBblBqNpwvWsT4C'),
    ('Ceramica Tijolo Preto Ltda',            '11422377000821', 'Estrada do tijolo',     'Goiania',         '2000', 'Bloco B',   60,  '74000-000', '6230006600', 'contato@tijolopreto.com.br',         'https://tijolopreto.com.br',            'admin.tijolopreto','$2a$10$TR3DdkIMJ9J6yBz6nlHcJOOmxbXbuCUlFLqglcCYzI30E/F/PufBi'),
    ('Papelaria Guanabara Ltda',              '11422377000902', 'Rua da Praia',           'Rio de Janeiro',   '310',  'Sala 4',    25,  '22000-000', '2130007700', 'sac@guanabara.com.br',              'https://guanabara.com.br',              'vendas.guanabara','$2a$10$So1AsyI.IeyzV4TsB7iPzO.FufLYmuUwBjN/N0Ijwz.hkfoUBcfbi'),
    ('Madeiras do Sul Ltda',                  '11422377001046', 'Estrada do Barreiro',    'Curitiba',         '7700', 'Galpao 7', 110, '81200-000', '4130008800', 'comercial@madeirassul.com.br',        'https://madeirassul.com.br',             'admin.madeirassul','$2a$10$PsZWXHMg.gx/lSWElK/nKecvAR7bpEZjX7qn0YG2FUq6lhzhCgGNu'),
    ('Mineracao Serra do Vale Ltda',          '11422377001127', 'Rodovia dos Minerios',   'Belo Horizonte',   '12',   'Sede',      200, '30000-000', '3130009900', 'sac@serradovale.com.br',            'https://serradovale.com.br',            'operacao.serradovale','$2a$10$rG7.zndSTA0Dz0RnWQfafuUInbxTkhRC4eAaNuPDqDdiZ80hcC/oa'),
    ('Eletrobras Refrigeracao Ltda',          '11422377001208', 'Avenida das Nacoes',     'Manaus',          '1450', 'Galpao 12',70,  '69000-000', '9230001100', 'contato@refrigamazonia.com.br',       'https://refrigamazonia.com.br',         'admin.refrigamazonia','$2a$10$jHosFNs0dq4QXkd.fQDpO.kFzhMFAqbaAs2vsiqA49JF8fwpadHcW');

-- -----------------------------------------------------------------------------
-- PASSO 4 - LEILOES (12 registros)
-- -----------------------------------------------------------------------------
-- "vendedor" aponta para o id de uma empresa existente (FK).
-- "inicioPrevisto" e o horario previsto do inicio do leilao.
-- -----------------------------------------------------------------------------
INSERT INTO tb_leilao (codigo, descricao, vendedor, inicio_previsto) VALUES
    (1001, 'Leilao de maquinas industriais',        1,  '2026-11-05 09:00:00'),
    (1002, 'Leilao de componentes eletronicos',     2,  '2026-11-06 14:00:00'),
    (1003, 'Leilao de graos e insumos agricolas',   3,  '2026-11-09 10:30:00'),
    (1004, 'Leilao de eletrodomesticos usados',     5,  '2026-11-10 15:00:00'),
    (1005, 'Leilao de residuo industrial',          6,  '2026-11-12 09:00:00'),
    (1006, 'Leilao de tratores e implementos',      7,  '2026-11-13 13:00:00'),
    (1007, 'Leilao de material de construcao',      8,  '2026-11-16 10:00:00'),
    (1008, 'Leilao de moveis e papelaria',          9,  '2026-11-17 14:30:00'),
    (1009, 'Leilao de madeira e Derivatives',        10, '2026-11-18 09:30:00'),
    (1010, 'Leilao de minios de ferro',              11, '2026-11-20 11:00:00'),
    (1011, 'Leilao de refrigeracao e clima',        12, '2026-11-23 15:30:00'),
    (1012, 'Leilao de insumos diversos',           4,  '2026-11-25 10:00:00');

-- -----------------------------------------------------------------------------
-- PASSO 5 - LOTES (18 registros)
-- -----------------------------------------------------------------------------
-- Cada lote pertence a um leilao existente.
-- "valor_inicial" e o preco inicial de cada unidade do lote.
-- O total do leilao (mostrado na tela do exercicio 7) e a soma de
-- (quantidade * valor_inicial) de todos os lotes daquele leilao.
--
-- Exemplos de calculo:
--   Leilao 1001 -> 5 * 12.500,00 + 2 * 8.750,00 = 80.000,00
--   Leilao 1002 -> 40 * 250,00 + 25 * 1.990,00 = 59.750,00
-- -----------------------------------------------------------------------------
INSERT INTO tb_lote (numero_lote, descricao, quantidade, valor_inicial, unidade, leilao) VALUES
    -- Leilao 1001
    (1, 'Torno CNC 3000 series',          5,  12500.00, 'Torno CNC serie 3000 usado',        1),
    (2, 'Furadeira de bancada industrial', 2,   8750.00, 'Furadeira de bancada 220V',         1),
    -- Leilao 1002
    (1, 'Placa de video GT 1660 SUPER',   40,    250.00, 'Placa de video 6GB GDDR6',          2),
    (2, 'Monitor LED 24 polegadas',       25,   1990.00, 'Monitor LED 24" 144Hz',              2),
    -- Leilao 1003
    (1, 'Fertilizante nitrogenado 45%',   120,    185.00, 'Fertilizante ureia granulada',       3),
    (2, 'Semente de milho hibrida',        80,    640.00, 'Semente de milho hybride ciclo 5',   3),
    -- Leilao 1004
    (1, 'Geladeira 340 litros',            60,    890.00, 'Geladeira frost free 340L',          4),
    (2, 'Maquina de lavar 11 kg',         35,    720.00, 'Lavadora de roupas 11kg',            4),
    -- Leilao 1005
    (1, 'Tambor de aco carbono 200L',      18,   1450.00, 'Tambor de aco 200 litros',           5),
    (2, 'Betoneira 400 litros',            30,    620.00, 'Betoneira 400L semeado',             5),
    -- Leilao 1006
    (1, 'Bovino de corte',                 45,   2380.00, 'Lote de bovinos de corte',            6),
    (2, 'Frango congelado resfriado',      22,   1150.00, 'Pallet de frango congelado',         6),
    -- Leilao 1007
    (1, 'Tijolo ceramico vermelho',       150,    135.00, 'Tijolo ceramico 11x5x20',            7),
    (2, 'Cimento saco 50 kg',              90,    385.00, 'Cimento portland CPII 50kg',          7),
    -- Leilao 1008
    (1, 'Mesa de reuniao 12 lugares',       12,   2240.00, 'Mesa de reuniao em madeira',         8),
    (2, 'Ar condicionado splits 12 mil BTU', 25,  1750.00, 'Split hi-wall 12000 BTU',            8),
    -- Leilao 1009
    (1, 'Tabua de pinus 20cm',             200,    320.00, 'Tabua de pinus serrada 20x30cm',      9),
    (2, 'Piso laminado click',              70,    295.00, 'Piso laminado AC5 8mm',             9);

-- -----------------------------------------------------------------------------
-- PASSO 6 - COMPRADORES (14 registros)
-- -----------------------------------------------------------------------------
-- Tabela de junção: cada linha diz "a empresa X comprou o leilao Y".
-- A chave primaria e composta (empresa, leilao) - por isso nao existe
-- uma coluna "id" nesta tabela.
-- -----------------------------------------------------------------------------
INSERT INTO tb_comprador (empresa, leilao) VALUES
    (2,  1),
    (3,  1),
    (4,  1),
    (5,  2),
    (6,  2),
    (7,  2),
    (8,  3),
    (9,  3),
    (10, 3),
    (11, 4),
    (12, 4),
    (1,  5),
    (4,  6),
    (9,  7);

-- -----------------------------------------------------------------------------
-- PASSO 7 - Confirmacao
-- -----------------------------------------------------------------------------
COMMIT;


-- Como verificar que a massa de dados foi carregada.
-- Rode estas consultas depois de executar o script:
--
--   SELECT 'empresa' AS tabela, count(*) FROM tb_empresa
--   UNION ALL SELECT 'leilao',  count(*) FROM tb_leilao
--   UNION ALL SELECT 'lote',    count(*) FROM tb_lote
--   UNION ALL SELECT 'unidade', count(*) FROM tb_unidade
--   UNION ALL SELECT 'comprador', count(*) FROM tb_comprador;
--
-- O esperado e: 12 / 12 / 18 / 12 / 14 - ou seja, todas as tabelas com
-- pelo menos 10 registros, como o enunciado pede.
--
-- Para conferir o total de um leilao (mesma conta que a tela faz):
--   SELECT l.id, l.codigo, sum(lo.quantidade * lo.valor_inicial) AS total
--     FROM tb_leilao l JOIN tb_lote lo ON lo.leilao = l.id
--    GROUP BY l.id, l.codigo ORDER BY l.id;
