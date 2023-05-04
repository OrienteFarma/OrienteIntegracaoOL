var regexDocumentosFaltantes = new RegExp('.*Produto: ([0-9]+).*', 'g');
if(typeof var_DEADLOCK != 'undefined')
    mostraErro(" [integracao] ABORTADO DEVIDO A DEADLOCK ANTERIOR. Nro. OL: "+var_pedidoOL)


function parceiroCriado(){

    if(typeof var_RODOUPARC != 'undefined' && typeof var_CODPARC == 'undefined') {
        gravaLogCabecalhoSankhya("PEDIDO IGNORARDO - PARCEIRO NAO LOCALIZADO", "CADASTRO", 3);
        log("[integracao] PEDIDO IGNORARDO - PARCEIRO NAO LOCALIZADO");
        return false
    }

    return true


}

var_pedidoJaExisteNaBase = false;
var_SUCESSO = true;
var_ehParceiroNovo = false;
var_ehParceiroInativo = false;
SNK_NOMEARQUIVO = getCampoArquivo("SNK_NOMEARQUIVO")


function getParceiro(cnpjCampoArquivo) {
    
    
    var_RODOUPARC = true
log("Busca cliente, primeira tentativa: " + cnpjCampoArquivo);
    var parceiro = encontrarRegistroParam('Parceiro', "CGC_CPF = ?", [cnpjCampoArquivo]);

    if (!parceiro) {

        log("Busca cliente, segunda tentativa: " + cnpjCampoArquivo);
         parceiro = encontrarRegistroParam('Parceiro', "CGC_CPF = ?", [Number(cnpjCampoArquivo).toString()]);

        if (!parceiro) {
            gravaLogCabecalhoSankhya("PARCEIRO NÃO ENCONTRADO", "CADASTRO", 3);
            try{
                
                parceiro = novoParceiroDadosSefaz(cnpjCampoArquivo);
                log("Novo cliente registrado: " + cnpjCampoArquivo);
                var_ehParceiroNovo = true;
				
            } catch (err) {
                var mensagemErro = getMensagemErro(err);
                gravaLogCabecalhoSankhya("FALHA AO GRAVAR O PARCEIRO"+mensagem, "CADASTRO", 3);
                VAR_INGORE = true
                return

            }
        }
    }
    if(parceiro.getCampo('ATIVO') == "N" ){

        log("O cliente estava inativo: " + cnpjCampoArquivo);
        var_ehParceiroInativo = true;
        parceiro.setCampo("ATIVO", "S");

    }

	parceiro.setCampo("CLIENTE", "S");
	parceiro.save();
	
    getPrazoParceiro(parceiro.getCampo("CODPARC"));

    var_VENDEDOR = parceiro.getCampo('CODVEND');
    var_CODPARC = parceiro.getCampo("CODPARC");
    return var_CODPARC;
}


/* function getParceiro(cnpjCampoArquivo) {
    
    
    var_RODOUPARC = true
    var parceiroDefault = encontrarRegistroParam('Parceiro', "CGC_CPF = ?", [cnpjCampoArquivo]);

    if (!parceiroDefault) {
        var parceiroNumber = encontrarRegistroParam('Parceiro', "CGC_CPF = ?", [Number(cnpjCampoArquivo).toString()]);

        if (!parceiroNumber) {
            gravaLogCabecalhoSankhya("PARCEIRO NÃO ENCONTRADO", "CADASTRO", 3);
            try{
                parceiroNumber = novoParceiroDadosSefaz(cnpjCampoArquivo);
                var_ehClienteNovo = true;
				parceiroDefault = encontrarRegistroParam('Parceiro', "CODPARC = ?", parceiroNumber.getCampo("CODPARC"));
            } catch (err) {
                var mensagemErro = getMensagemErro(err);
                gravaLogCabecalhoSankhya("FALHA AO GRAVAR O PARCEIRO"+mensagem, "CADASTRO", 3);
                VAR_INGORE = true
                return

            }
        }
    }
    
	parceiroDefault.setCampo("CLIENTE", "S");
	parceiroDefault.save();
	
    getPrazoParceiro(parceiroDefault.getCampo("CODPARC"));

    var_VENDEDOR = parceiroDefault.getCampo('CODVEND');
    var_CODPARC = parceiroDefault.getCampo("CODPARC");
    return var_CODPARC;
}

 */


function getPrazoParceiro(codParceiro) {
    var prazo = encontrarRegistroParam('ComplementoParc', "CODPARC = ? ", [codParceiro]);
    if (!prazo || prazo.getCampo("SUGTIPNEGSAID") == "0") {
        var_PRAZO = 210;
    } else {
        var_PRAZO = 210;
        //var_PRAZO = prazo.getCampo("SUGTIPNEGSAID");
    }
}

function getEmpresa(cnpj) {
    var_CODEMP = 1;
    var empresa = encontrarRegistroParam("Empresa", "CGC = ? ", [cnpj]);
    if (!empresa) {
        gravaLogCabecalhoSankhya('000000', "EMPRESA NÃO ENCONTRADA", "ERRO", var_NOMEINT, 8);
    }
    else{
        var_CODEMP = empresa.getCampo("CODEMP");
        return var_CODEMP;
    }
}



function getMensagemErro(err) {
    var mensagemErro = "";

    if (err.hasOwnProperty('lineNumber') && err.lineNumber > ").append(firstRow).append(") {
        errorLine = err.lineNumber - ").append(firstRow).append(";
    }
    if (err.hasOwnProperty('javaException')) {
        err = err.javaException;
    }
    if (err.message === undefined || err.message === null) {
        mensagemErro = 'Erro de execução no Script:';
    } else {
        mensagemErro = err.message;
    }
    return mensagemErro;
}

function getProdutosErroDocumentacao(mensagem) {
    var produtos = [];
    while ((result = regexDocumentosFaltantes.exec(mensagem)) !== null) {
        if (produtos.indexOf(result[1]) < 0);
        produtos.push(result[1]);
    }
    return produtos;
}

function marcarItemComoNaoPendente(nunota, codprod) {
    var items = encontrarRegistrosParam("ItemNota", "NUNOTA = ? AND CODPROD = ?", [nunota, codprod]);
    items.forEach(function (itemNota) {
        itemNota.setCampo("PENDENTE", "N");
        itemNota.save();
    });
}

