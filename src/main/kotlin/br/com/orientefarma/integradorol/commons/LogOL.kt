package br.com.orientefarma.integradorol.commons

@Suppress("unused")
class LogOL{

    companion object {
        fun info(mensagem: String, nome: String = "IntegracaoOL"){
            println(mensagem)
        }

        fun erro(mensagem: String, nome: String = "IntegracaoOL"){
            println(mensagem)
        }
    }

}