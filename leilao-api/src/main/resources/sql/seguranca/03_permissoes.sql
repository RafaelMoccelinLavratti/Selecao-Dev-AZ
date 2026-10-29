-- =============================================================================
--  03_permissoes.sql  |  PAPEL DE BANCO COM MENOR PRIVILEGIO (OPCIONAL)
--  Projeto: Avaliação AZ Tecnologia - Leilões
--
--  ATENÇÃO: ESTE SCRIPT É OPCIONAL E NÃO É EXECUTADO AUTOMATICAMENTE.
--  Ele fica na subpasta "seguranca/" justamente por isso: o container do
--  Postgres só executa os arquivos .sql que estão DIRETAMENTE na pasta
--  montada em /docker-entrypoint-initdb.d (o utilitário find do container
--  usa "maxdepth 1" e não entra em subpastas).
--
--  PARA QUE SERVE ESTE SCRIPT?
--  Aplicar o "princípio do menor privilégio" no banco de dados.
--
--  O QUE É ESSE PRINCÍPIO?
--  A ideia é: a aplicação deve usar um usuário que SÓ consegue fazer o
--  que precisa - e nada mais.
--
--  Sem isso (cenário comum):
--      A aplicação conecta com o superusuário "postgres", que pode:
--        - DROPAR o banco inteiro;
--        - ALTERAR a estrutura das tabelas;
--        - criar outros usuários;
--        - ler TODOS os bancos do servidor.
--      Se a aplicação for invadida (um SQL Injection, uma dependência
--      maliciosa), o atacante herda TODO esse poder.
--
--  Com isso (o que este script faz):
--      Cria o usuário "leilao_app", que pode apenas:
--        - SELECT, INSERT, UPDATE, DELETE nas tabelas do projeto.
--      E NÃO pode:
--        - DROP / ALTER / TRUNCATE;
--        - criar ou alterar usuários;
--        - acessar outros bancos do servidor.
--      Se a aplicação for invadida, o dano se limita aos DADOS - que é
--      exatamente o que se quer (e é o que a ISO 27001 e a LGPD pedem
--      em termos de contenção de impacto).
--
--  COMO APLICAR (opcional):
--      psql -U postgres -d az -f 03_permissoes.sql
--
--  DEPOIS DE APLICAR, APONTE A APLICAÇÃO PARA O NOVO USUÁRIO:
--      DATABASE_USER=leilao_app
--      DATABASE_PASSWORD=<a senha que voce definir>
--
--  NOTA SOBRE A SENHA:
--  O placeholder abaixo ("troque-esta-senha") é proposital. Em um ambiente
--  real, a senha viria de um "secret" do orquestrador (Docker Swarm
--  secrets, Kubernetes Secret, AWS Secrets Manager, HashiCorp Vault) e
--  NUNCA de um arquivo versionado no Git.
-- =============================================================================

BEGIN;

-- -----------------------------------------------------------------------------
-- 1) Criar o grupo de papéis da aplicação
-- -----------------------------------------------------------------------------
-- Um "role" (papel) no PostgreSQL é um conjunto de permissões que pode ser
-- dado a vários usuários. Criar o papel separado do usuário permite dar
-- as mesmas permissões a várias aplicações sem repetir GRANT.
-- -----------------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'leilao_app') THEN
        CREATE ROLE leilao_app LOGIN PASSWORD 'troque-esta-senha';
    END IF;
END
$$;

-- -----------------------------------------------------------------------------
-- 2) Permissões sobre o BANCO
-- -----------------------------------------------------------------------------
-- CONNECT: pode abrir conexões com este banco. Sem isso, nem conseguiria
--           entrar. É a permissão mínima para existir.
-- TEMP: pode criar tabelas temporárias na sua sessão (necessário para
--       algumas operações do Hibernate/JDBC). É temporário e some no fim.
-- -----------------------------------------------------------------------------
GRANT CONNECT ON DATABASE az TO leilao_app;
GRANT TEMPORARY ON DATABASE az TO leilao_app;

-- -----------------------------------------------------------------------------
-- 3) Permissões sobre o ESQUEMA (as tabelas)
-- -----------------------------------------------------------------------------
-- USAGE no schema: permite enxergar os objetos (tabelas) do schema.
-- SELECT / INSERT / UPDATE / DELETE: exatamente o que a aplicação faz.
--
-- Repare no que NÃO está aqui: CREATE, ALTER, DROP, TRUNCATE, REFERENCES,
-- GRANT. A aplicação não cria nem altera estrutura (quem faz isso é o
-- script 01_ddl.sql, executado pelo DBA) e não pode destruir o schema.
-- -----------------------------------------------------------------------------
GRANT USAGE ON SCHEMA public TO leilao_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO leilao_app;

-- -----------------------------------------------------------------------------
-- 4) Permissões sobre as SEQUÊNCIAS
-- -----------------------------------------------------------------------------
-- As sequências geram os ids. Para o nextval() funcionar, o usuário
-- precisa de USAGE e SELECT nelas.
-- -----------------------------------------------------------------------------
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO leilao_app;

-- -----------------------------------------------------------------------------
-- 5) Regra para o FUTURO
-- -----------------------------------------------------------------------------
-- "ALTER DEFAULT PRIVILEGES" faz com que QUALQUER tabela ou sequência que
-- for criada DEPOIS daqui já nasça com as permissões da aplicação. Sem
-- isso, bastaria alguém criar uma nova tabela para o usuário leilao_app
-- perder o acesso a ela (e o sistema quebraria em silêncio).
-- -----------------------------------------------------------------------------
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO leilao_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO leilao_app;

COMMIT;

-- =============================================================================
--  COMO CONFERIR QUE FUNCIONOU
--  Entre com o novo usuario e veja o que ele NAO pode fazer:
--
--      psql -U leilao_app -d az
--
--      -- isto FUNCIONA (leitura):
--      SELECT * FROM tb_empresa LIMIT 1;
--
--      -- isto FALHA com "permission denied for schema public" (e o
--      -- esperado e exatamente isso - o usuario da aplicacao nao deve
--      -- poder mexer na estrutura):
--      DROP TABLE tb_empresa;
--
--  E para confirmar o NAO acesso a outros bancos do servidor:
--      psql -U leilao_app -d postgres
--      -- deve falhar com "permission denied for database postgres"
-- =============================================================================
