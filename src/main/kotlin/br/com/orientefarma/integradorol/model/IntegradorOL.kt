package br.com.orientefarma.integradorol.model


import br.com.lugh.bh.CentralNotasUtils
import br.com.lugh.bh.tryOrNull
import br.com.lugh.dao.EntityFacadeW
import br.com.lughconsultoria.dao.ItemNotaDAO
import br.com.lughconsultoria.dao.ParceiroDAO
import br.com.lughconsultoria.dao.ProdutoDAO
import br.com.lughconsultoria.dao.vo.ItemNotaVO
import br.com.lughconsultoria.dao.vo.ParceiroVO
import br.com.lughconsultoria.dao.vo.ProdutoVO
import br.com.orientefarma.integradorol.commons.LogOL
import br.com.orientefarma.integradorol.commons.RetornoPedidoEnum
import br.com.orientefarma.integradorol.commons.StatusPedidoOLEnum
import br.com.orientefarma.integradorol.dao.*
import br.com.orientefarma.integradorol.dao.vo.CabecalhoNotaVO
import br.com.orientefarma.integradorol.dao.vo.ItemPedidoOLVO
import br.com.orientefarma.integradorol.dao.vo.PedidoOLVO
import br.com.orientefarma.integradorol.exceptions.EnviarItemPedidoCentralException
import br.com.orientefarma.integradorol.exceptions.EnviarPedidoCentralException
import br.com.orientefarma.integradorol.model.pojo.ItemPedidoPojo
import br.com.sankhya.jape.core.JapeSession
import br.com.sankhya.jape.util.JapeSessionContext
import br.com.sankhya.modelcore.auth.AuthenticationInfo
import br.com.sankhya.modelcore.comercial.BarramentoRegra
import br.com.sankhya.modelcore.comercial.CentralFaturamento
import br.com.sankhya.modelcore.comercial.ConfirmacaoNotaHelper
import br.com.sankhya.modelcore.comercial.impostos.ImpostosHelpper
import br.com.sankhya.modelcore.util.MGECoreParameter
import java.math.BigDecimal


class IntegradorOL {

    private val cabecalhoNotaDAO = CabecalhoNotaDAO()
    private val itemNotaDAO = ItemNotaDAO()
    private val pedidoOLDAO = PedidoOLDAO()
    private val itemPedidoOLDAO = ItemPedidoOLDAO()
    private val parceiroDAO = ParceiroDAO()
    private val produtoDAO = ProdutoDAO()

    private val paramTOPPedido: BigDecimal
    private val paraModeloPedido: BigDecimal

    private val fatorPercentual = 0.01.toBigDecimal()

    private var tentativarConfirmacao = 30

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
        criarItensCentral(pedidoOLVO, pedidoCentralVO)
        sumarizar(pedidoCentralVO)


