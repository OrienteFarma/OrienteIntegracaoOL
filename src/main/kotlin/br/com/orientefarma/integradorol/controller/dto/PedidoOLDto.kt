package br.com.orientefarma.integradorol.controller.dto

import br.com.sankhya.extensions.actionbutton.Registro

data class PedidoOLDto(val nuPedOL: String, val codProjeto: Int) {
    var nuNotaEnviado: Int? = null
    companion object {
        fun fromLinha(linhas: Array<Registro>): List<PedidoOLDto> {
            val dtos = mutableListOf<PedidoOLDto>()
            for (linha in linhas) {
                val nuPedOL = linha.getCampo("NUPEDOL").toString()
                val codProjeto = linha.getCampo("CODPRJ").toString().toInt()
                dtos.add(PedidoOLDto(nuPedOL, codProjeto))
            }
            return dtos.toList()
        }

        fun fromLinhas(linha: Registro): List<PedidoOLDto> {
            val dtos = mutableListOf<PedidoOLDto>()
            val nuPedOL = linha.getCampo("NUPEDOL").toString()
            val codProjeto = linha.getCampo("CODPRJ").toString().toInt()
            dtos.add(PedidoOLDto(nuPedOL, codProjeto))
            return dtos.toList()
        }

        fun fromLinha(linha: Registro): PedidoOLDto {
            val nuPedOL = linha.getCampo("NUPEDOL").toString()
            val codProjeto = linha.getCampo("CODPRJ").toString().toInt()
            return PedidoOLDto(nuPedOL, codProjeto)
        }
    }
}
