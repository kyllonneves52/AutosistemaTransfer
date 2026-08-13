package com.kreysam.autosistematransfer;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.core.app.NotificationCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/* JADX INFO: loaded from: classes3.dex */
public class UssdAccessibilityService extends AccessibilityService {
    private static final String CANAL_ID = "autosistema_monitor";
    private static final long DEBOUNCE_MS = 800;
    private static final String KW_CANCELAR = "CANCELAR";
    private static final String KW_CRED_INTRODUZ_VALOR = "introduz valor";
    private static final String KW_CRED_MENU_PRINCIPAL = "super jackpot";
    private static final String KW_CRED_OFERTA_REJEITAR = "rejeitar";
    private static final String KW_CRED_PROMO_INTERSTICIAL = "couldnt guess the best offer";
    private static final String KW_CRED_SALDO_LABEL = "saldo:";
    private static final String KW_CRED_SUCESSO = "efectuada com sucesso";
    private static final String KW_CRED_TRANSFERIR = "transferir credito";
    private static final String KW_ENVIAR = "ENVIAR";
    private static final String KW_INSUF = "saldo insuficiente";
    private static final String KW_INTERNET = "servicos de internet";
    private static final String KW_LIMITE = "limite diario";
    private static final String KW_MMI = "codigo mmi invalido";
    private static final String KW_OK = "OK";
    private static final String KW_PROPRIO_NUMERO = "proprio numero";
    private static final String KW_PROPRIO_NUMERO_ALT = "nao estas permitido";
    private static final String KW_QUANTOS = "quantos megas";
    private static final String KW_RECIPIENTE = "numero do recipiente";
    private static final String KW_SALDO_DLG = "saldo de dados";
    private static final String KW_SUCESSO = "transferiste com sucesso";
    private static final String KW_TRANSFERIR = "transferir megas";
    private static final int NOTIF_ID_USSD = 7789;
    private static final long REDE_SEGURANCA_MS = 1500;
    private static final long RETRY_MMI_INVALIDO_MS = 5000;
    private static final long STALE_THRESHOLD_MS = 60000;
    private static final String TAG = "UssdAccessibility";
    private static final long TIMEOUT_TOTAL_MS = 45000;
    private static volatile long inicioOperacaoMs = 0;
    private static UssdAccessibilityService instancia;
    private TransferenciaCallback callbackAtual;
    private CreditoCallback creditoCallbackAtual;
    private String numeroAtual;
    private int quantidadeMBAtual;
    private Runnable timeoutRunnable;
    private String valorCreditoAtual;
    private PowerManager.WakeLock wakeLock;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private volatile Estado estado = Estado.IDLE;
    private volatile TipoOperacao tipoOperacaoAtual = TipoOperacao.MEGAS;
    private long lastEventMs = 0;
    private int numeroTentativa = 0;
    private int saldoInicialLido = -1;
    private boolean apenasConsulta = false;
    private volatile boolean ultimoPassoEnviado = false;

    public interface CreditoCallback {
        void onErro(String str);

        void onSaldoLido(double d);

        void onSucesso();
    }

    private enum Estado {
        IDLE,
        MENU_PRINCIPAL,
        SUBMENU,
        SALDO,
        NUMERO,
        RESULTADO,
        CREDITO_MENU,
        CREDITO_SUBMENU,
        CREDITO_VALOR_NUMERO,
        CREDITO_RESULTADO,
        CREDITO_CONSULTA_SALDO
    }

    private enum TipoOperacao {
        MEGAS,
        CREDITO
    }

    public interface TransferenciaCallback {
        void onErro(String str);

        void onLimiteDiarioAtingido();

        void onSaldoInsuficiente(int i);

        void onSaldoLido(int i);

        void onSucesso(int i);
    }

