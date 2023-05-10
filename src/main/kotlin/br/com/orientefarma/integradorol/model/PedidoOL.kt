package br.com.orientefarma.integradorol.model

import br.com.orientefarma.integradorol.commons.RetornoPedidoEnum
import br.com.orientefarma.integradorol.commons.StatusPedidoOLEnum
import br.com.orientefarma.integradorol.commons.retirarTagsHtml
import br.com.orientefarma.integradorol.dao.PedidoOLDAO
import br.com.orientefarma.integradorol.dao.vo.PedidoOLVO
import br.com.orientefarma.integradorol.exceptions.EnviarPedidoCentralException

class PedidoOL(val vo: PedidoOLVO) {
    private val pedidoOLDAO = PedidoOLDAO()

    val nuPedOL: String = vo.nuPedOL
    val codPrj: Int = vo.codPrj

    private val itensPedidoOL = ItemPedidoOL.fromPedidoOL(this)
    private var codigoRetorno: RetornoPedidoEnum? = null
    private var mensagem: String? = null
    private var nuNotaCentral: Int? = null

    fun temFeedback(): Boolean {
        return codigoRetorno != null
    }

    fun setFeedback(retorno: RetornoPedidoEnum, mensagem: String = ""){
        this.codigoRetorno = retorno
        this.mensagem = mensagem.retirarTagsHtml().take(100)
    }

    fun setNuNotaCentral(nuNota: Int){
        this.nuNotaCentral = nuNota
    }

    fun salvarRetornoSankhya(retorno: RetornoPedidoEnum, mensagem: String = ""){
        setFeedback(retorno, mensagem)
        vo.codRetSkw = this.codigoRetorno
        vo.retSkw = this.mensagem?.retirarTagsHtml()?.take(100)
        vo.status = StatusPedidoOLEnum.PENDENTE
        vo.nuNota = this.nuNotaCentral
        pedidoOLDAO.save(vo)
    }

    fun salvarRetornoSankhya(e: EnviarPedidoCentralException){
        setFeedback(e.retornoOL, e.mensagem)
        vo.codRetSkw = this.codigoRetorno
        vo.retSkw = this.mensagem?.retirarTagsHtml()?.take(100)
        vo.nuNota = this.nuNotaCentral
        pedidoOLDAO.save(vo)
    }

    fun salvarErroSankhya(e: EnviarPedidoCentralException){
        setFeedback(e.retornoOL, e.mensagem)
        vo.codRetSkw = this.codigoRetorno
        vo.retSkw = this.mensagem?.retirarTagsHtml()?.take(100)
        vo.status = StatusPedidoOLEnum.ERRO
        vo.nuNota = this.nuNotaCentral
        pedidoOLDAO.save(vo)
    }

    fun salvarErroSankhya(exception: Exception, nuNota: Int? = null){
        val exceptionDesconhecida =
            EnviarPedidoCentralException(exception.message ?: "Sem mensagem", RetornoPedidoEnum.ERRO_DESCONHECIDO)
        vo.codRetSkw = exceptionDesconhecida.retornoOL
        vo.retSkw = exceptionDesconhecida.mensagem.retirarTagsHtml().take(100)
        vo.status = StatusPedidoOLEnum.ERRO
        vo.nuNota = this.nuNotaCentral
        pedidoOLDAO.save(vo)
    }

    fun marcarSucessoEnvioCentral(nuNota: Int) {
        setNuNotaCentral(nuNota)
        vo.codRetSkw = RetornoPedidoEnum.SUCESSO
        vo.retSkw = ""
        vo.status = StatusPedidoOLEnum.PENDENTE
        vo.nuNota = this.nuNotaCentral
        pedidoOLDAO.save(vo)
    }

    fun getItens(): Collection<ItemPedidoOL> {
        return itensPedidoOL
    }

    fun marcarComoEnviandoParaCentral() {
        this.vo.status = StatusPedidoOLEnum.ENVIANDO_CENTRAL
        pedidoOLDAO.save(this.vo)
    }

    companion object {
        private val pedidoOLDAO = PedidoOLDAO()
        fun fromPk(nuPedOL: String, codPrj: Int): PedidoOL {
            val vo = pedidoOLDAO.findByPk(nuPedOL, codPrj)
            return PedidoOL(vo)
        }

        fun fromPendentes(): Collection<PedidoOL>{
            val pedidoOLVOIntegrados = PedidoOLDAO().findIntegrados(1)
            return pedidoOLVOIntegrados.map { PedidoOL(it) }
        }
    }


}