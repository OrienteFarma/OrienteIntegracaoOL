package br.com.orientefarma.integradorol.model


import br.com.lugh.bh.CentralNotasUtils
import br.com.lugh.bh.tryOrNull
import br.com.lugh.dao.EntityFacadeW
import br.com.lugh.dao.update
import br.com.lughconsultoria.dao.ItemNotaDAO
import br.com.lughconsultoria.dao.ParceiroDAO
import br.com.lughconsultoria.dao.ProdutoDAO
import br.com.lughconsultoria.dao.vo.ItemNotaVO
import br.com.lughconsultoria.dao.vo.ParceiroVO
import br.com.lughconsultoria.dao.vo.ProdutoVO
import br.com.orientefarma.integradorol.commons.LogOL
import br.com.orientefarma.integradorol.commons.RetornoItemPedidoEnum
import br.com.orientefarma.integradorol.commons.RetornoPedidoEnum
import br.com.orientefarma.integradorol.commons.StatusPedidoOLEnum
import br.com.orientefarma.integradorol.dao.CabecalhoNotaDAO
import br.com.orientefarma.integradorol.dao.toCabecalhoNotaVO
import br.com.orientefarma.integradorol.dao.vo.CabecalhoNotaVO
import br.com.orientefarma.integradorol.dao.vo.ItemPedidoOLVO
import br.com.orientefarma.integradorol.dao.vo.PedidoOLVO
import br.com.orientefarma.integradorol.exceptions.EnviarItemPedidoCentralException
import br.com.orientefarma.integradorol.exceptions.EnviarPedidoCentralException
import br.com.sankhya.jape.core.JapeSession
import br.com.sankhya.jape.util.JapeSessionContext
import br.com.sankhya.modelcore.auth.AuthenticationInfo
import br.com.sankhya.modelcore.comercial.BarramentoRegra
import br.com.sankhya.modelcore.comercial.CentralFaturamento
import br.com.sankhya.modelcore.comercial.ConfirmacaoNotaHelper
import br.com.sankhya.modelcore.comercial.impostos.ImpostosHelpper
import br.com.sankhya.modelcore.util.MGECoreParameter
import java.math.BigDecimal


class IntegradorOL(val pedidoOL: PedidoOL) {

    private val cabecalhoNotaDAO = CabecalhoNotaDAO()
    private val itemNotaDAO = ItemNotaDAO()
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

