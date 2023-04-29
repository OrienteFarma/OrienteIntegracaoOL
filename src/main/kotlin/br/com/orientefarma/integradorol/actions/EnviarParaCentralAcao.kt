package br.com.orientefarma.integradorol.actions

import br.com.orientefarma.integradorol.controller.IntegradorOLController
import br.com.sankhya.extensions.actionbutton.AcaoRotinaJava
import br.com.sankhya.extensions.actionbutton.ContextoAcao

@Suppress("unused")
class EnviarParaCentralAcao : AcaoRotinaJava {
    override fun doAction(contextoAcao: ContextoAcao) {
        Thread.currentThread().name = "IntegradorOL"

        if(contextoAcao.linhas.isEmpty()){
            contextoAcao.setMensagemRetorno("Selecione ao menos um pedido.")
            return
        }

        val controller = IntegradorOLController()
        for (linha in contextoAcao.linhas) {
            val nuPedOL = linha.getCampo("NUPEDOL").toString()
            val codProjeto = linha.getCampo("CODPRJ").toString().toInt()
            controller.enviarParaCentral(nuPedOL, codProjeto)
        }

    }
}