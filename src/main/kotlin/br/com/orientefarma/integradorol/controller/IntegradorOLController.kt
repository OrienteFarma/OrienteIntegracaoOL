package br.com.orientefarma.integradorol.controller

import br.com.lugh.dao.openSession
import br.com.orientefarma.integradorol.commons.LogOL
import br.com.orientefarma.integradorol.exceptions.EnviarPedidoCentralException
import br.com.orientefarma.integradorol.model.IntegradorOL
import br.com.orientefarma.integradorol.model.PedidoOL
import br.com.sankhya.jape.core.JapeSession
import br.com.sankhya.modelcore.auth.AuthenticationInfo
import br.com.sankhya.modelcore.util.SPBeanUtils
import br.com.sankhya.ws.ServiceContext

class IntegradorOLController {
    private fun enviarParaCentral(hnd: JapeSession.SessionHandle, pedidoOL: PedidoOL, ehJob: Boolean = false): Int? {
        var nuNotaEnviado: Int? = null
        try{
            if(ehJob) setarVariaveisSessao()
            LogOL.info("Iniciando envio para a central...")
            hnd.execWithTX { pedidoOL.marcarComoEnviandoParaCentral() }
            hnd.execWithTX {
                val integradorOL = IntegradorOL(pedidoOL)
                nuNotaEnviado = integradorOL.enviarParaCentral()
            }
        }catch (e: EnviarPedidoCentralException){
            LogOL.info("Registrando erro tratado (${e.retornoOL.name}/${pedidoOL.nuPedOL})...")
            hnd.execWithTX { pedidoOL.salvarErroSankhya(e) }
        }catch (e: Exception){
            LogOL.erro("Erro nao conhecido: ${e.message} ...")
            e.printStackTrace()
            hnd.execWithTX {
                pedidoOL.salvarErroSankhya(e)
            }
        }
        return nuNotaEnviado
    }

    fun enviarParaCentral(nuPedOL: String, codProjeto: Int): Int? {
        var nuNotaEnviado: Int? = null
        openSession {
            val pedidoOL = PedidoOL.fromPk(nuPedOL, codProjeto)
            nuNotaEnviado = enviarParaCentral(it, pedidoOL)
        }
        return nuNotaEnviado
    }

    fun enviarPendentesParaCentral(){
        openSession {
            val pedidoOLPendentes = PedidoOL.fromPendentes()
            for (pedidoOLPendente in pedidoOLPendentes) {
                enviarParaCentral(it, pedidoOLPendente, true)
            }
        }
    }

    private fun setarVariaveisSessao() {
        val serviceContext = ServiceContext(null)
        serviceContext.autentication = AuthenticationInfo.getCurrent()
        serviceContext.makeCurrent()
        SPBeanUtils.setupContext(serviceContext)
    }
}