function marcaPedidoComoNaoPendente(nuNota) {
    var pedido = encontrarRegistrosParam("NUNOTA", "NUNOTA= ?", [nuNota]);
    pedido.setCampo("PENDENTE", "N");
    pedido.save();
}

function ajustaDescontoQuatroDigitos(descontoArquivoParam) {
    var num = Number(descontoArquivoParam.toString().match(/^\d+(?:\.\d{0,2})?/));
    return num.toFixed(2);
}// Recebe 8382 retorna 83.82

function ajustaDescontoNumber(descontoArquivoParam) {
    var descF = parseFloat(descontoArquivoParam)
    if(descF <= 0){
        log('[integracao] Calculo de desconto: resultado -> '+descF +', parametro -> '+descontoArquivoParam )
        return descF
    }

//     if(descontoArquivoParam.contains('.' )){
//         log('resultado desconto '+descF +' desconto param '+descontoArquivoParam )
//         return descF
//     }

//     if(descontoArquivoParam.toString().length()==1)
//       return descF

//     //fórmula para formatar desconto. Mónimo 2 dígitos requeridos.
//   var desconto = (descF/parseFloat("100000000".slice(0,descF.toString().length-1))).toString()

    var desconto = (descF/100).toString();



    log('[integracao] Calculo de desconto: resultado -> '+descF +', parametro -> '+descontoArquivoParam )
    return desconto



    // return descontoArquivoParam.substring(0,2).concat(".").concat(descontoArquivoParam.substring(2,4));
}// Recebe 8382 retorna 83.82

function consultaPedido(var_pedidoOL) {
    var pedido = encontrarRegistroParam("CabecalhoNota", "AD_NUMPEDIDO_OL = ? ", [var_pedidoOL]);
    if (pedido) {
        gravaLogCabecalhoSankhya("PEDIDO JA REGISTRADO ANTERIORMENTE ol: "+var_pedidoOL+ "- NUNOTA: " + pedido.getCampo("NUNOTA"), "DUPLICIDADE", 5);
        return false;
    } else {
        return true;
    }
} // Veririca se o numero de pedido OL já foi registrado anteriormente

function mudaStatusPedido(nuNota, status) {
    try {
        setJapeSessionVar("br.com.sankhya.bh.integrou.wms", true);
        log("[integracao] Executando metodo mudaStatusPedido: Parametros[nunota: "+nuNota+ ", status:"+status+"]" )

        var pedido = encontrarRegistroParam("CabecalhoNota", " NUNOTA = ?", [nuNota]);
        var f = "UNDEFINED"
        if(typeof SNK_NOMEARQUIVO != 'undefined') {
            f = SNK_NOMEARQUIVO
        }
        if (pedido) {

            log("Mudando status do pedido nunota = "+nuNota +" para  "+ status +", arquivo gerado "+ f)
            pedido.setCampo("AD_STATUSOL", status);
            pedido.save();
            return;
        }

        var pedidoNotaOrig = encontrarRegistroParam("CompraVendavariosPedido", "NUNOTAORIG = ?", [nuNota]);
        if (pedidoNotaOrig) {
            log("Mudando status do pedido nunota = "+nuNota +" para  "+ status +", arquivo gerado "+f)
            var pedidoNotaCab = encontrarRegistroParam("CabecalhoNota", " NUNOTA = ?", [pedidoNotaOrig.getCampo("NUNOTA")]);
            pedidoNotaCab.setCampo("AD_STATUSOL", status);
            pedidoNotaCab.save();
            return;
        }
        log("[integracao] Nao localizou documento para mudar o status do pedido nunota = "+nuNota+ " para "+ status )

    } catch (err) {
        gravaLogCabException("MUDASTATUSPEDIDO", "EXCECAO EM " + status + ": " + err);
    }
} // Muda o status do pedido (PENDENTE, RETORNO, ESPELHO)
// Muda o status do pedido (PENDENTE, RETORNO, ESPELHO)

function validaPendenciaItems() {

    var itemsPedido = encontrarRegistrosParam("ItemNota", "NUNOTA = ?", [var_NUNOTA]);
    var pendenteSim = 0;
    var pendenteNao = 0;

    itemsPedido.forEach(function (item) {
        if(item.getCampo("PENDENTE") == 'N'){
            pendenteNao = pendenteNao + 1;
        } else{
            pendenteSim = pendenteSim + 1;
        }
    });

    if (pendenteSim == 0) {
        gravaLogCabecalhoSankhya("NENHUM ITEM ATENDIDO", "CANCELADO", 2);
    } else if (pendenteNao == 0) {
        gravaLogCabecalhoSankhya("ATENDIDO", "ATENDIDO", 0);
    } else {
        gravaLogCabecalhoSankhya("PARCIALMENTE ATENDIDO", "ATENDIDO", 1);
    }
}

function validaItems() {
    var items = encontrarRegistrosParam("ItemNota", "NUNOTA = ?", [var_NUNOTA]);

    items.forEach(function (item) {
        ajustaQtdCaixaFechada(item.getCampo("CODPROD"));

        if (item.getCampo("AD_QTDESTOQUE") <= 0) {
            marcarItemComoNaoPendente(var_NUNOTA, item.getCampo("CODPROD"));
            addObservaoByItemNota(item, "N ATENDIDO - ESTOQUE")
            item.save();
        }
        if(item.getCampo("VLRTOT") == 0){
            var_SUCESSO = false;
            var qtd =  item.getCampo("QTDNEG");
            gravaLogItemSankhya(undefined,
                item.getCampo("CODPROD"),
                "VALOR ZERADO",
                "VALOR ZERADO",
                99,
                qtd);
        }
    });
}

function gravaObservacaoItem(codProd, mensagem) {
    var itemNota = encontrarRegistroParam("ItemNota", "NUNOTA = ? AND CODPROD = ?", [var_NUNOTA, codProd]);
    itemNota.setCampo("OBSERVACAO", mensagem);
    itemNota.save();
}

function getPedidosCliente() {
    //Implementar
}

