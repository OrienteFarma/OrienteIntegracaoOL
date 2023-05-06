package br.com.orientefarma.integradorol.commons

@Suppress("unused")
enum class RetornoPedidoEnum(val codigo: Int) {
    SUCESSO(200),

    CNPJ_INVALIDO(401),
    CLIENTE_NAO_CADASTRADO(402),
    CLIENTE_INATIVO(402),
    FALHA_DOCUMENTACAO(403),

    PEDIDO_DUPLICADO(501),
    ERRO_DESCONHECIDO(599);

    fun getValue() = codigo.toBigDecimal()
}