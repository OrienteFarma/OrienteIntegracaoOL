package br.com.orientefarma.integradorol.dao

import br.com.lugh.dao.GenericDAO
import br.com.orientefarma.integradorol.dao.vo.ItemPedidoOLVO

@Suppress("unused")
class ItemPedidoOLDAO : GenericDAO<ItemPedidoOLVO>("AD_INTITEOL", ItemPedidoOLVO::class.java) {

    fun findByPk(nuPedOL: String, codProjeto: Int, referencia: String): ItemPedidoOLVO {
        val itemPedidoOLVO = findOne {
            it.where = " NUPEDOL = ? AND CODPRJ = ? AND REFERENCIA = ? "
            it.parameters = arrayOf(nuPedOL, codProjeto, referencia)
        }
        return requireNotNull(itemPedidoOLVO){
            " Item Pedido OL com chaves $nuPedOL, $codProjeto e $referencia não foi encontrado. "
        }
    }

    fun findByNumPedOL(nuPedOL: String, codProjeto: Int): Collection<ItemPedidoOLVO> {
        return find {
            it.where = " NUPEDOL = ? AND CODPRJ = ? "
            it.parameters = arrayOf(nuPedOL, codProjeto)
        }
    }

}