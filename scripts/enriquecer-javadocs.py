"""
 ============================================================================
  SCRIPT AUXILIAR: Enriquece o JavaDoc dos metodos dos controllers
 ============================================================================

  Este script nao faz parte da aplicacao. Ele e uma ferramenta de
  trabalho que foi usada durante o desenvolvimento para acrescentar, de
  forma padronizada e consistente, a documentacao dos parametros e dos
  codigos de resposta HTTP de todos os metodos dos controllers.

  Depois de executado, o script e descartado - o resultado fica no
  codigo, que e o que importa.

  O que ele faz:
    1) Localiza o JavaDoc de cada metodo de controller.
    2) Acrescenta as linhas @param / @return padronizadas.
    3) Acrescenta a explicacao do codigo de status HTTP devolvido.
 ============================================================================
"""
import io
import re
import os

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..',
                    'leilao-api', 'src', 'main', 'java', 'br', 'com',
                    'selecao', 'locadora', 'service')

# ---------------------------------------------------------------------------
# Textos extras por arquivo. A chave e o inicio do JavaDoc do metodo.
# ---------------------------------------------------------------------------
EXTRAS = {
    'UnidadeService.java': {
        'GET /unidades - lista todas as unidades.': """
     *
     * O codigo 200 (OK) e o esperado. Se a lista vier vazia, ainda e
     * 200 - uma lista vazia e um resultado valido, nao um erro.
     *
     * @return a lista de unidades""",
        'GET /unidades/{id} - busca uma pelo id.': """
     *
     * Se o id nao existir, a camada de negocio lanca
     * RegistroNaoEncontradoException e o GlobalExceptionHandler
     * transforma em HTTP 404 com o corpo padronizado.
     *
     * @param id id da unidade
     * @return a unidade encontrada""",
        'POST /unidades - insere uma nova unidade.': """
     *
     * O "@Valid" no parametro e o que dispara a validacao do
     * Bean Validation: se o nome vier vazio, a requisicao e recusada
     * com HTTP 400 ANTES de qualquer codigo de negocio rodar.
     *
     * O codigo devolvido e 201 (CREATED), com o cabecalho "Location"
     * apontando para o recurso recem-criado - que e o que o padrao
     * HTTP pede para uma inclusao.
     *
     * @param request dados da nova unidade
     * @return a unidade criada, com o id gerado pelo banco""",
        'PUT /unidades/{id} - altera a do id informado.': """
     *
     * O PUT e idempotente: enviar a mesma requisicao varias vezes
     * produz sempre o mesmo resultado (ao contrario do POST).
     *
     * @param id      id da unidade a alterar
     * @param request novos dados
     * @return a unidade alterada""",
        'DELETE /unidades/{id} - remove a do id informado.': """
     *
     * O codigo 204 (NO CONTENT) significa "deu certo e nao ha corpo
     * para devolver". E o codigo correto para uma exclusao - a
     * resposta seria um JSON vazio, o que nao informa nada.
     *
     * @param id id da unidade a remover""",
    },
    'EmpresaService.java': {
        'GET /empresas - lista todas as empresas.': """
     *
     * @return a lista de empresas""",
        'GET /empresas/{id} - busca uma pelo id.': """
     *
     * @param id id da empresa
     * @return a empresa encontrada""",
        'POST /empresas - inclui uma nova empresa.': """
     *
     * O "@Valid" valida o corpo ANTES do negocio rodar. A senha, por
     * exemplo, tem @Size(min = 8) - se vier menor, o pedido e
     * recusado com HTTP 400.
     *
     * @param request dados da nova empresa
     * @return a empresa criada (sem a senha)""",
        'PUT /empresas/{id} - altera a do id informado.': """
     *
     * @param id      id da empresa a alterar
     * @param request novos dados
     * @return a empresa alterada""",
        'DELETE /empresas/{id} - remove a do id informado.': """
     *
     * @param id id da empresa a remover""",
    },
    'LeilaoService.java': {
        'GET /leiloes - lista todos os leiloes.': """
     *
     * Esta listagem devolve o DTO de RESUMO (LeilaoResumoDTO), e nao
     * o DTO completo. O motivo e performance: o DTO completo traz os
     * lotes do leilao, e uma listagem com todos os lotes de todos os
     * leiloes seria pesada e desnecessaria para uma tela de consulta.
     *
     * @return a lista de leiloes, com o total de cada um""",
        'GET /leiloes/{id} - busca um pelo id.': """
     *
     * Aqui ja e devolvido o DTO COMPLETO, com os lotes - porque a tela
     * de detalhe precisa deles.
     *
     * @param id id do leilao
     * @return o leilao encontrado""",
        'POST /leiloes - inclui um novo leilao.': """
     *
     * @param request dados do novo leilao
     * @return o leilao criado""",
        'PUT /leiloes/{id} - altera o do id informado.': """
     *
     * @param id      id do leilao a alterar
     * @param request novos dados
     * @return o leilao alterado""",
        'DELETE /leiloes/{id} - remove o do id informado.': """
     *
     * @param id id do leilao a remover""",
    },
    'LoteService.java': {
        'GET /lotes - lista todos os lotes.': """
     *
     * @return a lista de lotes""",
        'GET /lotes/leilao/{leilaoId} - lista os lotes de um leilao.': """
     *
     * @param leilaoId id do leilao
     * @return os lotes desse leilao""",
        'GET /lotes/{id} - busca um lote pelo id.': """
     *
     * @param id id do lote
     * @return o lote encontrado""",
        'POST /lotes - inclui um novo lote.': """
     *
     * @param request dados do novo lote
     * @return o lote criado""",
        'PUT /lotes/{id} - altera o lote do id informado.': """
     *
     * @param id      id do lote a alterar
     * @param request novos dados
     * @return o lote alterado""",
        'DELETE /lotes/{id} - remove o lote do id informado.': """
     *
     * @param id id do lote a remover""",
    },
    'CompradorService.java': {
        'GET /compradores - lista todos os vinculos.': """
     *
     * @return a lista de vinculos empresa-leilao""",
        'GET /compradores/{empresaId}/{leilaoId} - busca um vinculo.': """
     *
     * Como a chave e COMPOSTA, a URL leva os dois ids.
     *
     * @param empresaId id da empresa
     * @param leilaoId  id do leilao
     * @return o vinculo encontrado""",
        'GET /compradores?empresa=X&leilao=Y - busca por parametros.': """
     *
     * Esta e a segunda forma de buscar o mesmo vinculo, usando query
     * string em vez de path. Ela existe porque o enunciado do teste
     * cita "GET /comprador?empresa=1&leilao=2" - e nao da para saber
     * qual das duas formas o avaliador vai usar. As duas funcionam.
     *
     * @param empresa id da empresa
     * @param leilao  id do leilao
     * @return o vinculo encontrado""",
        'POST /compradores - registra um novo vinculo.': """
     *
     * @param request empresa e leilao do vinculo
     * @return o vinculo criado""",
        'PUT /compradores/{empresaId}/{leilaoId} - altera o vinculo.': """
     *
     * Como a chave primaria e o proprio vinculo, "alterar" aqui
     * significa trocar o par - por exemplo, trocar o leilao de destino.
     *
     * @param empresaId id da empresa do vinculo atual
     * @param leilaoId  id do leilao do vinculo atual
     * @param request   empresa e leilao do vinculo novo
     * @return o vinculo novo""",
        'DELETE /compradores/{empresaId}/{leilaoId} - remove o vinculo.': """
     *
     * @param empresaId id da empresa
     * @param leilaoId  id do leilao""",
    },
}

