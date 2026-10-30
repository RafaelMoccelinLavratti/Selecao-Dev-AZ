package br.com.selecao.locadora.repository;

import br.com.selecao.locadora.entity.Empresa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositorio da empresa.
 *
 * O Spring Data JPA gera as queries a partir dos metodos declarados. Nao ha
 * concatenacao de texto digitado pelo usuario em lugar nenhum: o Hibernate
 * envia os valores como parametros separados da consulta.
 */
@Repository
public interface EmpresaRepository extends JpaRepository<Empresa, Integer> {

    /** Existe alguma empresa com este CNPJ? Usado na validacao de unicidade. */
    boolean existsByCnpj(String cnpj);

    /**
     * Existe alguma empresa com este CNPJ em um id diferente do informado?
     * (o "AndIdNot" existe pro UPDATE: ao editar a empresa 5, o proprio
     * CNPJ da empresa 5 nao pode contar como duplicado)
     */
    boolean existsByCnpjAndIdNot(String cnpj, Integer id);

    /** Existe algum usuario com este nome? */
    boolean existsByUsuario(String usuario);

    /** Mesmo raciocinio do CNPJ, mas pro UPDATE. */
    boolean existsByUsuarioAndIdNot(String usuario, Integer id);

    /** Busca a empresa pelo CNPJ (util no login). */
    Optional<Empresa> findByCnpj(String cnpj);

    /** Busca a empresa pelo nome de usuario (util no login). */
    Optional<Empresa> findByUsuario(String usuario);
}
