package br.com.orientefarma.integradorol.jobs

import br.com.orientefarma.integradorol.controller.IntegradorOLController
import org.cuckoo.core.ScheduledAction
import org.cuckoo.core.ScheduledActionContext

class EnviarParaCentralJob : ScheduledAction {
    override fun onTime(contextoJob: ScheduledActionContext) {
        val controller = IntegradorOLController()
        controller.enviarPendentesParaCentral()
    }
}