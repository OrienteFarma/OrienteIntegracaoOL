package br.com.orientefarma.integradorol.model

import br.com.sankhya.jape.sql.NativeSql
import br.com.sankhya.modelcore.util.EntityFacadeFactory
import com.sankhya.util.JdbcUtils
import java.math.BigDecimal
import java.sql.ResultSet

class Estoque {
    fun consultaEstoque(codEmp: BigDecimal?, codProd: BigDecimal?, codLocal: BigDecimal?, reserva: Boolean): BigDecimal {
        val jdbc = EntityFacadeFactory.getDWFFacade().jdbcWrapper
        val sql = NativeSql(jdbc)
        lateinit var resultSet: ResultSet
        try {
            sql.appendSql("SELECT SUM(ESTOQUE ")
            if (reserva) {
                sql.appendSql("- RESERVADO")
            }
            sql.appendSql(") FROM TGFEST WHERE CODPROD = :PRODUTO AND TIPO='P' AND CODPARC = 0")
            if (codLocal != null) {
                sql.appendSql(" AND CODLOCAL = :CODLOCAL")
                sql.setNamedParameter("CODLOCAL", codLocal)
            }
            if (codEmp != null) {
                sql.appendSql(" AND CODEMP = :CODEMP")
                sql.setNamedParameter("CODEMP", codEmp)
            }
            sql.setNamedParameter("PRODUTO", codProd)
            resultSet = sql.executeQuery()
            return if (resultSet.next()) {
                resultSet.getBigDecimal(1) ?: BigDecimal.ZERO
            } else BigDecimal.ZERO
        }finally {
            JdbcUtils.closeResultSet(resultSet)
            NativeSql.releaseResources(sql)
            jdbc.closeSession()

        }
    }
}