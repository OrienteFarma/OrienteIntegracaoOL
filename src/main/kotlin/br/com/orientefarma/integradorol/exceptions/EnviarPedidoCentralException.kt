package br.com.orientefarma.integradorol.exceptions

import br.com.orientefarma.integradorol.commons.RetornoPedidoEnum

class EnviarPedidoCentralException(mensagem: String, retornoOL: RetornoPedidoEnum)
    : IntegradorOLException(mensagem, retornoOL)