function ajustaQtdCaixaFechada(codProd) {

    var produto = encontrarRegistroParam("Produto", "CODPROD = ?", [codProd]);
    var agrupamentoMin = parseInt(produto.getCampo("AGRUPMIN"));
    var item = encontrarRegistroParam("ItemNota", "NUNOTA = ? AND CODPROD = ?", [var_NUNOTA, codProd]);

    if (agrupamentoMin > 0) {
        var qtdAtendida = parseInt(item.getCampo("QTDNEG"));
        var fatorMultiplicacao = parseInt(Math.floor(qtdAtendida / agrupamentoMin));


        if (fatorMultiplicacao == 0) {
            marcarItemComoNaoPendente(var_NUNOTA, codProd);
            gravaObservacaoItem(codProd, "CORTE TOTAL - SOMENTE CX EMBARQUE");
        } else {
            var qtdCortar = (qtdAtendida - (agrupamentoMin * fatorMultiplicacao));
            item.setCampo("QTDCONFERIDA", qtdCortar);
            item.save();
            gravaObservacaoItem(codProd, "CORTE PARCIAL - SOMENTE CX EMBARQUE");
        }

    } else {
        return
    }

}


// CONTROLE DO LOG DE INTEGRACAO
function updateLogCabByItem(codRetornoItem) {
    var codRetornoCabecalho;
    var mensagem;

    switch (codRetornoItem) {
        case 0: codRetornoCabecalho = 0; mensagem = "ATENDIDO"; break;
        case 1: codRetornoCabecalho = 1; mensagem = "PARCIALMENTE ATENDIDO"; break;
        case 2: codRetornoCabecalho = 1; mensagem = "PARCIALMENTE ATENDIDO"; break;
        case 3: codRetornoCabecalho = 1; mensagem = "PARCIALMENTE ATENDIDO"; break;
        case 4: codRetornoCabecalho = 1; mensagem = "PARCIALMENTE ATENDIDO"; break;
        case 5: codRetornoCabecalho = 7; mensagem = "DOCUMENTAÇÃO INSUFICIENTE"; break;
        case 99: codRetornoCabecalho = 99; mensagem = "ERRO NAO CATALOGADO"; break;
    }

    gravaLogCabecalhoSankhya(mensagem, "PENDENTE", codRetornoCabecalho);
}

function updateLogItemByItem(codRetornoItem, codBarras, codProd) {
    var codRetornoItemSankhya;
    var mensagem = '';
    var logTitle = '';

    switch (codRetornoItem) {
        case 1: codRetornoItemSankhya = 0; mensagem = "PARCIALMENTE ATENDIDO"; logTitle = 'ESTOQUE'; break;
        case 2: codRetornoItemSankhya = 2; mensagem = "PRODUTO NAO CADASTRADO"; logTitle = 'CADASTRO'; break;
        case 3: codRetornoItemSankhya = 3; mensagem = "ESTOQUE INSUFICIENTE"; logTitle = 'ESTOQUE'; break;
        case 5: codRetornoItemSankhya = 5; mensagem = "DOCUMENTACAO INSUFICIENTE"; logTitle = 'DOCUMENTACAO'; break;
        case 6: codRetornoItemSankhya = 6; mensagem = "N PERTENCE A CONDICAO"; logTitle = 'CONDICAO'; break;
        case 99: codRetornoItemSankhya = 99; mensagem = "ERRO NAO CATALOGADO"; logTitle = 'EXCEPTION'; break;
    }

    gravaLogItemSankhya(codBarras, codProd, logTitle, mensagem, codRetornoItemSankhya, getCampoArquivo("QUANTIDADE"));
}

function updateLogByCab(codRetornoCab) {
    var mensagem;
    var status;

    switch (codRetornoCab) {
        case 1: mensagem = "PARCIALMENTE ATENDIDO"; status = "ATENDIDO"; break;
        case 2: mensagem = "NENHUM ITEM ATENDIDO"; status = "CANCELADO"; break;
        case 3: mensagem = "CLIENTE INVALIDO"; status = "CANCELADO"; break;
        case 4: mensagem = "PEDIDO NAO ALCANCOU VALOR MINIMO DA CONDICAO"; status = "CANCELADO"; break;
        case 5: mensagem = "PEDIDO DUPLICADO"; status = "CANCELADO"; break;
        case 6: mensagem = "CONDICAO INVALIDA"; status = "CANCELADO"; break;
        case 7: mensagem = "DOCUMENTAÇÃO INSUFICIENTE"; status = "ATENDIDO"; break;
        case 8: mensagem = "CNPJ DISTRIBUIDOR INVALIDO"; status = "CANCELADO"; break;
        case 99: mensagem = "ERRO NAO CATALOGADO"; status = "CANCELADO"; break;
    }

    gravaLogCabecalhoSankhya(mensagem, status, codRetornoCab);
}

function getCodRetornoItem(mensagem) {
    if (mensagem.search("ESTOQUE INSUFICIENTE PARA O PRODUTO")) {
        return 3;
    }
    if (mensagem.search("DEVE SER NEGOCIADO EM M")) {
        return 3;
    }
    if (mensagem.search("documento(s) faltante(s)")) {
        return 5;
    }
    if (mensagem.search("O VALOR DO PEDIDO NÃO ATENDE O VALOR MÍNIMO")) {
        return 0;
    }
    if (mensage.search("NÃO PERTENCE A CONDIÇÃO COMERCIAL")) {
        return 6;
    }
    return 99;
}

function getCodRetornoCab(mensagem) {
    if (mensagem.search("NO CURRENT ROW")) {
        return 2;
    }
    if (mensagem.search("O VALOR DO PEDIDO NÃO ATENDE O VALOR MÍNIMO")) {
        return 4;
    }
    if (mensagem.search("NÃO ESTÁ ATIVO NA NOTA DE NRO")) {
        return 3;
    }
    return 99;
}

