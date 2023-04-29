package br.com.orientefarma.integradorol.dao.vo

import br.com.lugh.dao.WrapperVO
import br.com.lugh.dsl.metadados.pojo.Delegate
import br.com.lugh.dsl.metadados.pojo.DelegateNotNull
import br.com.lugh.dsl.metadados.pojo.Pojo
import java.math.BigDecimal
import java.sql.Timestamp

@Suppress("unused")
class ItemPedidoOLVO(vo: WrapperVO) : Pojo(vo) {
    var nuPedOL: String by DelegateNotNull()
    var codPrj: Int by DelegateNotNull()
    var referencia: String by DelegateNotNull()
    var qtdPed: Int? by Delegate()
    var qtdAtd: Int? by Delegate()
    var dtPro: Timestamp? by Delegate()
    var prodDesc: BigDecimal? by Delegate()
    var codProdInd: String? by Delegate()
    var status: String? by Delegate()
    var codRetSkw: String? by Delegate()
    var retSkw: String? by Delegate()
    var sequenciaArquivo: Int? by Delegate()
    var codProd: Int? by Delegate()
}