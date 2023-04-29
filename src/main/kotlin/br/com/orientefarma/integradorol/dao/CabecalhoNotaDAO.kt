package br.com.orientefarma.integradorol.dao

import br.com.lugh.dao.GenericDAO
import br.com.orientefarma.integradorol.dao.vo.CabecalhoNotaVO
import br.com.sankhya.modelcore.util.DynamicEntityNames

@Suppress("unused")
class CabecalhoNotaDAO : GenericDAO<CabecalhoNotaVO>(DynamicEntityNames.CABECALHO_NOTA, CabecalhoNotaVO::class.java) {

    fun findByPkOL(nuPedOL: String, codProjeto: Int): CabecalhoNotaVO? {
        return findOne {
            it.where = " AD_NUMPEDIDO_OL = ? AND AD_NUINTEGRACAO = ? "
            it.parameters = arrayOf(nuPedOL, codProjeto)
        }
    }

    fun findByPk(nuNota: Int): CabecalhoNotaVO {
        val cabecalhoNotaVO = findOne {
            it.where = " NUNOTA = ?  "
            it.parameters = arrayOf(nuNota)
        }
        return requireNotNull(cabecalhoNotaVO){" Falha ao encontrar cabecalho nota com a pk $nuNota"}
    }
}