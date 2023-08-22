package br.com.orientefarma.integradorol.uitls

import br.com.lugh.bh.CentralNotasUtils

class CentralNotaUtilsWrapper {

    fun refazerFinanceiro(nuNota: Int){
        CentralNotasUtils.refazerFinanceiro(nuNota.toBigDecimal())
    }
}