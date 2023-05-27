package br.com.orientefarma.integradorol.model

import br.com.orientefarma.integradorol.commons.RetornoItemPedidoEnum
import br.com.orientefarma.integradorol.commons.retirarTagsHtml
import br.com.orientefarma.integradorol.dao.ItemPedidoOLDAO
import br.com.orientefarma.integradorol.dao.vo.ItemPedidoOLVO
import com.sankhya.util.StringUtils

class ItemPedidoOL(val vo: ItemPedidoOLVO) {
    private val itemPedidoOLDAO = ItemPedidoOLDAO()
    private var codigoRetorno: RetornoItemPedidoEnum? = null
    private var mensagem: String = ""
    private var qtdAtendida: Int = 0

    fun temFeedback(): Boolean {
        return codigoRetorno != null
    }

    /**
     * Capaz de setar os dados para retorno do item de pedido OL.
     * Por exemplo: Se o item tem DESCONTO INVALIDO ou ESTOQUE INSUFICIENTE.
     * USADO quando somente há a mensagem de erro.
     */
    fun setFeedback(mensagem: String, qtdAtendida: Int){
        this.mensagem = mensagem.retirarTagsHtml().take(100)
        this.qtdAtendida = qtdAtendida
    }

    /**
     * Capaz de setar os dados para retorno do item de pedido OL.
     * Por exemplo: Se o item tem DESCONTO INVALIDO ou ESTOQUE INSUFICIENTE.
     * USADO quando se tem o RetornoItemPedidoEnum - erro catalogado.
     */
    fun setFeedback(retorno: RetornoItemPedidoEnum, qtdAtendida: Int, mensagem: String = ""){
        this.codigoRetorno = retorno

        if (StringUtils.isEmpty(mensagem)) {
            this.mensagem = retorno.name
        }
        else{
            this.mensagem = mensagem.retirarTagsHtml().take(100)
        }
        this.qtdAtendida = qtdAtendida
    }

    /**
     * Deve ser chamado APÓS setar dasdos de feedback.
     * Este método é responsável por persistir o feedback no banco de dados.
     */
    fun salvarRetornoItemPedidoOL() {
        val retornoItem = codigoRetorno ?: calcularCodigoRetorno()
        vo.codRetSkw = retornoItem.codigo
        vo.retSkw = mensagem.retirarTagsHtml().take(100)
        vo.qtdAtd = qtdAtendida
        itemPedidoOLDAO.save(vo)
    }

    /**
     * Marca o ItemPedidoOL como NÃO pendente, sometne na tabela intermediária.
     */
    fun marcarComoNaoPendente(){
        this.vo.pendente = false
        itemPedidoOLDAO.save(this.vo)
    }

    /**
     * Com base na mensagem de feedback, este método é capaz de calcular - utilizando REGEX - qual o código de
     * retorno da mensagem de erro/aviso.
     */
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

    /**
     * Métodos Fabrica.
     */
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