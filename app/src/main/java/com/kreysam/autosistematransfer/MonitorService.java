package com.kreysam.autosistematransfer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.core.app.NotificationCompat;
import com.kreysam.autosistematransfer.ApiClient;
import com.kreysam.autosistematransfer.MonitorService;
import com.kreysam.autosistematransfer.UssdTransferManager;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/* JADX INFO: loaded from: classes3.dex */
public class MonitorService extends Service {
    private static final String CANAL_ID = "autosistema_monitor";
    private static final long COOLDOWN_FALHA_MS = 120000;
    private static final long INTERVALO_FILA_MS = 20000;
    private static final long INTERVALO_HEARTBEAT_MS = 30000;
    private static final long INTERVALO_RETRY_ERRO_MS = 6000;
    private static final int MAX_TENTATIVAS_ERRO = 3;
    private static final int NOTIF_ID = 7788;
    private static final String TAG = "MonitorService";
    private static volatile boolean processandoPedido = false;
    private static final Map<String, Long> ultimaFalhaPorPedido = new HashMap();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable heartbeatRunnable = new Runnable() { // from class: com.kreysam.autosistematransfer.MonitorService.1
        @Override // java.lang.Runnable
        public void run() {
            ApiClient.enviarHeartbeat(MonitorService.this.getApplicationContext());
            MonitorService.this.handler.postDelayed(this, MonitorService.INTERVALO_HEARTBEAT_MS);
        }
    };
    private final Runnable filaRunnable = new Runnable() { // from class: com.kreysam.autosistematransfer.MonitorService.2
        @Override // java.lang.Runnable
        public void run() {
            Context ctx = MonitorService.this.getApplicationContext();
            if (Prefs.getComunicacaoAtiva(ctx) && !MonitorService.processandoPedido && MonitorService.this.temSimDisponivel(ctx)) {
                MonitorService.this.consultarFila(ctx);
            }
            MonitorService.this.handler.postDelayed(this, MonitorService.INTERVALO_FILA_MS);
        }
    };

    /* JADX INFO: Access modifiers changed from: private */
    public boolean temSimDisponivel(Context ctx) {
        for (int sim = 1; sim <= 2; sim++) {
            if (Prefs.getTransferenciasRestantes(ctx, sim) > 0 && UssdTransferManager.simEhVodacom(ctx, sim)) {
                return true;
            }
        }
        AppLog.add(ctx, TAG, "Nenhum SIM disponivel -- a saltar consulta de fila.");
        return false;
    }

    @Override // android.app.Service
    public void onCreate() {
        super.onCreate();
        criarCanalSeNecessario();
        startForeground(NOTIF_ID, construirNotificacao());
        this.handler.post(this.heartbeatRunnable);
        this.handler.post(this.filaRunnable);
        TtsHelper.iniciar(getApplicationContext());
    }

    @Override // android.app.Service
    public int onStartCommand(Intent intent, int flags, int startId) {
        return 1;
    }

    @Override // android.app.Service
    public void onDestroy() {
        super.onDestroy();
        this.handler.removeCallbacks(this.heartbeatRunnable);
        this.handler.removeCallbacks(this.filaRunnable);
    }

