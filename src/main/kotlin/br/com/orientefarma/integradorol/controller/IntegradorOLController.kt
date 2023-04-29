package br.com.orientefarma.integradorol.controller

import br.com.lugh.dao.openSession
import br.com.orientefarma.integradorol.commons.LogOL
import br.com.orientefarma.integradorol.exceptions.EnviarPedidoCentralException
import br.com.orientefarma.integradorol.model.IntegradorOL

class IntegradorOLController {
    fun enviarParaCentral(nuPedOL: String, codProjeto: Int) {
        val model = IntegradorOL()
        openSession { hnd ->
            val pedidoOLVO = model.buscarPedidoOL(nuPedOL, codProjeto)
            try{
                LogOL.info("Iniciando envio para a central...")
                hnd.execWithTX {
                    model.enviarParaCentral(pedidoOLVO)
                }
            }catch (e: EnviarPedidoCentralException){
                LogOL.info("Registrando erro tratado (${e.retornoOL.name}/${pedidoOLVO.nuPedOL})...")
                hnd.execWithTX {
                    model.salvarRetornoSankhya(pedidoOLVO, e)
                }
            }catch (e: Exception){
                LogOL.erro("Erro nao conhecido: ${e.message} ...")
                e.printStackTrace()
                hnd.execWithTX {
                    model.salvarErroSankhya(pedidoOLVO, e)
                }
            }
        }
    }
}