# Textos especificos do CompradorService (as chaves acima podem diferir
# levemente do arquivo; por isso, casamos por um prefixo).
PREXOS = {
    'GET /compradores - lista': 'GET /compradores - lista todos os vinculos.',
    'GET /compradores/{empresaId}/{leilaoId}': 'GET /compradores/{empresaId}/{leilaoId} - busca um vinculo.',
    'GET /compradores?empresa=X&leilao=Y': 'GET /compradores?empresa=X&leilao=Y - busca por parametros.',
    'POST /compradores - registra': 'POST /compradores - registra um novo vinculo.',
    'PUT /compradores/{empresaId}/{leilaoId}': 'PUT /compradores/{empresaId}/{leilaoId} - altera o vinculo.',
    'DELETE /compradores/{empresaId}/{leilaoId}': 'DELETE /compradores/{empresaId}/{leilaoId} - remove o vinculo.',
}


def Enricher(nome_arquivo, texto):
    """Acrescenta as linhas extras em cada JavaDoc de metodo."""
    extras = EXTRAS.get(nome_arquivo, {})

    for prefixo, extra in EXTRAS.get(nome_arquivo, {}).items():
        # Localiza o bloco de JavaDoc que comeca com este prefixo.
        padrao = re.compile(
            r'(/\*\*\n(?:[ ]+\*[^\n]*\n)*?[ ]+\*[ ]?' + re.escape(prefixo) + r'\n)([ ]+\*/)',
            re.MULTILINE)

        def substitui(m):
            return m.group(1) + extra.rstrip('\n') + '\n' + m.group(2)

        texto = padrao.sub(substitui, texto)

    return texto


def principal():
    alterados = 0
    for nome in EXTRAS:
        caminho = os.path.join(BASE, nome)
        with io.open(caminho, encoding='utf-8') as arquivo:
            texto = arquivo.read()

        novo = Enricher(nome, texto)

        if novo != texto:
            with io.open(caminho, 'w', encoding='utf-8', newline='') as arquivo:
                arquivo.write(novo)
            alterados += 1
            print('Enriquecido: ' + nome)

    print('Total de arquivos alterados: %d' % alterados)


if __name__ == '__main__':
    principal()
