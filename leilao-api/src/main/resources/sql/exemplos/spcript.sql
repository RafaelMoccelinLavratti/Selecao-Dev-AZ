-- =============================================================================
--  EXEMPLO DO STARTER (script original do projeto de exemplo)
--  ---------------------------------------------------------------------
--  Este arquivo NÃO é executado pela aplicação. Ele fica na subpasta
--  "exemplos/" justamente por isso: o container do PostgreSQL executa
--  automaticamente apenas os arquivos .sql que estão DIRETAMENTE na pasta
--  montada em /docker-entrypoint-initdb.d (o utilitário do container usa
--  "maxdepth 1" e não entra em subpastas).
--
--  O motivo de ele ter ficado na subpasta: este script cria a tabela
--  "tb_unidade" e a sequence "seq_unidade", que JÁ são criadas pelo
--  01_ddl.sql. Se ele fosse executado automaticamente, o container do
--  PostgreSQL abortaria a inicialização com o erro
--  "relation tb_unidade already exists" - e o banco nem subiria.
--
--  Ele é mantido aqui apenas como referência do script original fornecido
--  no starter. Os scripts que valem para a entrega são:
--      01_ddl.sql  -> estrutura das 5 tabelas
--      02_dml.sql  -> massa de dados inicial
-- =============================================================================

--esse script deve ser utilizado apenas para testar o projeto de exemplo
--OBS: não deve ser utilizado como padrão para os scripts da prova.
CREATE SEQUENCE seq_unidade INCREMENT 1 MINVALUE 1 MAXVALUE 9999999 START 1 CACHE 1;
create table tb_unidade
(
    id     integer primary key,
    nome character varying
);
