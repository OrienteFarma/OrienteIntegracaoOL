package br.com.orientefarma.integradorol.actions

import br.com.lugh.dao.get
import br.com.orientefarma.integradorol.commons.ParallelExecutor
import br.com.sankhya.extensions.actionbutton.AcaoRotinaJava
import br.com.sankhya.extensions.actionbutton.ContextoAcao

class LiberarReprocessamentoJobAcao : AcaoRotinaJava{
    override fun doAction(contextoAcao: ContextoAcao) {
        if(contextoAcao.linhas.isEmpty()){
            contextoAcao.setMensagemRetorno("Selecione ao menos um pedido.")
            return
        }

        if(contextoAcao.linhas.size > 1){
            contextoAcao.setMensagemRetorno("Selecione um pedido por vez.")
            return
        }

        for (linha in contextoAcao.linhas) {
            val codProjeto = linha["CODPRJ"].toString().toInt()
            val nuPedOL = linha["NUPEDOL"].toString()
            ParallelExecutor.liberarReprocessamentoJob(codProjeto, nuPedOL)
        }
    }
}