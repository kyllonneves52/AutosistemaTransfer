package com.kreysam.autosistematransfer;

import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import com.kreysam.autosistematransfer.ApiClient;
import java.util.Objects;
import org.json.JSONObject;

/* JADX INFO: loaded from: classes3.dex */
public class WebAppInterface {
    private final MainActivity activity;

    public WebAppInterface(MainActivity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public String getUrlPainel() {
        return Prefs.getUrlPainel(this.activity);
    }

    @JavascriptInterface
    public void saveUrlPainel(String url) {
        Prefs.setUrlPainel(this.activity, url);
    }

    @JavascriptInterface
    public String getAndroidId() {
        return ApiClient.obterAndroidId(this.activity);
    }

    @JavascriptInterface
    public String getStatusDispositivo() {
        return Prefs.getStatusDispositivo(this.activity);
    }

    static /* synthetic */ void lambda$registarDispositivo$0(boolean ok, String msg) {
    }

    @JavascriptInterface
    public void registarDispositivo() {
        ApiClient.registarDispositivo(this.activity, new ApiClient.Callback() { // from class: com.kreysam.autosistematransfer.WebAppInterface$$ExternalSyntheticLambda1
            @Override // com.kreysam.autosistematransfer.ApiClient.Callback
            public final void onResultado(boolean z, String str) {
                WebAppInterface.lambda$registarDispositivo$0(z, str);
            }
        });
    }

    static /* synthetic */ void lambda$verificarStatusDispositivo$1(boolean ok, String msg) {
    }

    @JavascriptInterface
    public void verificarStatusDispositivo() {
        ApiClient.verificarStatusDispositivo(this.activity, new ApiClient.Callback() { // from class: com.kreysam.autosistematransfer.WebAppInterface$$ExternalSyntheticLambda10
            @Override // com.kreysam.autosistematransfer.ApiClient.Callback
            public final void onResultado(boolean z, String str) {
                WebAppInterface.lambda$verificarStatusDispositivo$1(z, str);
            }
        });
    }

    @JavascriptInterface
    public boolean getComunicacaoAtiva() {
        return Prefs.getComunicacaoAtiva(this.activity);
    }

    @JavascriptInterface
    public void setComunicacaoAtiva(boolean ativa) {
        Prefs.setComunicacaoAtiva(this.activity, ativa);
        ApiClient.enviarHeartbeat(this.activity);
    }

    @JavascriptInterface
    public int getSimCreditoDisponivel() {
        return Prefs.getSimCreditoDisponivel(this.activity);
    }

    @JavascriptInterface
    public void setSimCreditoDisponivel(int simOuZero) {
        Prefs.setSimCreditoDisponivel(this.activity, simOuZero);
        ApiClient.enviarHeartbeat(this.activity);
    }

    @JavascriptInterface
    public boolean acessibilidadeAtiva() {
        return UssdAccessibilityService.estaAtivo();
    }

    @JavascriptInterface
    public void abrirConfigAcessibilidade() {
        final MainActivity mainActivity = this.activity;
        Objects.requireNonNull(mainActivity);
        mainActivity.runOnUiThread(new Runnable() { // from class: com.kreysam.autosistematransfer.WebAppInterface$$ExternalSyntheticLambda2
            @Override // java.lang.Runnable
            public final void run() {
                mainActivity.abrirConfigAcessibilidade();
            }
        });
    }

    /* JADX INFO: renamed from: lambda$transferirManual$2$com-kreysam-autosistematransfer-WebAppInterface, reason: not valid java name */
    /* synthetic */ void m57x391410c9(int quantidadeMB, String numero) {
        this.activity.iniciarTransferenciaManual(quantidadeMB, numero);
    }

    @JavascriptInterface
    public void transferirManual(final int quantidadeMB, final String numero) {
        this.activity.runOnUiThread(new Runnable() { // from class: com.kreysam.autosistematransfer.WebAppInterface$$ExternalSyntheticLambda4
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.m57x391410c9(quantidadeMB, numero);
            }
        });
    }

