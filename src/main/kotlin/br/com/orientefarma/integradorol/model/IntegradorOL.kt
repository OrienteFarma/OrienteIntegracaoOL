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
import br.com.orientefarma.integradorol.commons.*
import br.com.orientefarma.integradorol.dao.CabecalhoNotaDAO
import br.com.orientefarma.integradorol.dao.ProjetoIntegracaoDAO
import br.com.orientefarma.integradorol.dao.toCabecalhoNotaVO
import br.com.orientefarma.integradorol.dao.vo.CabecalhoNotaVO
import br.com.orientefarma.integradorol.dao.vo.ItemPedidoOLVO
import br.com.orientefarma.integradorol.dao.vo.PedidoOLVO
import br.com.orientefarma.integradorol.exceptions.EnviarItemPedidoCentralException
import br.com.orientefarma.integradorol.exceptions.EnviarPedidoCentralException
import br.com.orientefarma.integradorol.exceptions.ItemNaoInseridoException
import br.com.sankhya.jape.core.JapeSession
import br.com.sankhya.jape.util.JapeSessionContext
import br.com.sankhya.jape.wrapper.JapeFactory
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
    private  val projetoIntegracaoDAO = ProjetoIntegracaoDAO()
    private val condicaoDAO = JapeFactory.dao("AD_CONDCOMERCIAL")
    private val facadeW = EntityFacadeW()
    private val impostohelp = ImpostosHelpper()

    private val paramTOPPedido: BigDecimal
    private val paraModeloPedido: BigDecimal

    private val fatorPercentual = 0.01.toBigDecimal()

    private var tentativarConfirmacao = 30

    /**
     * Inicia os parametros (Preferencias Sankhya) necessarios para execucao das rotinas.
     */
    init {
        val nomeParamTOPPedido = "OR_OLTOPPED"
        paramTOPPedido = tryOrNull {
            MGECoreParameter.getParameter(nomeParamTOPPedido).toString().toBigDecimal()
        } ?: throw IllegalStateException("Verifique o parãmetro $nomeParamTOPPedido.")

        val nomeParamModeloPedido = "OR_OLMODPED"
        paraModeloPedido = tryOrNull {
            MGECoreParameter.getParameter(nomeParamModeloPedido).toString().toBigDecimal()
        } ?: throw IllegalStateException("Verifique o parãmetro $nomeParamModeloPedido.")
    }

    /**
     * Envia o PedidoOL para a central com os passos:
     * - Verifica se pedido já foi integrado atrav?s das chaves do pedido OL
     * - Verifica se o cliente está cadastrado e ativo;
     * - Cria o cabe?alho marcando com o AD_STATUSOL = Importando
     * - Cria os itens tratando estoque, desconto e motivos de n?o atendimento
     * - Realiza a confirmação do pedido na central, bem como trata possíveis erros de documentação e afins.
     */
    fun enviarParaCentral(): Int {
        val cabVO = cabecalhoNotaDAO.findByPkOL(pedidoOL.nuPedOL, pedidoOL.codPrj)
        if(cabVO != null){
            atualizarPedidoOLDadosCentral(cabVO)
            return cabVO.nuNota
        }
        val clienteVO = buscarCliente(pedidoOL.vo)
        val pedidoCentralVO = criarCabecalho(pedidoOL.vo, clienteVO)
        criarItensCentral(pedidoOL, pedidoCentralVO)
        sumarizar(pedidoCentralVO)
        pedidoOL.salvarNuNotaCentral(pedidoCentralVO.nuNota)
        return pedidoCentralVO.nuNota
    }

    private fun atualizarPedidoOLDadosCentral(cabVO: CabecalhoNotaVO) {
        pedidoOL.setNuNotaCentral(cabVO.nuNota)
        if (StatusPedidoOLEnum.ENVIANDO_CENTRAL == pedidoOL.vo.status) {
            pedidoOL.vo.status = StatusPedidoOLEnum.PENDENTE
        }
        pedidoOL.save()
    }

    fun cancelarPedido(codJustificativa: Int){
        val nuNotaCentral = requireNotNull(this.pedidoOL.vo.nuNota){"Esse pedido não foi enviado para a central."}
        val pedidoCentralVO = cabecalhoNotaDAO.findByPk(nuNotaCentral)

        cancelarPedido(pedidoCentralVO, nuNotaCentral)

        this.pedidoOL.vo.codJustificativa = codJustificativa
        this.pedidoOL.salvarRetornoSankhya(StatusPedidoOLEnum.CANCELADO, RetornoPedidoEnum.SUCESSO,
            "Pedido cancelado pelo usuário ${AuthenticationInfo.getCurrent().name}.")
    }

    /**
     * Tenta confirmar o pedido na central e avalia possiveis erros na confirmacao, como pedido minimo e documentacao.
     * Em alguns erros tratados, a tentativa de cofirmacao eh feita novamente.
     * Alem disso, aqui os itens que nao serao atendidos sao marcados como NAO pendente de forma tardia.
     */
    private fun sumarizar(pedidoCentralVO: CabecalhoNotaVO) {
        try {
            if(tentativarConfirmacao <= 0) return
            tentativarConfirmacao--
            marcarComoNaoPendenteFormaTardia(pedidoCentralVO)
            setSessionProperty(JapeProperty.IGNORAR_VALIDACAO_FINANCEIRO, true)
            setSessionProperty(JapeProperty.VALIDAR_ALTERACAO_BAIXADOS, false)
            setSessionProperty(JapeProperty.EH_CENTRAL_COMPRA_VENDA, true)
            setSessionProperty(JapeProperty.INCLUINDO_PELA_CENTRAL, true)
            validarAgrupamentoMinimoEmbalagem(pedidoCentralVO)
            totalizarPedido(pedidoCentralVO.nuNota)
            CentralNotasUtils.refazerFinanceiro(pedidoCentralVO.nuNota.toBigDecimal())
            simularConfirmacaoCentral(pedidoCentralVO.nuNota)
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
            if(!pedidoOL.temCodRetorno()){
                pedidoOL.marcarSucessoEnvioCentral(pedidoCentralVO.nuNota)
            }
        }
    }

    /**
     * Em alguns casos, em consequencia de interferência de outras personaliza??es,
     * os valores precisam ser recalculados. Acreditamos que seja sobre ST.
     */
    private fun totalizarPedido(nuNota: Int) {
        try{
            val impostosHelpper = ImpostosHelpper()
            impostosHelpper.totalizarNota(nuNota.toBigDecimal())
            impostosHelpper.salvarNota()
        }catch (e:Exception){
            e.printStackTrace()
            throw EnviarPedidoCentralException("Erro ao totalizar o pedido $nuNota. ${e.message}. ",
                RetornoPedidoEnum.ERRO_AO_TOTALIZAR_PEDIDO)
        }
    }

    /**
     * Altera o campo AD_STATUSOL na Central de Vendas.
     * Por motivos de performance, usaremos o nativeSql.
     * O fato de ser um campo adcional, mitiga os riscos de fazer este procedimento.
     */
    private fun alterarStatusCentral(nuNota: Int, status: StatusPedidoOLEnum){
        update("UPDATE TGFCAB SET AD_STATUSOL = :STATUS WHERE NUNOTA = :NUNOTA ",
            mapOf("STATUS" to status.name, "NUNOTA" to nuNota))
    }

    /**
     * Verifica os erros - da confirmacao na central - para tentar trata-lo e pedir ao sankhya para
     * confirmar novamente - isso eh feito atraves do retorno 'true'. Por exemplo.: em caso de falta de documentacao,
     * o item eh marcado como nao pendente, e tenta-se confirmar novamente os itens restantes.
     */
    private fun verificarRegrasComerciais(mensagem: String, pedidoCentralVO: CabecalhoNotaVO): Boolean {
        val ehErroFinanceiro = mensagem.contains("A somat\u00f3ria dos valores do financeiro")
        if(ehErroFinanceiro){
            pedidoOL.marcarSucessoEnvioCentral(pedidoCentralVO.nuNota)
            return false
        }

        val ehNaoPertenceCondicaoComercial = mensagem.contains("n\u00e3o pertence a condi\u00e7\u00e3o comercial")
        if (ehNaoPertenceCondicaoComercial) {
            val codProd = extrairCodigoProdutoPorMsgCondicaoComercial(mensagem)
            if (codProd != null) {
                marcarItemComoNaoPendente(pedidoCentralVO.nuNota, codProd.toInt())
                return true
            }
        }

        val ehDocumentoFaltante = mensagem.contains("documento(s) faltante(s)")
        if (ehDocumentoFaltante) {
            var podeTentarSumarizarNovamente = false
            val regexProdutosComDocsFaltantes = Regex(".*Produto: \\d*")
            for (matchResult in regexProdutosComDocsFaltantes.findAll(mensagem)) {
                val codProd = matchResult.value.replace(Regex(".*Produto: "), "").toInt()

                marcarItemComoNaoPendente(pedidoCentralVO.nuNota, codProd, "FALTA DOCUMENTACAO")

                val numPedidoOL = pedidoCentralVO.vo.asString("AD_NUMPEDIDO_OL") ?: "0"
                val codProjeto = pedidoCentralVO.vo.asInt("AD_NUINTEGRACAO")
                val itemPedidoOL = ItemPedidoOL.fromCodProd(numPedidoOL, codProjeto, codProd)
                itemPedidoOL?.setFeedback("Falta de documentação", 0)
                itemPedidoOL?.salvarRetornoItemPedidoOL()
                podeTentarSumarizarNovamente = true
            }
            return podeTentarSumarizarNovamente
        }

        val naoAtendeuMinimo = mensagem.contains("pedido n\u00e3o atende o valor m\u00ednimo")
        if(naoAtendeuMinimo){
            pedidoOL.salvarRetornoSankhya(StatusPedidoOLEnum.PENDENTE, RetornoPedidoEnum.CONDICAO, mensagem)
        }

        if(!pedidoOL.temCodRetorno()){
            pedidoOL.salvarRetornoSankhya(StatusPedidoOLEnum.PENDENTE, RetornoPedidoEnum.ERRO_DESCONHECIDO, mensagem)
        }

        marcarTodosItensComoNaoPendente(pedidoCentralVO.nuNota)
        return false
    }

    /**
     * Capaz de, com base numa mensagem, extrair agrupamento de números.
     * Neste caso, utilizado para retornar o CODPROD da mensagem.
     */
    private fun extrairCodigoProdutoPorMsgCondicaoComercial(mensagem: String): Int? {
        val resultadoRegex = Regex("[0-9]+").findAll(mensagem)
        var codProd = ""
        for (matchResult in resultadoRegex.iterator()) {
            codProd += matchResult.value
        }
        return tryOrNull { codProd.toInt() }
    }

    /**
     * Valida o agrupamento mínimo para venda.
     * Exemplo: Cx de Dipirona com 100
     * Num pedido de 150, 50 devem ser cortados.
     */
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

    /**
     * Todos os itens que estao marcados com 'AD_OLMARCARPENDENTE_NAO' = 'S'
     * sao marcados como pendente NAO neste momento.
     * Motivo: Quando  o primeiro item eh marcado como nao pendente, o Sankhya marca o cabecalho tambem
     * como nao pendente e aborta os demais itens.
     */
    private fun marcarComoNaoPendenteFormaTardia(pedidoCentralVO: CabecalhoNotaVO) {
        val itensParaCancelarVO = itemNotaDAO.find {
            it.where = " nullValue(AD_OLMARCARPENDENTE_NAO,'N') = 'S' AND NUNOTA = ? "
            it.parameters = arrayOf(pedidoCentralVO.nuNota)
        }
        for (itemNotaVO in itensParaCancelarVO) {
            marcarItemComoNaoPendente(itemNotaVO)
        }
    }

    /**
     * Marcar o item como NAO pendente na central, bem como adiciona uma mensagem no campo observa??o.
     */
    private fun marcarItemComoNaoPendente(itemNotaVO: ItemNotaVO, observacao: String? = null) {
        itemNotaVO.observacao = observacao ?: itemNotaVO.observacao
        itemNotaVO.pendente = false
        itemNotaDAO.save(itemNotaVO)
    }

    /**
     * Marcar TODOS os itens do PRODUTO como NaO pendente na central, bem como adiciona uma mensagem no campo observação.
     * Lembrando que este processo precisa ser feito item a item - trigger de estoque Sankhya.
     */
    private fun marcarItemComoNaoPendente(nuNota: Int, codProd: Int, observacao: String? = null) {
        val itensVO = itemNotaDAO.find {
            it.where = "CODPROD = ? AND NUNOTA = ? "
            it.parameters = arrayOf(codProd, nuNota)
        }
        itensVO.forEach { marcarItemComoNaoPendente(it, observacao) }
    }

    /**
     * Marcar TODOS os itens do PEDIDO como NAO pendente na central, bem como adiciona uma mensagem no campo observação.
     * Lembrando que este processo precisa ser feito item a item - trigger de estoque Sankhya.
     */
    private fun marcarTodosItensComoNaoPendente(nuNota: Int, observacao: String? = null) {
        val itensVO = itemNotaDAO.find {
            it.where = " NUNOTA = ? "
            it.parameters = arrayOf(nuNota)
        }
        itensVO.forEach { marcarItemComoNaoPendente(it, observacao) }
        pedidoOL.getItens().forEach { it.marcarComoNaoPendente() }
    }

    /**
     * Busca todos os itens do Pedido OL e incia a criação na central de um a um.
     * Além disso, gerencia os erros e os converte para feedback na tela de OL.
     */
    private fun criarItensCentral(pedidoOL: PedidoOL, pedidoCentralVO: CabecalhoNotaVO){
        val propertyFinanceiroJaCriado = "financeiros.ja.foram.criados"
        val itensPedidoOL = ItemPedidoOL.fromPedidoOL(pedidoOL)
        for (itemPedidoOL in itensPedidoOL) {
            try {
                JapeSession.putProperty(propertyFinanceiroJaCriado, true)
                criarItemCentral(itemPedidoOL, pedidoCentralVO)
            }catch (e: EnviarItemPedidoCentralException){
                itemPedidoOL.setFeedback(e.mensagem ?: "", 0)
            }finally {
                itemPedidoOL.salvarRetornoItemPedidoOL()
                JapeSession.putProperty(propertyFinanceiroJaCriado, false)
            }
        }
    }

    /**
     * Cria o item na central de vendas, validando as seguintes regras: campos básicos, valida estoque, valida desconto
     * e calcula o status de retorno, por exemplo: se foi atendido por completo ou parcialmente.
     */
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

        val itemInseridoVO = itemInseridoDados.first ?: throw ItemNaoInseridoException()

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

    /**
     * Com base na quantidade de estoque e quantidade atendida, calcula qual eh o retorno de atendimento do item.
     * Estoque insuficiente, não atendido, atendido parcialmente ou atendido totalmente.
     */
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

    /**
     * Responsavel por verificar o quanto da quantidade solicitada poderemos atender.
     * Além disso, preenche o campo corte (adcional), o motivo de corte e a quantidade que realmente
     * sera atendida.
     */
    private fun preencherCamposEstoque(pedidoCentralVO: CabecalhoNotaVO, itemPedidoOL: ItemPedidoOL,
                                       camposItem: MutableMap<String, Any?>): Int {
        val qtdEstoque = Estoque().consultaEstoque(
            codEmp = pedidoCentralVO.codEmp.toBigDecimal(),
            codProd =  camposItem["CODPROD"].toString().toBigDecimal(),
            codLocal = camposItem["CODLOCALORIG"].toString().toBigDecimal(),
            reserva = true
        ).toInt()

        val qtdArquivo = requireNotNull(itemPedidoOL.vo.qtdPed)

        camposItem["BH_QTDNEGORIGINAL"] = qtdArquivo.toBigDecimal()
        camposItem["QTDNEG"] = qtdArquivo.toBigDecimal()

        if (qtdEstoque >= qtdArquivo) {
            camposItem["AD_QTDESTOQUE"] = qtdEstoque.toBigDecimal()
        }
        else {
            val qtdAtendida = qtdArquivo - (qtdArquivo - zeroSeNegativo(qtdEstoque))
            if (qtdAtendida < 1) {
                camposItem["QTDNEG"] = qtdArquivo.toBigDecimal()
                camposItem["BH_QTDCORTE"] = qtdArquivo.toBigDecimal()
                camposItem["OBSERVACAO"] = "Estoque insuficiente"
                camposItem["AD_OLMARCARPENDENTE_NAO"] = "S"
                itemPedidoOL.setFeedback(RetornoItemPedidoEnum.ESTOQUE_INSUFICIENTE, 0,
                    "Estoque insuficiente")
            } else {
                camposItem["QTDNEG"] = qtdAtendida.toBigDecimal()
                camposItem["BH_QTDCORTE"] = qtdArquivo.toBigDecimal() - qtdAtendida.toBigDecimal()
            }
            camposItem["AD_QTDESTOQUE"] = qtdEstoque.toBigDecimal()
        }

        camposItem["ATUALESTOQUE"] = 1.toBigDecimal()
        camposItem["RESERVADO"] = "S"
        return qtdEstoque
    }

    /**
     * Prepara e retorna um Map com os campos basicos para criar um item nota na central.
     */
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

    /**
     * Buscar o cód. de local padr?o no cadastro do produto.
     */
    private fun getCodLocalProduto(produtoVO: ProdutoVO): BigDecimal {
        return if (produtoVO.usalocal && produtoVO.codlocalpadrao != null) {
            (produtoVO.codlocalpadrao ?: 0).toBigDecimal()
        } else BigDecimal.ZERO
    }

    /**
     * Com base no cód. de produto, retorna uma inst?ncia de ProdutoVO
     */
    private fun getProdutoVO(codProd: Int?): ProdutoVO {
        if(codProd == null)
            throw EnviarItemPedidoCentralException("Produto não informado.")
        val produtoVO = produtoDAO.findByPK(codProd)
        if (!produtoVO.ativo) {
            throw EnviarItemPedidoCentralException("Produto ${codProd} n\u00e3o esta ativo.")
        }
        return produtoVO
    }

    /**
     * Responsavel por calcular o percentual de desconto confrontando o desconto do arquivo vs desconto
     * da condicao comercial. Além disso, caso o item nao respeite as regras de desconto, ele eh marcado
     * como nao pendente.
     */
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


            //Checa SE deconsto do arquivo, na BH_INSPRJ
            if (projetoIntegracaoDAO.descontoArquivo(itemPedidoOL.vo.codPrj) == "S") {


                if (percDescArquivo > percDescCondicao) {
                    itemInseridoVO.vo.setProperty("AD_OLMARCARPENDENTE_NAO", "S")
                    itemInseridoVO.observacao = "DESCONTO INVALIDO"
                    // log desconto maior que o permitido
                } else {
                    itemInseridoVO.vo["AD_PERCDESC"] = percDescArquivo
                    val valorBruto = zeroSeNulo(itemInseridoVO.qtdneg?.toInt()).toBigDecimal() * precoBase
                    val valorDesconto = if (percDescArquivo <= BigDecimal.ZERO) {
                        BigDecimal.ZERO
                    } else {
                        valorBruto * (percDescArquivo * fatorPercentual)
                    }

                    val vlrUnitario = precoBase - (precoBase * (percDescArquivo * fatorPercentual))

                    itemInseridoVO.vo.setProperty("AD_VLRDESC", valorDesconto)
                    itemInseridoVO.vo.setProperty("VLRUNIT", vlrUnitario)
                    itemInseridoVO.vo.setProperty("AD_VLRUNITCM", vlrUnitario)
                    itemInseridoVO.vo.setProperty("VLRTOT", vlrUnitario * itemInseridoVO.qtdneg!!)


                }
            }




        }
    }

    /**
     * Tenta inserir um item na central obedecendo as regras de barramento.
     * Retorna um par com o item inserido ou a excecao que impediu a insercao.
     */
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

    /**
     * Seta propriedades de sessão Sankhya.
     */
    private fun setSessionProperty(nome: String, valor: Boolean) {
        JapeSession.putProperty(nome, valor)
        JapeSessionContext.putProperty(nome, valor)
    }

    private fun zeroSeNegativo(qtdEstoque: Int) = if (qtdEstoque <= 0) 0 else qtdEstoque

    /**
     * Responsavel por criar o cabecalho do PedidoOL na central de vendas.
     * Valida tambem se o tipo de negociação e condi??o comercial sao validos.
     */
    private fun criarCabecalho(pedidoOLVO: PedidoOLVO, clienteVO: ParceiroVO): CabecalhoNotaVO {
        LogOL.info("Preparando a criacao do cabecalho...")
        val codTipVenda = requireNotNull(pedidoOLVO.codPrz) { " Prazo n?o informado. " }
        val condicaoComercial = requireNotNull(pedidoOLVO.cond?.toBigDecimal()) { " Condição comercial não informada. " }

        verificarCondicaoComercial(condicaoComercial)

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
            "AD_CODCOND" to condicaoComercial,
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

    /**
     * Verifica se a condi??o comercial esta cadastrada.
     */
    private fun verificarCondicaoComercial(condicaoComercial: BigDecimal) {
        condicaoDAO.findByPK(condicaoComercial) ?: throw EnviarPedidoCentralException(
            "Condição Comercial $condicaoComercial não encontrada.", RetornoPedidoEnum.CONDICAO
        )
    }

    /**
     * Busca o parceiro no Sankhya com base nos dados do PedidoOL.
     * Alem disso, valida se o CNPJ ? valido e se o parceiro est? ativo e cadastro no Sankhya.
     */
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

    /**
     * Atraves do Barramento Central, tenta incluir itens na central Sankhya e, se a acao for concluida
     * calcula-se os impostos do item e retorno o barramento regra.
     * Barramento = Mecanismo de controle de regras/falhas da central de vendas Sankhya.
     */
    @Throws(Exception::class)
    private fun incluirItensSemPreco(nuNota: BigDecimal, itens: Array<Map<String, Any?>>): BarramentoRegra.DadosBarramento? {
        try {
            val barramentoRegra = CentralNotasUtils.incluirItens(nuNota, itens.toList(),false)
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

    /**
     * Chama a rotina de confirmação nativa da central, caso exista item pendente no pedido.
     */
    @Throws(Exception::class)
    @Deprecated("use simularConfirmacaoNota")
    private fun confirmarMovCentral(nuNota: Int): BarramentoRegra.DadosBarramento {
        val barramento = BarramentoRegra.build(CentralFaturamento::class.java,
            "regrasConfirmacaoSilenciosa.xml", AuthenticationInfo.getCurrent())

        val temItemPendente = itemNotaDAO.find {
            it.where = " PENDENTE = 'S' AND NUNOTA = ? "
            it.parameters = arrayOf(nuNota)
        }.isNotEmpty()

        if(temItemPendente){
            ConfirmacaoNotaHelper.confirmarNota(nuNota.toBigDecimal(), barramento, true)
        }

        return barramento.dadosBarramento
    }

    @Throws(java.lang.Exception::class)
    private fun simularConfirmacaoCentral(nuNota: Int) {
        val barramento = BarramentoRegra.build(CentralFaturamento::class.java,
            "regrasConfirmacaoSilenciosa.xml", AuthenticationInfo.getCurrent())

        val temItemPendente = itemNotaDAO.find {
            it.where = " PENDENTE = 'S' AND NUNOTA = ? "
            it.parameters = arrayOf(nuNota)
        }.isNotEmpty()

        if(temItemPendente){
            ConfirmacaoNotaHelper.confirmarNota(nuNota.toBigDecimal(), barramento,
                false, true)
        }
    }

    /**
     * Seta propriedades de autentica??o na sess?o atual.
     */
    @Deprecated("Nao deve ser usado no model")
    private fun setAuthenticationInfo(): AuthenticationInfo {
        val info = AuthenticationInfo.getCurrentOrNull()
            ?: AuthenticationInfo("SUP", BigDecimal.ZERO, BigDecimal.ZERO, 0).apply { makeCurrent() }

        JapeSessionContext.putProperty("usuario_logado", info.userID)
        JapeSessionContext.putProperty("authInfo", info)

        return info
    }

    private fun cancelarPedido(pedidoCentralVO: CabecalhoNotaVO, nuNotaCentral: Int) {
        val jaEnviadoWMS = pedidoCentralVO.vo.asString("BH_STATUS") != "Nao integrado no WMS"
        if (jaEnviadoWMS) {
            throw IllegalStateException("<H2>Não é possível cancelar. PEDIDO JA INTEGRADO COM WMS</H2>")
        }
        marcarTodosItensComoNaoPendente(nuNotaCentral, "Pedido OL Cancelado")
        pedidoCentralVO.vo.setProperty("AD_NUMPEDIDO_OL", null)
        pedidoCentralVO.vo.setProperty("AD_NUINTEGRACAO", null)
        pedidoCentralVO.vo.setProperty("AD_STATUSOL", "CANCELADO")
        cabecalhoNotaDAO.save(pedidoCentralVO)
    }

}