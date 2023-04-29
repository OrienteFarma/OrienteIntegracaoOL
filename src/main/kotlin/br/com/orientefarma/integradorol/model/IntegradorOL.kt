package br.com.orientefarma.integradorol.model


import br.com.lugh.bh.CentralNotasUtils
import br.com.lugh.bh.tryOrNull
import br.com.lugh.dao.EntityFacadeW
import br.com.lughconsultoria.dao.ItemNotaDAO
import br.com.lughconsultoria.dao.ParceiroDAO
import br.com.lughconsultoria.dao.vo.ItemNotaVO
import br.com.lughconsultoria.dao.vo.ParceiroVO
import br.com.orientefarma.integradorol.commons.LogOL
import br.com.orientefarma.integradorol.commons.RetornoItemPedidoEnum
import br.com.orientefarma.integradorol.commons.RetornoPedidoEnum
import br.com.orientefarma.integradorol.commons.StatusPedidoOLEnum
import br.com.orientefarma.integradorol.dao.*
import br.com.orientefarma.integradorol.dao.vo.CabecalhoNotaVO
import br.com.orientefarma.integradorol.dao.vo.PedidoOLVO
import br.com.orientefarma.integradorol.exceptions.EnviarItemPedidoCentralException
import br.com.orientefarma.integradorol.exceptions.EnviarPedidoCentralException
import br.com.sankhya.jape.core.JapeSession
import br.com.sankhya.jape.util.JapeSessionContext
import br.com.sankhya.modelcore.auth.AuthenticationInfo
import br.com.sankhya.modelcore.comercial.BarramentoRegra
import br.com.sankhya.modelcore.comercial.impostos.ImpostosHelpper
import br.com.sankhya.modelcore.util.DynamicEntityNames
import br.com.sankhya.modelcore.util.MGECoreParameter
import java.math.BigDecimal


class IntegradorOL {

    private val cabecalhoNotaDAO = CabecalhoNotaDAO()
    private val itemNotaDAO = ItemNotaDAO()
    private val pedidoOLDAO = PedidoOLDAO()
    private val itemPedidoOLDAO = ItemPedidoOLDAO()
    private val parceiroDAO = ParceiroDAO()

    private val paramTOPPedido: BigDecimal
    private val paraModeloPedido: BigDecimal

    private val fatorPercentual = 0.01.toBigDecimal()

    init {
        val nomeParamTOPPedido = "OR_OLTOPPED"
        paramTOPPedido = tryOrNull {
            MGECoreParameter.getParameter(nomeParamTOPPedido).toString().toBigDecimal()
        } ?: throw IllegalStateException("Verifique o parâmetro $nomeParamTOPPedido.")

        val nomeParamModeloPedido = "OR_OLMODPED"
        paraModeloPedido = tryOrNull {
            MGECoreParameter.getParameter(nomeParamModeloPedido).toString().toBigDecimal()
        } ?: throw IllegalStateException("Verifique o parâmetro $nomeParamModeloPedido.")
    }

    fun buscarPedidoOL(nuPedOL: String, codProjeto: Int): PedidoOLVO {
        return pedidoOLDAO.findByPk(nuPedOL, codProjeto)
    }