        marcarSucessoStatusCabecalhoOL(pedidoOLVO)
    }

    private fun sumarizar(pedidoCentralVO: CabecalhoNotaVO) {
        try {
            if(tentativarConfirmacao <= 0) return
            tentativarConfirmacao--

            marcarComoNaoPendenteFormaTardia(pedidoCentralVO)
            setSessionProperty("mov.financeiro.ignoraValidacao", true)
            setSessionProperty("validar.alteracao.campos.em.titulos.baixados", false)
            setSessionProperty("br.com.sankhya.com.CentralCompraVenda", true)
            setSessionProperty("ItemNota.incluindo.alterando.pela.central", true)
            validarAgrupamentoMinimoEmbalagem(pedidoCentralVO)
            confirmarMovCentral(pedidoCentralVO.nuNota)
            setSessionProperty("br.com.sankhya.com.CentralCompraVenda", false)
        } catch (e: Exception) {
            val message = e.message ?: ""
            val tentarSumarizarNovamente = verificarRegrasComerciais(message, pedidoCentralVO)
            if (tentarSumarizarNovamente){
                return sumarizar(pedidoCentralVO)
            }
        }
    }

    private fun verificarRegrasComerciais(messagem: String, pedidoCentralVO: CabecalhoNotaVO): Boolean {
        val ehNaoPertenceCondicaoComercial = messagem.contains("não pertence a condição comercial")
        if (ehNaoPertenceCondicaoComercial) {
            val resultadoRegex = Regex("[0-9]+").findAll(messagem)
            var codProd = ""
            for (matchResult in resultadoRegex.iterator()) {
                codProd += matchResult.value
            }
            if (codProd.isNotEmpty()) {
                marcarItemComoNaoPendente(pedidoCentralVO.nuNota, codProd.toInt())
                return true
            }
        }

        return false
    }

    private fun validarAgrupamentoMinimoEmbalagem(pedidoCentralVO: CabecalhoNotaVO) {
        val itensPendentesVO = itemNotaDAO.find {
            it.where = " PENDENTE = 'S' AND NUNOTA = ? "
            it.parameters = arrayOf(pedidoCentralVO.nuNota)
        }

        for (itemNotaVO in itensPendentesVO) {
            val agrupamentoMinimo = itemNotaVO.vo.asInt("Produto.AGRUPMIN")
            if (agrupamentoMinimo > 0) {
                val qtdAtendida = requireNotNull(itemNotaVO.qtdneg).toInt()
                val fatorMutiplicacao = qtdAtendida / agrupamentoMinimo
                if (fatorMutiplicacao == 0) {
                    marcarItemComoNaoPendente(itemNotaVO, "Corte total - Somente CX Embarque")
                } else {
                    val qtdCortar = (qtdAtendida - (agrupamentoMinimo * fatorMutiplicacao))
                    itemNotaVO.observacao = "Corte Parcial - Somente CX Embarque"
                    itemNotaVO.qtdconferida = qtdCortar.toBigDecimal()
                    itemNotaDAO.save(itemNotaVO)
                }
            }
        }
    }

    private fun marcarComoNaoPendenteFormaTardia(pedidoCentralVO: CabecalhoNotaVO) {
        val itensParaCancelarVO = itemNotaDAO.find {
            it.where = " nullValue(AD_OLMARCARPENDENTE_NAO,'N') = 'S' AND NUNOTA = ? "
            it.parameters = arrayOf(pedidoCentralVO.nuNota)
        }
        for (itemNotaVO in itensParaCancelarVO) {
            marcarItemComoNaoPendente(itemNotaVO)
        }
    }

    private fun marcarItemComoNaoPendente(itemNotaVO: ItemNotaVO, observacao: String? = null) {
        itemNotaVO.observacao = observacao
        itemNotaVO.pendente = false
        itemNotaDAO.save(itemNotaVO)
    }

    private fun marcarItemComoNaoPendente(nuNota: Int, codProd: Int, observacao: String? = null) {
        val itensVO = itemNotaDAO.find {
            it.where = "CODPROD = ? AND NUNOTA = ? "
            it.parameters = arrayOf(codProd, nuNota)
        }
        itensVO.forEach { marcarItemComoNaoPendente(it, observacao) }
    }

    private fun marcarSucessoStatusCabecalhoOL(pedidoOLVO: PedidoOLVO) {
        pedidoOLVO.codRetSkw = RetornoPedidoEnum.SUCESSO
        pedidoOLVO.retSkw = ""
        pedidoOLVO.status = StatusPedidoOLEnum.PENDENTE
        pedidoOLDAO.save(pedidoOLVO)
    }

    private fun criarItensCentral(pedidoOLVO: PedidoOLVO, pedidoCentralVO: CabecalhoNotaVO){
        val itensOL = itemPedidoOLDAO.findByNumPedOL(pedidoOLVO.nuPedOL, pedidoOLVO.codPrj)
        for (itemPedidoOLVO in itensOL) {
            val itemPedidoPojo = try {
                criarItemCentral(itemPedidoOLVO, pedidoCentralVO)
            }catch (e: EnviarItemPedidoCentralException){
                e.itemPedidoPojo
            }
            if(itemPedidoPojo != null){
                salvarRetornoItemPedidoOL(itemPedidoOLVO, itemPedidoPojo)
            }
        }
    }

    private fun criarItemCentral(itemPedidoOLVO: ItemPedidoOLVO, pedidoCentralVO: CabecalhoNotaVO): ItemPedidoPojo? {
        try {

            val camposItem = preencherCamposGerais(pedidoCentralVO, itemPedidoOLVO)

            val qtdEstoque = preencherCamposEstoque(pedidoCentralVO, itemPedidoOLVO, camposItem)

            val itemInseridoDados = inserirItemSemPreco(pedidoCentralVO, camposItem)

            val retornoException = itemInseridoDados.second
            if (retornoException != null) {
                throw retornoException
            }

            val itemInseridoVO = itemInseridoDados.first ?: return null

            tratarDesconto(itemInseridoVO, itemPedidoOLVO)

            itemNotaDAO.save(itemInseridoVO)

            return if (qtdEstoque <= 0) {
                ItemPedidoPojo("Estoque insuficiente")
            } else {
                ItemPedidoPojo("Parcialmente atendido")
            }

        } catch (excecaoTratada: EnviarItemPedidoCentralException) {
            throw excecaoTratada
        } catch (e: Exception) {
            throw EnviarItemPedidoCentralException(ItemPedidoPojo(e.message))
        }
    }

    private fun preencherCamposEstoque(pedidoCentralVO: CabecalhoNotaVO, itemPedidoOLVO: ItemPedidoOLVO,
                                       camposItem: MutableMap<String, Any?>): Int {
        val qtdEstoque = Estoque().consultaEstoque(
            codEmp = pedidoCentralVO.codEmp.toBigDecimal(),
            codProd =  camposItem["CODPROD"].toString().toBigDecimal(),
            codLocal = camposItem["CODLOCALORIG"].toString().toBigDecimal(),
            reserva = true
        ).toInt()

        val qtdArquivo = requireNotNull(itemPedidoOLVO.qtdPed)

        camposItem.put("BH_QTDNEGORIGINAL", qtdArquivo.toBigDecimal())
        camposItem.put("QTDNEG", qtdArquivo.toBigDecimal())

        if (qtdEstoque >= qtdArquivo) {
            camposItem.put("AD_QTDESTOQUE", qtdEstoque.toBigDecimal())
        } else {
            val qtdAtendida = qtdArquivo - (qtdArquivo - zeroSeNegativo(qtdEstoque))
            if (qtdAtendida < 1) {
                camposItem.put("QTDNEG", qtdArquivo.toBigDecimal())
                camposItem.put("BH_QTDCORTE", qtdArquivo.toBigDecimal())
            } else {
                camposItem.put("QTDNEG", qtdAtendida.toBigDecimal())
                camposItem.put("BH_QTDCORTE", qtdArquivo.toBigDecimal() - qtdAtendida.toBigDecimal())
                camposItem.put("AD_OLMARCARPENDENTE_NAO", "S")
            }
            camposItem.put("AD_QTDESTOQUE", qtdEstoque.toBigDecimal())
        }

        camposItem.put("ATUALESTOQUE", 1.toBigDecimal())
        camposItem.put("RESERVADO", "S")
        return qtdEstoque
    }

    private fun preencherCamposGerais(pedidoCentralVO: CabecalhoNotaVO, itemPedidoOLVO: ItemPedidoOLVO):
            MutableMap<String, Any?> {
        val produtoVO = getProdutoVO(itemPedidoOLVO.codProd)
        val codLocalOrig: BigDecimal = getCodLocalProduto(produtoVO)
        return mutableMapOf(
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
    }

    private fun getCodLocalProduto(produtoVO: ProdutoVO): BigDecimal {
        return if (produtoVO.usalocal && produtoVO.codlocalpadrao != null) {
            (produtoVO.codlocalpadrao ?: 0).toBigDecimal()
        } else BigDecimal.ZERO
    }


    private fun getProdutoVO(codProd: Int?): ProdutoVO {
        val produtoVO = produtoDAO.findByPK(requireNotNull(codProd){"Produto n\u00e3o informado."})
        if (!produtoVO.ativo) {
            val itemPedidoPojo = ItemPedidoPojo("Produto ${codProd} n\u00e3o esta ativo.")
            throw EnviarItemPedidoCentralException(itemPedidoPojo)
        }
        return produtoVO
    }

    private fun salvarRetornoItemPedidoOL(itemPedidoOLVO: ItemPedidoOLVO, itemPedidoPojo: ItemPedidoPojo) {
        val retornoItem = itemPedidoPojo.calcularCodigoRetorno()
        itemPedidoOLVO.codRetSkw = retornoItem.codigo
        itemPedidoOLVO.retSkw = itemPedidoPojo.mensagem
        itemPedidoOLDAO.save(itemPedidoOLVO)
    }

    private fun tratarDesconto(itemInseridoVO: ItemNotaVO, itemPedidoOLVO: ItemPedidoOLVO) {
        val precoBase = itemInseridoVO.precobase ?: 0.toBigDecimal()
        if (precoBase <= BigDecimal.ZERO) {
            itemInseridoVO.vo.setProperty("AD_OLMARCARPENDENTE_NAO", "S")
            itemInseridoVO.observacao = "PRECO BASE ZERADO"
        } else {
            val percDescCondicao = itemInseridoVO.vo.asBigDecimalOrZero("AD_PERCDESC")
            val percDescArquivo = itemPedidoOLVO.prodDesc ?: 0.toBigDecimal()
            if (percDescArquivo > percDescCondicao) {
                itemInseridoVO.vo.setProperty("AD_OLMARCARPENDENTE_NAO", "S")
                itemInseridoVO.observacao = "DESCONTO INVALIDO"
                // log desconto maior que o permitido
            } else {
                itemInseridoVO.vo.set("AD_PERCDESC", percDescArquivo)
                val valorBruto = zeroSeNulo(itemPedidoOLVO.qtdPed).toBigDecimal() * precoBase
                val valorDesconto = if (percDescArquivo <= BigDecimal.ZERO) {
                    BigDecimal.ZERO
                } else {
                    valorBruto - (valorBruto * (percDescArquivo * fatorPercentual))
                }

                val vlrUnitario = precoBase - (precoBase * (percDescArquivo * fatorPercentual))

                itemInseridoVO.vo.setProperty("AD_VLRDESC", valorDesconto)
                itemInseridoVO.vo.setProperty("VLRUNIT", vlrUnitario)
                itemInseridoVO.vo.setProperty("AD_VLRUNITCM", vlrUnitario)
                itemInseridoVO.vo.setProperty("VLRTOT", vlrUnitario * itemInseridoVO.qtdneg!!)


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
            exceptionAoIncluir = EnviarItemPedidoCentralException(ItemPedidoPojo(e.message))
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
            // todo descomentar apos testes
//            throw EnviarPedidoCentralException("Pedido OL ja existe. Nro. Único: ${pedidoOL.nuNota}",
//                RetornoPedidoEnum.PEDIDO_DUPLICADO)
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
        pedidoOLVO.codRetSkw = exception.retornoOL
        pedidoOLVO.retSkw = exception.mensagem
        pedidoOLVO.status = StatusPedidoOLEnum.ERRO
        pedidoOLDAO.save(pedidoOLVO)
    }

    fun salvarErroSankhya(pedidoOLVO: PedidoOLVO, exception: Exception){
        pedidoOLVO.codRetSkw = RetornoPedidoEnum.ERRO_DESCONHECIDO
        pedidoOLVO.retSkw = exception.message
        pedidoOLVO.status = StatusPedidoOLEnum.ERRO
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
                    facadeW.findEntityByPrimaryKey("ItemNota", it)!!
                        .wrapInterface(br.com.sankhya.modelcore.dwfdata.vo.ItemNotaVO::class.java)
                            as br.com.sankhya.modelcore.dwfdata.vo.ItemNotaVO
                impostohelp.calcularImpostosItem(item, item.asBigDecimal("CODPROD"))
            }
            return barramentoRegra
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    @Throws(Exception::class)
    fun confirmarMovCentral(nuNota: Int): BarramentoRegra.DadosBarramento {
        val auth = setAuthenticationInfo()
        val barramento = BarramentoRegra.build(CentralFaturamento::class.java, "regrasConfirmacaoSilenciosa.xml", auth)

        ConfirmacaoNotaHelper.confirmarNota(nuNota.toBigDecimal(), barramento, true)

        return barramento.dadosBarramento
    }

    private fun setAuthenticationInfo(): AuthenticationInfo {
        val info = AuthenticationInfo.getCurrentOrNull()
            ?: AuthenticationInfo("SUP", BigDecimal.ZERO, BigDecimal.ZERO, 0).apply { makeCurrent() }

        JapeSessionContext.putProperty("usuario_logado", info.userID)
        JapeSessionContext.putProperty("authInfo", info)

        return info
    }

}