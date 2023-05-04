package br.com.orientefarma.integradorol.model.pojo

import br.com.orientefarma.integradorol.commons.RetornoItemPedidoEnum

class ItemPedidoPojo(val mensagem: String? = "Sem mensagem") {
    override fun toString(): String {
        return "EnviarItemPedidoCentralException(mensagem=$mensagem)"
    }

    fun calcularCodigoRetorno(): RetornoItemPedidoEnum {
        for (retornoItem in RetornoItemPedidoEnum.values()) {
            if(this.mensagem != null){
                if (this.mensagem.contains(retornoItem.expressaoRegex)) {
                    return retornoItem
                }
            }
        }
        return RetornoItemPedidoEnum.FALHA_DESCONHECIDA
    }
}