function gravaLogCabecalhoSankhya(mensagem, status, codRetorno) {
    log("gravaLogCabecalhoSankhya : "+mensagem)
    try {
        var logReg = encontrarRegistroParam("AD_LOGINTCAB", "NUMPEDIDOOL = ? AND AD_NUINTEGRACAO = ?", [var_pedidoOL, var_CODINTEGRACAO]);

        if (logReg) {
            //Update
            logReg.setCampo("STATUS", status.toString());
            logReg.setCampo("DESCRICAO", mensagem.substr(0, 4000).toUpperCase());
            logReg.setCampo("DATA", getNow());
            if (typeof (codRetorno) != "undefined") {
                logReg.setCampo("COD_RETORNO", toBigDecimal(codRetorno));
            }
            if(typeof SNK_NOMEARQUIVO != 'undefined') {
                logReg.setCampo("NOMEARQUIVO", SNK_NOMEARQUIVO);
            }

            logReg.save();
        } else {
            //Create
            var novoLog = novaLinha("AD_LOGINTCAB");
            novoLog.setCampo("NUMPEDIDOOL", var_pedidoOL);
            novoLog.setCampo("STATUS", status.toString());
            novoLog.setCampo("DESCRICAO", mensagem.substr(0, 4000).toUpperCase());
            novoLog.setCampo("NOME_INTEGRACAO", var_NOMEINT);
            novoLog.setCampo("AD_NUINTEGRACAO", var_CODINTEGRACAO);
            novoLog.setCampo("DATA", getNow());
            if(typeof SNK_NOMEARQUIVO != 'undefined') {
                novoLog.setCampo("NOMEARQUIVO", SNK_NOMEARQUIVO);
            }

            if (typeof (codRetorno) != "undefined") {
                novoLog.setCampo("COD_RETORNO", toBigDecimal(codRetorno));
            }
            novoLog.save();
        }
    } catch (err) {
        var mensagemErro = getMensagemErro(err);
        var_SUCESSO = false;
    }
}

function gravaLogItemSankhya(codbarras, codprod, logtitle, mensagem, codRetorno, quantidade) {
    var logIte = novaLinha("AD_LOGINTITE");
    logIte.setCampo("NUMPEDIDOOL", var_pedidoOL);
    if (codbarras) {
        logIte.setCampo("REFERENCIA", codbarras);
    }
    logIte.setCampo("AD_NUINTEGRACAO", var_CODINTEGRACAO);
    logIte.setCampo("DATA", getNow());
    logIte.setCampo("DESCRICAO", mensagem.toUpperCase());
    if (codprod) {
        logIte.setCampo("CODPROD", codprod);
    }
    if(quantidade && quantidade != null && quantidade != undefined){
        logIte.setCampo("QUANTIDADE", toBigDecimal(quantidade))
    }
    logIte.setCampo("LOGTITLE", logtitle);
    logIte.setCampo("COD_RETORNO", codRetorno);

    logIte.save();

}

function gravaLogCabException(acaoFuncao, mensagem) {
    try {

        log("[integracao] Erro ao gravar gravaLogCabException : "+mensagem)
        var logReg = encontrarRegistroParam("AD_EXCEPTIONCAB", "NUMPEDIDOOL = ? AND ADNUINTEGRACAO = ?", [var_pedidoOL, var_CODINTEGRACAO]);
        if (logReg) {
            //Update
            logReg.setCampo("DESCRICAO", mensagem.substr(0, 4000).toUpperCase());
            logReg.setCampo("ACAOFUNCAO", acaoFuncao);
            logReg.setCampo("DATA", getNow());
            if(typeof SNK_NOMEARQUIVO != 'undefined') {
                logReg.setCampo("NOMEARQUIVO", SNK_NOMEARQUIVO);
            }
            logReg.save();
        } else {
            //Create
            var novoLog = novaLinha("AD_EXCEPTIONCAB");
            novoLog.setCampo("NUMPEDIDOOL", var_pedidoOL);
            novoLog.setCampo("DESCRICAO", mensagem.substr(0, 4000).toUpperCase());
            novoLog.setCampo("NOMEINTEGRACAO", var_NOMEINT);
            novoLog.setCampo("ADNUINTEGRACAO", var_CODINTEGRACAO);
            novoLog.setCampo("ACAOFUNCAO", acaoFuncao);
            novoLog.setCampo("DATA", getNow());
            if(typeof SNK_NOMEARQUIVO != 'undefined') {
                novoLog.setCampo("NOMEARQUIVO", SNK_NOMEARQUIVO);
            }
            novoLog.save();
        }

        codRetorno = getCodRetornoCab(mensagem);
        if (codRetorno != 0) {
            updateLogByCab(codRetorno);
        }

    } catch (err) {
        var mensagemErro = getMensagemErro(err);
        var_SUCESSO = false;
        log("[integracao] Erro ao criar log de Exeption Cabecalho: " + mensagemErro);
    }
}

function gravaLogItemException(codbarras, codprod, mensagem) {
    log(mensagem)
    var logIte = novaLinha("AD_EXCEPTIONITE");
    logIte.setCampo("NUMPEDIDOOL", var_pedidoOL);
    logIte.setCampo("REFERENCIA", codbarras.toString());
    logIte.setCampo("CODPROD", codprod);
    logIte.setCampo("ADNUINTEGRACAO", var_CODINTEGRACAO);
    logIte.setCampo("DATA", getNow());
    logIte.setCampo("DESCRICAO", mensagem.toUpperCase());
    logIte.save();

    codRetorno = getCodRetornoItem(mensagem);
    if (codRetorno != 0) {
        updateLogItemByItem(codRetorno, codbarras.toString(), codprod);
    }
}





// ### FUNÇÕES MAIN ###
function cabecalho(var_pedidoOL, empresa, codigoParceiro) {
    if(!parceiroCriado()){
        var_NUNOTA = null;
        return
    }

    if (consultaPedido(var_pedidoOL)) {
        try {
            var modelo = encontrarRegistroParam("CabecalhoNota", "NUNOTA = ?", [26]);
            pedido = novaLinha("CabecalhoNota");
            pedido.setCampo("AD_NUMPEDIDO_OL", var_pedidoOL);
            pedido.setCampo("CODVEND", var_VENDEDOR);
            pedido.setCampo("CODPARC", codigoParceiro);
            pedido.setCampo("CODEMP", 1); // VOLTAR PARA VARIAVEL empresa.
            pedido.setCampo("NUMNOTA", 0);
            pedido.setCampo("CIF_FOB", "F");
            pedido.setCampo("CODTIPOPER", 1001);
            pedido.setCampo("AD_TIPOCONDICAO", "O");
            pedido.setCampo("CODTIPVENDA", var_PRAZO);
            pedido.setCampo("CODNAT", modelo.getCampo("CODNAT"));
            pedido.setCampo("CODCENCUS", modelo.getCampo("CODCENCUS"));
            pedido.setCampo("CODPROJ", modelo.getCampo("CODPROJ"));
            pedido.setCampo("AD_CODCOND", var_CODCONDICAO);
            pedido.setCampo("AD_CODTIPVENDA", var_PRAZO);
            pedido.setCampo("AD_NUINTEGRACAO", var_CODINTEGRACAO);
            pedido.setCampo("OBSERVACAO", var_NUMPEDCLIENTE);
            pedido.setCampo("AD_STATUSOL", "INTEGRANDO");
            pedido.save();

        } catch (err) {
            gravaLogCabException("CABECALHO", "EXCECAO - CABECALHO: " + err);
            var_SUCESSO = false;
        }
        var_NUNOTA = pedido.getCampo("NUNOTA");
    } else {
        log("[integracao] Pedido OL: "+ var_pedidoOL+ " ja existe na base.")
        var_NUNOTA = null;
    }
}

