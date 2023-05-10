package br.com.orientefarma.integradorol.commons

fun String.retirarTagsHtml() = this.replace(Regex("<[^>]*>"), "")