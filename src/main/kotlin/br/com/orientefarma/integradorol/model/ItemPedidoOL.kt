package br.com.orientefarma.integradorol.model

import br.com.orientefarma.integradorol.commons.RetornoItemPedidoEnum
import br.com.orientefarma.integradorol.commons.retirarTagsHtml
import br.com.orientefarma.integradorol.dao.ItemPedidoOLDAO
import br.com.orientefarma.integradorol.dao.vo.ItemPedidoOLVO
import com.sankhya.util.StringUtils

class ItemPedidoOL(val vo: ItemPedidoOLVO) {
    private val itemPedidoOLDAO = ItemPedidoOLDAO()

    /**
     * Capaz de setar os dados para retorno do item de pedido OL.
     * Por exemplo: Se o item tem DESCONTO INVALIDO ou ESTOQUE INSUFICIENTE.
     * USADO quando somente h� a mensagem de erro.
     */
    fun setFeedback(mensagem: String, qtdAtendida: Int){
        this.vo.retSkw = mensagem.retirarTagsHtml().take(100)
        this.vo.qtdAtd = qtdAtendida
    }

    fun setFeedbackMensagem(mensagem: String){
        this.vo.retSkw = mensagem.retirarTagsHtml().take(100)
    }

    /**
     * Capaz de setar os dados para retorno do item de pedido OL.
     * Por exemplo: Se o item tem DESCONTO INVALIDO ou ESTOQUE INSUFICIENTE.
     * USADO quando se tem o RetornoItemPedidoEnum - erro catalogado.
     */
    fun setFeedback(retorno: RetornoItemPedidoEnum, qtdAtendida: Int, mensagem: String = ""){
        this.vo.codRetSkw = retorno.codigo

        if (StringUtils.isEmpty(mensagem)) {
            this.vo.retSkw = retorno.name
        }
        else{
            this.vo.retSkw = mensagem.retirarTagsHtml().take(100)
        }
        this.vo.qtdAtd = qtdAtendida
    }

    /**
     * Deve ser chamado AP�S setar dasdos de feedback.
     * Este m�todo � respons�vel por persistir o feedback no banco de dados.
     */
    fun salvarRetornoItemPedidoOL() {
        val retornoItem = calcularCodigoRetorno()
        vo.codRetSkw = retornoItem.codigo
        itemPedidoOLDAO.save(vo)
    }

    /**
     * Marca o ItemPedidoOL como N�O pendente, sometne na tabela intermedi�ria.
     */
    fun marcarComoNaoPendente(){
        this.vo.pendente = false
        itemPedidoOLDAO.save(this.vo)
    }

    /**
     * Com base na mensagem de feedback, este m�todo � capaz de calcular - utilizando REGEX - qual o c�digo de
     * retorno da mensagem de erro/aviso.
     */
    private fun calcularCodigoRetorno(): RetornoItemPedidoEnum {
        val codRetSkw = this.vo.codRetSkw
        if(codRetSkw != null){
            return RetornoItemPedidoEnum.fromValor(codRetSkw)
        }else{
            for (retornoItem in RetornoItemPedidoEnum.values()) {
                val mensagemRetorno = this.vo.retSkw
                if (mensagemRetorno != null && mensagemRetorno.contains(retornoItem.expressaoRegex)) {
                    return retornoItem
                }
            }

        }
        return RetornoItemPedidoEnum.FALHA_DESCONHECIDA
    }
    override fun toString(): String {
        return "EnviarItemPedidoCentralException(mensagem=${this.vo.retSkw})"
    }

    /**
     * M�todos Fabrica.
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