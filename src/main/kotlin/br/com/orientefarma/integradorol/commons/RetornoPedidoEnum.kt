package br.com.orientefarma.integradorol.commons

enum class RetornoPedidoEnum(val codigo: Int) {
    CNPJ_INVALIDO(401),
    CLIENTE_NAO_CADASTRADO(402),
    CLIENTE_INATIVO(402),

    PEDIDO_DUPLICADO(501),
}