    @JavascriptInterface
    public void consultarSaldo(final int sim) {
        this.activity.runOnUiThread(new Runnable() { // from class: com.kreysam.autosistematransfer.WebAppInterface$$ExternalSyntheticLambda3
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.m54x3e65881c(sim);
            }
        });
    }

    /* JADX INFO: renamed from: lambda$consultarSaldo$3$com-kreysam-autosistematransfer-WebAppInterface, reason: not valid java name */
    /* synthetic */ void m54x3e65881c(int sim) {
        this.activity.consultarSaldoSim(sim);
    }

    /* JADX INFO: renamed from: lambda$transferirCreditoManual$4$com-kreysam-autosistematransfer-WebAppInterface, reason: not valid java name */
    /* synthetic */ void m56x234dd17f(String valorMT, String numero) {
        this.activity.iniciarTransferenciaCreditoManual(valorMT, numero);
    }

    @JavascriptInterface
    public void transferirCreditoManual(final String valorMT, final String numero) {
        this.activity.runOnUiThread(new Runnable() { // from class: com.kreysam.autosistematransfer.WebAppInterface$$ExternalSyntheticLambda6
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.m56x234dd17f(valorMT, numero);
            }
        });
    }

    @JavascriptInterface
    public void consultarSaldoCredito(final int sim) {
        this.activity.runOnUiThread(new Runnable() { // from class: com.kreysam.autosistematransfer.WebAppInterface$$ExternalSyntheticLambda8
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.m55xa66a62e2(sim);
            }
        });
    }

    /* JADX INFO: renamed from: lambda$consultarSaldoCredito$5$com-kreysam-autosistematransfer-WebAppInterface, reason: not valid java name */
    /* synthetic */ void m55xa66a62e2(int sim) {
        this.activity.consultarSaldoCreditoSim(sim);
    }

    @JavascriptInterface
    public void ajustarRestantes(int sim, int delta) {
        Prefs.ajustarRestantes(this.activity, sim, delta);
    }

    @JavascriptInterface
    public void resetarTransferencias(int sim) {
        Prefs.resetarTransferencias(this.activity, sim);
    }

    @JavascriptInterface
    public boolean setNumeroFixo(int sim, String numero) {
        if (numero == null || numero.trim().isEmpty()) {
            Prefs.setNumeroFixo(this.activity, sim, "");
            return true;
        }
        String limpo = numero.trim().replaceAll("[^0-9]", "");
        if (limpo.length() != 9) {
            return false;
        }
        if (limpo.startsWith("84") || limpo.startsWith("85")) {
            Prefs.setNumeroFixo(this.activity, sim, limpo);
            return true;
        }
        return false;
    }

    @JavascriptInterface
    public String getNumeroFixo(int sim) {
        return Prefs.getNumeroFixo(this.activity, sim);
    }

    @JavascriptInterface
    public boolean temPermissaoOverlay() {
        return Settings.canDrawOverlays(this.activity);
    }

    @JavascriptInterface
    public void pedirPermissaoOverlay() {
        if (!Settings.canDrawOverlays(this.activity)) {
            Intent intent = new Intent("android.settings.action.MANAGE_OVERLAY_PERMISSION", Uri.parse("package:" + this.activity.getPackageName()));
            this.activity.startActivity(intent);
        }
    }

    @JavascriptInterface
    public boolean getBloqueioActivo() {
        return GhostTouchBlocker.isActivo();
    }

    @JavascriptInterface
    public void activarBloqueio() {
        if (temPermissaoOverlay()) {
            GhostTouchBlocker.activar(this.activity);
        } else {
            pedirPermissaoOverlay();
        }
    }

    @JavascriptInterface
    public void desactivarBloqueio() {
        GhostTouchBlocker.desactivar();
    }

    @JavascriptInterface
    public void transferirUltimaAgora(int sim) {
        String numeroFixo = Prefs.getNumeroFixo(this.activity, sim);
        if (numeroFixo == null || numeroFixo.isEmpty()) {
            return;
        }
        UssdTransferManager.transferirUltimaAgora(this.activity, sim, numeroFixo);
    }

    @JavascriptInterface
    public String getToken() {
        return Prefs.getToken(this.activity);
    }

    @JavascriptInterface
    public void saveToken(String token) {
        Prefs.setToken(this.activity, token);
    }

    @JavascriptInterface
    public String getStatus() {
        try {
            JSONObject o = new JSONObject();
            o.put("totalEnviados", Prefs.getTotalEnviados(this.activity));
            o.put("ultimoEnvio", Prefs.getUltimoEnvio(this.activity));
            o.put("ultimoErro", Prefs.getUltimoErro(this.activity));
            o.put("temToken", !Prefs.getToken(this.activity).isEmpty());
            o.put("temUrl", Prefs.getUrlPainel(this.activity).isEmpty() ? false : true);
            o.put("statusDispositivo", Prefs.getStatusDispositivo(this.activity));
            o.put("ultimaVerificacaoFalhou", Prefs.getUltimaVerificacaoFalhou(this.activity));
            o.put("bateriaIgnorada", this.activity.bateriaOtimizacaoIgnorada());
            o.put("deviceAdminAtivo", this.activity.deviceAdminAtivo());
            o.put("saldoSim1", Prefs.getSaldo(this.activity, 1));
            o.put("saldoSim2", Prefs.getSaldo(this.activity, 2));
            o.put("saldoSim1Ts", Prefs.getSaldoTimestamp(this.activity, 1));
            o.put("saldoSim2Ts", Prefs.getSaldoTimestamp(this.activity, 2));
            o.put("transferenciasRestantesSim1", Prefs.getTransferenciasRestantes(this.activity, 1));
            o.put("transferenciasRestantesSim2", Prefs.getTransferenciasRestantes(this.activity, 2));
            o.put("transferenciasUsadasSim1", Prefs.getTransferenciasUsadasHoje(this.activity, 1));
            o.put("transferenciasUsadasSim2", Prefs.getTransferenciasUsadasHoje(this.activity, 2));
            o.put("maxTransferenciasDia", 10);
            o.put("numeroFixoSim1", Prefs.getNumeroFixo(this.activity, 1));
            o.put("numeroFixoSim2", Prefs.getNumeroFixo(this.activity, 2));
            o.put("simCreditoDisponivel", Prefs.getSimCreditoDisponivel(this.activity));
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    @JavascriptInterface
    public boolean temPermissaoChamadas() {
        return this.activity.temPermissaoChamadas();
    }

    @JavascriptInterface
    public void pedirPermissoesChamadas() {
        final MainActivity mainActivity = this.activity;
        Objects.requireNonNull(mainActivity);
        mainActivity.runOnUiThread(new Runnable() { // from class: com.kreysam.autosistematransfer.WebAppInterface$$ExternalSyntheticLambda7
            @Override // java.lang.Runnable
            public final void run() {
                mainActivity.pedirPermissoesChamadas();
            }
        });
    }

    @JavascriptInterface
    public void abrirConfigBateria() {
        final MainActivity mainActivity = this.activity;
        Objects.requireNonNull(mainActivity);
        mainActivity.runOnUiThread(new Runnable() { // from class: com.kreysam.autosistematransfer.WebAppInterface$$ExternalSyntheticLambda9
            @Override // java.lang.Runnable
            public final void run() {
                mainActivity.abrirConfigBateria();
            }
        });
    }

    @JavascriptInterface
    public void abrirConfigDeviceAdmin() {
        final MainActivity mainActivity = this.activity;
        Objects.requireNonNull(mainActivity);
        mainActivity.runOnUiThread(new Runnable() { // from class: com.kreysam.autosistematransfer.WebAppInterface$$ExternalSyntheticLambda5
            @Override // java.lang.Runnable
            public final void run() {
                mainActivity.abrirConfigDeviceAdmin();
            }
        });
    }

    @JavascriptInterface
    public void iniciarServico() {
        final MainActivity mainActivity = this.activity;
        Objects.requireNonNull(mainActivity);
        mainActivity.runOnUiThread(new Runnable() { // from class: com.kreysam.autosistematransfer.WebAppInterface$$ExternalSyntheticLambda0
            @Override // java.lang.Runnable
            public final void run() {
                mainActivity.iniciarMonitorService();
            }
        });
    }

    @JavascriptInterface
    public String lerLog() {
        return AppLog.ler(this.activity);
    }

    @JavascriptInterface
    public void limparLog() {
        AppLog.limpar(this.activity);
    }
}