function detalhe() {
    log('PD77_INICIANDO_DETALHE')
    if (!var_NUNOTA || var_NUNOTA == undefined || var_NUNOTA == null) {
        log("PD77_RETURN_NUNOTA_NULL" + var_SUCESSO);
        return
    } else {
        lancarItem();
    }
    log('PD77_FINALIZANDO_DETALHE')
}

var_itensParaNaoPendente = [{}];

function addObservaoByItemNota(itemNotaReg, mensagem) {
    var observacao = itemNotaReg.getCampo("OBSERVACAO")
    itemNotaReg.setCampo("OBSERVACAO", observacao + " - " + mensagem);
}

function lancarItem() {
    if(!parceiroCriado())
        return

    var produto = encontrarRegistroParam("Produto", "REFERENCIA = ?", [getCampoArquivo("EAN")]);

    //CodigoBarras

    if (!produto) {

        // gravaLogItemSankhya(getCampoArquivo("EAN"), 0, "CADASTRO", "PRODUTO NÃO CADASTRADO", 2);
        // return


        log("[integracao] Produto nao encontrado EAN -> "+getCampoArquivo("EAN")+". Buscando codigo de barras alternativo.");
        var codBarras = encontrarRegistroParam("CodigoBarras", "CODBARRA = ?", [getCampoArquivo("EAN")]);

        if(codBarras){

            log("[integracao] Codigo de barras alternativo encontrado. Buscando produto pelo codigo de produto relacionado ao codigo de barras:  "+ codBarras.getCampo("CODPROD"));
            produto = encontrarRegistroParam("Produto", "CODPROD = ?", [codBarras.getCampo("CODPROD")]);

        }

        if(!produto) {
            log("produto não encontrado!")

            gravaLogItemSankhya(getCampoArquivo("EAN"), 0, "CADASTRO", "PRODUTO NÃO CADASTRADO", 2, getCampoArquivo("QUANTIDADE"));
            return
        }
    }

    var itemNota = {};
    var pendente = "S"
    itemNota["NUNOTA"] = toBigDecimal(var_NUNOTA);
    itemNota["CODEMP"] = toBigDecimal(var_CODEMP);
    itemNota["CODPROD"] = produto.getCampo("CODPROD");
    itemNota["AD_CODCOND"] = toBigDecimal(var_CODCONDICAO);
    itemNota["VLRUNIT"] = null;
    itemNota["CODVEND"] = toBigDecimal(var_VENDEDOR);
    itemNota["CODVOL"] = produto.getCampo("CODVOL");
    itemNota["PERCDESC"] = toBigDecimal(0);
    itemNota["VLRDESC"] = toBigDecimal(0);
    itemNota["PENDENTE"] = "S";

    if (produto.getCampo("USALOCAL") == "S") {
        itemNota["CODLOCALORIG"] = produto.getCampo("CODLOCALPADRAO");
    } else {
        itemNota["CODLOCALORIG"] = toBigDecimal(0);
    }

    var quantidadeArquivo = getCampoArquivo("QUANTIDADE")
    var codemp = toBigDecimal(var_CODEMP.toString());
    var codprod = toBigDecimal(produto.getCampo("CODPROD").toString());

    //Validação de estoque
    var qtdEstoque = consultaEstoqueEmp(codemp, codprod, toBigDecimal("1000000"), true);
    log("Consulta de estoque. Produto -> "+codprod + ", Empresa -> "+codemp+", Estoque -> "+qtdEstoque)
    itemNota["BH_QTDNEGORIGINAL"] = toBigDecimal(quantidadeArquivo);
    itemNota["QTDNEG"] = toBigDecimal(quantidadeArquivo);
    if (qtdEstoque >= toBigDecimal(quantidadeArquivo)) {
        itemNota["AD_QTDESTOQUE"] = toBigDecimal(qtdEstoque <= 0 ? 0 : qtdEstoque);
    } else {
        var qtdAtendida = toBigDecimal(quantidadeArquivo) - toBigDecimal((quantidadeArquivo - (qtdEstoque <= 0 ? 0 : qtdEstoque)));

        if(toBigDecimal(qtdAtendida) < toBigDecimal(1)){
            itemNota["QTDNEG"] = toBigDecimal(quantidadeArquivo);
            itemNota["BH_QTDCORTE"] = toBigDecimal(quantidadeArquivo);
            pendente = "N";
        }else{
            itemNota["QTDNEG"] = toBigDecimal(qtdAtendida);
            itemNota["BH_QTDCORTE"] = toBigDecimal(quantidadeArquivo  - qtdAtendida );
            pendente = "S";
        }

        itemNota["AD_QTDESTOQUE"] = toBigDecimal(qtdEstoque <= 0 ? 0 : qtdEstoque);

        if (qtdEstoque.toString() <= 0) {
            //gravaLogItemSankhya(produto.getCampo("REFERENCIA"), produto.getCampo("CODPROD"), "ESTOQUE", "ESTOQUE INSUFICIENTE", 3);
            //melhorado msg de log;
            gravaLogItemSankhya(produto.getCampo("REFERENCIA"),
                produto.getCampo("CODPROD"),
                "ESTOQUE",
                "ESTOQUE INSUFICIENTE: PEDIDO -> " + quantidadeArquivo+ ", ESTOQUE -> " +qtdEstoque,
                3,
                toBigDecimal(quantidadeArquivo));

        } else {
            gravaLogItemSankhya(produto.getCampo("REFERENCIA"),
                produto.getCampo("CODPROD"),
                "ESTOQUE",
                "PARCIALMENTE ATENDIDO, SOLICITADO: " + quantidadeArquivo + " ATENDIDO: " + toBigDecimal(qtdEstoque),
                1,
                toBigDecimal(quantidadeArquivo));
            itemNota["OBSERVACAO"] = "PARCIALMENTE ATENDIDO";
            updateLogCabByItem(1);
        }
    }
    itemNota["ATUALESTOQUE"] = toBigDecimal(1);
    itemNota["RESERVADO"] = "S"



    try {
        setJapeSessionVar("mov.financeiro.ignoraValidacao", true);
        setJapeSessionVar("br.com.sankhya.com.CentralCompraVenda", true);
        setJapeSessionVar("ItemNota.incluindo.alterando.pela.central", true);
        setJapeSessionVar("validar.alteracao.campos.em.titulos.baixados", false);

        try{
            log("AGORA_TENTANDO_SALVAR_ITEM");
            incluirItensSemPreco(var_NUNOTA, [itemNota]);
        }catch (e){
            log("AGORA_ERRO_SALVAR_ITEM"+e);
            //se ocorrer deadlock, aborta a inserção de itens
            
            if(mensagem.indexOf("deadlock") > 0){
                log("[integracao] deadlock detectado  " + mensagem  )
                var_SUCESSO = false
                var_DEADLOCK = true
            } 
        }

        var itemNotaReg = encontrarRegistroParam(
            "ItemNota", "NUNOTA = ? AND SEQUENCIA = (SELECT MAX(SEQUENCIA) FROM TGFITE ITE WHERE ITE.NUNOTA = ItemNota.NUNOTA)", [var_NUNOTA]);

        log("AGORAPD77_PRECO_BASE"+itemNotaReg.getCampo("PRECOBASE"));
        if(itemNotaReg.getCampo("PRECOBASE") <= 0 ){
            log("AGORA_SERA_MARCADO_COMO_NAO_PENDENTE");
            pendente = "N"
            addObservaoByItemNota(itemNotaReg, "PRECO BASE ZERADO");
        } else if (var_CHAVEDESCONTOARQUIVO) {
            //Validacao de desconto do arquivo

            var descontoArquivo = 0;

            if (var_AJUSTADESCONTO) {
                descontoArquivo = ajustaDescontoQuatroDigitos(getCampoArquivo("DESCONTO"));
            } else {
                descontoArquivo = ajustaDescontoNumber(getCampoArquivo("DESCONTO"));
            }


            var percDesc = itemNotaReg.getCampo("AD_PERCDESC");


            log("[integracao] Percdesc após incluirItensSemPreco() -> "+percDesc);

            if (toBigDecimal(descontoArquivo) > toBigDecimal(percDesc)){



                pendente = "N"
                addObservaoByItemNota(itemNotaReg, "DESCONTO INVALIDO" )
                log("[integracao] Desconto invalido nunota "+var_NUNOTA+". Arquivo > Condicao. Arquivo -> "+toBigDecimal(descontoArquivo)+", Condicao -> "+ toBigDecimal(percDesc));

                var qtd = quantidadeArquivo ;
                gravaLogItemSankhya(produto.getCampo("REFERENCIA"),
                    produto.getCampo("CODPROD"), "DESCONTO",
                    "DESCONTO MAIOR DO QUE O PERMITIDO, DESCONTO ARQUIVO: " + descontoArquivo + " DESCONTO PERMITIDO: " +
                    toBigDecimal(percDesc),
                    4,
                    qtd);
                updateLogCabByItem(1);

            } else {

                log("[integracao] Desconto menor que 99.99: " +descontoArquivo);
                var qtd = quantidadeArquivo ;


                log("[integracao] Inserindo desconto do arquivo no campo AD_PERCDESC");
                itemNotaReg.setCampo("AD_PERCDESC", toBigDecimal(descontoArquivo));



                var precoBase = itemNotaReg.getCampo("PRECOBASE");
                log("[integracao] Preço base " +precoBase);

                var valorBruto = qtd * precoBase;

                log("[integracao] Valor bruto " +valorBruto);
                //var descontoBruto = valorBruto - (valorBruto * (toBigDecimal(descontoArquivo) / 100));
                //var vlrUnitario = precoBase - (precoBase * (toBigDecimal(descontoArquivo) / 100));

                //melhorada para caso o desconto do arquivo for zero. Estava gerando erro de calculo, com valor NaN;
                var descontoBruto =   toBigDecimal(descontoArquivo) == 0 ? 0 :  valorBruto - (valorBruto * (toBigDecimal(descontoArquivo) / 100));
                log("[integracao] Desconto bruto " +descontoBruto);

                var vlrUnitario = toBigDecimal(descontoArquivo) == precoBase ? 0 :   precoBase - (precoBase * (toBigDecimal(descontoArquivo) / 100));

                log("[integracao] Valor unitario " +vlrUnitario);

                itemNotaReg.setCampo("AD_VLRDESC", toBigDecimal(descontoBruto));
                itemNotaReg.setCampo("VLRUNIT", vlrUnitario);
                itemNotaReg.setCampo("AD_VLRUNITCM", arredondar(vlrUnitario));
                itemNotaReg.setCampo("VLRTOT",  itemNotaReg.getCampo("QTDNEG") * vlrUnitario)
                log("[integracao] Desconto do arquivo aceito, nunota "+var_NUNOTA+". Desconto arquivo "+toBigDecimal(descontoArquivo) + " < Desconto sistema "+ percDesc +  ". Valor unitario arquivo "+vlrUnitario+", preco base+ "+ precoBase)

            }
        }
        log("AGORA_INICIANDO_MARCAR_SEQ"+itemNotaReg.getCampo("SEQUENCIA"));
        if(pendente === "N"){
            itemNotaReg.setCampo("BH_LOTEORIGSTR", "PENDENTE_NAO");
            /*Plataforma nao consegue manter o valor do array de objetos entre linhas*/
            var_itensParaNaoPendente.push({nuNota: itemNotaReg.getCampo("NUNOTA"), sequencia: itemNotaReg.getCampo("SEQUENCIA")});
        }
        log(var_CODINTEGRACAO+"AGORA_FINALIZANDO_MARCAR"+ var_itensParaNaoPendente.length);
        itemNotaReg.save();
        log("AGORA_SALVOU_ITEM");
    }
    catch (err) {
        log("AGORA_DEU_ERRO"+err);
        var mensagem = getMensagemErro(err);
        log("[integracao] VERIFICACAO EXCECAO NAO TRATADA  " + mensagem + " " + var_NUNOTA  )
        if(mensagem.indexOf("deadlock") > 0){
            log("[integracao] deadlock detectado  " + mensagem  )
            var_SUCESSO = false
            var_DEADLOCK = true
        }

        gravaLogItemException(getCampoArquivo("EAN"), produto.getCampo("CODPROD"), mensagem);

        //gravaLogItemException(produto.getCampo("REFERENCIA"), produto.getCampo("CODPROD"), mensagem);

    }
}

