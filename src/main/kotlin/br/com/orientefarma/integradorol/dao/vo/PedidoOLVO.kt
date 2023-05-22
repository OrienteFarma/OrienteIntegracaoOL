package br.com.orientefarma.integradorol.dao.vo

import br.com.lugh.dao.WrapperVO
import br.com.lugh.dsl.metadados.pojo.Delegate
import br.com.lugh.dsl.metadados.pojo.DelegateNotNull
import br.com.lugh.dsl.metadados.pojo.EnumDelegate
import br.com.lugh.dsl.metadados.pojo.Pojo
import br.com.orientefarma.integradorol.commons.RetornoPedidoEnum
import br.com.orientefarma.integradorol.commons.StatusPedidoOLEnum
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
    var codRetSkw: RetornoPedidoEnum? by EnumDelegate(RetornoPedidoEnum::class.java)
    var status: StatusPedidoOLEnum? by EnumDelegate(StatusPedidoOLEnum::class.java)
    var nuNota: Int? by Delegate()
    var nuPedCli: String? by Delegate()
    var dhInclusao: Timestamp? by Delegate()
    var codJustificativa: Int? by Delegate()
}