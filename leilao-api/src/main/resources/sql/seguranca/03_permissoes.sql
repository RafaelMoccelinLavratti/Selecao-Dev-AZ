-- 03_permissoes.sql - papel de banco com menor privilegio (opcional).
--
-- Este script nao e executado automaticamente. Ele fica na subpasta
-- "seguranca/" porque o container do Postgres so executa os .sql que estao
-- DIRETAMENTE na pasta montada em /docker-entrypoint-initdb.d (o utilitario
-- find do container usa "maxdepth 1" e nao entra em subpastas).
--
-- A ideia e aplicar "principio do menor privilegio" no banco. Sem isso, a
-- aplicacao conecta com o superusuario "postgres", que pode derrubar o
-- banco, alterar a estrutura das tabelas, criar outros usuarios e ler todos
-- os bancos do servidor - e, se a aplicacao for invadida (um SQL Injection,
-- uma dependencia maliciosa), o atacante herda todo esse poder.
--
-- Este script cria o usuario "leilao_app", que so pode fazer SELECT, INSERT,
-- UPDATE e DELETE nas tabelas do projeto. Ele nao pode DROP, ALTER, TRUNCATE,
-- criar usuarios nem acessar outros bancos. Se a aplicacao for invadida, o
-- dano se limita aos dados - que e o que a ISO 27001 e a LGPD pedem em
-- termos de contencao de impacto.
--
-- Para aplicar:
--   psql -U postgres -d az -f 03_permissoes.sql
--
-- Depois, aponte a aplicacao para o novo usuario:
--   DATABASE_USER=leilao_app
--   DATABASE_PASSWORD=<a senha que voce definir>
--
-- A senha "troque-esta-senha" abaixo e um placeholder. Em um ambiente real
-- ela viria de um "secret" do orquestrador (Docker Swarm secrets,
-- Kubernetes Secret, AWS Secrets Manager, HashiCorp Vault) e nunca de um
-- arquivo versionado no Git.

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
