package br.com.orientefarma.integradorol.actions

import br.com.orientefarma.integradorol.controller.IntegradorOLController
import br.com.orientefarma.integradorol.controller.dto.CancelarPedidoOLDto
import br.com.orientefarma.integradorol.controller.dto.PedidoOLDto
import br.com.sankhya.extensions.actionbutton.AcaoRotinaJava
import br.com.sankhya.extensions.actionbutton.ContextoAcao

class CancelarPedidoAcao: AcaoRotinaJava {
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

        val pedidoOlDTO = PedidoOLDto.fromLinha(contextoAcao.linhas[0])
        val codJustificativa = contextoAcao.getParam("CODJUSTIFICATIVA").toString().toInt()

        val cancelamentoDTO = CancelarPedidoOLDto(pedidoOlDTO, codJustificativa)

        IntegradorOLController().cancelarPedidoCentral(cancelamentoDTO)

        contextoAcao.setMensagemRetorno("Pedido OL ${pedidoOlDTO.nuPedOL} cancelado com sucesso.")
    }
}