package br.com.orientefarma.integradorol.exceptions

import br.com.orientefarma.integradorol.commons.RetornoPedidoEnum

open class IntegradorOLException(val mensagem: String, val retornoOL: RetornoPedidoEnum) : Exception(mensagem)