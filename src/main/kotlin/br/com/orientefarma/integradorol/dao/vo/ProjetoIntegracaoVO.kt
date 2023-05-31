package br.com.orientefarma.integradorol.dao.vo

import br.com.lugh.dao.WrapperVO
import br.com.lugh.dsl.metadados.pojo.Delegate
import br.com.lugh.dsl.metadados.pojo.DelegateNotNull
import br.com.lugh.dsl.metadados.pojo.Pojo


@Suppress("unused")
class ProjetoIntegracaoVO(vo: WrapperVO) : Pojo(vo) {
    var nuIntegracao: Int by DelegateNotNull()
    var descricao: String by DelegateNotNull()
    var ativo: String? by Delegate()
    var ad_nova_abordagem: String? by Delegate()
    var ad_descArquivo: String? by Delegate()
}