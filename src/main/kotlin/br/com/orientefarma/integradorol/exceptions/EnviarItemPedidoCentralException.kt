package br.com.orientefarma.integradorol.exceptions

@Suppress("unused")
class EnviarItemPedidoCentralException(val mensagem: String? = "Sem mensagem", e: Exception? = null)
    : Exception(mensagem, e)