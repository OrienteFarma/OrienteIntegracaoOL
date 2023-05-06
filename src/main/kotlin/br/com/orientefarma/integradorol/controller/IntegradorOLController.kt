package br.com.orientefarma.integradorol.controller

import br.com.lugh.dao.openSession
import br.com.orientefarma.integradorol.commons.LogOL
import br.com.orientefarma.integradorol.exceptions.EnviarPedidoCentralException
import br.com.orientefarma.integradorol.model.IntegradorOL
import br.com.orientefarma.integradorol.model.PedidoOL

class IntegradorOLController {
    fun enviarParaCentral(nuPedOL: String, codProjeto: Int): Int? {
        var nuNotaEnviado: Int? = null
        openSession { hnd ->
            val pedidoOL = PedidoOL(nuPedOL, codProjeto)
            try{
                LogOL.info("Iniciando envio para a central...")
                hnd.execWithTX {
                    val integradorOL = IntegradorOL(pedidoOL)
                    nuNotaEnviado = integradorOL.enviarParaCentral()
                }
            }catch (e: EnviarPedidoCentralException){
                LogOL.info("Registrando erro tratado (${e.retornoOL.name}/${pedidoOL.nuPedOL})...")
                hnd.execWithTX { pedidoOL.salvarRetornoSankhya(e) }
            }catch (e: Exception){
                LogOL.erro("Erro nao conhecido: ${e.message} ...")
                e.printStackTrace()
                hnd.execWithTX {
                    pedidoOL.salvarErroSankhya(e)
                }
            }
        }
        return nuNotaEnviado
    }
}