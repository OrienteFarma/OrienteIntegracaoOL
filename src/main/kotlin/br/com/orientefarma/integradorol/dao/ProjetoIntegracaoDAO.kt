package br.com.orientefarma.integradorol.dao

import br.com.lugh.dao.GenericDAO
import br.com.orientefarma.integradorol.dao.vo.PedidoOLVO
import br.com.orientefarma.integradorol.dao.vo.ProjetoIntegracaoVO

class ProjetoIntegracaoDAO : GenericDAO<ProjetoIntegracaoVO>("BHIntegracaoProjeto", ProjetoIntegracaoVO::class.java) {

    fun descontoArquivo(codProjeto: Int): String {
        val projetoIntegracao = findOne {
            it.where = " NUINTEGRACAO = ? "
            it.parameters = arrayOf(codProjeto)
        }
        return requireNotNull(projetoIntegracao?.ad_descArquivo.toString())
    }



}