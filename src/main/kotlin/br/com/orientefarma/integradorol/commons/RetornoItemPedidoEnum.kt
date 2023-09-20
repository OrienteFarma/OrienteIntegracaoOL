package br.com.orientefarma.integradorol.commons

/**
 * A Ordem dos ENUMS eh respeitado no momento de fazer as verificacoes de mensagens
 */
@Suppress("unused")
enum class RetornoItemPedidoEnum(val codigo: Int, val expressaoRegex: Regex) {
    CADASTRO(401, Regex("(Produto.\\d.*ativo)|(Produto n�o informado)")),
    CONDICAO(402, Regex("(PRODUTO \\d*.*PERTENCE.*COMERCIAL)|(SEM.*TABELA.*PODE SER VENDIDO)")),
    ESTOQUE_INSUFICIENTE(403, Regex("Estoque insuficiente")),
    ESTOQUE_PARCIALMENTE(404, Regex("Parcialmente atendido")),
    DOCUMENTACAO(405, Regex("(DOCUMENTO(S) FALTANTE(S))|(Falta de documenta��o)")),
    NAO_ATENDIDO(406, Regex("N�o atendido")),
    DESCONTO_INVALIDO(407, Regex("DESCONTO INVALIDO")),

    SUCESSO(200, Regex("SUCESSO")),

    FALHA_DESCONHECIDA(599, Regex(".*")),
}