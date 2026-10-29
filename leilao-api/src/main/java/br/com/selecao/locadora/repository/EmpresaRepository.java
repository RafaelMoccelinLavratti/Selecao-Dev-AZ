package br.com.selecao.locadora.repository;

import br.com.selecao.locadora.entity.Empresa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * ============================================================================
 *  REPOSITORY: EMPRESA
 * ============================================================================
 *
 *  O QUE E UM REPOSITORY?
 *  E a camada que fala com o BANCO DE DADOS. Ela extends JpaRepository e,
 *  com isso, o Spring Data JPA gera sozinho todas as queries basicas
 *  (listar, buscar por id, salvar, apagar). Nao ha SQL escrito a mao aqui -
 *  o framework monta o SQL parametrizado.
 *
 *  POR QUE ISSO E SEGURO CONTRA SQL INJECTION?
 *  Porque o Hibernate envia os valores como PARAMETROS separados da
 *  consulta ("prepared statement"), e nunca cola o texto digitado pelo
 *  usuario dentro do SQL. Um usuario que digitar "'; DROP TABLE tb_empresa; --"
 *  no filtro vai ser tratado como TEXTO a buscar, nunca como comando.
 *  Esse e o motivo de a aplicacao inteira passar por aqui.
 *
 *  METODOS DE BUSCA PERSONALIZADOS (consultas derivadas):
 *  O Spring Data le o nome do metodo e monta o SQL. Por exemplo,
 *  "existsByCnpjAndIdNot" vira:
 *      SELECT count(*) FROM tb_empresa WHERE cnpj = ? AND id <> ?
 *  Isso permite validar a regra de unicidade ANTES de gravar, devolvendo
 *  uma mensagem de erro clara em vez de deixar o banco estourar com uma
 *  excecao tecnica.
 * ============================================================================
 */
@Repository
public interface EmpresaRepository extends JpaRepository<Empresa, Integer> {

    /** Existe alguma empresa com este CNPJ? Usado na validacao de unicidade. */
    boolean existsByCnpj(String cnpj);

    /**
     * Existe alguma empresa com este CNPJ em um id DIFERENTE do informado?
     * (o "AndIdNot" existe para o UPDATE: ao editar a empresa 5, o
     * proprio CNPJ da empresa 5 nao pode contar como duplicado)
     */
    boolean existsByCnpjAndIdNot(String cnpj, Integer id);

    /** Existe algum usuario com este nome? */
    boolean existsByUsuario(String usuario);

    /** Mesmo raciocinio do CNPJ, mas para o UPDATE. */
    boolean existsByUsuarioAndIdNot(String usuario, Integer id);

    /** Busca a empresa pelo CNPJ (util no login). */
    Optional<Empresa> findByCnpj(String cnpj);

    /** Busca a empresa pelo nome de usuario (util no login). */
    Optional<Empresa> findByUsuario(String usuario);
}