/*
A marcação como nao pendente foi adiada para evitar que o sistema marque o cabecalho como Nao pendente de forma automatica.
Isso acontece quando o primeiro item e marcado como nao pendente.
*/
function marcarComoNaoPendenteFormaTardia(){
    var itens = encontrarRegistrosParam("ItemNota", " PENDENTE = 'S' AND BH_LOTEORIGSTR = 'PENDENTE_NAO' AND NUNOTA = ? ", [var_NUNOTA]);
    itens.forEach(function (item){
        item.setCampo("PENDENTE", "N");
        item.save();
    });
}

var_timeoutValidacaoItens = 0;
var TIMEOUT_RECURSIVIDADE_SUMARIZAR = 30;
/**
 * @returns {boolean} - Tentar Sumarizar Novamente ?
 */
function verificarRegrasComerciais(mensagemErro, pedido) {
    var ehDeadLock = mensagemErro.contains("deadlock")
    var naoPertenceCondicaoComercial = mensagemErro.contains("não pertence a condição comercial")
    var ehDocumentoFaltante = mensagemErro.contains("documento(s) faltante(s)")
    log("a mensagem de erro do pedido eh "+ mensagemErro);

    log("PD77_ehDeadLock"+ehDeadLock);
    log("PD77_naoPertenceCondicaoComercial"+naoPertenceCondicaoComercial);
    log("PD77_ehDocumentoFaltante"+ehDocumentoFaltante);

    if (ehDeadLock) {
        pedido.setCampo("AD_STATUSCONF", "Temporariamente abortado por deadlock.");
        return false;
    }

    //pedido.setCampo("AD_STATUSCONF", mensagemErro.substring(0, Math.min(40, mensagemErro.length) ));

    if (naoPertenceCondicaoComercial) {
        var codProd = new RegExp('[0-9]+', 'g').exec(mensagemErro)[0]
        if(codProd){
            marcarItemComoNaoPendente(var_NUNOTA, codProd);
            return true;
        }
    }

    if (ehDocumentoFaltante) {
        var produtos = getProdutosErroDocumentacao(mensagemErro);
        produtos.forEach(function (codProd) {
            marcarItemComoNaoPendente(var_NUNOTA, codProd);
            gravaLogItemException(codProd, codProd, mensagemErro);
            gravaObservacaoItem(codProd, "FALTA DOCUMENTACAO");
            updateLogCabByItem(5);
        });
        // Confirma agora com os itens cortados por falta de documentação.
        validaPendenciaItems();
        return true;
    }

    if (pedido != null) {
        var itensPedido = encontrarRegistrosParam("ItemNota", "NUNOTA = ?", [var_NUNOTA]);
        itensPedido.forEach( function (item){
            item.setCampo("PENDENTE", "N");
            item.save();
        })
        pedido.setCampo("PENDENTE", "N");
    }
    gravaLogCabException("SUMARIZAR 3", mensagemErro);
    return false;
}

