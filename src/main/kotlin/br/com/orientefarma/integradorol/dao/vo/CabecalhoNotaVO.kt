package br.com.orientefarma.integradorol.dao.vo

import br.com.lugh.dao.WrapperVO
import br.com.lugh.dsl.metadados.pojo.Delegate
import br.com.lugh.dsl.metadados.pojo.DelegateNotNull
import br.com.lugh.dsl.metadados.pojo.Pojo
import java.math.BigDecimal
import java.sql.Timestamp

@Suppress("unused")
class CabecalhoNotaVO(vo: WrapperVO) : Pojo(vo) {
    var nuNota: Int by DelegateNotNull()
    var numNota: Int by DelegateNotNull()
    var codEmp: Int by DelegateNotNull()
    var dhMov: Timestamp? by Delegate()
    var codParc: Int by DelegateNotNull()
    var codVend: Int? by Delegate()
    var dtNeg: Timestamp by DelegateNotNull()
    var vlrNota: BigDecimal by DelegateNotNull()
    var codNat: Int by DelegateNotNull()
    var codCenCus: Int by DelegateNotNull()
    var codProj: Int by DelegateNotNull()
}