    fun enviarParaCentral(): Int {
        verificarSePedidoExisteCentral(pedidoOL.nuPedOL, pedidoOL.codPrj)
        val clienteVO = buscarCliente(pedidoOL.vo)
        val pedidoCentralVO = criarCabecalho(pedidoOL.vo, clienteVO)
        criarItensCentral(pedidoOL, pedidoCentralVO)
        sumarizar(pedidoCentralVO)
        return pedidoCentralVO.nuNota
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
            pedidoOL.marcarSucessoEnvioCentral(pedidoCentralVO.nuNota)
            setSessionProperty("br.com.sankhya.com.CentralCompraVenda", false)
        } catch (e: Exception) {
            val message = e.message ?: ""
            val tentarSumarizarNovamente = verificarRegrasComerciais(message, pedidoCentralVO)
            if (tentarSumarizarNovamente){
                return sumarizar(pedidoCentralVO)
            }
        } finally {
            alterarStatusCentral(pedidoCentralVO.nuNota, StatusPedidoOLEnum.PENDENTE)
        }
    }

    private fun alterarStatusCentral(nuNota: Int, status: StatusPedidoOLEnum){
        update("UPDATE TGFCAB SET AD_STATUSOL = :STATUS WHERE NUNOTA = :NUNOTA ",
            mapOf("STATUS" to status.name, "NUNOTA" to nuNota))
    }

    private fun verificarRegrasComerciais(mensagem: String, pedidoCentralVO: CabecalhoNotaVO): Boolean {
        val ehNaoPertenceCondicaoComercial = mensagem.contains("não pertence a condição comercial")
        if (ehNaoPertenceCondicaoComercial) {
            val codProd = extrairCodigoProdutoPorMsgCondicaoComercial(mensagem)
            if (codProd != null) {
                marcarItemComoNaoPendente(pedidoCentralVO.nuNota, codProd.toInt())
                return true
            }
        }

        val ehDocumentoFaltante = mensagem.contains("documento(s) faltante(s)")
        if (ehDocumentoFaltante) {
            val regexProdutosComDocsFaltantes = Regex(".*Produto: \\d*")
            for (matchResult in regexProdutosComDocsFaltantes.findAll(mensagem)) {
                val codProd = matchResult.value.replace(Regex(".*Produto: "), "").toInt()

                marcarItemComoNaoPendente(pedidoCentralVO.nuNota, codProd, "FALTA DOCUMENTACAO")

                val numPedidoOL = pedidoCentralVO.vo.asString("AD_NUMPEDIDO_OL") ?: "0"
                val codProjeto = pedidoCentralVO.vo.asInt("AD_NUINTEGRACAO")
                val itemPedidoOL = ItemPedidoOL.fromCodProd(numPedidoOL, codProjeto, codProd)
                itemPedidoOL?.setFeedback("Falta de documentação", 0)
                itemPedidoOL?.salvarRetornoItemPedidoOL()
                return true
            }
        }

        val naoAtendeuMinimo = mensagem.contains("pedido não atende o valor mínimo")
        if(naoAtendeuMinimo){
            pedidoOL.salvarRetornoSankhya(RetornoPedidoEnum.CONDICAO, mensagem)
        }

        if(!pedidoOL.temFeedback()){
            pedidoOL.salvarRetornoSankhya(RetornoPedidoEnum.ERRO_DESCONHECIDO, mensagem)
        }

        marcarTodosItensComoNaoPendente(pedidoCentralVO.nuNota)
        return false
    }

    private fun extrairCodigoProdutoPorMsgCondicaoComercial(mensagem: String): Int? {
        val resultadoRegex = Regex("[0-9]+").findAll(mensagem)
        var codProd = ""
        for (matchResult in resultadoRegex.iterator()) {
            codProd += matchResult.value
        }
        return tryOrNull { codProd.toInt() }
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
        itemNotaVO.observacao = observacao ?: itemNotaVO.observacao
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

    private fun marcarTodosItensComoNaoPendente(nuNota: Int) {
        val itensVO = itemNotaDAO.find {
            it.where = "NUNOTA = ?"
            it.parameters = arrayOf(nuNota)
        }
        itensVO.forEach { marcarItemComoNaoPendente(it) }
        pedidoOL.getItens().forEach { it.marcarComoNaoPendente() }
    }

    private fun criarItensCentral(pedidoOL: PedidoOL, pedidoCentralVO: CabecalhoNotaVO){
        val itensPedidoOL = ItemPedidoOL.fromPedidoOL(pedidoOL)
        for (itemPedidoOL in itensPedidoOL) {
            try {
                criarItemCentral(itemPedidoOL, pedidoCentralVO)
            }catch (e: EnviarItemPedidoCentralException){
                itemPedidoOL.setFeedback(e.mensagem ?: "", 0)
            }finally {
                itemPedidoOL.salvarRetornoItemPedidoOL()
            }

        }
    }

    private fun criarItemCentral(itemPedidoOL: ItemPedidoOL, pedidoCentralVO: CabecalhoNotaVO){
        val itemPedidoOLVO = itemPedidoOL.vo

        val camposItem = preencherCamposGerais(pedidoCentralVO, itemPedidoOLVO)

        val qtdEstoque = preencherCamposEstoque(pedidoCentralVO, itemPedidoOL, camposItem)

        val itemInseridoDados = inserirItemSemPreco(pedidoCentralVO, camposItem)

        val retornoException = itemInseridoDados.second
        if (retornoException != null) {
            retornoException.printStackTrace()
            itemPedidoOL.setFeedback("Erro ao inserir o item: ${retornoException.mensagem}", 0)
            return
        }

        val itemInseridoVO = itemInseridoDados.first ?: return

        tratarDesconto(itemInseridoVO, itemPedidoOL)

        itemNotaDAO.save(itemInseridoVO)

        val qtdAtendida = if("S" == itemInseridoVO.vo.asString("AD_OLMARCARPENDENTE_NAO")){
            0
        } else {
            requireNotNull(itemInseridoVO.qtdneg).toInt()
        }

        if(!itemPedidoOL.temFeedback()){
            val codRetorno =
                calcularRetornoAtendimentoItem(qtdEstoque, requireNotNull(itemPedidoOL.vo.qtdPed), qtdAtendida)
            itemPedidoOL.setFeedback(codRetorno, qtdAtendida)
        }
    }

    private fun calcularRetornoAtendimentoItem(qtdEstoque: Int, qtdPedida: Int, qtdAtendida: Int): RetornoItemPedidoEnum {
        return if (qtdEstoque <= 0) {
            RetornoItemPedidoEnum.ESTOQUE_INSUFICIENTE
        } else {
            if (qtdAtendida == 0) {
                RetornoItemPedidoEnum.NAO_ATENDIDO
            } else {
                if (qtdAtendida < qtdPedida) {
                    RetornoItemPedidoEnum.ESTOQUE_PARCIALMENTE
                } else {
                    RetornoItemPedidoEnum.SUCESSO
                }
            }
        }
    }

    private fun preencherCamposEstoque(pedidoCentralVO: CabecalhoNotaVO, itemPedidoOL: ItemPedidoOL,
                                       camposItem: MutableMap<String, Any?>): Int {
        val qtdEstoque = Estoque().consultaEstoque(
            codEmp = pedidoCentralVO.codEmp.toBigDecimal(),
            codProd =  camposItem["CODPROD"].toString().toBigDecimal(),
            codLocal = camposItem["CODLOCALORIG"].toString().toBigDecimal(),
            reserva = true
        ).toInt()

        val qtdArquivo = requireNotNull(itemPedidoOL.vo.qtdPed)

        camposItem.put("BH_QTDNEGORIGINAL", qtdArquivo.toBigDecimal())
        camposItem.put("QTDNEG", qtdArquivo.toBigDecimal())

        if (qtdEstoque >= qtdArquivo) {
            camposItem.put("AD_QTDESTOQUE", qtdEstoque.toBigDecimal())
        } else {
            val qtdAtendida = qtdArquivo - (qtdArquivo - zeroSeNegativo(qtdEstoque))
            if (qtdAtendida < 1) {
                camposItem.put("QTDNEG", qtdArquivo.toBigDecimal())
                camposItem.put("BH_QTDCORTE", qtdArquivo.toBigDecimal())
                itemPedidoOL.setFeedback(RetornoItemPedidoEnum.ESTOQUE_INSUFICIENTE, 0,
                    "Estoque insuficiente")
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
            "SEQUENCIA" to itemPedidoOLVO.sequenciaArquivo?.toBigDecimal(),
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
            throw EnviarItemPedidoCentralException("Produto ${codProd} n\u00e3o esta ativo.")
        }
        return produtoVO
    }

    private fun tratarDesconto(itemInseridoVO: ItemNotaVO, itemPedidoOL: ItemPedidoOL) {
        val itemPedidoOLVO = itemPedidoOL.vo
        val precoBase = itemInseridoVO.precobase ?: 0.toBigDecimal()
        if (precoBase <= BigDecimal.ZERO) {
            val mensagem = "Preço base zerado"
            itemInseridoVO.vo.setProperty("AD_OLMARCARPENDENTE_NAO", "S")
            itemInseridoVO.observacao = mensagem
            itemPedidoOL.setFeedback(RetornoItemPedidoEnum.CONDICAO, 0,mensagem)
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
            exceptionAoIncluir = EnviarItemPedidoCentralException(e.message)
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
            "NUMPEDIDO2" to pedidoOLVO.nuPedCli?.take(15),
            "NUMNOTA" to BigDecimal.ZERO,
            "CIF_FOB" to "F",
            "CODTIPOPER" to paramTOPPedido,
            "AD_TIPOCONDICAO" to "O",
            "CODTIPVENDA" to codTipVenda,
            "AD_CODCOND" to pedidoOLVO.cond?.toBigDecimal(),
            "AD_CODTIPVENDA" to codTipVenda,
            "AD_NUINTEGRACAO" to pedidoOLVO.codPrj.toBigDecimal(),
            "OBSERVACAO" to pedidoOLVO.nuPedCli,
            "AD_STATUSOL" to StatusPedidoOLEnum.IMPORTANDO.name
        )

        LogOL.info("Tentando criar cabecalho com os dados $camposPedidoCentral...")

        val cabecalhoVO = CentralNotasUtils.duplicaNota(paraModeloPedido, camposPedidoCentral).toCabecalhoNotaVO()

        LogOL.info("Conseguiu criar o cabecalho de numero unico ${cabecalhoVO.nuNota}...")

        pedidoOL.setNuNotaCentral(cabecalhoVO.nuNota)

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

    @Throws(Exception::class)
    private fun incluirItensSemPreco(nuNota: BigDecimal, itens: Array<Map<String, Any?>>): BarramentoRegra.DadosBarramento? {
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
    private fun confirmarMovCentral(nuNota: Int): BarramentoRegra.DadosBarramento {
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