function sumarizar(pedido) {
    var totalizou = false
    try {
        if(!parceiroCriado())
            return

        if (var_NUNOTA) {

            if(!pedido){
                pedido = encontrarRegistroParam("CabecalhoNota", " NUNOTA = ? ", [var_NUNOTA]);
            }

            log('PD77_INICIANDO_SUMARIZAR3')
            
            

            marcarComoNaoPendenteFormaTardia();
            setJapeSessionVar("mov.financeiro.ignoraValidacao", true);
            setJapeSessionVar("validar.alteracao.campos.em.titulos.baixados", false);
            setJapeSessionVar("br.com.sankhya.com.CentralCompraVenda", true);
            setJapeSessionVar("ItemNota.incluindo.alterando.pela.central", true);

            validaItems();
            log('PD77_INICIANDO_SUMARIZAR4')

            
            validaPendenciaItems();
            confirmarnf(var_NUNOTA)
            totalizou = true
            setJapeSessionVar("br.com.sankhya.com.CentralCompraVenda", false);
        }
    } catch (err) {
        log('PD77_INICIANDO_CATCH_SUMARIZAR')
        var_timeoutValidacaoItens++
        if(var_timeoutValidacaoItens > TIMEOUT_RECURSIVIDADE_SUMARIZAR){
            log("Recursividade sumarizar atingiu o máx. de "+ TIMEOUT_RECURSIVIDADE_SUMARIZAR)
            return;
        }
        var mensagemErro = getMensagemErro(err);
        var ehPraTentarSumarizarNovamente = verificarRegrasComerciais(mensagemErro, pedido);
        if(ehPraTentarSumarizarNovamente){
            return sumarizar(pedido)
        }
        log('PD77_FINALIZANDO_CATCH_SUMARIZAR')
    } finally {


        if(var_ehParceiroNovo|| var_ehParceiroInativo){

            log("Eh cliente novo? " + var_ehParceiroNovo);
            log("O cliente estava inativo? " + var_ehParceiroInativo);
      
            var parceiro = encontrarRegistroParam('Parceiro', "CODPARC = ?", [var_CODPARC]);
            parceiro.setCampo("ATIVO", "N");
            parceiro.save();
        }



        if(pedido != null){
            pedido.setCampo("AD_STATUSOL", "PENDENTE");
            pedido.save();
        }
        if(!totalizou){
          log('PD77_FINALIZANDO_TOTALIZAR_FINNALY')
           if (var_NUNOTA) {
             totalizar(var_NUNOTA)
           }
          
        }
        /*Se chegou até aqui, eh sucesso, pois o pedido ja esta dentro do sankhya.*/
        var_SUCESSO = true;
    }
    log('PD77_FINALIZANDO_SUMARIZAR')
}


function confirmarnf(nuNota){
    totalizar(nuNota)
    confirmarNota(nuNota);
}

function totalizar(nuNota){
    var impostosHelpper = newJava('br.com.sankhya.modelcore.comercial.impostos.ImpostosHelpper');
            impostosHelpper.totalizarNota(nuNota);
            impostosHelpper.salvarNota()
}

// ### INÍCIO DE IMPLEMENTAÇÕES DO CENTRALIZADOR ###

