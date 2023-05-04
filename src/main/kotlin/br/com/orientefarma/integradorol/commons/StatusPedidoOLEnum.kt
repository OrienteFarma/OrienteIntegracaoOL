package br.com.orientefarma.integradorol.commons

@Suppress("unused")
enum class StatusPedidoOLEnum(val valor: String) {
    INTEGRANDO("I"),
    PENDENTE("P"),
    RETORNO("R"),
    ESPELHO("E"),
    ERRO("O");
    fun getValue() = valor
}