package br.com.selecao.locadora;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ============================================================================
 *  CLASSE PRINCIPAL DA APLICACAO
 * ============================================================================
 *
 *  ------------------------------------------------------
 *  O QUE E ESTA CLASSE?
 *  ------------------------------------------------------
 *  Este e o ponto de ENTRADA do programa - o metodo "main" que a JVM
 *  executa quando voce roda a aplicacao com "mvn spring-boot:run" ou
 *  "java -jar target/leilao-api-1.0.0-SNAPSHOT.jar".
 *
 *  Quando a JVM encontra esta classe no classpath, ela e o que dispara
 *  TODO o processo de inicializacao do Spring Boot.
 *
 *  ------------------------------------------------------
 *  O QUE FAZ O "SpringApplication.run(...)"?
 *  ------------------------------------------------------
 *  Basicamente, ele constroi o container de dependencias (o "contexto" da
 *  aplicacao) e faz TRES coisas:
 *
 *  1) Lê o arquivo "application.properties" (e os arquivos do perfil
 *     ativo, como "application-prod.properties") e carrega todas as
 *     configuracoes - porta do servidor, string de conexao do banco,
 *     variaveis de ambiente, e por ai vai.
 *
 *  2) Varre as classes do pacote "br.com.selecao.locadora" procurando
 *     anotacoes como @RestController, @Service, @Repository e @Component,
 *     e cria UM OBJETO de cada uma, guardando-os no container. E o que se
 *  chama de "inversao de dependencias": as classes nao se instanciam
 *  sozinhas (com "new"), elas RECEBEM suas dependencias ja prontas.
 *
 *  3) Sobe o servidor web embutido (Tomcat) na porta configurada
 *     (8081 por padrao) e comeca a atender requisicoes.
 *
 *  ------------------------------------------------------
 *  POR QUE A ANOTACAO @SpringBootApplication ESTA NO NOME DA CLASSE?
 *  ------------------------------------------------------
 *  Porque o Spring Boot usa a posicao da classe principal para DEFINIR
 *  O QUE DEVE SER COMPREENDIDO COMO "COMPONENTE DA APLICACAO". Por
 *  padrao, ele varre o pacote da classe principal E TODOS OS SUBPACOTES.
 *
 *  Como esta classe esta em "br.com.selecao.locadora", tudo o que esta
 *  em "br.com.selecao.locadora.controller", ".service", ".repository",
 *  ".business", ".entity", ".dto" etc. e automaticamente encontrado.
 *
 *  Se a classe principal estivesse em um pacote como "br.com.selecao"
 *  (acima da pasta do projeto), o Spring comecaria a varrer o machine
 *  inteiro e o projeto nao subiria. Por isso a classe fica sempre na
 *  RAIZ do pacote de codigo - e por isso que a pasta do projeto segue
 *  a convencao "br.com.selecao.locadora".
 *
 *  ------------------------------------------------------
 *  @SpringBootApplication NA VERDADE E TRÊS ANOTACOES
 *  ------------------------------------------------------
 *  | Anotacao              | O que faz                                        |
 *  |-----------------------|-------------------------------------------------|
 *  | @SpringBootConfiguration | Marca a classe como fonte de configuracao      |
 *  | @EnableAutoConfiguration | Liga as configuracoes automaticas do Spring Boot |
 *  | @ComponentScan       | Varre o pacote em busca de componentes          |
 *
 *  O @EnableAutoConfiguration e o que faz a "magia" do Spring Boot:
 *  ele detecta que existe o driver do PostgreSQL no classpath e cria
 *  automaticamente o DataSource, o EntityManager e o repositorio JPA -
 *  sem uma linha de codigo nossa.
 *
 *  ------------------------------------------------------
 *  @EnableScheduling - O QUE FAZ
 *  ------------------------------------------------------
 *  Esta anotacao habilita o AGENDAMENTO de tarefas no Spring. E o que
 *  permite que metodos anotados com @Scheduled sejam executados
 *  automaticamente pelo container.
 *
 *  No nosso projeto, ela existe por causa do metodo
 *  "limparExpirados()" da classe LoginAttemptService, que roda a cada
 *  minuto para apagar da memoria os contadores de tentativas de login
 *  que ja venceram.
 *
 *  Sem esta anotacao, o Spring simplesmente ignora o @Scheduled e
 *  nenhuma limpeza aconteceria - o contador continuaria crescendo.
 * ============================================================================
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
