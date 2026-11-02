package br.com.selecao.locadora.runner;

import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.junit.platform.engine.TestExecutionResult;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request;

/**
 * Executa uma ou mais classes de teste e imprime o resultado.
 *
 * O por que existir: o Maven tem ~2,6s de overhead puro (so' para
 * iniciar), e com o Surefire AREA essa conta sobe para ~6,6s. Na
 * apresentacao, cada rodada de teste e' um momento em que o avaliador
 * esta' olhando para voce esperando. 6,6s de silencio e' desconfortavel;
 * 2s nao.
 *
 * Este runner usa a mesma biblioteca do Surefire (o JUnit Platform
 * Launcher) e a mesma classpath de teste. A diferenca e' que nao existe
 * o Maven no meio: o processo comeca, roda os testes, imprime o
 * resultado e sai.
 *
 * A saida e' propositalmente parecida com a do Surefire - quem ja leu
 * a saida do Maven reconhece na hora. E o codigo de saida carrega o
 * mesmo significado (0 passou, 1 falhou), para o CI e para o painel
 * poderem confiar nele.
 */
public final class TestRunnerUnico {

    private TestRunnerUnico() {
        // Classe utilitaria: nao instancia.
    }

    public static void main(String[] argumentos) {
        if (argumentos.length == 0) {
            System.err.println("Uso: TestRunnerUnico <classe.de.Teste> [outra.classe ...]");
            System.exit(2);
        }

        long inicio = System.currentTimeMillis();

        List<DiscoverySelector> seletores = new ArrayList<>();
        for (String classe : argumentos) {
            seletores.add(selectClass(classe));
        }

        LauncherDiscoveryRequest requisicao = request().selectors(seletores).build();

        Launcher launcher = LauncherFactory.create();
        SummaryGeneratingListener ouvinte = new SummaryGeneratingListener();
        launcher.execute(requisicao, ouvinte);

        TestExecutionSummary resumo = ouvinte.getSummary();
        long duracao = System.currentTimeMillis() - inicio;

        // O Surefire separa "failure" (a assercao falhou) de "error"
        // (a classe nem chegou a rodar). O JUnit Platform junta os dois
        // em "failures" - mas o relatorio do Surefire separava, e o
        // avaliador pode estar lendo a saida do Maven.
        int erros = 0;
        int falhas = 0;

        for (TestExecutionSummary.Failure falha : resumo.getFailures()) {
            if (falha.getException() instanceof AssertionError) {
                falhas++;
            } else {
                erros++;
            }
        }

        PrintWriter saida = new PrintWriter(System.out);

        saida.printf("Tests run: %d, Failures: %d, Errors: %d, Skipped: %d%n",
                resumo.getTestsStartedCount(), falhas, erros, resumo.getTestsSkippedCount());
        saida.printf("Tempo: %.2f s%n", duracao / 1000.0);

        for (TestExecutionSummary.Failure falha : resumo.getFailures()) {
            saida.println();
            saida.println("FALHOU: " + falha.getTestIdentifier().getDisplayName());
            saida.println("  " + falha.getException());
        }

        saida.flush();

        // O codigo de saida e' o do Surefire: 0 passou, 1 falhou.
        System.exit(resumo.getTotalFailureCount() > 0 ? 1 : 0);
    }
}
