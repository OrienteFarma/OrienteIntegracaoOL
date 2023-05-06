package br.com.orientefarma.integradorol.model

import br.com.orientefarma.integradorol.commons.RetornoPedidoEnum
import br.com.orientefarma.integradorol.commons.StatusPedidoOLEnum
import br.com.orientefarma.integradorol.dao.ItemPedidoOLDAO
import br.com.orientefarma.integradorol.dao.PedidoOLDAO
import br.com.orientefarma.integradorol.dao.vo.PedidoOLVO
import br.com.orientefarma.integradorol.exceptions.EnviarPedidoCentralException

class PedidoOL(val nuPedOL: String, val codPrj: Int) {
    private val pedidoOLDAO = PedidoOLDAO()
    private val itemPedidoOLDAO = ItemPedidoOLDAO()

    val vo: PedidoOLVO = pedidoOLDAO.findByPk(nuPedOL, codPrj)

    fun salvarRetornoSankhya(exception: EnviarPedidoCentralException){
        vo.codRetSkw = exception.retornoOL
        vo.retSkw = exception.mensagem
        vo.status = StatusPedidoOLEnum.ERRO
        pedidoOLDAO.save(vo)
    }

    fun salvarErroSankhya(exception: Exception){
        val exceptionDesconhecida =
            EnviarPedidoCentralException(exception.message ?: "Sem mensagem", RetornoPedidoEnum.ERRO_DESCONHECIDO)
        salvarRetornoSankhya(exceptionDesconhecida)
    }

    fun marcarSucessoEnvioCentral(nuNota: Int) {
        vo.codRetSkw = RetornoPedidoEnum.SUCESSO
        vo.retSkw = ""
        vo.status = StatusPedidoOLEnum.PENDENTE
        vo.nuNota = nuNota
        pedidoOLDAO.save(vo)
    }


}