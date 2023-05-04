package br.com.orientefarma.integradorol.exceptions

import br.com.orientefarma.integradorol.model.pojo.ItemPedidoPojo

@Suppress("unused")
class EnviarItemPedidoCentralException(val itemPedidoPojo: ItemPedidoPojo): Exception()