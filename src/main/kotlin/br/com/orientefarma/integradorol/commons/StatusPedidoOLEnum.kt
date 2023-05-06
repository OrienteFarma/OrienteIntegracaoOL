package br.com.orientefarma.integradorol.commons

@Suppress("unused")
enum class StatusPedidoOLEnum(val valor: String) {
    IMPORTANDO("I"),
    ENVIANDO_CENTRAL("C"),
    PENDENTE("P"),
    RETORNO("R"),
    ESPELHO("E"),
    ERRO("O");
    fun getValue() = valor
}