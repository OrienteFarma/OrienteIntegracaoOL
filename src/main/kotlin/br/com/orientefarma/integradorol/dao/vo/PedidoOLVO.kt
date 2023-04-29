package br.com.orientefarma.integradorol.dao.vo

import br.com.lugh.dao.WrapperVO
import br.com.lugh.dsl.metadados.pojo.Delegate
import br.com.lugh.dsl.metadados.pojo.DelegateNotNull
import br.com.lugh.dsl.metadados.pojo.Pojo
import java.math.BigDecimal
import java.sql.Timestamp

@Suppress("unused")
class PedidoOLVO(vo: WrapperVO) : Pojo(vo) {
    var nuPedOL: String by DelegateNotNull()
    var codPrj: Int by DelegateNotNull()
    var codEmp: Int? by Delegate()
    var cnpjInd: String? by Delegate()
    var cnpjCli: String? by Delegate()
    var dtPro: Timestamp? by Delegate()
    var hrPro: Int? by Delegate()
    var nFile: String? by Delegate()
    var cond: String? by Delegate()
    var codPrz: BigDecimal? by Delegate()
    var retSkw: String? by Delegate()
    var codRetSkw: Int? by Delegate()
    var status: String? by Delegate()
    var nuNota: String? by Delegate()
    var nuPedCli: String? by Delegate()
    var dhInclusao: Timestamp? by Delegate()
}