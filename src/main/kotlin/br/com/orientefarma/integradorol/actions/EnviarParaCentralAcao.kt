package br.com.orientefarma.integradorol.actions

import br.com.orientefarma.integradorol.controller.IntegradorOLController
import br.com.orientefarma.integradorol.controller.dto.PedidoOLDto
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

        if(contextoAcao.linhas.size > 1){
            contextoAcao.setMensagemRetorno("Selecione um pedido por vez.")
            return
        }

        val pedidoOlDto = PedidoOLDto.fromLinhas(contextoAcao.linhas[0])

        IntegradorOLController().enviarParaCentral(pedidoOlDto)

        if(pedidoOlDto.size > 1 || pedidoOlDto.isEmpty() || pedidoOlDto.first().nuNotaEnviado == null){
            contextoAcao.setMensagemRetorno("Processamento concluído.")
            return
        }else{
            contextoAcao.setMensagemRetorno("Pedido ${pedidoOlDto.first().nuNotaEnviado} criado.")
        }
    }
}