function pedidoJaExiste(pedidoOL, codIntegracao) {

    log('[INTEGRACAO OL]: Consultando se o pedido '+var_pedidoOL+'ja existe na base, gravaItemIntegracao()')
    var pedidoIntegracao = encontrarRegistroParam("AD_INTCABOL", "NUPEDOL = ? AND  CODPRJ = ?", [pedidoOL, codIntegracao]);
    if (pedidoIntegracao) {
        return true;
    }
    return false;
}

function gravaCabecalhoIntegracao(cabecalho) {
    log('CABECALHO PEDIDO EXISTE? ' + var_pedidoJaExisteNaBase);
    if(pedidoJaExiste(cabecalho.nroPedidoOL, cabecalho.codIntegracao)){
       log("[INTEGRACAO OL]: O Pedido OL "+ cabecalho.nroPedidoOL + " ja existe na base. gravaCabecalhoIntegracao() abortado.")
        var_pedidoJaExisteNaBase = true;
        /*var_BREAK = true
        var_SUCESSO = false*/
        return;
    }

    try {
        log('[INTEGRACAO OL]: Registrando Cabeçalho do pedido '+cabecalho.nroPedidoOL +', gravaCabecalhoIntegracao()')
        var_pedidoJaExisteNaBase = false;
        cabecalhoIntegracao = novaLinha("AD_INTCABOL");
        cabecalhoIntegracao.setCampo("NUPEDOL", cabecalho.nroPedidoOL );
        cabecalhoIntegracao.setCampo("CODPRJ", toBigDecimal(cabecalho.codIntegracao));
        cabecalhoIntegracao.setCampo("CNPJIND", cabecalho.cnpjIndustria);
        cabecalhoIntegracao.setCampo("CNPJCLI", cabecalho.cnpjCliente);
        cabecalhoIntegracao.setCampo("NFILE", SNK_NOMEARQUIVO);
        cabecalhoIntegracao.setCampo("COND", cabecalho.codCondicao.toString());
        cabecalhoIntegracao.setCampo("CODPRZ", cabecalho.codPrazo);
        cabecalhoIntegracao.setCampo("NUPEDCLI", cabecalho.nroPedidoCliente);
        cabecalhoIntegracao.setCampo("DHINCLUSAO", globalDateToTimestamp(new Date()));
        cabecalhoIntegracao.setCampo("STATUS", 'P');
        //cabecalhoIntegracao.setCampo("NUNOTA", var_pedidoOL);
        //cabecalhoIntegracao.setCampo("RETSKW", var_pedidoOL);
        //cabecalhoIntegracao.setCampo("CODRETSKW", var_pedidoOL);
        cabecalhoIntegracao.save();

        log('[INTEGRACAO OL]: Cabeçalho do pedido '+var_pedidoOL+' salvo, gravaCabecalhoIntegracao(): ')
    } catch (err) {
        log('[INTEGRACAO OL]: Erro na inserção do cabeçalho do pedido '+var_pedidoOL+', gravaCabecalhoIntegracao(): '+ err)
        var_SUCESSO = false;
    }
}

function gravaItemIntegracao(item) {
    
    if(var_pedidoJaExisteNaBase){
        log("[INTEGRACAO OL]: O Pedido OL "+ cabecalho.nroPedidoOL + " ja existe na base. gravaItemIntegracao() abortado.")
        return;
    }
    
    try {
        log('[INTEGRACAO OL]: Tentativa de insercao do item '+item.ean+', gravaItemIntegracao()')
        itemIntegracao = novaLinha("AD_INTITEOL");
        itemIntegracao.setCampo("NUPEDOL", item.nroPedidoOL);
        itemIntegracao.setCampo("CODPRJ", toBigDecimal(item.codIntegracao));
        itemIntegracao.setCampo("REFERENCIA", item.ean);
        itemIntegracao.setCampo("QTDPED",  item.quantidade);
        itemIntegracao.setCampo("DTPRO", item.dataProcessamento);
        itemIntegracao.setCampo("PRODDESC", toBigDecimal(item.percDesc));
        itemIntegracao.setCampo("SEQUENCIAARQUIVO", item.sequenciaarquivo == null ? toBigDecimal(0) : toBigDecimal(item.sequenciaarquivo));
        itemIntegracao.save();
        log('[INTEGRACAO OL]: Item '+item.ean+' inserido com sucesso, gravaItemIntegracao()')
    } catch (err) {
        log('[INTEGRACAO OL] Erro na inserção de item no pedido de integração '+item.ean+', gravaItemIntegracao(): '+ err)
        var_SUCESSO = false;
    }
}



function globalTo2CasasDecimais(numero){
    return parseFloat(numero).toFixed(2)
}

function globalTo2CasasDecimaisAndToBigDecimal(numero){
    var num = String(numero)
    var indexInteiro = num.length - 2
    var numInteiro = num.substring(0, indexInteiro)
    var numDecimal = num.substring(indexInteiro, num.length)
    return toBigDecimal(numInteiro+'.'+numDecimal)
}

function globalDDMMYYYYToDate(data){
    var aux = String(data)
    var dia = aux.substring(0, 2)
    var mes = aux.substring(2, 4)
    var ano = aux.substring(4, 8)
    return new Date(ano,mes - 1 ,dia, 0, 0, 0)
}

function globalYYYYMMDDToDate(data){
    var aux = String(data)
    var ano = aux.substring(0, 4)
    var mes = aux.substring(4, 6)
    var dia = aux.substring(6, 8)
    return new Date(ano,mes - 1 ,dia, 0, 0, 0)
}

function globalDateToTimestamp(data){
    if(data){
        return toTimeStampByTime(data.getTime())
    }
    return data
}

function atualizaNunotaIntegracao() {
    var atualizaCabecalhoIntegracao = encontrarRegistroParam("AD_INTCABOL", "NUPEDOL = ? AND CODPRJ = ?", [var_pedidoOL, var_CODINTEGRACAO]);
    atualizaCabecalhoIntegracao.setCampo("NUNOTA", var_NUNOTA);
    atualizaCabecalhoIntegracao.save();
}

/**
 * No JS nao ha como arredondar passando a precisao decimal.
 * A solucao abaixo aumenta duas casas, arredonda e depois volta as 2 casas para o ponto decimal.
 */
function arredondar(numero){
    return Math.round(numero * 100) / 100
}