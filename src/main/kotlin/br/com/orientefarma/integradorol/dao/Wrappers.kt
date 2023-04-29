package br.com.orientefarma.integradorol.dao

import br.com.lugh.dao.WrapperVO
import br.com.lughconsultoria.dao.vo.ProdutoVO
import br.com.orientefarma.integradorol.dao.vo.CabecalhoNotaVO
import br.com.sankhya.jape.vo.DynamicVO

fun DynamicVO.toCabecalhoNotaVO() = CabecalhoNotaVO(WrapperVO(this))
fun DynamicVO.toProdutoVO() = ProdutoVO(WrapperVO(this))