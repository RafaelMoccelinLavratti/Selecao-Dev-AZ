package br.com.selecao.locadora;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Ponto de entrada da aplicacao.
 *
 * O metodo main nao faz nada de especifico: ele entrega o controle ao
 * Spring Boot, que passa a cuidar dos beans, do servidor web e da
 * conexao com o banco.
 */
@SpringBootApplication
@EnableScheduling
public class LeilaoApplication {

    /**
     * Metodo de entrada do programa.
     *
     * @param args argumentos passados pela linha de comando (nao usamos
     *             nenhum especifico, mas o Spring Boot le alguns daqui,
     *             como "--server.port=9090")
     */
    public static void main(String[] args) {

        // Inicializa e sobe a aplicacao.
        SpringApplication.run(LeilaoApplication.class, args);
    }

}
