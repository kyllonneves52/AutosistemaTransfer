package com.kreysam.autosistematransfer;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.kreysam.autosistematransfer.UssdTransferManager;
import org.json.JSONObject;

/* JADX INFO: loaded from: classes3.dex */
public class MainActivity extends AppCompatActivity {
    private static final int REQ_PERMISSOES = 501;
    private WebView webView;

    @Override // androidx.fragment.app.FragmentActivity, androidx.activity.ComponentActivity, androidx.core.app.ComponentActivity, android.app.Activity
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        WebView webView = (WebView) findViewById(R.id.webview);
        this.webView = webView;
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        this.webView.addJavascriptInterface(new WebAppInterface(this), "Android");
        this.webView.loadUrl("file:///android_asset/index.html");
        if (Prefs.getUrlPainel(this).isEmpty()) {
            Prefs.setUrlPainel(this, getString(R.string.url_painel_sugestao));
        }
        iniciarMonitorService();
    }

    public boolean temPermissaoChamadas() {
        boolean base = ContextCompat.checkSelfPermission(this, "android.permission.CALL_PHONE") == 0 && ContextCompat.checkSelfPermission(this, "android.permission.READ_PHONE_STATE") == 0;
        if (Build.VERSION.SDK_INT >= 26) {
            return base && ContextCompat.checkSelfPermission(this, "android.permission.READ_PHONE_NUMBERS") == 0;
        }
        return base;
    }

    public void pedirPermissoesChamadas() {
        ActivityCompat.requestPermissions(this, new String[]{"android.permission.CALL_PHONE", "android.permission.READ_PHONE_STATE", "android.permission.READ_PHONE_NUMBERS", "android.permission.POST_NOTIFICATIONS"}, REQ_PERMISSOES);
    }

    public boolean bateriaOtimizacaoIgnorada() {
        PowerManager pm = (PowerManager) getSystemService("power");
        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    public void abrirConfigBateria() {
        try {
            Intent intent = new Intent("android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS");
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            startActivity(new Intent("android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS"));
        }
    }

    public boolean deviceAdminAtivo() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService("device_policy");
        ComponentName admin = new ComponentName(this, (Class<?>) DeviceAdminReceiverImpl.class);
        return dpm != null && dpm.isAdminActive(admin);
    }

    public void abrirConfigDeviceAdmin() {
        ComponentName admin = new ComponentName(this, (Class<?>) DeviceAdminReceiverImpl.class);
        Intent intent = new Intent("android.app.action.ADD_DEVICE_ADMIN");
        intent.putExtra("android.app.extra.DEVICE_ADMIN", admin);
        intent.putExtra("android.app.extra.ADD_EXPLANATION", "Ajuda o Autosistema Transfer a continuar ativo em segundo plano.");
        startActivity(intent);
    }

    public void abrirConfigAcessibilidade() {
        startActivity(new Intent("android.settings.ACCESSIBILITY_SETTINGS"));
    }

    /* JADX INFO: renamed from: com.kreysam.autosistematransfer.MainActivity$1, reason: invalid class name */
    class AnonymousClass1 implements UssdTransferManager.ResultadoCallback {
        AnonymousClass1() {
        }

        @Override // com.kreysam.autosistematransfer.UssdTransferManager.ResultadoCallback
        public void onSucesso(int sim, int saldoRestanteMB) {
            final String msg = "Transferido via SIM " + sim + " — saldo restante: " + saldoRestanteMB + "MB";
            MainActivity.this.runOnUiThread(new Runnable() { // from class: com.kreysam.autosistematransfer.MainActivity$1$$ExternalSyntheticLambda0
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.m38x31e0ab82(msg);
                }
            });
        }

        /* JADX INFO: renamed from: lambda$onSucesso$0$com-kreysam-autosistematransfer-MainActivity$1, reason: not valid java name */
        /* synthetic */ void m38x31e0ab82(String msg) {
            MainActivity.this.webView.evaluateJavascript("mostrarResultadoTransferencia(true, " + JSONObject.quote(msg) + ")", null);
        }

        @Override // com.kreysam.autosistematransfer.UssdTransferManager.ResultadoCallback
        public void onFalhaSaldoInsuficiente(String detalhes) {
            final String msg = "Nenhum SIM disponivel. " + (detalhes != null ? detalhes : "");
            MainActivity.this.runOnUiThread(new Runnable() { // from class: com.kreysam.autosistematransfer.MainActivity$1$$ExternalSyntheticLambda2
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.m37x9162520b(msg);
                }
            });
        }

        /* JADX INFO: renamed from: lambda$onFalhaSaldoInsuficiente$1$com-kreysam-autosistematransfer-MainActivity$1, reason: not valid java name */
        /* synthetic */ void m37x9162520b(String msg) {
            MainActivity.this.webView.evaluateJavascript("mostrarResultadoTransferencia(false, " + JSONObject.quote(msg) + ")", null);
        }

        /* JADX INFO: renamed from: lambda$onErro$2$com-kreysam-autosistematransfer-MainActivity$1, reason: not valid java name */
        /* synthetic */ void m36lambda$onErro$2$comkreysamautosistematransferMainActivity$1(String motivo) {
            MainActivity.this.webView.evaluateJavascript("mostrarResultadoTransferencia(false, " + JSONObject.quote(motivo) + ")", null);
        }

        @Override // com.kreysam.autosistematransfer.UssdTransferManager.ResultadoCallback
        public void onErro(final String motivo) {
            MainActivity.this.runOnUiThread(new Runnable() { // from class: com.kreysam.autosistematransfer.MainActivity$1$$ExternalSyntheticLambda1
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.m36lambda$onErro$2$comkreysamautosistematransferMainActivity$1(motivo);
                }
            });
        }
    }

    public void iniciarTransferenciaManual(int quantidadeMB, String numero) {
        UssdTransferManager.transferir(this, quantidadeMB, numero, new AnonymousClass1());
    }

    /* JADX INFO: renamed from: com.kreysam.autosistematransfer.MainActivity$2, reason: invalid class name */
    class AnonymousClass2 implements UssdTransferManager.SaldoCallback {
        AnonymousClass2() {
        }

        /* JADX INFO: renamed from: lambda$onSaldoLido$0$com-kreysam-autosistematransfer-MainActivity$2, reason: not valid java name */
        /* synthetic */ void m40x12e74f3d(int simRetornado, int saldoMB) {
            MainActivity.this.webView.evaluateJavascript("mostrarSaldoConsultado(" + simRetornado + ", " + saldoMB + ", true, null)", null);
        }

        @Override // com.kreysam.autosistematransfer.UssdTransferManager.SaldoCallback
        public void onSaldoLido(final int simRetornado, final int saldoMB) {
            MainActivity.this.runOnUiThread(new Runnable() { // from class: com.kreysam.autosistematransfer.MainActivity$2$$ExternalSyntheticLambda1
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.m40x12e74f3d(simRetornado, saldoMB);
                }
            });
        }

        /* JADX INFO: renamed from: lambda$onErro$1$com-kreysam-autosistematransfer-MainActivity$2, reason: not valid java name */
        /* synthetic */ void m39lambda$onErro$1$comkreysamautosistematransferMainActivity$2(int simRetornado, String motivo) {
            MainActivity.this.webView.evaluateJavascript("mostrarSaldoConsultado(" + simRetornado + ", -1, false, " + JSONObject.quote(motivo) + ")", null);
        }

        @Override // com.kreysam.autosistematransfer.UssdTransferManager.SaldoCallback
        public void onErro(final int simRetornado, final String motivo) {
            MainActivity.this.runOnUiThread(new Runnable() { // from class: com.kreysam.autosistematransfer.MainActivity$2$$ExternalSyntheticLambda0
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.m39lambda$onErro$1$comkreysamautosistematransferMainActivity$2(simRetornado, motivo);
                }
            });
        }
    }

    public void consultarSaldoSim(int sim) {
        UssdTransferManager.consultarSaldo(this, sim, new AnonymousClass2());
    }

    /* JADX INFO: renamed from: com.kreysam.autosistematransfer.MainActivity$3, reason: invalid class name */
    class AnonymousClass3 implements UssdTransferManager.ResultadoCreditoCallback {
        AnonymousClass3() {
        }

        @Override // com.kreysam.autosistematransfer.UssdTransferManager.ResultadoCreditoCallback
        public void onSucesso(int sim) {
            final String msg = "Credito transferido via SIM " + sim;
            MainActivity.this.runOnUiThread(new Runnable() { // from class: com.kreysam.autosistematransfer.MainActivity$3$$ExternalSyntheticLambda1
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.m42x31e0ab84(msg);
                }
            });
        }

        /* JADX INFO: renamed from: lambda$onSucesso$0$com-kreysam-autosistematransfer-MainActivity$3, reason: not valid java name */
        /* synthetic */ void m42x31e0ab84(String msg) {
            MainActivity.this.webView.evaluateJavascript("mostrarResultadoCredito(true, " + JSONObject.quote(msg) + ")", null);
        }

        /* JADX INFO: renamed from: lambda$onErro$1$com-kreysam-autosistematransfer-MainActivity$3, reason: not valid java name */
        /* synthetic */ void m41lambda$onErro$1$comkreysamautosistematransferMainActivity$3(String motivo) {
            MainActivity.this.webView.evaluateJavascript("mostrarResultadoCredito(false, " + JSONObject.quote(motivo) + ")", null);
        }

        @Override // com.kreysam.autosistematransfer.UssdTransferManager.ResultadoCreditoCallback
        public void onErro(final String motivo) {
            MainActivity.this.runOnUiThread(new Runnable() { // from class: com.kreysam.autosistematransfer.MainActivity$3$$ExternalSyntheticLambda0
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.m41lambda$onErro$1$comkreysamautosistematransferMainActivity$3(motivo);
                }
            });
        }
    }

    public void iniciarTransferenciaCreditoManual(String valorMT, String numero) {
        UssdTransferManager.transferirCredito(this, valorMT, numero, new AnonymousClass3());
    }

    /* JADX INFO: renamed from: com.kreysam.autosistematransfer.MainActivity$4, reason: invalid class name */
    class AnonymousClass4 implements UssdTransferManager.SaldoCreditoCallback {
        AnonymousClass4() {
        }

        /* JADX INFO: renamed from: lambda$onSaldoLido$0$com-kreysam-autosistematransfer-MainActivity$4, reason: not valid java name */
        /* synthetic */ void m44x12e74f3f(int simRetornado, double saldoMT) {
            MainActivity.this.webView.evaluateJavascript("mostrarSaldoCreditoConsultado(" + simRetornado + ", " + saldoMT + ", true, null)", null);
        }

        @Override // com.kreysam.autosistematransfer.UssdTransferManager.SaldoCreditoCallback
        public void onSaldoLido(final int simRetornado, final double saldoMT) {
            MainActivity.this.runOnUiThread(new Runnable() { // from class: com.kreysam.autosistematransfer.MainActivity$4$$ExternalSyntheticLambda0
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.m44x12e74f3f(simRetornado, saldoMT);
                }
            });
        }

        /* JADX INFO: renamed from: lambda$onErro$1$com-kreysam-autosistematransfer-MainActivity$4, reason: not valid java name */
        /* synthetic */ void m43lambda$onErro$1$comkreysamautosistematransferMainActivity$4(int simRetornado, String motivo) {
            MainActivity.this.webView.evaluateJavascript("mostrarSaldoCreditoConsultado(" + simRetornado + ", -1, false, " + JSONObject.quote(motivo) + ")", null);
        }

        @Override // com.kreysam.autosistematransfer.UssdTransferManager.SaldoCreditoCallback
        public void onErro(final int simRetornado, final String motivo) {
            MainActivity.this.runOnUiThread(new Runnable() { // from class: com.kreysam.autosistematransfer.MainActivity$4$$ExternalSyntheticLambda1
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.m43lambda$onErro$1$comkreysamautosistematransferMainActivity$4(simRetornado, motivo);
                }
            });
        }
    }

    public void consultarSaldoCreditoSim(int sim) {
        UssdTransferManager.consultarSaldoCredito(this, sim, new AnonymousClass4());
    }

    public void iniciarMonitorService() {
        Intent servico = new Intent(this, (Class<?>) MonitorService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(servico);
        } else {
            startService(servico);
        }
    }
}
