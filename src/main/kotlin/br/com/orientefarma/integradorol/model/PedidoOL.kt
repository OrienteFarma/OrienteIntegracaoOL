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

    /**
     * Capaz de setar os dados para retorno do pedido OL.
     * Por exemplo: Se a condição comercial é inválida.
     */
    fun setFeedback(retorno: RetornoPedidoEnum, mensagem: String = ""){
        this.codigoRetorno = retorno
        this.mensagem = mensagem.retirarTagsHtml().take(100)
    }

    /**
     * Seta o número de pedido na central na tabela intermediária.
     */
    fun setNuNotaCentral(nuNota: Int){
        this.nuNotaCentral = nuNota
    }

    /**
     * Seta em feedback e PERSISTE no banco de dados.
     */
    fun salvarRetornoSankhya(status: StatusPedidoOLEnum, retorno: RetornoPedidoEnum, mensagem: String = ""){
        setFeedback(retorno, mensagem)
        vo.codRetSkw = this.codigoRetorno
        vo.retSkw = this.mensagem?.retirarTagsHtml()?.take(100)
        vo.status = status
        vo.nuNota = this.nuNotaCentral ?: vo.nuNota
        pedidoOLDAO.save(vo)
    }

    /**
     * Usado para salvar retorno de exceções não tratadas.
     */
    fun salvarErroSankhya(exception: Exception){
        val exceptionDesconhecida =
            EnviarPedidoCentralException(exception.message ?: "Sem mensagem", RetornoPedidoEnum.ERRO_DESCONHECIDO)
        vo.codRetSkw = exceptionDesconhecida.retornoOL
        vo.retSkw = exceptionDesconhecida.mensagem.retirarTagsHtml().take(100)
        vo.status = StatusPedidoOLEnum.ERRO
        vo.nuNota = this.nuNotaCentral
        pedidoOLDAO.save(vo)
    }

    /**
     * Usado para salvar retorno de sucesso no envio para a central de vendas.
     */
    fun marcarSucessoEnvioCentral(nuNota: Int) {
        setNuNotaCentral(nuNota)
        vo.codRetSkw = RetornoPedidoEnum.SUCESSO
        vo.retSkw = ""
        vo.status = StatusPedidoOLEnum.PENDENTE
        vo.nuNota = this.nuNotaCentral
        pedidoOLDAO.save(vo)
    }

    /**
     * Retorna os itens do pedido OL.
     */
    fun getItens(): Collection<ItemPedidoOL> {
        return itensPedidoOL
    }

    /**
     * Usado para salvar retorno de ENVIADO PARA A CENTRAL, ou seja, o processo foi iniciado.
     */
    fun marcarComoEnviandoParaCentral() {
        this.vo.status = StatusPedidoOLEnum.ENVIANDO_CENTRAL
        pedidoOLDAO.save(this.vo)
    }

    fun save(){
        pedidoOLDAO.save(vo)
    }
    /**
     * Métodos Fábrica.
     */
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