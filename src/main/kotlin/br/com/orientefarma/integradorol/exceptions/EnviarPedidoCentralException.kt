package br.com.orientefarma.integradorol.exceptions

import br.com.orientefarma.integradorol.commons.RetornoPedidoEnum

class EnviarPedidoCentralException(val mensagem: String, val retornoOL: RetornoPedidoEnum) : Exception() {

}