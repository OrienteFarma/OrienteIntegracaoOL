package br.com.orientefarma.integradorol.model

import br.com.orientefarma.integradorol.commons.RetornoItemPedidoEnum
import br.com.orientefarma.integradorol.dao.ItemPedidoOLDAO
import br.com.orientefarma.integradorol.dao.vo.ItemPedidoOLVO

class ItemPedidoOL(val vo: ItemPedidoOLVO) {
    private val itemPedidoOLDAO = ItemPedidoOLDAO()
    private var codigoRetorno: RetornoItemPedidoEnum? = null
    private var mensagem: String = ""
    private var qtdAtendida: Int = 0

    fun temFeedback(): Boolean {
        return codigoRetorno != null
    }
    fun setFeedback(mensagem: String, qtdAtendida: Int){
        this.mensagem = mensagem
        this.qtdAtendida = qtdAtendida
    }

    fun setFeedback(retorno: RetornoItemPedidoEnum, qtdAtendida: Int, mensagem: String = ""){
        this.codigoRetorno = retorno
        this.mensagem = mensagem
        this.qtdAtendida = qtdAtendida
    }

    fun salvarRetornoItemPedidoOL() {
        val retornoItem = codigoRetorno ?: calcularCodigoRetorno()
        vo.codRetSkw = retornoItem.codigo
        vo.retSkw = mensagem
        vo.qtdAtd = qtdAtendida
        itemPedidoOLDAO.save(vo)
    }

    fun marcarComoNaoPendente(){
        this.vo.pendente = false
        itemPedidoOLDAO.save(this.vo)
    }

    private fun calcularCodigoRetorno(): RetornoItemPedidoEnum {
        for (retornoItem in RetornoItemPedidoEnum.values()) {
            if (this.mensagem.contains(retornoItem.expressaoRegex)) {
                return retornoItem
            }
        }
        return RetornoItemPedidoEnum.FALHA_DESCONHECIDA
    }
    override fun toString(): String {
        return "EnviarItemPedidoCentralException(mensagem=$mensagem)"
    }

    companion object {
        fun fromCodProd(numPedidoOL: String, codProjeto: Int, codProd: Int): ItemPedidoOL? {
            val itemOLVO = ItemPedidoOLDAO().findByNumPedOLAndCodProd(numPedidoOL, codProjeto, codProd)
            if(itemOLVO != null){
                return ItemPedidoOL(itemOLVO)
            }
            return null
        }
        fun fromPedidoOL(pedidoOL: PedidoOL): Collection<ItemPedidoOL> {
            val itensOL = ItemPedidoOLDAO().findByNumPedOL(pedidoOL.nuPedOL, pedidoOL.codPrj)
            return itensOL.map { ItemPedidoOL(it) }
        }
    }
}