    @Override // android.app.Service
    public IBinder onBind(Intent intent) {
        return null;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void consultarFila(final Context ctx) {
        AppLog.add(ctx, TAG, "A consultar fila...");
        ApiClient.buscarPedidos(ctx, new ApiClient.PedidosCallback() { // from class: com.kreysam.autosistematransfer.MonitorService.3
            @Override // com.kreysam.autosistematransfer.ApiClient.PedidosCallback
            public void onPedidos(JSONArray pedidos) {
                Long ultimaFalha;
                if (pedidos == null || pedidos.length() == 0) {
                    return;
                }
                for (int i = 0; i < pedidos.length(); i++) {
                    JSONObject pedido = pedidos.optJSONObject(i);
                    if (pedido != null) {
                        String pedidoId = pedido.optString("pedidoId", "");
                        if (!pedidoId.isEmpty() && ((ultimaFalha = (Long) MonitorService.ultimaFalhaPorPedido.get(pedidoId)) == null || System.currentTimeMillis() - ultimaFalha.longValue() >= MonitorService.COOLDOWN_FALHA_MS)) {
                            MonitorService.this.processarPedido(ctx, pedido);
                            return;
                        }
                    }
                }
            }

            @Override // com.kreysam.autosistematransfer.ApiClient.PedidosCallback
            public void onErro(String motivo) {
                AppLog.add(ctx, MonitorService.TAG, "Falha ao consultar fila: " + motivo);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void processarPedido(final Context ctx, JSONObject pedido) {
        final String pedidoId = pedido.optString("pedidoId", "");
        if (pedidoId.isEmpty()) {
            return;
        }
        if (Prefs.pedidoJaEntregue(ctx, pedidoId)) {
            AppLog.add(ctx, TAG, "Pedido " + pedidoId + " ja foi entregue antes -- NAO vou redisscar, so reenvio a confirmacao ao painel.");
            ApiClient.concluirPedido(ctx, pedidoId, new ApiClient.ConcluirCallback() { // from class: com.kreysam.autosistematransfer.MonitorService$$ExternalSyntheticLambda0
                @Override // com.kreysam.autosistematransfer.ApiClient.ConcluirCallback
                public final void onResultado(boolean z) {
                    AppLog.add(ctx, MonitorService.TAG, "Reenvio de confirmacao do pedido " + pedidoId + ": " + z);
                }
            });
            return;
        }
        String tipo = pedido.optString("tipo", "megas");
        if ("credito".equals(tipo)) {
            processarPedidoCredito(ctx, pedido, pedidoId);
            return;
        }
        String numero = pedido.optString("numero", "");
        int quantidadeMB = pedido.optInt("quantidadeMB", 0);
        String quantidadeLabel = pedido.optString("quantidadeLabel", quantidadeMB + "MB");
        if (numero.isEmpty() || quantidadeMB <= 0) {
            AppLog.add(ctx, TAG, "Pedido " + pedidoId + " ignorado -- dados invalidos (numero=" + numero + " mb=" + quantidadeMB + ")");
            ApiClient.falharPedido(ctx, pedidoId, "Dados do pedido invalidos (numero ou quantidade em falta).");
            ultimaFalhaPorPedido.put(pedidoId, Long.valueOf(System.currentTimeMillis()));
        } else {
            processandoPedido = true;
            AppLog.add(ctx, TAG, "Pedido recebido: " + quantidadeLabel + " para " + numero + " (id=" + pedidoId + ")");
            tentarTransferencia(ctx, pedidoId, numero, quantidadeMB, 1);
        }
    }

    private void processarPedidoCredito(Context ctx, JSONObject pedido, String pedidoId) {
        String numero = pedido.optString("numero", "");
        String valorMT = pedido.optString("valorMT", pedido.optString("valor", ""));
        if (numero.isEmpty() || valorMT.isEmpty()) {
            AppLog.add(ctx, TAG, "Pedido de credito " + pedidoId + " ignorado -- dados invalidos (numero=" + numero + " valorMT=" + valorMT + ")");
            ApiClient.falharPedido(ctx, pedidoId, "Dados do pedido de credito invalidos (numero ou valorMT em falta).");
            ultimaFalhaPorPedido.put(pedidoId, Long.valueOf(System.currentTimeMillis()));
        } else {
            processandoPedido = true;
            AppLog.add(ctx, TAG, "Pedido de credito recebido: " + valorMT + "MT para " + numero + " (id=" + pedidoId + ")");
            tentarTransferenciaCredito(ctx, pedidoId, numero, valorMT, 1);
        }
    }

    /* JADX INFO: renamed from: com.kreysam.autosistematransfer.MonitorService$4, reason: invalid class name */
    class AnonymousClass4 implements UssdTransferManager.ResultadoCreditoCallback {
        final /* synthetic */ Context val$ctx;
        final /* synthetic */ String val$numero;
        final /* synthetic */ String val$pedidoId;
        final /* synthetic */ int val$tentativa;
        final /* synthetic */ String val$valorMT;

        AnonymousClass4(Context context, String str, int i, String str2, String str3) {
            this.val$ctx = context;
            this.val$pedidoId = str;
            this.val$tentativa = i;
            this.val$numero = str2;
            this.val$valorMT = str3;
        }

        @Override // com.kreysam.autosistematransfer.UssdTransferManager.ResultadoCreditoCallback
        public void onSucesso(int sim) {
            AppLog.add(this.val$ctx, MonitorService.TAG, "Pedido de credito concluido (id=" + this.val$pedidoId + ", via SIM " + sim + ") -- a confirmar ao painel...");
            MonitorService.ultimaFalhaPorPedido.remove(this.val$pedidoId);
            Prefs.marcarPedidoEntregue(this.val$ctx, this.val$pedidoId);
            final boolean[] jaLibertado = {false};
            final Runnable libertarUmaVezSo = new Runnable() { // from class: com.kreysam.autosistematransfer.MonitorService$4$$ExternalSyntheticLambda1
                @Override // java.lang.Runnable
                public final void run() {
                    MonitorService.AnonymousClass4.lambda$onSucesso$0(jaLibertado);
                }
            };
            Handler handler = MonitorService.this.handler;
            final Context context = this.val$ctx;
            final String str = this.val$pedidoId;
            handler.postDelayed(new Runnable() { // from class: com.kreysam.autosistematransfer.MonitorService$4$$ExternalSyntheticLambda2
                @Override // java.lang.Runnable
                public final void run() {
                    MonitorService.AnonymousClass4.lambda$onSucesso$1(jaLibertado, context, str, libertarUmaVezSo);
                }
            }, MonitorService.INTERVALO_HEARTBEAT_MS);
            final Context context2 = this.val$ctx;
            final String str2 = this.val$pedidoId;
            ApiClient.concluirPedido(context2, str2, new ApiClient.ConcluirCallback() { // from class: com.kreysam.autosistematransfer.MonitorService$4$$ExternalSyntheticLambda3
                @Override // com.kreysam.autosistematransfer.ApiClient.ConcluirCallback
                public final void onResultado(boolean z) {
                    MonitorService.AnonymousClass4.lambda$onSucesso$2(context2, str2, libertarUmaVezSo, z);
                }
            });
        }

        static /* synthetic */ void lambda$onSucesso$0(boolean[] jaLibertado) {
            synchronized (jaLibertado) {
                if (jaLibertado[0]) {
                    return;
                }
                jaLibertado[0] = true;
                boolean unused = MonitorService.processandoPedido = false;
            }
        }

        static /* synthetic */ void lambda$onSucesso$1(boolean[] jaLibertado, Context ctx, String pedidoId, Runnable libertarUmaVezSo) {
            synchronized (jaLibertado) {
                if (!jaLibertado[0]) {
                    AppLog.add(ctx, MonitorService.TAG, "Pedido " + pedidoId + " (credito): confirmacao ao painel nunca chegou apos 30s -- a libertar mesmo assim.");
                }
            }
            libertarUmaVezSo.run();
        }

        static /* synthetic */ void lambda$onSucesso$2(Context ctx, String pedidoId, Runnable libertarUmaVezSo, boolean ok) {
            AppLog.add(ctx, MonitorService.TAG, "Pedido de credito " + pedidoId + " confirmado ao painel: " + ok);
            libertarUmaVezSo.run();
        }

        @Override // com.kreysam.autosistematransfer.UssdTransferManager.ResultadoCreditoCallback
        public void onErro(String motivo) {
            int i;
            String m = motivo == null ? "" : motivo.toLowerCase();
            boolean definitivo = m.contains("saldo insuficiente") || m.contains("numero invalido") || m.contains("valor de credito invalido") || m.contains("proprio numero");
            if (definitivo || (i = this.val$tentativa) >= 3) {
                AppLog.add(this.val$ctx, MonitorService.TAG, "Pedido de credito " + this.val$pedidoId + ": falhou (" + motivo + ")");
                ApiClient.falharPedido(this.val$ctx, this.val$pedidoId, motivo);
                MonitorService.ultimaFalhaPorPedido.put(this.val$pedidoId, Long.valueOf(System.currentTimeMillis()));
                boolean unused = MonitorService.processandoPedido = false;
                return;
            }
            final int proximaTentativa = i + 1;
            AppLog.add(this.val$ctx, MonitorService.TAG, "Pedido de credito " + this.val$pedidoId + ": erro tecnico (" + motivo + ") -- tentativa " + proximaTentativa + "/3 em 6s...");
            Handler handler = MonitorService.this.handler;
            final Context context = this.val$ctx;
            final String str = this.val$pedidoId;
            final String str2 = this.val$numero;
            final String str3 = this.val$valorMT;
            handler.postDelayed(new Runnable() { // from class: com.kreysam.autosistematransfer.MonitorService$4$$ExternalSyntheticLambda0
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.m45lambda$onErro$3$comkreysamautosistematransferMonitorService$4(context, str, str2, str3, proximaTentativa);
                }
            }, MonitorService.INTERVALO_RETRY_ERRO_MS);
        }

        /* JADX INFO: renamed from: lambda$onErro$3$com-kreysam-autosistematransfer-MonitorService$4, reason: not valid java name */
        /* synthetic */ void m45lambda$onErro$3$comkreysamautosistematransferMonitorService$4(Context ctx, String pedidoId, String numero, String valorMT, int proximaTentativa) {
            MonitorService.this.tentarTransferenciaCredito(ctx, pedidoId, numero, valorMT, proximaTentativa);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void tentarTransferenciaCredito(Context ctx, String pedidoId, String numero, String valorMT, int tentativa) {
        UssdTransferManager.transferirCredito(ctx, valorMT, numero, new AnonymousClass4(ctx, pedidoId, tentativa, numero, valorMT));
    }

    /* JADX INFO: renamed from: com.kreysam.autosistematransfer.MonitorService$5, reason: invalid class name */
    class AnonymousClass5 implements UssdTransferManager.ResultadoCallback {
        final /* synthetic */ Context val$ctx;
        final /* synthetic */ String val$numero;
        final /* synthetic */ String val$pedidoId;
        final /* synthetic */ int val$quantidadeMB;
        final /* synthetic */ int val$tentativa;

        AnonymousClass5(Context context, String str, int i, String str2, int i2) {
            this.val$ctx = context;
            this.val$pedidoId = str;
            this.val$tentativa = i;
            this.val$numero = str2;
            this.val$quantidadeMB = i2;
        }

        @Override // com.kreysam.autosistematransfer.UssdTransferManager.ResultadoCallback
        public void onSucesso(int sim, int saldoRestanteMB) {
            AppLog.add(this.val$ctx, MonitorService.TAG, "Pedido concluido (id=" + this.val$pedidoId + ", via SIM " + sim + ", saldo restante " + saldoRestanteMB + "MB) -- a confirmar ao painel...");
            MonitorService.ultimaFalhaPorPedido.remove(this.val$pedidoId);
            Prefs.marcarPedidoEntregue(this.val$ctx, this.val$pedidoId);
            final boolean[] jaLibertado = {false};
            final Runnable libertarUmaVezSo = new Runnable() { // from class: com.kreysam.autosistematransfer.MonitorService$5$$ExternalSyntheticLambda1
                @Override // java.lang.Runnable
                public final void run() {
                    MonitorService.AnonymousClass5.lambda$onSucesso$0(jaLibertado);
                }
            };
            Handler handler = MonitorService.this.handler;
            final Context context = this.val$ctx;
            final String str = this.val$pedidoId;
            handler.postDelayed(new Runnable() { // from class: com.kreysam.autosistematransfer.MonitorService$5$$ExternalSyntheticLambda2
                @Override // java.lang.Runnable
                public final void run() {
                    MonitorService.AnonymousClass5.lambda$onSucesso$1(jaLibertado, context, str, libertarUmaVezSo);
                }
            }, MonitorService.INTERVALO_HEARTBEAT_MS);
            final Context context2 = this.val$ctx;
            final String str2 = this.val$pedidoId;
            ApiClient.concluirPedido(context2, str2, new ApiClient.ConcluirCallback() { // from class: com.kreysam.autosistematransfer.MonitorService$5$$ExternalSyntheticLambda3
                @Override // com.kreysam.autosistematransfer.ApiClient.ConcluirCallback
                public final void onResultado(boolean z) {
                    MonitorService.AnonymousClass5.lambda$onSucesso$2(context2, str2, libertarUmaVezSo, z);
                }
            });
        }

        static /* synthetic */ void lambda$onSucesso$0(boolean[] jaLibertado) {
            synchronized (jaLibertado) {
                if (jaLibertado[0]) {
                    return;
                }
                jaLibertado[0] = true;
                boolean unused = MonitorService.processandoPedido = false;
            }
        }

        static /* synthetic */ void lambda$onSucesso$1(boolean[] jaLibertado, Context ctx, String pedidoId, Runnable libertarUmaVezSo) {
            synchronized (jaLibertado) {
                if (!jaLibertado[0]) {
                    AppLog.add(ctx, MonitorService.TAG, "Pedido " + pedidoId + ": confirmacao ao painel nunca chegou apos 30s -- a libertar mesmo assim (pedido ja esta marcado como entregue, nao sera redisscado).");
                }
            }
            libertarUmaVezSo.run();
        }

        static /* synthetic */ void lambda$onSucesso$2(Context ctx, String pedidoId, Runnable libertarUmaVezSo, boolean ok) {
            AppLog.add(ctx, MonitorService.TAG, "Pedido " + pedidoId + " confirmado ao painel: " + ok);
            libertarUmaVezSo.run();
        }

        @Override // com.kreysam.autosistematransfer.UssdTransferManager.ResultadoCallback
        public void onFalhaSaldoInsuficiente(String detalhes) {
            AppLog.add(this.val$ctx, MonitorService.TAG, "Pedido falhou (id=" + this.val$pedidoId + "): " + detalhes);
            ApiClient.falharPedido(this.val$ctx, this.val$pedidoId, detalhes);
            MonitorService.ultimaFalhaPorPedido.put(this.val$pedidoId, Long.valueOf(System.currentTimeMillis()));
            boolean unused = MonitorService.processandoPedido = false;
        }

        @Override // com.kreysam.autosistematransfer.UssdTransferManager.ResultadoCallback
        public void onErro(String motivo) {
            int i = this.val$tentativa;
            if (i < 3) {
                final int proximaTentativa = i + 1;
                AppLog.add(this.val$ctx, MonitorService.TAG, "Pedido " + this.val$pedidoId + ": erro tecnico (" + motivo + ") -- tentativa " + proximaTentativa + "/3 em 6s...");
                Handler handler = MonitorService.this.handler;
                final Context context = this.val$ctx;
                final String str = this.val$pedidoId;
                final String str2 = this.val$numero;
                final int i2 = this.val$quantidadeMB;
                handler.postDelayed(new Runnable() { // from class: com.kreysam.autosistematransfer.MonitorService$5$$ExternalSyntheticLambda0
                    @Override // java.lang.Runnable
                    public final void run() {
                        this.f$0.m46lambda$onErro$3$comkreysamautosistematransferMonitorService$5(context, str, str2, i2, proximaTentativa);
                    }
                }, MonitorService.INTERVALO_RETRY_ERRO_MS);
                return;
            }
            AppLog.add(this.val$ctx, MonitorService.TAG, "Pedido " + this.val$pedidoId + ": falhou apos 3 tentativas: " + motivo);
            ApiClient.falharPedido(this.val$ctx, this.val$pedidoId, motivo);
            MonitorService.ultimaFalhaPorPedido.put(this.val$pedidoId, Long.valueOf(System.currentTimeMillis()));
            boolean unused = MonitorService.processandoPedido = false;
        }

        /* JADX INFO: renamed from: lambda$onErro$3$com-kreysam-autosistematransfer-MonitorService$5, reason: not valid java name */
        /* synthetic */ void m46lambda$onErro$3$comkreysamautosistematransferMonitorService$5(Context ctx, String pedidoId, String numero, int quantidadeMB, int proximaTentativa) {
            MonitorService.this.tentarTransferencia(ctx, pedidoId, numero, quantidadeMB, proximaTentativa);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void tentarTransferencia(Context ctx, String pedidoId, String numero, int quantidadeMB, int tentativa) {
        UssdTransferManager.transferir(ctx, quantidadeMB, numero, new AnonymousClass5(ctx, pedidoId, tentativa, numero, quantidadeMB));
    }

    private void criarCanalSeNecessario() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel canal = new NotificationChannel(CANAL_ID, "Monitor Autosistema", 4);
            canal.setDescription("Mantém o monitor de SMS M-Pesa/E-Mola sempre ativo.");
            canal.setShowBadge(false);
            NotificationManager nm = (NotificationManager) getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(canal);
            }
        }
    }

    private Notification construirNotificacao() {
        int total = Prefs.getTotalEnviados(this);
        return new NotificationCompat.Builder(this, CANAL_ID).setContentTitle("Autosistema Transfer ativo").setContentText("A monitorizar M-Pesa/E-Mola • " + total + " enviados").setSmallIcon(android.R.drawable.stat_sys_download_done).setOngoing(true).setPriority(2).build();
    }
}
