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
            val nuNota = controller.enviarParaCentral(nuPedOL, codProjeto)
            if(nuNota != null){
                contextoAcao.setMensagemRetorno("Pedido $nuNota criado com sucesso.")
            }else{
                contextoAcao.setMensagemRetorno("Não foi possível criar o pedido, verifique os campos de retorno.")
            }
        }

    }
}