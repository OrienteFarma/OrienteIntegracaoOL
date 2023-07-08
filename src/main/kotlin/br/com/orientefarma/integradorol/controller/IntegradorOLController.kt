package br.com.orientefarma.integradorol.controller

import br.com.lugh.dao.openSession
import br.com.orientefarma.integradorol.commons.LogOL
import br.com.orientefarma.integradorol.commons.ParallelExecutor
import br.com.orientefarma.integradorol.controller.dto.CancelarPedidoOLDto
import br.com.orientefarma.integradorol.controller.dto.PedidoOLDto
import br.com.orientefarma.integradorol.exceptions.CancelarPedidoCentralException
import br.com.orientefarma.integradorol.exceptions.EnviarPedidoCentralException
import br.com.orientefarma.integradorol.exceptions.IntegradorOLException
import br.com.orientefarma.integradorol.model.IntegradorOL
import br.com.orientefarma.integradorol.model.PedidoOL
import br.com.sankhya.jape.core.JapeSession
import br.com.sankhya.modelcore.auth.AuthenticationInfo
import br.com.sankhya.modelcore.util.SPBeanUtils
import br.com.sankhya.ws.ServiceContext

class IntegradorOLController {

    /**
     * Responsavel por chamar o model para enviar o PedidoOL para a central, bem como controlar as
     * transações apartardas (status, principal e log de erro).
     */
    private fun enviarParaCentral(hnd: JapeSession.SessionHandle, pedidoOLDto: PedidoOLDto, ehJob: Boolean = false): Int? {
        var nuNotaEnviado: Int? = null
        try {
            if (ehJob) setarVariaveisSessao()
            LogOL.info("Iniciando envio para a central...")
            hnd.execWithTX {
                val pedidoOL = PedidoOL.fromPk(pedidoOLDto.nuPedOL, pedidoOLDto.codProjeto)
                pedidoOL.marcarComoEnviandoParaCentral()
            }
            hnd.execWithTX {
                val pedidoOL = PedidoOL.fromPk(pedidoOLDto.nuPedOL, pedidoOLDto.codProjeto)
                val integradorOL = IntegradorOL(pedidoOL)
                nuNotaEnviado = integradorOL.enviarParaCentral()
            }
        }catch (e: EnviarPedidoCentralException) {
            salvarErroTratado(e, pedidoOLDto, hnd)
        }catch (e: Exception){
            LogOL.erro("Erro nao conhecido: ${e.message} ...")
            e.printStackTrace()
            hnd.execWithTX {
                val pedidoOL = PedidoOL.fromPk(pedidoOLDto.nuPedOL, pedidoOLDto.codProjeto)
                pedidoOL.salvarErroSankhya(e)
            }
        }
        return nuNotaEnviado
    }

    fun enviarParaCentral(pedidosOLDto: List<PedidoOLDto>){
        openSession {
            for (pedidoOLDto in pedidosOLDto) {
                val nuNotaEnviado = enviarParaCentral(it, pedidoOLDto)
                pedidoOLDto.nuNotaEnviado = nuNotaEnviado
            }
        }
    }

    fun enviarPendentesParaCentral(){
        val poolThreads = ParallelExecutor.getInstance(2)
        try{
            val pedidoOLDtoPendentes = PedidoOL.fromPendentes()
            for (pedidoOLDto in pedidoOLDtoPendentes) {
                poolThreads.addTask("Enviando para central, pedido OL ${pedidoOLDto.codProjeto}/${pedidoOLDto.nuPedOL}."){
                    openSession {
                        enviarParaCentral(it, pedidoOLDto, true)
                    }
                }
            }
        }finally{
            poolThreads.executeTasks()
        }
    }

    fun cancelarPedidoCentral(cancelamentoDTO: CancelarPedidoOLDto){
        openSession {
            val pedidoDTO = cancelamentoDTO.pedidoOLDto
            val pedidoOL = PedidoOL.fromPk(pedidoDTO.nuPedOL, pedidoDTO.codProjeto)
            val integradorOL = IntegradorOL(pedidoOL)
            try{
                it.execWithTX { integradorOL.cancelarPedido(cancelamentoDTO.codJustificativa) }
            }catch (e: CancelarPedidoCentralException){
                salvarErroTratado(e, pedidoDTO, it)
            }
        }
    }

    private fun setarVariaveisSessao() {
        val auth = AuthenticationInfo("SUP", 0.toBigDecimal(), 0.toBigDecimal(), 0)
        auth.makeCurrent()
        val serviceContext = ServiceContext(null)
        serviceContext.autentication = auth
        serviceContext.makeCurrent()
        SPBeanUtils.setupContext(serviceContext)
    }

    private fun salvarErroTratado(e: IntegradorOLException, pedidoOLDto: PedidoOLDto, hnd: JapeSession.SessionHandle){
        val pedidoOL = PedidoOL.fromPk(pedidoOLDto.nuPedOL, pedidoOLDto.codProjeto)
        LogOL.info("Registrando erro tratado (${e.retornoOL.name}/${pedidoOL.nuPedOL})...")
        hnd.execWithTX { pedidoOL.salvarErroSankhya(e) }
    }
}