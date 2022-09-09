--esse script deve ser utilizado apenas para testar o projeto de exemplo
--OBS: não deve ser utilizado como padrão para os scripts da prova.
CREATE SEQUENCE seq_unidade INCREMENT 1 MINVALUE 1 MAXVALUE 9999999 START 1 CACHE 1;
create table tb_unidade
(
    id     integer primary key,
    nome character varying
);