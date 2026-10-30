package br.com.selecao.locadora.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO de saida do vinculo comprador.
 *
 * Inclui a razao social da empresa e o codigo do leilao para evitar o
 * N+1: sem isso, a tela precisaria de uma consulta extra para cada linha
 * da listagem.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompradorResponse {

    /**
     * Id da empresa compradora.
     *
     * Faz parte da chave composta do vinculo.
     */
    private Integer empresa;

    /**
     * Razao social da empresa compradora.
     *
     * Preenchido pelo backend para a tela exibir algo legivel, sem
     * precisar de uma consulta extra por linha.
     */
    private String razaoSocial;

    /**
     * Id do leilao comprado.
     *
     * Faz parte da chave composta do vinculo.
     */
    private Integer leilao;

    /**
     * Codigo do leilao comprado.
     *
     * Como a tela mostra "#12" e nao "12", este campo e mais legivel
     * que o id.
     */
    private Integer codigoLeilao;

    /**
     * Momento da criacao do registro.
     */
    private LocalDateTime createdAt;

    /**
     * Momento da ultima alteracao do registro.
     */
    private LocalDateTime updatedAt;
}
