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

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_PERMISSOES = 501;

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);

        configurarWebView();

        iniciarMonitorService();
    }

    // =========================================================
    // WEBVIEW
    // =========================================================

    private void configurarWebView() {

        WebSettings ws = webView.getSettings();

        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);

        webView.addJavascriptInterface(
                new WebAppInterface(this),
                "Android"
        );

        webView.loadUrl(
                "file:///android_asset/index.html"
        );

        if (Prefs.getUrlPainel(this).isEmpty()) {

            Prefs.setUrlPainel(
                    this,
                    getString(
                            R.string.url_painel_sugestao
                    )
            );
        }
    }

    // =========================================================
    // PERMISSÕES DE CHAMADA
    // =========================================================

    public boolean temPermissaoChamadas() {

        boolean base =
                ContextCompat.checkSelfPermission(
                        this,
                        "android.permission.CALL_PHONE"
                ) == 0
                &&
                ContextCompat.checkSelfPermission(
                        this,
                        "android.permission.READ_PHONE_STATE"
                ) == 0;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            return base &&
                    ContextCompat.checkSelfPermission(
                            this,
                            "android.permission.READ_PHONE_NUMBERS"
                    ) == 0;
        }

        return base;
    }

    public void pedirPermissoesChamadas() {

        List<String> permissoes =
                new ArrayList<>();

        permissoes.add(
                "android.permission.CALL_PHONE"
        );

        permissoes.add(
                "android.permission.READ_PHONE_STATE"
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            permissoes.add(
                    "android.permission.READ_PHONE_NUMBERS"
            );
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            permissoes.add(
                    "android.permission.POST_NOTIFICATIONS"
            );
        }

        ActivityCompat.requestPermissions(
                this,
                permissoes.toArray(
                        new String[0]
                ),
                REQ_PERMISSOES
        );
    }

    // =========================================================
    // BATERIA
    // =========================================================

    public boolean bateriaOtimizacaoIgnorada() {

        PowerManager pm =
                (PowerManager)
                        getSystemService(
                                POWER_SERVICE
                        );

        if (pm == null) {
            return false;
        }

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.M) {

            return pm.isIgnoringBatteryOptimizations(
                    getPackageName()
            );
        }

        return true;
    }

    public void abrirConfigBateria() {

        try {

            if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.M) {

                Intent intent =
                        new Intent(
                                "android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"
                        );

                intent.setData(
                        Uri.parse(
                                "package:" +
                                getPackageName()
                        )
                );

                startActivity(intent);

            } else {

                abrirTelaBateria();

            }

        } catch (Exception e) {

            abrirTelaBateria();
        }
    }

    private void abrirTelaBateria() {

        try {

            startActivity(
                    new Intent(
                            "android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS"
                    )
            );

        } catch (Exception ignored) {
        }
    }

    // =========================================================
    // DEVICE ADMIN
    // =========================================================

    public boolean deviceAdminAtivo() {

        DevicePolicyManager dpm =
                (DevicePolicyManager)
                        getSystemService(
                                DEVICE_POLICY_SERVICE
                        );

        ComponentName admin =
                new ComponentName(
                        this,
                        DeviceAdminReceiverImpl.class
                );

        return dpm != null &&
                dpm.isAdminActive(admin);
    }

    public void abrirConfigDeviceAdmin() {

        ComponentName admin =
                new ComponentName(
                        this,
                        DeviceAdminReceiverImpl.class
                );

        Intent intent =
                new Intent(
                        "android.app.action.ADD_DEVICE_ADMIN"
                );

        intent.putExtra(
                "android.app.extra.DEVICE_ADMIN",
                admin
        );

        intent.putExtra(
                "android.app.extra.ADD_EXPLANATION",
                "Ajuda o Autosistema Transfer a continuar ativo em segundo plano."
        );

        startActivity(intent);
    }

    // =========================================================
    // ACESSIBILIDADE
    // =========================================================

    public void abrirConfigAcessibilidade() {

        try {

            startActivity(
                    new Intent(
                            "android.settings.ACCESSIBILITY_SETTINGS"
                    )
            );

        } catch (Exception ignored) {
        }
    }

    // =========================================================
    // TRANSFERÊNCIA MANUAL DE MB
    // =========================================================

    public void iniciarTransferenciaManual(
            int quantidadeMB,
            String numero
    ) {

        UssdTransferManager.transferir(
                this,
                quantidadeMB,
                numero,
                new UssdTransferManager.ResultadoCallback() {

                    @Override
                    public void onSucesso(
                            final int sim,
                            final int saldoRestanteMB
                    ) {

                        final String msg =
                                "Transferido via SIM " +
                                sim +
                                " — saldo restante: " +
                                saldoRestanteMB +
                                "MB";

                        executarJavascript(
                                "mostrarResultadoTransferencia(true, " +
                                JSONObject.quote(msg) +
                                ")"
                        );
                    }

                    @Override
                    public void onFalhaSaldoInsuficiente(
                            final String detalhes
                    ) {

                        final String msg =
                                "Nenhum SIM disponivel. " +
                                (
                                    detalhes != null
                                    ? detalhes
                                    : ""
                                );

                        executarJavascript(
                                "mostrarResultadoTransferencia(false, " +
                                JSONObject.quote(msg) +
                                ")"
                        );
                    }

                    @Override
                    public void onErro(
                            final String motivo
                    ) {

                        executarJavascript(
                                "mostrarResultadoTransferencia(false, " +
                                JSONObject.quote(motivo) +
                                ")"
                        );
                    }
                }
        );
    }

    // =========================================================
    // CONSULTAR SALDO MB
    // =========================================================

    public void consultarSaldoSim(
            final int sim
    ) {

        UssdTransferManager.consultarSaldo(
                this,
                sim,
                new UssdTransferManager.SaldoCallback() {

                    @Override
                    public void onSaldoLido(
                            final int simRetornado,
                            final int saldoMB
                    ) {

                        executarJavascript(
                                "mostrarSaldoConsultado(" +
                                simRetornado +
                                ", " +
                                saldoMB +
                                ", true, null)"
                        );
                    }

                    @Override
                    public void onErro(
                            final int simRetornado,
                            final String motivo
                    ) {

                        executarJavascript(
                                "mostrarSaldoConsultado(" +
                                simRetornado +
                                ", -1, false, " +
                                JSONObject.quote(motivo) +
                                ")"
                        );
                    }
                }
        );
    }

    // =========================================================
    // TRANSFERÊNCIA MANUAL DE CRÉDITO
    // =========================================================

    public void iniciarTransferenciaCreditoManual(
            String valorMT,
            String numero
    ) {

        UssdTransferManager.transferirCredito(
                this,
                valorMT,
                numero,
                new UssdTransferManager.ResultadoCreditoCallback() {

                    @Override
                    public void onSucesso(
                            final int sim
                    ) {

                        final String msg =
                                "Credito transferido via SIM " +
                                sim;

                        executarJavascript(
                                "mostrarResultadoCredito(true, " +
                                JSONObject.quote(msg) +
                                ")"
                        );
                    }

                    @Override
                    public void onErro(
                            final String motivo
                    ) {

                        executarJavascript(
                                "mostrarResultadoCredito(false, " +
                                JSONObject.quote(motivo) +
                                ")"
                        );
                    }
                }
        );
    }

    // =========================================================
    // CONSULTAR SALDO DE CRÉDITO
    // =========================================================

    public void consultarSaldoCreditoSim(
            final int sim
    ) {

        UssdTransferManager.consultarSaldoCredito(
                this,
                sim,
                new UssdTransferManager.SaldoCreditoCallback() {

                    @Override
                    public void onSaldoLido(
                            final int simRetornado,
                            final double saldoMT
                    ) {

                        executarJavascript(
                                "mostrarSaldoCreditoConsultado(" +
                                simRetornado +
                                ", " +
                                saldoMT +
                                ", true, null)"
                        );
                    }

                    @Override
                    public void onErro(
                            final int simRetornado,
                            final String motivo
                    ) {

                        executarJavascript(
                                "mostrarSaldoCreditoConsultado(" +
                                simRetornado +
                                ", -1, false, " +
                                JSONObject.quote(motivo) +
                                ")"
                        );
                    }
                }
        );
    }

    // =========================================================
    // JAVASCRIPT
    // =========================================================

    private void executarJavascript(
            final String javascript
    ) {

        runOnUiThread(
                new Runnable() {

                    @Override
                    public void run() {

                        if (webView != null) {

                            webView.evaluateJavascript(
                                    javascript,
                                    null
                            );
                        }
                    }
                }
        );
    }

    // =========================================================
    // MONITOR SERVICE
    // =========================================================

    public void iniciarMonitorService() {

        try {

            Intent servico =
                    new Intent(
                            this,
                            MonitorService.class
                    );

            if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.O) {

                startForegroundService(servico);

            } else {

                startService(servico);
            }

        } catch (Exception e) {

            AppLog.add(
                    this,
                    "MainActivity",
                    "Erro ao iniciar MonitorService: " +
                    e.getMessage()
            );
        }
    }
}