    fun enviarParaCentral(pedidoOLVO: PedidoOLVO){
        verificarSePedidoExisteCentral(pedidoOLVO.nuPedOL, pedidoOLVO.codPrj)
        val clienteVO = buscarCliente(pedidoOLVO)
        val pedidoCentralVO = criarCabecalho(pedidoOLVO, clienteVO)

        val estoque = Estoque()
        val retornos = mutableListOf<EnviarItemPedidoCentralException>()

        val itensOL = itemPedidoOLDAO.findByNumPedOL(pedidoOLVO.nuPedOL, pedidoOLVO.codPrj)
        for (itemPedidoOLVO in itensOL) {
            try{
                if(itemPedidoOLVO.codProd == null) continue
                val produtoVO = requireNotNull(itemPedidoOLVO.vo.asDymamicVO(DynamicEntityNames.PRODUTO)).toProdutoVO()

                val codLocalOrig = if(produtoVO.usalocal && produtoVO.codlocalpadrao != null){
                    requireNotNull(produtoVO.codlocalpadrao).toBigDecimal()
                }else BigDecimal.ZERO

                val camposItem = mutableMapOf(
                    "NUNOTA" to pedidoCentralVO.nuNota.toBigDecimal(),
                    "CODEMP" to pedidoCentralVO.codEmp.toBigDecimal(),
                    "CODPROD" to requireNotNull(itemPedidoOLVO.codProd).toBigDecimal(),
                    "AD_CODCOND" to pedidoCentralVO.getAditionalField("CODCOND"),
                    "VLRUNIT" to null,
                    "CODVEND" to (pedidoCentralVO.codVend ?: 0).toBigDecimal(),
                    "CODVOL" to itemPedidoOLVO.vo.asString("Produto.CODVOL"),
                    "PERCDESC" to BigDecimal.ZERO,
                    "VLRDESC" to BigDecimal.ZERO,
                    "PENDENTE" to "S",
                    "CODLOCALORIG" to codLocalOrig,
                )



                val qtdEstoque = estoque.consultaEstoque(
                    codEmp = pedidoCentralVO.codEmp.toBigDecimal(),
                    codProd = produtoVO.codprod!!.toBigDecimal(),
                    codLocal = codLocalOrig,
                    reserva = true
                ).toInt()

                val qtdArquivo = requireNotNull(itemPedidoOLVO.qtdPed)

                camposItem.put("BH_QTDNEGORIGINAL", qtdArquivo.toBigDecimal())
                camposItem.put("QTDNEG", qtdArquivo.toBigDecimal())

                if(qtdEstoque >= qtdArquivo){
                    camposItem.put("AD_QTDESTOQUE", qtdEstoque.toBigDecimal())
                }else{
                    val qtdAtendida = qtdArquivo - ( qtdArquivo - zeroSeNegativo(qtdEstoque) )
                    if(qtdAtendida < 1){
                        camposItem.put("QTDNEG", qtdArquivo.toBigDecimal())
                        camposItem.put("BH_QTDCORTE", qtdArquivo.toBigDecimal())
                    }else{
                        camposItem.put("QTDNEG", qtdAtendida.toBigDecimal())
                        camposItem.put("BH_QTDCORTE", qtdArquivo.toBigDecimal() - qtdAtendida.toBigDecimal())
                        camposItem.put("AD_OLMARCARPENDENTE_NAO", "S")
                    }
                    camposItem.put("AD_QTDESTOQUE", qtdEstoque.toBigDecimal())
                }

                if(qtdEstoque <= 0){
                    // log cortado
                }else{
                    // log parcialmente atendido
                }

                camposItem.put("ATUALESTOQUE", 1.toBigDecimal())
                camposItem.put("RESERVADO", "S")

                val itemInseridoDados = inserirItemSemPreco(pedidoCentralVO, camposItem)

                val retornoException = itemInseridoDados.second
                if(retornoException != null){
                    retornos.add(retornoException)
                }

                val itemInseridoVO = itemInseridoDados.first ?: continue


                val precoBase = itemInseridoVO.precobase ?: 0.toBigDecimal()
                if(precoBase <= BigDecimal.ZERO){
                    itemInseridoVO.vo.setProperty("AD_OLMARCARPENDENTE_NAO", "S")
                    itemInseridoVO.observacao = "PRECO BASE ZERADO"
                } else {
                    val percDescCondicao = itemInseridoVO.vo.asBigDecimalOrZero("AD_PERCDESC")
                    val percDescArquivo = itemPedidoOLVO.prodDesc ?: 0.toBigDecimal()
                    if(percDescArquivo > percDescCondicao){
                        itemInseridoVO.vo.setProperty("AD_OLMARCARPENDENTE_NAO", "S")
                        itemInseridoVO.observacao = "DESCONTO INVALIDO"
                        // log desconto maior que o permitido
                    }else{
                        itemInseridoVO.vo.set("AD_PERCDESC", percDescArquivo)
                        val valorBruto = zeroSeNulo(itemPedidoOLVO.qtdPed).toBigDecimal() * precoBase
                        val valorDesconto = if(percDescArquivo <= BigDecimal.ZERO) {
                            BigDecimal.ZERO
                        }else{
                            valorBruto - ( valorBruto * (percDescArquivo * fatorPercentual))
                        }

                        val vlrUnitario = precoBase - (precoBase * (percDescArquivo * fatorPercentual))

                        itemInseridoVO.vo.setProperty("AD_VLRDESC", valorDesconto)
                        itemInseridoVO.vo.setProperty("VLRUNIT", vlrUnitario)
                        itemInseridoVO.vo.setProperty("AD_VLRUNITCM", vlrUnitario)
                        itemInseridoVO.vo.setProperty("VLRTOT", vlrUnitario * itemInseridoVO.qtdneg!!)


                    }

                }

                itemNotaDAO.save(itemInseridoVO)

            }catch (e: Exception){
                val enviarItemPedidoCentralException =
                    EnviarItemPedidoCentralException(e.message, RetornoItemPedidoEnum.FALHA_DESCONHECIDA)
                retornos.add(enviarItemPedidoCentralException)
            }
        }
    }

