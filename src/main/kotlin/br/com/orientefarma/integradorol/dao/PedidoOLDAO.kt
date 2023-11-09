package br.com.orientefarma.integradorol.dao

import br.com.lugh.dao.GenericDAO
import br.com.orientefarma.integradorol.dao.vo.PedidoOLVO

class PedidoOLDAO : GenericDAO<PedidoOLVO>("AD_INTCABOL", PedidoOLVO::class.java) {

    fun findByPk(nuPedOL: String, codProjeto: Int): PedidoOLVO {
        val pedidoOLVO = findOne {
            it.where = " NUPEDOL = ? AND CODPRJ = ? "
            it.parameters = arrayOf(nuPedOL, codProjeto)
        }
        return requireNotNull(pedidoOLVO){
            " Pedido OL com chaves $nuPedOL, $codProjeto n�o foi encontrado. "
        }
    }

    fun findIntegrados(maxResults: Int = 10): Collection<PedidoOLVO> {
        return find {
            it.where = " STATUS = 'I' AND nullValue(BHIntegracaoProjeto->AD_NOVA_ABORDAGEM, 'N') = 'S' "
            it.maxResults = maxResults
            it.orderBy = "DHINCLUSAO ASC"
        }

    }

}