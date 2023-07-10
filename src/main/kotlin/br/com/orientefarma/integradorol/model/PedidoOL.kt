package br.com.orientefarma.integradorol.model

import br.com.orientefarma.integradorol.commons.RetornoPedidoEnum
import br.com.orientefarma.integradorol.commons.StatusPedidoOLEnum
import br.com.orientefarma.integradorol.commons.retirarTagsHtml
import br.com.orientefarma.integradorol.controller.dto.PedidoOLDto
import br.com.orientefarma.integradorol.dao.PedidoOLDAO
import br.com.orientefarma.integradorol.dao.vo.PedidoOLVO
import br.com.orientefarma.integradorol.exceptions.EnviarPedidoCentralException
import br.com.orientefarma.integradorol.exceptions.IntegradorOLException
import java.math.BigDecimal

class PedidoOL(val vo: PedidoOLVO) {
    private val pedidoOLDAO = PedidoOLDAO()

    val nuPedOL: String = vo.nuPedOL
    val codPrj: Int = vo.codPrj

    private val itensPedidoOL = ItemPedidoOL.fromPedidoOL(this)
    private var nuNotaCentral: Int? = null

    fun temCodRetorno(): Boolean {
        // vo.vo = valueObject dentro do PedidoOLVO (WrapperVO)
        val codRetSkw = this.vo.vo["CODRETSKW"]
        return codRetSkw != null && (codRetSkw as BigDecimal) > BigDecimal.ZERO
    }

    /**
     * Seta o número de pedido na central na tabela intermediária.
     */
    fun setNuNotaCentral(nuNota: Int){
        this.nuNotaCentral = nuNota
        this.vo.nuNota = nuNota
    }

    /**
     * Seta em feedback e PERSISTE no banco de dados.
     */
    fun salvarRetornoSankhya(status: StatusPedidoOLEnum, retorno: RetornoPedidoEnum, mensagem: String = ""){
        vo.codRetSkw = retorno
        vo.retSkw = mensagem.retirarTagsHtml().take(100)
        vo.status = status
        vo.nuNota = this.nuNotaCentral ?: vo.nuNota
        pedidoOLDAO.save(vo)
    }

    /**
     * Usado para salvar retorno de exceções não tratadas.
     */
    fun salvarErroSankhya(exception: Exception){
        if(exception is IntegradorOLException){
            vo.codRetSkw = exception.retornoOL
            vo.retSkw = exception.mensagem
        }else{
            val exceptionDesconhecida =
                EnviarPedidoCentralException(exception.message ?: "Sem mensagem", RetornoPedidoEnum.ERRO_DESCONHECIDO)
            vo.codRetSkw = exceptionDesconhecida.retornoOL
            val mensagem = exceptionDesconhecida.mensagem.retirarTagsHtml().take(100)
            vo.retSkw = mensagem
        }
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

    fun salvarNuNotaCentral(nuNota: Int) {
        setNuNotaCentral(nuNota)
        save()
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

        fun fromPendentes(): Collection<PedidoOLDto>{
            val pedidoOLVOIntegrados = PedidoOLDAO().findIntegrados(1)
            return pedidoOLVOIntegrados.map { PedidoOLDto(it.nuPedOL, it.codPrj) }
        }
    }


}