    private fun inserirItemSemPreco(pedidoCentralVO: CabecalhoNotaVO, camposItem: MutableMap<String, Any?>):
            Pair<ItemNotaVO?, EnviarItemPedidoCentralException?> {

        setSessionProperty("mov.financeiro.ignoraValidacao", true)
        setSessionProperty("br.com.sankhya.com.CentralCompraVenda", true)
        setSessionProperty("ItemNota.incluindo.alterando.pela.central", true)
        setSessionProperty("validar.alteracao.campos.em.titulos.baixados", false)

        var barramento: BarramentoRegra.DadosBarramento? = null
        var exceptionAoIncluir: EnviarItemPedidoCentralException? = null
        try {
            barramento = incluirItensSemPreco(pedidoCentralVO.nuNota.toBigDecimal(), arrayOf(camposItem))
        } catch (e: Exception) {
            exceptionAoIncluir = EnviarItemPedidoCentralException(e.message, RetornoItemPedidoEnum.FALHA_DESCONHECIDA)
        }

        val pksItemNota = barramento?.pksEnvolvidas?.first()
        if(pksItemNota != null){
            val sequencia = pksItemNota.values[1].toString().toInt()
            val itemInseridoVO = tryOrNull { itemNotaDAO.findByPK(pedidoCentralVO.nuNota, sequencia) }
            if(itemInseridoVO != null){
                return Pair(itemInseridoVO, exceptionAoIncluir)
            }
        }

        return Pair(null, exceptionAoIncluir)
    }

    private fun zeroSeNulo(valor: Int?) = valor ?: 0

    private fun setSessionProperty(nome: String, valor: Boolean) {
        JapeSession.putProperty(nome, valor)
        JapeSessionContext.putProperty(nome, valor)
    }

    private fun zeroSeNegativo(qtdEstoque: Int) = if (qtdEstoque <= 0) 0 else qtdEstoque

    private fun criarCabecalho(pedidoOLVO: PedidoOLVO, clienteVO: ParceiroVO): CabecalhoNotaVO {
        LogOL.info("Preparando a criacao do cabecalho...")
        val codTipVenda = requireNotNull(pedidoOLVO.codPrz) { " Prazo não informado. " }

        val camposPedidoCentral = mapOf(
            "AD_NUMPEDIDO_OL" to pedidoOLVO.nuPedOL,
            "CODVEND" to (clienteVO.codvend ?: 0).toBigDecimal(),
            "CODPARC" to clienteVO.codparc!!.toBigDecimal(),
            "CODEMP" to (pedidoOLVO.codEmp ?: 1).toBigDecimal(),
            "NUMPEDIDO2" to pedidoOLVO.nuPedCli,
            "NUMNOTA" to BigDecimal.ZERO,
            "CIF_FOB" to "F",
            "CODTIPOPER" to paramTOPPedido,
            "AD_TIPOCONDICAO" to "O",
            "CODTIPVENDA" to codTipVenda,
            "AD_CODCOND" to pedidoOLVO.cond?.toBigDecimal(),
            "AD_CODTIPVENDA" to codTipVenda,
            "AD_NUINTEGRACAO" to pedidoOLVO.codPrj.toBigDecimal(),
            "OBSERVACAO" to pedidoOLVO.nuPedCli,
            "AD_STATUSOL" to StatusPedidoOLEnum.INTEGRANDO.valor
        )

        LogOL.info("Tentando criar cabecalho com os dados $camposPedidoCentral...")

        val cabecalhoVO = CentralNotasUtils.duplicaNota(paraModeloPedido, camposPedidoCentral).toCabecalhoNotaVO()

        LogOL.info("Conseguiu criar o cabecalho de numero unico ${cabecalhoVO.nuNota}...")

        return cabecalhoVO

    }


