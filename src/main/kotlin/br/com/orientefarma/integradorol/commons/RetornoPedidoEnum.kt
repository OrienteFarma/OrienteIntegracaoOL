package br.com.orientefarma.integradorol.commons

@Suppress("unused")
enum class RetornoPedidoEnum(val codigo: Int, val podeMarcarComoErro: Boolean = true) {
    SUCESSO(200),

    CNPJ_INVALIDO(401, false),
    CLIENTE_NAO_CADASTRADO(402, false),
    CLIENTE_INATIVO(405, false),
    FALHA_DOCUMENTACAO(403),
    CONDICAO(404),
    NENHUM_ITEM_PENDENTE(406),

    PEDIDO_DUPLICADO(501),
    ERRO_AO_TOTALIZAR_PEDIDO(502),
    ERRO_DESCONHECIDO(599);

    fun getValue() = codigo.toBigDecimal()
}