package br.com.orientefarma.integradorol.exceptions

import br.com.orientefarma.integradorol.commons.RetornoItemPedidoEnum

@Suppress("unused")
class EnviarItemPedidoCentralException(val mensagem: String? = "Sem mensagem", val retornoOL: RetornoItemPedidoEnum):
    Exception() {

}