    private fun verificarSePedidoExisteCentral(nuPedOL: String, codProjeto: Int) {
        val pedidoOL = cabecalhoNotaDAO.findByPkOL(nuPedOL, codProjeto)
        if (pedidoOL != null) {
            throw EnviarPedidoCentralException("Pedido OL ja existe. Nro. Único: ${pedidoOL.nuNota}",
                RetornoPedidoEnum.PEDIDO_DUPLICADO)
        }
    }

    private fun buscarCliente(pedidoOLVO: PedidoOLVO): ParceiroVO {
        val cnpjCliente = if(pedidoOLVO.cnpjCli == null){
            val mensagem = "CNPJ do cliente deve ser informado."
            throw EnviarPedidoCentralException(mensagem, RetornoPedidoEnum.CNPJ_INVALIDO)
        }else{
            pedidoOLVO.cnpjCli!!
        }


        LogOL.info("Tentando encontrar cliente com o ${pedidoOLVO.cnpjCli}")

        val clienteVO = parceiroDAO.findOne {
            it.where = " CLIENTE = 'S' AND CGC_CPF = ?  "
            it.parameters = arrayOf(cnpjCliente)
        }

        if (clienteVO == null){
            val mensagem = "Cliente não cadastrado com o CNPJ ${pedidoOLVO.cnpjCli}"
            throw EnviarPedidoCentralException(mensagem, RetornoPedidoEnum.CLIENTE_NAO_CADASTRADO)
        }

        if (!clienteVO.ativo) {
            val mensagem = "Cliente ${clienteVO.codparc} nao esta ativo."
            throw EnviarPedidoCentralException(mensagem, RetornoPedidoEnum.CLIENTE_INATIVO)
        }

        LogOL.info("Cliente localizado, cod.: ${clienteVO.codparc}")

        return clienteVO

    }

    fun salvarRetornoSankhya(pedidoOLVO: PedidoOLVO, exception: EnviarPedidoCentralException){
        pedidoOLVO.codRetSkw = exception.retornoOL.codigo
        pedidoOLVO.retSkw = exception.mensagem
        pedidoOLVO.status = StatusPedidoOLEnum.ERRO.valor
        pedidoOLDAO.save(pedidoOLVO)
    }

    fun salvarErroSankhya(pedidoOLVO: PedidoOLVO, exception: Exception){
        pedidoOLVO.codRetSkw = -1
        pedidoOLVO.retSkw = exception.message
        pedidoOLVO.status = StatusPedidoOLEnum.ERRO.valor
        pedidoOLDAO.save(pedidoOLVO)
    }

    @Throws(Exception::class)
    fun incluirItensSemPreco(nuNota: BigDecimal, itens: Array<Map<String, Any?>>): BarramentoRegra.DadosBarramento? {
        try {
            setAuthenticationInfo()
            val barramentoRegra =  CentralNotasUtils.incluirItens(nuNota, itens.toList(),false)
            val facadeW = EntityFacadeW()
            val impostohelp = ImpostosHelpper()
            impostohelp.setForcarRecalculo(true)
            impostohelp.setSankhya(false)

            barramentoRegra?.pksEnvolvidas?.forEach {
                val item =
                    facadeW.findEntityByPrimaryKey("ItemNota", it)!!.wrapInterface(ItemNotaVO::class.java)
                            as br.com.sankhya.modelcore.dwfdata.vo.ItemNotaVO
                impostohelp.calcularImpostosItem(item, item.asBigDecimal("CODPROD"))
            }
            return barramentoRegra
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    private fun setAuthenticationInfo(): AuthenticationInfo {
        val info = AuthenticationInfo.getCurrentOrNull()
            ?: AuthenticationInfo("SUP", BigDecimal.ZERO, BigDecimal.ZERO, 0).apply { makeCurrent() }

        JapeSessionContext.putProperty("usuario_logado", info.userID)
        JapeSessionContext.putProperty("authInfo", info)

        return info
    }

}