    @Override // android.accessibilityservice.AccessibilityService
    protected void onServiceConnected() {
        super.onServiceConnected();
        instancia = this;
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) {
            info = new AccessibilityServiceInfo();
        }
        info.eventTypes = 2080;
        info.feedbackType = 16;
        info.flags = 65;
        info.notificationTimeout = 200L;
        setServiceInfo(info);
        Log.i(TAG, "Serviço de acessibilidade pronto.");
    }

    private void promoverParaForeground(String mensagem) {
        try {
            PowerManager pm = (PowerManager) getSystemService("power");
            if (pm != null) {
                PowerManager.WakeLock wakeLock = this.wakeLock;
                if (wakeLock != null && wakeLock.isHeld()) {
                    this.wakeLock.release();
                }
                PowerManager.WakeLock wakeLockNewWakeLock = pm.newWakeLock(1, "Autosistema:UssdWakeLock");
                this.wakeLock = wakeLockNewWakeLock;
                wakeLockNewWakeLock.acquire(STALE_THRESHOLD_MS);
            }
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel canal = new NotificationChannel(CANAL_ID, "Monitor Autosistema", 4);
                canal.setShowBadge(false);
                NotificationManager nm = (NotificationManager) getSystemService(NotificationManager.class);
                if (nm != null) {
                    nm.createNotificationChannel(canal);
                }
            }
            Notification notif = new NotificationCompat.Builder(this, CANAL_ID).setContentTitle("Autosistema Transfer").setContentText(mensagem).setSmallIcon(android.R.drawable.stat_sys_download_done).setOngoing(true).setPriority(2).build();
            startForeground(NOTIF_ID_USSD, notif);
        } catch (Exception e) {
            Log.w(TAG, "promoverParaForeground falhou: " + e.getMessage());
        }
    }

    private void voltarAoBackground() {
        try {
            PowerManager.WakeLock wakeLock = this.wakeLock;
            if (wakeLock != null && wakeLock.isHeld()) {
                this.wakeLock.release();
                this.wakeLock = null;
            }
            stopForeground(true);
        } catch (Exception e) {
            Log.w(TAG, "voltarAoBackground falhou: " + e.getMessage());
        }
    }

    public static boolean estaAtivo() {
        return instancia != null;
    }

    public static void iniciarProcessamento(int quantidadeMB, String numero, TransferenciaCallback callback) {
        UssdAccessibilityService ussdAccessibilityService = instancia;
        if (ussdAccessibilityService == null) {
            callback.onErro("Serviço de acessibilidade não está ativo. Ativa em Definições → Acessibilidade.");
        } else {
            ussdAccessibilityService.iniciarMegas(quantidadeMB, numero, callback, false);
        }
    }

    public static void iniciarTransferenciaComSaldoReal(String numero, TransferenciaCallback callback) {
        UssdAccessibilityService ussdAccessibilityService = instancia;
        if (ussdAccessibilityService == null) {
            callback.onErro("Serviço de acessibilidade não está ativo. Ativa em Definições → Acessibilidade.");
        } else {
            ussdAccessibilityService.iniciarMegas(-1, numero, callback, false);
        }
    }

    public static void iniciarConsultaSaldo(TransferenciaCallback callback) {
        UssdAccessibilityService ussdAccessibilityService = instancia;
        if (ussdAccessibilityService == null) {
            callback.onErro("Serviço de acessibilidade não está ativo. Ativa em Definições → Acessibilidade.");
        } else {
            ussdAccessibilityService.iniciarMegas(0, "", callback, true);
        }
    }

    public static void iniciarTransferenciaCredito(String valorMT, String numero, CreditoCallback callback) {
        UssdAccessibilityService ussdAccessibilityService = instancia;
        if (ussdAccessibilityService == null) {
            callback.onErro("Serviço de acessibilidade não está ativo. Ativa em Definições → Acessibilidade.");
        } else {
            ussdAccessibilityService.iniciarCredito(valorMT, numero, callback, false);
        }
    }

    public static void iniciarConsultaSaldoCredito(CreditoCallback callback) {
        UssdAccessibilityService ussdAccessibilityService = instancia;
        if (ussdAccessibilityService == null) {
            callback.onErro("Serviço de acessibilidade não está ativo. Ativa em Definições → Acessibilidade.");
        } else {
            ussdAccessibilityService.iniciarCredito(null, null, callback, true);
        }
    }

    private void agendarPollingContinuo() {
        this.handler.postDelayed(new Runnable() { // from class: com.kreysam.autosistematransfer.UssdAccessibilityService$$ExternalSyntheticLambda7
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.m47xc1758a67();
            }
        }, 2000L);
    }

    /* JADX INFO: renamed from: lambda$agendarPollingContinuo$0$com-kreysam-autosistematransfer-UssdAccessibilityService, reason: not valid java name */
    /* synthetic */ void m47xc1758a67() {
        if (this.estado != Estado.IDLE) {
            Log.d(TAG, "Polling continuo -- relendo ecra por garantia (estado=" + this.estado + ")");
            processarEcraAtual();
            agendarPollingContinuo();
        }
    }

    private void cancelarOperacaoEmCurso() {
        if (this.estado == Estado.IDLE) {
            return;
        }
        Log.w(TAG, "Nova operacao pedida -- cancelando a anterior (estado=" + this.estado + ") e comecando ja.");
        cancelarTimeout();
        TransferenciaCallback cbMegasAntigo = this.callbackAtual;
        CreditoCallback cbCreditoAntigo = this.creditoCallbackAtual;
        this.estado = Estado.IDLE;
        this.callbackAtual = null;
        this.creditoCallbackAtual = null;
        if (cbMegasAntigo != null) {
            cbMegasAntigo.onErro("Operacao cancelada -- uma nova foi iniciada por cima.");
        }
        if (cbCreditoAntigo != null) {
            cbCreditoAntigo.onErro("Operacao cancelada -- uma nova foi iniciada por cima.");
        }
    }

    private void iniciarMegas(int quantidadeMB, String numero, TransferenciaCallback callback, boolean somenteConsulta) {
        cancelarOperacaoEmCurso();
        this.tipoOperacaoAtual = TipoOperacao.MEGAS;
        this.quantidadeMBAtual = quantidadeMB;
        this.numeroAtual = numero;
        this.callbackAtual = callback;
        this.saldoInicialLido = -1;
        this.apenasConsulta = somenteConsulta;
        this.ultimoPassoEnviado = false;
        this.estado = Estado.MENU_PRINCIPAL;
        inicioOperacaoMs = System.currentTimeMillis();
        this.numeroTentativa++;
        AppLog.add(this, TAG, "INICIAR[MEGAS] tentativa=" + this.numeroTentativa + " consulta=" + somenteConsulta + " mb=" + quantidadeMB + " numero=" + numero);
        promoverParaForeground(somenteConsulta ? "A consultar saldo..." : "A transferir " + quantidadeMB + "MB...");
        Runnable runnable = new Runnable() { // from class: com.kreysam.autosistematransfer.UssdAccessibilityService$$ExternalSyntheticLambda6
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.m49x7ab1170e();
            }
        };
        this.timeoutRunnable = runnable;
        this.handler.postDelayed(runnable, TIMEOUT_TOTAL_MS);
        agendarPollingContinuo();
    }

    /* JADX INFO: renamed from: lambda$iniciarMegas$1$com-kreysam-autosistematransfer-UssdAccessibilityService, reason: not valid java name */
    /* synthetic */ void m49x7ab1170e() {
        finalizarComErro("Timeout — a rede/USSD pode estar instável. Tenta novamente.");
    }

    private void iniciarCredito(String valorMT, String numero, CreditoCallback callback, boolean somenteConsultaSaldo) {
        cancelarOperacaoEmCurso();
        this.tipoOperacaoAtual = TipoOperacao.CREDITO;
        this.valorCreditoAtual = valorMT;
        this.numeroAtual = numero;
        this.creditoCallbackAtual = callback;
        this.ultimoPassoEnviado = false;
        this.estado = somenteConsultaSaldo ? Estado.CREDITO_CONSULTA_SALDO : Estado.CREDITO_MENU;
        inicioOperacaoMs = System.currentTimeMillis();
        this.numeroTentativa++;
        AppLog.add(this, TAG, "INICIAR[CREDITO] tentativa=" + this.numeroTentativa + " consultaSaldo=" + somenteConsultaSaldo + " valor=" + valorMT + " numero=" + numero);
        promoverParaForeground(somenteConsultaSaldo ? "A consultar saldo crédito..." : "A transferir crédito " + valorMT + "MT...");
        Runnable runnable = new Runnable() { // from class: com.kreysam.autosistematransfer.UssdAccessibilityService$$ExternalSyntheticLambda3
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.m48xa441e338();
            }
        };
        this.timeoutRunnable = runnable;
        this.handler.postDelayed(runnable, TIMEOUT_TOTAL_MS);
        agendarPollingContinuo();
    }

    /* JADX INFO: renamed from: lambda$iniciarCredito$2$com-kreysam-autosistematransfer-UssdAccessibilityService, reason: not valid java name */
    /* synthetic */ void m48xa441e338() {
        finalizarComErro("Timeout — a rede/USSD pode estar instável. Tenta novamente.");
    }

    @Override // android.accessibilityservice.AccessibilityService
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (this.estado == Estado.IDLE) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - this.lastEventMs < DEBOUNCE_MS) {
            return;
        }
        this.lastEventMs = now;
        this.handler.postDelayed(new Runnable() { // from class: com.kreysam.autosistematransfer.UssdAccessibilityService$$ExternalSyntheticLambda8
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.processarEcraAtual();
            }
        }, 500L);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void processarEcraAtual() {
        AccessibilityNodeInfo root;
        if (this.estado == Estado.IDLE || (root = getRootInActiveWindow()) == null) {
            return;
        }
        try {
            String textoOriginal = coletarTexto(root);
            if (textoOriginal.trim().isEmpty()) {
                return;
            }
            String norm = normalizar(textoOriginal);
            if (getPackageName().equals(root.getPackageName()) && !pareceRespostaOperadora(norm)) {
                AppLog.add(this, TAG, "tentativa=" + this.numeroTentativa + " ignorado -- janela da propria app, nao e o dialogo USSD.");
                return;
            }
            if (pareceTelaPropriaApp(norm)) {
                AppLog.add(this, TAG, "tentativa=" + this.numeroTentativa + " ignorado -- texto parece ser da WebView da propria app.");
                return;
            }
            AppLog.add(this, TAG, "tentativa=" + this.numeroTentativa + " tipo=" + this.tipoOperacaoAtual + " estado=" + this.estado + " | texto=" + norm.replace("\n", " | "));
            if (norm.contains(KW_CRED_PROMO_INTERSTICIAL)) {
                clicarOK(root);
                AppLog.add(this, TAG, "tentativa=" + this.numeroTentativa + " interstitial promocional (*109#) descartado -- a aguardar o ecra real, sem abortar.");
                return;
            }
            if (!norm.contains(KW_MMI) && !norm.contains("codigo invalido")) {
                if (this.tipoOperacaoAtual == TipoOperacao.CREDITO) {
                    processarEcraCredito(root, textoOriginal, norm);
                } else {
                    processarEcraMegas(root, textoOriginal, norm);
                }
                return;
            }
            clicarOK(root);
            Log.w(TAG, "Codigo MMI invalido -- cancelando e limpando a operacao atual.");
            finalizarComErro("MMI_INVALIDO");
        } finally {
            root.recycle();
        }
    }

    private boolean pareceRespostaOperadora(String norm) {
        return norm.contains(KW_MMI) || norm.contains("codigo invalido") || norm.contains(KW_SUCESSO) || norm.contains(KW_LIMITE) || norm.contains(KW_INSUF) || norm.contains(KW_PROPRIO_NUMERO) || norm.contains(KW_PROPRIO_NUMERO_ALT) || norm.contains(KW_INTERNET) || norm.contains(KW_TRANSFERIR) || norm.contains(KW_SALDO_DLG) || norm.contains(KW_QUANTOS) || norm.contains(KW_RECIPIENTE) || norm.contains(KW_CRED_MENU_PRINCIPAL) || norm.contains(KW_CRED_TRANSFERIR) || norm.contains(KW_CRED_INTRODUZ_VALOR) || norm.contains(KW_CRED_SUCESSO) || norm.contains(KW_CRED_SALDO_LABEL) || norm.contains(KW_CRED_PROMO_INTERSTICIAL);
    }

    private boolean pareceTelaPropriaApp(String norm) {
        return norm.contains("autosistema transfer") || norm.contains("automacao de transferencia") || norm.contains("saldo e transferencias") || norm.contains("toca pra consultar") || norm.contains("restantes hoje") || norm.contains("resetar / n") || norm.contains("link do painel") || norm.contains("token do painel") || norm.contains("log detalhado");
    }

    private void processarEcraMegas(AccessibilityNodeInfo root, String textoOriginal, String norm) {
        int saldoRestante;
        if (norm.contains(KW_SUCESSO)) {
            if (!numeroConfereNaMensagem(textoOriginal)) {
                Log.w(TAG, "Mensagem de sucesso encontrada mas o numero NAO confere com numeroAtual (" + this.numeroAtual + ") -- ignorando este ecra.");
                AppLog.add(this, TAG, "tentativa=" + this.numeroTentativa + " AVISO: mensagem de sucesso ignorada -- numero na tela nao confere com " + this.numeroAtual);
            }
            int i = this.saldoInicialLido;
            if (i >= 0) {
                saldoRestante = i - this.quantidadeMBAtual;
                Log.i(TAG, "Saldo restante calculado: " + this.saldoInicialLido + " - " + this.quantidadeMBAtual + " = " + saldoRestante);
            } else {
                saldoRestante = parseSaldoMegas(textoOriginal);
                Log.w(TAG, "Saldo inicial não disponível — usando parse do ecrã final: " + saldoRestante);
            }
            clicarOK(root);
            finalizarComSucesso(saldoRestante);
            return;
        }
        if (norm.contains(KW_LIMITE)) {
            clicarOK(root);
            finalizarComLimiteDiario();
            return;
        }
        if (norm.contains(KW_INSUF)) {
            clicarOK(root);
            finalizarComSaldoInsuficiente(-1);
            return;
        }
        if (norm.contains(KW_PROPRIO_NUMERO) || norm.contains(KW_PROPRIO_NUMERO_ALT)) {
            clicarOK(root);
            Log.w(TAG, "Operadora recusou -- nao podes transferir para o proprio numero deste SIM.");
            finalizarComErro("PROPRIO_NUMERO");
            return;
        }
        switch (AnonymousClass1.$SwitchMap$com$kreysam$autosistematransfer$UssdAccessibilityService$Estado[this.estado.ordinal()]) {
            case 1:
                if (norm.contains(KW_INTERNET) && responder(root, "8")) {
                    this.estado = Estado.SUBMENU;
                    break;
                }
                break;
            case 2:
                if (norm.contains(KW_TRANSFERIR) && responder(root, "2")) {
                    this.estado = Estado.SALDO;
                    break;
                }
                break;
            case 3:
                if (norm.contains(KW_SALDO_DLG) || norm.contains(KW_QUANTOS)) {
                    int saldo = parseSaldoMegas(textoOriginal);
                    if (saldo >= 0) {
                        this.saldoInicialLido = saldo;
                        TransferenciaCallback transferenciaCallback = this.callbackAtual;
                        if (transferenciaCallback != null) {
                            transferenciaCallback.onSaldoLido(saldo);
                        }
                    }
                    if (this.apenasConsulta) {
                        clicarCancelar(root);
                        finalizarComSucesso(saldo);
                    } else {
                        if (this.quantidadeMBAtual == -1) {
                            if (saldo < 0) {
                                clicarCancelar(root);
                                finalizarComErro("Nao foi possivel ler o saldo do ecra.");
                            } else if (saldo < 100) {
                                clicarCancelar(root);
                                finalizarComSaldoInsuficiente(saldo);
                            } else {
                                this.quantidadeMBAtual = Math.min(saldo, UssdTransferManager.MAX_MB_TRANSFERENCIA);
                                AppLog.add(this, TAG, "Modo saldo real: saldo=" + saldo + "MB, vai transferir=" + this.quantidadeMBAtual + "MB");
                            }
                        }
                        if (saldo >= 0 && saldo < this.quantidadeMBAtual) {
                            Log.w(TAG, "Saldo insuficiente antes de enviar: " + saldo + " MB");
                            clicarCancelar(root);
                            finalizarComSaldoInsuficiente(saldo);
                        } else if (responder(root, String.valueOf(this.quantidadeMBAtual))) {
                            this.estado = Estado.NUMERO;
                        }
                    }
                }
                break;
            case 4:
                if (norm.contains(KW_RECIPIENTE) && responder(root, this.numeroAtual)) {
                    this.estado = Estado.RESULTADO;
                    this.ultimoPassoEnviado = true;
                    break;
                }
                break;
        }
    }

    private void processarEcraCredito(AccessibilityNodeInfo root, String textoOriginal, String norm) {
        CreditoCallback creditoCallback;
        if (norm.contains(KW_CRED_SUCESSO)) {
            if (!numeroConfereNaMensagem(textoOriginal)) {
                Log.w(TAG, "[CREDITO] Mensagem de sucesso encontrada mas o numero NAO confere com numeroAtual (" + this.numeroAtual + ") -- ignorando este ecra.");
                AppLog.add(this, TAG, "tentativa=" + this.numeroTentativa + " [CREDITO] AVISO: mensagem de sucesso ignorada -- numero na tela nao confere com " + this.numeroAtual);
            } else {
                clicarOK(root);
                finalizarComSucessoCredito();
                return;
            }
        }
        if (norm.contains(KW_PROPRIO_NUMERO) || norm.contains(KW_PROPRIO_NUMERO_ALT)) {
            clicarOK(root);
            Log.w(TAG, "[CREDITO] Operadora recusou -- nao podes transferir para o proprio numero deste SIM.");
            finalizarComErro("PROPRIO_NUMERO");
            return;
        }
        switch (this.estado) {
            case CREDITO_MENU:
                if (norm.contains(KW_CRED_MENU_PRINCIPAL) && responder(root, "11")) {
                    this.estado = Estado.CREDITO_SUBMENU;
                    break;
                }
                break;
            case CREDITO_SUBMENU:
                if (norm.contains(KW_CRED_TRANSFERIR) && responder(root, "4")) {
                    this.estado = Estado.CREDITO_VALOR_NUMERO;
                    break;
                }
                break;
            case CREDITO_VALOR_NUMERO:
                if (norm.contains(KW_CRED_INTRODUZ_VALOR)) {
                    String resposta = this.valorCreditoAtual + " " + this.numeroAtual;
                    if (responder(root, resposta)) {
                        this.estado = Estado.CREDITO_RESULTADO;
                        this.ultimoPassoEnviado = true;
                    }
                }
                break;
            case CREDITO_CONSULTA_SALDO:
                if (norm.contains(KW_CRED_SALDO_LABEL)) {
                    double saldo = parseSaldoCredito(textoOriginal);
                    if (norm.contains(KW_CRED_OFERTA_REJEITAR)) {
                        Log.i(TAG, "[CREDITO] Oferta promocional detectada ao consultar saldo -- a cancelar diretamente (sem digitar).");
                        CreditoCallback creditoCallback2 = this.creditoCallbackAtual;
                        if (creditoCallback2 != null && saldo >= 0.0d) {
                            creditoCallback2.onSaldoLido(saldo);
                        }
                        clicarCancelar(root);
                        finalizarComSucessoCredito();
                    } else {
                        if (saldo >= 0.0d && (creditoCallback = this.creditoCallbackAtual) != null) {
                            creditoCallback.onSaldoLido(saldo);
                        }
                        clicarCancelar(root);
                        finalizarComSucessoCredito();
                    }
                }
                break;
        }
    }

    private boolean responder(AccessibilityNodeInfo root, String texto) {
        return tentarResponder(texto, 0);
    }

    private boolean tentarResponder(final String texto, final int tentativa) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            if (tentativa >= 3) {
                return false;
            }
            this.handler.postDelayed(new Runnable() { // from class: com.kreysam.autosistematransfer.UssdAccessibilityService$$ExternalSyntheticLambda0
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.m50x2a331d32(texto, tentativa);
                }
            }, 300L);
            return true;
        }
        try {
            AccessibilityNodeInfo campo = encontrarPorId(root, "android:id/edit");
            if (campo == null) {
                campo = root.findFocus(1);
            }
            if (campo != null) {
                Bundle args = new Bundle();
                args.putCharSequence(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, texto);
                campo.performAction(2097152, args);
                this.handler.postDelayed(new Runnable() { // from class: com.kreysam.autosistematransfer.UssdAccessibilityService$$ExternalSyntheticLambda2
                    @Override // java.lang.Runnable
                    public final void run() {
                        this.f$0.m53xd0fa628f();
                    }
                }, 500L);
                return true;
            }
            if (tentativa >= 3) {
                Log.w(TAG, "Campo de texto nao encontrado apos " + tentativa + " tentativas -- desisti.");
                return false;
            }
            Log.w(TAG, "Campo de texto nao encontrado, tentativa " + tentativa + " -- repetindo em 300ms.");
            this.handler.postDelayed(new Runnable() { // from class: com.kreysam.autosistematransfer.UssdAccessibilityService$$ExternalSyntheticLambda1
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.m51xb7203451(texto, tentativa);
                }
            }, 300L);
            return true;
        } finally {
            root.recycle();
        }
    }

    /* JADX INFO: renamed from: lambda$tentarResponder$3$com-kreysam-autosistematransfer-UssdAccessibilityService, reason: not valid java name */
    /* synthetic */ void m50x2a331d32(String texto, int tentativa) {
        tentarResponder(texto, tentativa + 1);
    }

    /* JADX INFO: renamed from: lambda$tentarResponder$4$com-kreysam-autosistematransfer-UssdAccessibilityService, reason: not valid java name */
    /* synthetic */ void m51xb7203451(String texto, int tentativa) {
        tentarResponder(texto, tentativa + 1);
    }

    /* JADX INFO: renamed from: lambda$tentarResponder$6$com-kreysam-autosistematransfer-UssdAccessibilityService, reason: not valid java name */
    /* synthetic */ void m53xd0fa628f() {
        AccessibilityNodeInfo fresh = getRootInActiveWindow();
        if (fresh != null) {
            try {
                clicarEnviar(fresh);
            } finally {
                fresh.recycle();
            }
        }
        this.handler.postDelayed(new Runnable() { // from class: com.kreysam.autosistematransfer.UssdAccessibilityService$$ExternalSyntheticLambda5
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.m52x440d4b70();
            }
        }, REDE_SEGURANCA_MS);
    }

    /* JADX INFO: renamed from: lambda$tentarResponder$5$com-kreysam-autosistematransfer-UssdAccessibilityService, reason: not valid java name */
    /* synthetic */ void m52x440d4b70() {
        if (this.estado != Estado.IDLE) {
            Log.d(TAG, "Rede de seguranca: relendo ecra manualmente (estado=" + this.estado + ")");
            processarEcraAtual();
        }
    }

    private void clicarEnviar(AccessibilityNodeInfo root) {
        if (clicarPorTexto(root, KW_ENVIAR) || clicarPorId(root, "android:id/button2")) {
            return;
        }
        clicarPorId(root, "android:id/button1");
    }

    private void clicarCancelar(AccessibilityNodeInfo root) {
        if (clicarPorTexto(root, KW_CANCELAR) || clicarPorId(root, "android:id/button1")) {
            return;
        }
        clicarPorId(root, "android:id/button2");
    }

    private void clicarOK(AccessibilityNodeInfo root) {
        if (clicarPorId(root, "android:id/button1")) {
            return;
        }
        clicarPorTexto(root, KW_OK);
    }

    private boolean clicarPorTexto(AccessibilityNodeInfo root, String texto) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(texto);
        if (nodes == null) {
            return false;
        }
        for (AccessibilityNodeInfo n : nodes) {
            if (n.isClickable()) {
                n.performAction(16);
                return true;
            }
            AccessibilityNodeInfo pai = n.getParent();
            if (pai != null && pai.isClickable()) {
                pai.performAction(16);
                return true;
            }
        }
        return false;
    }

    private boolean clicarPorId(AccessibilityNodeInfo root, String id) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
        if (nodes == null || nodes.isEmpty()) {
            return false;
        }
        for (AccessibilityNodeInfo n : nodes) {
            if (n.isClickable() || n.isEnabled()) {
                n.performAction(16);
                return true;
            }
        }
        return false;
    }

    private AccessibilityNodeInfo encontrarPorId(AccessibilityNodeInfo root, String id) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
        if (nodes == null || nodes.isEmpty()) {
            return null;
        }
        return nodes.get(0);
    }

    private String coletarTexto(AccessibilityNodeInfo node) {
        StringBuilder sb = new StringBuilder();
        coletarTextoRec(node, sb);
        return sb.toString();
    }

    private void coletarTextoRec(AccessibilityNodeInfo node, StringBuilder sb) {
        if (node == null) {
            return;
        }
        if (node.getText() != null) {
            sb.append(node.getText()).append("\n");
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo filho = node.getChild(i);
            if (filho != null) {
                coletarTextoRec(filho, sb);
                filho.recycle();
            }
        }
    }

    private String normalizar(String s) {
        return s == null ? "" : s.toLowerCase().replace((char) 231, 'c').replace((char) 227, 'a').replace((char) 226, 'a').replace((char) 225, 'a').replace((char) 224, 'a').replace((char) 234, 'e').replace((char) 233, 'e').replace((char) 237, 'i').replace((char) 243, 'o').replace((char) 244, 'o').replace((char) 245, 'o').replace((char) 250, 'u').replace((char) 252, 'u');
    }

    private boolean numeroConfereNaMensagem(String texto) {
        String str = this.numeroAtual;
        if (str == null || str.isEmpty() || texto == null) {
            return true;
        }
        Matcher m = Pattern.compile("(\\d{9,12})").matcher(texto);
        boolean encontrouAlgumNumero = false;
        while (m.find()) {
            encontrouAlgumNumero = true;
            String candidato = m.group(1);
            if (candidato.length() == 12 && candidato.startsWith("258")) {
                candidato = candidato.substring(3);
            }
            if (candidato.equals(this.numeroAtual)) {
                return true;
            }
        }
        return !encontrouAlgumNumero;
    }

    private int parseSaldoMegas(String texto) {
        int v;
        if (texto == null) {
            return -1;
        }
        for (String linha : texto.split("\n")) {
            String low = linha.toLowerCase();
            if (low.contains("mb")) {
                for (String p : low.replaceAll("[^0-9 ]", " ").trim().split("\\s+")) {
                    try {
                        v = Integer.parseInt(p);
                    } catch (Exception e) {
                    }
                    if (v >= 0) {
                        return v;
                    }
                }
            }
        }
        return -1;
    }

    private double parseSaldoCredito(String texto) {
        if (texto == null) {
            return -1.0d;
        }
        Matcher m = Pattern.compile("saldo\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)", 2).matcher(texto);
        if (m.find()) {
            try {
                return Double.parseDouble(m.group(1));
            } catch (Exception e) {
            }
        }
        return -1.0d;
    }

    private void finalizarComSucesso(int saldoRestante) {
        AppLog.add(this, TAG, "tentativa=" + this.numeroTentativa + " FIM -> SUCESSO saldoRestante=" + saldoRestante);
        cancelarTimeout();
        voltarAoBackground();
        this.estado = Estado.IDLE;
        TransferenciaCallback cb = this.callbackAtual;
        this.callbackAtual = null;
        if (cb != null) {
            cb.onSucesso(saldoRestante);
        }
    }

    private void finalizarComSaldoInsuficiente(int saldoAtual) {
        AppLog.add(this, TAG, "tentativa=" + this.numeroTentativa + " FIM -> SALDO_INSUFICIENTE saldoAtual=" + saldoAtual);
        cancelarTimeout();
        voltarAoBackground();
        this.estado = Estado.IDLE;
        TransferenciaCallback cb = this.callbackAtual;
        this.callbackAtual = null;
        if (cb != null) {
            cb.onSaldoInsuficiente(saldoAtual);
        }
    }

    private void finalizarComLimiteDiario() {
        AppLog.add(this, TAG, "tentativa=" + this.numeroTentativa + " FIM -> LIMITE_DIARIO");
        cancelarTimeout();
        voltarAoBackground();
        this.estado = Estado.IDLE;
        TransferenciaCallback cb = this.callbackAtual;
        this.callbackAtual = null;
        if (cb != null) {
            cb.onLimiteDiarioAtingido();
        }
    }

    private void finalizarComSucessoCredito() {
        AppLog.add(this, TAG, "tentativa=" + this.numeroTentativa + " [CREDITO] FIM -> SUCESSO");
        cancelarTimeout();
        voltarAoBackground();
        this.estado = Estado.IDLE;
        CreditoCallback cb = this.creditoCallbackAtual;
        this.creditoCallbackAtual = null;
        if (cb != null) {
            cb.onSucesso();
        }
    }

    private void finalizarComErro(String motivo) {
        String motivoFinal = motivo;
        if (this.ultimoPassoEnviado) {
            if ("MMI_INVALIDO".equals(motivo)) {
                motivoFinal = "MMI_INVALIDO_POS_NUMERO";
            } else if (!"PROPRIO_NUMERO".equals(motivo)) {
                motivoFinal = "ERRO_POS_NUMERO:" + motivo;
            }
        }
        AppLog.add(this, TAG, "tentativa=" + this.numeroTentativa + " FIM -> ERRO motivo=" + motivoFinal + " (ultimoPassoEnviado=" + this.ultimoPassoEnviado + ")");
        cancelarTimeout();
        voltarAoBackground();
        this.estado = Estado.IDLE;
        TransferenciaCallback cbMegas = this.callbackAtual;
        CreditoCallback cbCredito = this.creditoCallbackAtual;
        this.callbackAtual = null;
        this.creditoCallbackAtual = null;
        if (cbMegas != null) {
            cbMegas.onErro(motivoFinal);
        }
        if (cbCredito != null) {
            cbCredito.onErro(motivoFinal);
        }
    }

    private void cancelarTimeout() {
        Runnable runnable = this.timeoutRunnable;
        if (runnable != null) {
            this.handler.removeCallbacks(runnable);
        }
    }

    @Override // android.accessibilityservice.AccessibilityService
    public void onInterrupt() {
        Log.w(TAG, "Serviço de acessibilidade interrompido.");
    }

    @Override // android.app.Service
    public boolean onUnbind(Intent intent) {
        Log.w(TAG, "Servico de acessibilidade a ser desativado -- limpando qualquer operacao em curso.");
        if (this.estado != Estado.IDLE) {
            TransferenciaCallback cbMegas = this.callbackAtual;
            CreditoCallback cbCredito = this.creditoCallbackAtual;
            this.estado = Estado.IDLE;
            this.callbackAtual = null;
            this.creditoCallbackAtual = null;
            cancelarTimeout();
            voltarAoBackground();
            this.handler.postDelayed(new Runnable() { // from class: com.kreysam.autosistematransfer.UssdAccessibilityService$$ExternalSyntheticLambda4
                @Override // java.lang.Runnable
                public final void run() {
                    UssdAccessibilityService.lambda$onUnbind$7();
                }
            }, 8000L);
            if (cbMegas != null) {
                cbMegas.onErro("Servico de acessibilidade foi desativado a meio da operacao.");
            }
            if (cbCredito != null) {
                cbCredito.onErro("Servico de acessibilidade foi desativado a meio da operacao.");
            }
        }
        return super.onUnbind(intent);
    }

    static /* synthetic */ void lambda$onUnbind$7() {
        UssdAccessibilityService ussdAccessibilityService = instancia;
        if (ussdAccessibilityService != null) {
            AppLog.add(ussdAccessibilityService, TAG, "Servico de acessibilidade restablecido -- operacao pode ser retentada.");
        }
    }

    @Override // android.app.Service
    public void onDestroy() {
        super.onDestroy();
        voltarAoBackground();
        instancia = null;
        Log.w(TAG, "Servico de acessibilidade destruido pelo sistema.");
    }
}
