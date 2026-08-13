package com.kreysam.autosistematransfer;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;
import androidx.core.content.ContextCompat;
import com.kreysam.autosistematransfer.UssdAccessibilityService;
import com.kreysam.autosistematransfer.UssdTransferManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/* JADX INFO: loaded from: classes3.dex */
public class UssdTransferManager {
    public static final int MAX_MB_TRANSFERENCIA = 10240;
    private static final int MAX_TENTATIVAS_MMI = 2;
    private static final int MAX_TENTATIVAS_ULTIMA = 3;
    public static final int MIN_MB_TRANSFERENCIA = 100;
    private static final String TAG = "UssdTransferManager";
    private static final String USSD_CREDITO_MENU = "*111#";
    private static final String USSD_CREDITO_SALDO = "*100#";
    private static final String USSD_MENU = "*162#";
    private static final Handler handler = new Handler(Looper.getMainLooper());

    /* JADX INFO: Access modifiers changed from: private */
    interface ErroSimples {
        void onErro(String str);
    }

    public interface ResultadoCallback {
        void onErro(String str);

        void onFalhaSaldoInsuficiente(String str);

        void onSucesso(int i, int i2);
    }

    public interface ResultadoCreditoCallback {
        void onErro(String str);

        void onSucesso(int i);
    }

    public interface SaldoCallback {
        void onErro(int i, String str);

        void onSaldoLido(int i, int i2);
    }

    public interface SaldoCreditoCallback {
        void onErro(int i, String str);

        void onSaldoLido(int i, double d);
    }

    public static void transferir(Context ctx, int quantidadeMB, String numero, final ResultadoCallback callback) {
        String numeroLimpo = limparNumero(numero);
        if (numeroLimpo == null) {
            callback.onErro("Numero invalido: " + numero + " -- deve ter 9 digitos e comecar com 84 ou 85.");
            return;
        }
        if (quantidadeMB < 100) {
            callback.onErro("Quantidade insuficiente: " + quantidadeMB + "MB -- minimo e 100MB.");
            return;
        }
        if (quantidadeMB > 10240) {
            quantidadeMB = MAX_MB_TRANSFERENCIA;
        }
        Objects.requireNonNull(callback);
        if (temPermissoesENecessario(ctx, new ErroSimples() { // from class: com.kreysam.autosistematransfer.UssdTransferManager$$ExternalSyntheticLambda1
            @Override // com.kreysam.autosistematransfer.UssdTransferManager.ErroSimples
            public final void onErro(String str) {
                callback.onErro(str);
            }
        })) {
            TelaHelper.ligar(ctx);
            TtsHelper.falar("A transferir " + quantidadeMB + " megabytes para " + TtsHelper.numeroTelefonePorExtenso(numeroLimpo));
            ResultadoCallback callbackComTela = new AnonymousClass1(ctx, callback);
            int simInicial = Prefs.getSimAtivo(ctx);
            tentarComSim(ctx, quantidadeMB, numeroLimpo, simInicial, callbackComTela, 0, null);
        }
    }

    /* JADX INFO: renamed from: com.kreysam.autosistematransfer.UssdTransferManager$1, reason: invalid class name */
    class AnonymousClass1 implements ResultadoCallback {
        final /* synthetic */ ResultadoCallback val$callback;
        final /* synthetic */ Context val$ctx;

        AnonymousClass1(Context context, ResultadoCallback resultadoCallback) {
            this.val$ctx = context;
            this.val$callback = resultadoCallback;
        }

        @Override // com.kreysam.autosistematransfer.UssdTransferManager.ResultadoCallback
        public void onSucesso(final int sim, int saldoRestanteMB) {
            TelaHelper.desligar();
            int restantes = Prefs.getTransferenciasRestantes(this.val$ctx, sim);
            final String numeroFixo = Prefs.getNumeroFixo(this.val$ctx, sim);
            if (restantes == 0) {
                TtsHelper.falar("Transferido com sucesso. Atencao, nao ha mais transferencias disponiveis hoje neste Sim.");
                this.val$callback.onSucesso(sim, saldoRestanteMB);
                return;
            }
            if (restantes == 1 && !numeroFixo.isEmpty()) {
                TtsHelper.falar("Transferido com sucesso. Resta apenas uma transferencia. A transferir saldo restante para o numero fixo.");
                AppLog.add(this.val$ctx, UssdTransferManager.TAG, "Ultima transferencia do SIM " + sim + " -- confirmando pedido e iniciando para " + numeroFixo);
                this.val$callback.onSucesso(sim, saldoRestanteMB);
                Handler handler = UssdTransferManager.handler;
                final Context context = this.val$ctx;
                handler.postDelayed(new Runnable() { // from class: com.kreysam.autosistematransfer.UssdTransferManager$1$$ExternalSyntheticLambda0
                    @Override // java.lang.Runnable
                    public final void run() {
                        UssdTransferManager.tentarComSimUltima(context, numeroFixo, sim, 1);
                    }
                }, 3500L);
                return;
            }
            if (restantes == 1) {
                TtsHelper.falar("Transferido com sucesso. Resta apenas uma transferencia hoje neste Sim.");
                this.val$callback.onSucesso(sim, saldoRestanteMB);
            } else {
                TtsHelper.falar("Transferido com sucesso. Restam " + restantes + " transferencias hoje.");
                this.val$callback.onSucesso(sim, saldoRestanteMB);
            }
        }

        @Override // com.kreysam.autosistematransfer.UssdTransferManager.ResultadoCallback
        public void onFalhaSaldoInsuficiente(String detalhes) {
            TelaHelper.desligar();
            TtsHelper.falar("Falha na transferencia. Nenhum Sim disponivel.");
            this.val$callback.onFalhaSaldoInsuficiente(detalhes);
        }

        @Override // com.kreysam.autosistematransfer.UssdTransferManager.ResultadoCallback
        public void onErro(String motivo) {
            TelaHelper.desligar();
            TtsHelper.falar("Falha na transferencia.");
            this.val$callback.onErro(motivo);
        }
    }

    public static void transferirUltimaAgora(final Context ctx, int sim, String numeroFixo) {
        if (!temPermissoesENecessario(ctx, new ErroSimples() { // from class: com.kreysam.autosistematransfer.UssdTransferManager$$ExternalSyntheticLambda8
            @Override // com.kreysam.autosistematransfer.UssdTransferManager.ErroSimples
            public final void onErro(String str) {
                UssdTransferManager.lambda$transferirUltimaAgora$0(ctx, str);
            }
        })) {
            return;
        }
        TtsHelper.falar("A iniciar transferencia para o numero fixo.");
        AppLog.add(ctx, TAG, "transferirUltimaAgora: SIM " + sim + " para " + numeroFixo);
        tentarComSimUltima(ctx, numeroFixo, sim, 1);
    }

    static /* synthetic */ void lambda$transferirUltimaAgora$0(Context ctx, String motivo) {
        TtsHelper.falar("Nao foi possivel iniciar. " + motivo);
        AppLog.add(ctx, TAG, "transferirUltimaAgora falhou: " + motivo);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void tentarComSimUltima(final Context ctx, final String numeroFixo, final int sim, final int tentativa) {
        if (!simEhVodacom(ctx, sim)) {
            TtsHelper.falar("Este SIM nao e Vodacom. Transferencia final cancelada.");
            AppLog.add(ctx, TAG, "tentarComSimUltima: SIM " + sim + " nao e Vodacom.");
            return;
        }
        if (Prefs.getTransferenciasRestantes(ctx, sim) <= 0) {
            TtsHelper.falar("Sem transferencias disponiveis para a transferencia final.");
            AppLog.add(ctx, TAG, "tentarComSimUltima: SIM " + sim + " sem transferencias restantes.");
            return;
        }
        AppLog.add(ctx, TAG, "tentarComSimUltima: tentativa " + tentativa + "/3 para " + numeroFixo);
        TelaHelper.ligar(ctx);
        String erroDiscagem = discar(ctx, USSD_MENU, sim);
        if (erroDiscagem != null) {
            TelaHelper.desligar();
            if (tentativa < 3) {
                AppLog.add(ctx, TAG, "tentarComSimUltima: falha ao discar -- nova tentativa em 6s.");
                handler.postDelayed(new Runnable() { // from class: com.kreysam.autosistematransfer.UssdTransferManager$$ExternalSyntheticLambda11
                    @Override // java.lang.Runnable
                    public final void run() {
                        UssdTransferManager.tentarComSimUltima(ctx, numeroFixo, sim, tentativa + 1);
                    }
                }, 6000L);
                return;
            } else {
                TtsHelper.falar("Falha na transferencia final apos 3 tentativas.");
                return;
            }
        }
        handler.postDelayed(new Runnable() { // from class: com.kreysam.autosistematransfer.UssdTransferManager$$ExternalSyntheticLambda12
            @Override // java.lang.Runnable
            public final void run() {
                String str = numeroFixo;
                UssdAccessibilityService.iniciarTransferenciaComSaldoReal(str, new UssdTransferManager.AnonymousClass2(ctx, str, sim, tentativa));
            }
        }, 1200L);
    }

    /* JADX INFO: renamed from: com.kreysam.autosistematransfer.UssdTransferManager$2, reason: invalid class name */
    class AnonymousClass2 implements UssdAccessibilityService.TransferenciaCallback {
        final /* synthetic */ Context val$ctx;
        final /* synthetic */ String val$numeroFixo;
        final /* synthetic */ int val$sim;
        final /* synthetic */ int val$tentativa;

        AnonymousClass2(Context context, String str, int i, int i2) {
            this.val$ctx = context;
            this.val$numeroFixo = str;
            this.val$sim = i;
            this.val$tentativa = i2;
        }

        @Override // com.kreysam.autosistematransfer.UssdAccessibilityService.TransferenciaCallback
        public void onSaldoLido(int saldoMB) {
            TtsHelper.falar("A transferir " + Math.min(saldoMB, UssdTransferManager.MAX_MB_TRANSFERENCIA) + " megabytes para o numero fixo.");
            AppLog.add(this.val$ctx, UssdTransferManager.TAG, "Ultima transferencia: saldo=" + saldoMB + "MB, enviando=" + Math.min(saldoMB, UssdTransferManager.MAX_MB_TRANSFERENCIA) + "MB para " + this.val$numeroFixo);
        }

        @Override // com.kreysam.autosistematransfer.UssdAccessibilityService.TransferenciaCallback
        public void onSucesso(int saldoFinal) {
            TelaHelper.desligar();
            Prefs.setSaldo(this.val$ctx, this.val$sim, saldoFinal);
            Prefs.registrarTransferenciaUsada(this.val$ctx, this.val$sim);
            Prefs.setSimAtivo(this.val$ctx, this.val$sim);
            Prefs.setNumeroFixo(this.val$ctx, this.val$sim, "");
            TtsHelper.falar("Transferencia final concluida. Nao ha mais transferencias disponiveis hoje neste Sim.");
            AppLog.add(this.val$ctx, UssdTransferManager.TAG, "Ultima transferencia concluida para " + this.val$numeroFixo);
        }

        @Override // com.kreysam.autosistematransfer.UssdAccessibilityService.TransferenciaCallback
        public void onSaldoInsuficiente(int saldoAtual) {
            TelaHelper.desligar();
            TtsHelper.falar("Saldo restante " + saldoAtual + " megabytes, insuficiente para transferir.");
            AppLog.add(this.val$ctx, UssdTransferManager.TAG, "Ultima transferencia cancelada -- saldo " + saldoAtual + "MB abaixo de 100MB.");
        }

        @Override // com.kreysam.autosistematransfer.UssdAccessibilityService.TransferenciaCallback
        public void onLimiteDiarioAtingido() {
            TelaHelper.desligar();
            Prefs.forcarLimiteAtingido(this.val$ctx, this.val$sim);
            Prefs.setNumeroFixo(this.val$ctx, this.val$sim, "");
            TtsHelper.falar("Limite diario atingido.");
            AppLog.add(this.val$ctx, UssdTransferManager.TAG, "Ultima transferencia: limite diario atingido.");
        }

        @Override // com.kreysam.autosistematransfer.UssdAccessibilityService.TransferenciaCallback
        public void onErro(String motivo) {
            TelaHelper.desligar();
            AppLog.add(this.val$ctx, UssdTransferManager.TAG, "Ultima transferencia erro (tentativa " + this.val$tentativa + "): " + motivo);
            if (this.val$tentativa < 3) {
                TtsHelper.falar("Falha na transferencia final. A tentar novamente.");
                Handler handler = UssdTransferManager.handler;
                final Context context = this.val$ctx;
                final String str = this.val$numeroFixo;
                final int i = this.val$sim;
                final int i2 = this.val$tentativa;
                handler.postDelayed(new Runnable() { // from class: com.kreysam.autosistematransfer.UssdTransferManager$2$$ExternalSyntheticLambda0
                    @Override // java.lang.Runnable
                    public final void run() {
                        UssdTransferManager.tentarComSimUltima(context, str, i, i2 + 1);
                    }
                }, 6000L);
                return;
            }
            TtsHelper.falar("Falha na transferencia final apos 3 tentativas.");
        }
    }

    private static String limparNumero(String bruto) {
        if (bruto == null) {
            return null;
        }
        String digitos = bruto.replaceAll("[^0-9]", "");
        if (digitos.length() == 12 && digitos.startsWith("258")) {
            digitos = digitos.substring(3);
        }
        if (digitos.length() != 9 || (!digitos.startsWith("84") && !digitos.startsWith("85"))) {
            return null;
        }
        return digitos;
    }

    public static void consultarSaldo(final Context ctx, final int sim, final SaldoCallback callback) {
        if (temPermissoesENecessario(ctx, new ErroSimples() { // from class: com.kreysam.autosistematransfer.UssdTransferManager$$ExternalSyntheticLambda6
            @Override // com.kreysam.autosistematransfer.UssdTransferManager.ErroSimples
            public final void onErro(String str) {
                callback.onErro(sim, str);
            }
        })) {
            final SaldoCallback callbackComTela = new SaldoCallback() { // from class: com.kreysam.autosistematransfer.UssdTransferManager.3
                @Override // com.kreysam.autosistematransfer.UssdTransferManager.SaldoCallback
                public void onSaldoLido(int simRetornado, int saldoMB) {
                    String txtRestantes;
                    TelaHelper.desligar();
                    int restantes = Prefs.getTransferenciasRestantes(ctx, simRetornado);
                    if (restantes == 0) {
                        txtRestantes = "Sem transferencias disponiveis hoje.";
                    } else if (restantes == 1) {
                        txtRestantes = "Resta uma transferencia hoje.";
                    } else {
                        txtRestantes = "Restam " + restantes + " transferencias hoje.";
                    }
                    TtsHelper.falar("Saldo do Sim " + simRetornado + ": " + saldoMB + " megabytes. " + txtRestantes);
                    callback.onSaldoLido(simRetornado, saldoMB);
                }

                @Override // com.kreysam.autosistematransfer.UssdTransferManager.SaldoCallback
                public void onErro(int simRetornado, String motivo) {
                    TelaHelper.desligar();
                    callback.onErro(simRetornado, motivo);
                }
            };
            if (!simEhVodacom(ctx, sim)) {
                callbackComTela.onErro(sim, "Este SIM nao e Vodacom -- o *162# so funciona na rede Vodacom.");
                return;
            }
            TelaHelper.ligar(ctx);
            TtsHelper.falar("A consultar saldo do Sim " + sim + ".");
            String erroDiscagem = discar(ctx, USSD_MENU, sim);
            if (erroDiscagem != null) {
                callbackComTela.onErro(sim, "Falha ao discar *162# no SIM " + sim + ": " + erroDiscagem);
            } else {
                handler.postDelayed(new Runnable() { // from class: com.kreysam.autosistematransfer.UssdTransferManager$$ExternalSyntheticLambda7
                    @Override // java.lang.Runnable
                    public final void run() {
                        UssdAccessibilityService.iniciarConsultaSaldo(new UssdAccessibilityService.TransferenciaCallback() { // from class: com.kreysam.autosistematransfer.UssdTransferManager.4
                            @Override // com.kreysam.autosistematransfer.UssdAccessibilityService.TransferenciaCallback
                            public void onSaldoLido(int saldoMB) {
                            }

                            @Override // com.kreysam.autosistematransfer.UssdAccessibilityService.TransferenciaCallback
                            public void onSucesso(int saldoLido) {
                                Prefs.setSaldo(context, i, saldoLido);
                                saldoCallback.onSaldoLido(i, saldoLido);
                            }

                            @Override // com.kreysam.autosistematransfer.UssdAccessibilityService.TransferenciaCallback
                            public void onSaldoInsuficiente(int saldoAtual) {
                                saldoCallback.onErro(i, "Resposta inesperada da operadora ao consultar saldo.");
                            }

                            @Override // com.kreysam.autosistematransfer.UssdAccessibilityService.TransferenciaCallback
                            public void onLimiteDiarioAtingido() {
                                Prefs.forcarLimiteAtingido(context, i);
                                AppLog.add(context, UssdTransferManager.TAG, "Consulta ao SIM " + i + " voltou limite diario atingido -- contador local sincronizado.");
                                saldoCallback.onErro(i, "Limite diário de 10 transferências já atingido neste SIM (confirmado pela operadora).");
                            }

                            @Override // com.kreysam.autosistematransfer.UssdAccessibilityService.TransferenciaCallback
                            public void onErro(String motivo) {
                                if ("MMI_INVALIDO".equals(motivo)) {
                                    saldoCallback.onErro(i, "Código MMI inválido — tenta consultar de novo.");
                                } else {
                                    saldoCallback.onErro(i, motivo);
                                }
                            }
                        });
                    }
                }, 1200L);
            }
        }
    }

    private static boolean temPermissoesENecessario(Context ctx, ErroSimples onErro) {
        if (ContextCompat.checkSelfPermission(ctx, "android.permission.CALL_PHONE") != 0) {
            onErro.onErro("Falta permissão CALL_PHONE — toca em 'Permitir chamadas (USSD)' e aceita o popup do sistema.");
            return false;
        }
        if (!UssdAccessibilityService.estaAtivo()) {
            onErro.onErro("Serviço de acessibilidade não está ativo. Ativa em Definições > Acessibilidade > Autosistema Transfer.");
            return false;
        }
        return true;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String combinarMotivos(String motivoAnterior, String motivoAtual) {
        if (motivoAnterior == null || motivoAnterior.isEmpty()) {
            return motivoAtual;
        }
        return motivoAnterior + " | " + motivoAtual;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void tentarComSim(final Context ctx, final int quantidadeMB, final String numero, final int sim, final ResultadoCallback callback, final int tentativaMmi, final String motivoSimAnterior) {
        final int outroSim = 3 - sim;
        final boolean primeiraTentativaDestaTransferencia = motivoSimAnterior == null;
        if (!simEhVodacom(ctx, sim)) {
            String meuMotivo = "SIM " + sim + ": nao e Vodacom";
            AppLog.add(ctx, TAG, meuMotivo);
            if (primeiraTentativaDestaTransferencia) {
                Prefs.setSimAtivo(ctx, outroSim);
                tentarComSim(ctx, quantidadeMB, numero, outroSim, callback, 0, meuMotivo);
                return;
            } else {
                callback.onErro(combinarMotivos(motivoSimAnterior, meuMotivo));
                return;
            }
        }
        if (Prefs.getTransferenciasRestantes(ctx, sim) <= 0) {
            String meuMotivo2 = "SIM " + sim + ": sem transferencias restantes hoje";
            AppLog.add(ctx, TAG, meuMotivo2 + " -- recusando SEM discar.");
            if (primeiraTentativaDestaTransferencia) {
                Prefs.setSimAtivo(ctx, outroSim);
                tentarComSim(ctx, quantidadeMB, numero, outroSim, callback, 0, meuMotivo2);
                return;
            } else {
                callback.onFalhaSaldoInsuficiente(combinarMotivos(motivoSimAnterior, meuMotivo2));
                return;
            }
        }
        Log.i(TAG, "Tentando SIM " + sim + " — " + quantidadeMB + "MB para " + numero + " (tentativaMmi=" + tentativaMmi + ")");
        AppLog.add(ctx, TAG, "Tentando SIM " + sim + " -- " + quantidadeMB + "MB para " + numero + " (tentativaMmi=" + tentativaMmi + ")");
        String erroDiscagem = discar(ctx, USSD_MENU, sim);
        if (erroDiscagem != null) {
            callback.onErro(combinarMotivos(motivoSimAnterior, "SIM " + sim + ": falha ao discar (" + erroDiscagem + ")"));
        } else {
            handler.postDelayed(new Runnable() { // from class: com.kreysam.autosistematransfer.UssdTransferManager$$ExternalSyntheticLambda0
                @Override // java.lang.Runnable
                public final void run() {
                    int i = quantidadeMB;
                    String str = numero;
                    UssdAccessibilityService.iniciarProcessamento(i, str, new UssdTransferManager.AnonymousClass5(ctx, sim, callback, primeiraTentativaDestaTransferencia, outroSim, i, str, motivoSimAnterior, tentativaMmi));
                }
            }, 1200L);
        }
    }

    /* JADX INFO: renamed from: com.kreysam.autosistematransfer.UssdTransferManager$5, reason: invalid class name */
    class AnonymousClass5 implements UssdAccessibilityService.TransferenciaCallback {
        final /* synthetic */ ResultadoCallback val$callback;
        final /* synthetic */ Context val$ctx;
        final /* synthetic */ String val$motivoSimAnterior;
        final /* synthetic */ String val$numero;
        final /* synthetic */ int val$outroSim;
        final /* synthetic */ boolean val$primeiraTentativaDestaTransferencia;
        final /* synthetic */ int val$quantidadeMB;
        final /* synthetic */ int val$sim;
        final /* synthetic */ int val$tentativaMmi;

        AnonymousClass5(Context context, int i, ResultadoCallback resultadoCallback, boolean z, int i2, int i3, String str, String str2, int i4) {
            this.val$ctx = context;
            this.val$sim = i;
            this.val$callback = resultadoCallback;
            this.val$primeiraTentativaDestaTransferencia = z;
            this.val$outroSim = i2;
            this.val$quantidadeMB = i3;
            this.val$numero = str;
            this.val$motivoSimAnterior = str2;
            this.val$tentativaMmi = i4;
        }

        @Override // com.kreysam.autosistematransfer.UssdAccessibilityService.TransferenciaCallback
        public void onSaldoLido(int saldoMB) {
            Prefs.setSaldo(this.val$ctx, this.val$sim, saldoMB);
            Log.i(UssdTransferManager.TAG, "Saldo lido no SIM " + this.val$sim + ": " + saldoMB + "MB");
            AppLog.add(this.val$ctx, UssdTransferManager.TAG, "Saldo lido no SIM " + this.val$sim + ": " + saldoMB + "MB");
        }

        @Override // com.kreysam.autosistematransfer.UssdAccessibilityService.TransferenciaCallback
        public void onSucesso(int saldoRestanteMB) {
            Prefs.setSaldo(this.val$ctx, this.val$sim, saldoRestanteMB);
            Prefs.registrarTransferenciaUsada(this.val$ctx, this.val$sim);
            Prefs.setSimAtivo(this.val$ctx, this.val$sim);
            if (Prefs.getTransferenciasRestantes(this.val$ctx, this.val$sim) == 0) {
                Prefs.setNumeroFixo(this.val$ctx, this.val$sim, "");
            }
            this.val$callback.onSucesso(this.val$sim, saldoRestanteMB);
        }

        @Override // com.kreysam.autosistematransfer.UssdAccessibilityService.TransferenciaCallback
        public void onSaldoInsuficiente(int saldoAtual) {
            String meuMotivo = "SIM " + this.val$sim + ": saldo insuficiente (" + saldoAtual + "MB)";
            if (this.val$primeiraTentativaDestaTransferencia) {
                Log.w(UssdTransferManager.TAG, "SIM " + this.val$sim + " sem saldo (" + saldoAtual + "MB) — tentando SIM " + this.val$outroSim + "...");
                AppLog.add(this.val$ctx, UssdTransferManager.TAG, "SIM " + this.val$sim + " sem saldo (" + saldoAtual + "MB) -- tentando SIM " + this.val$outroSim + "...");
                Prefs.setSimAtivo(this.val$ctx, this.val$outroSim);
                UssdTransferManager.tentarComSim(this.val$ctx, this.val$quantidadeMB, this.val$numero, this.val$outroSim, this.val$callback, 0, meuMotivo);
                return;
            }
            Log.w(UssdTransferManager.TAG, "SIM " + this.val$sim + " também sem saldo — nenhum SIM disponível.");
            AppLog.add(this.val$ctx, UssdTransferManager.TAG, "SIM " + this.val$sim + " tambem sem saldo -- nenhum SIM disponivel.");
            this.val$callback.onFalhaSaldoInsuficiente(UssdTransferManager.combinarMotivos(this.val$motivoSimAnterior, meuMotivo));
        }

        @Override // com.kreysam.autosistematransfer.UssdAccessibilityService.TransferenciaCallback
        public void onLimiteDiarioAtingido() {
            Prefs.forcarLimiteAtingido(this.val$ctx, this.val$sim);
            Prefs.setNumeroFixo(this.val$ctx, this.val$sim, "");
            String meuMotivo = "SIM " + this.val$sim + ": limite diario atingido";
            Log.w(UssdTransferManager.TAG, "SIM " + this.val$sim + " confirmou limite diário atingido — contador local sincronizado.");
            AppLog.add(this.val$ctx, UssdTransferManager.TAG, meuMotivo + " -- contador local sincronizado.");
            if (!this.val$primeiraTentativaDestaTransferencia) {
                this.val$callback.onFalhaSaldoInsuficiente(UssdTransferManager.combinarMotivos(this.val$motivoSimAnterior, meuMotivo));
            } else {
                Prefs.setSimAtivo(this.val$ctx, this.val$outroSim);
                UssdTransferManager.tentarComSim(this.val$ctx, this.val$quantidadeMB, this.val$numero, this.val$outroSim, this.val$callback, 0, meuMotivo);
            }
        }

        @Override // com.kreysam.autosistematransfer.UssdAccessibilityService.TransferenciaCallback
        public void onErro(String motivo) {
            if ("MMI_INVALIDO".equals(motivo)) {
                if (this.val$tentativaMmi < 2) {
                    Handler handler = UssdTransferManager.handler;
                    final Context context = this.val$ctx;
                    final int i = this.val$quantidadeMB;
                    final String str = this.val$numero;
                    final int i2 = this.val$sim;
                    final ResultadoCallback resultadoCallback = this.val$callback;
                    final int i3 = this.val$tentativaMmi;
                    final String str2 = this.val$motivoSimAnterior;
                    handler.postDelayed(new Runnable() { // from class: com.kreysam.autosistematransfer.UssdTransferManager$5$$ExternalSyntheticLambda0
                        @Override // java.lang.Runnable
                        public final void run() {
                            UssdTransferManager.tentarComSim(context, i, str, i2, resultadoCallback, i3 + 1, str2);
                        }
                    }, 3000L);
                    return;
                }
                String meuMotivo = "SIM " + this.val$sim + ": MMI invalido " + (this.val$tentativaMmi + 1) + "x seguidas";
                Log.w(UssdTransferManager.TAG, "SIM " + this.val$sim + " — MMI inválido " + (this.val$tentativaMmi + 1) + "x seguidas, desistindo.");
                this.val$callback.onErro(UssdTransferManager.combinarMotivos(this.val$motivoSimAnterior, meuMotivo));
                return;
            }
            if (!"PROPRIO_NUMERO".equals(motivo)) {
                if ("MMI_INVALIDO_POS_NUMERO".equals(motivo) || motivo.startsWith("ERRO_POS_NUMERO:")) {
                    UssdTransferManager.reconciliarAposErroAmbiguo(this.val$ctx, this.val$sim, this.val$quantidadeMB, this.val$numero, motivo, this.val$callback, this.val$motivoSimAnterior);
                    return;
                } else {
                    this.val$callback.onErro(UssdTransferManager.combinarMotivos(this.val$motivoSimAnterior, "SIM " + this.val$sim + ": " + motivo));
                    return;
                }
            }
            String meuMotivo2 = "SIM " + this.val$sim + ": numero e o proprio numero deste SIM";
            if (!this.val$primeiraTentativaDestaTransferencia) {
                this.val$callback.onErro(UssdTransferManager.combinarMotivos(this.val$motivoSimAnterior, meuMotivo2));
                return;
            }
            Log.w(UssdTransferManager.TAG, "SIM " + this.val$sim + " recusou -- numero de destino e o proprio numero deste SIM. Tentando SIM " + this.val$outroSim + "...");
            AppLog.add(this.val$ctx, UssdTransferManager.TAG, meuMotivo2 + " -- tentando SIM " + this.val$outroSim + "...");
            Prefs.setSimAtivo(this.val$ctx, this.val$outroSim);
            UssdTransferManager.tentarComSim(this.val$ctx, this.val$quantidadeMB, this.val$numero, this.val$outroSim, this.val$callback, 0, meuMotivo2);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void reconciliarAposErroAmbiguo(final Context ctx, final int sim, final int quantidadeMB, String numero, final String motivoOriginal, final ResultadoCallback callback, final String motivoSimAnterior) {
        AppLog.add(ctx, TAG, "SIM " + sim + ": erro ambiguo APOS enviar o numero (" + motivoOriginal + ") -- a consultar saldo para confirmar se saiu.");
        final long saldoAntes = Prefs.getSaldo(ctx, sim);
        consultarSaldo(ctx, sim, new SaldoCallback() { // from class: com.kreysam.autosistematransfer.UssdTransferManager.6
            @Override // com.kreysam.autosistematransfer.UssdTransferManager.SaldoCallback
            public void onSaldoLido(int simRetornado, int saldoAtual) {
                long j = saldoAntes;
                if (j >= 0 && saldoAtual == j - ((long) quantidadeMB)) {
                    AppLog.add(ctx, UssdTransferManager.TAG, "Reconciliacao SIM " + sim + ": saldo baixou exatamente " + quantidadeMB + "MB (antes=" + saldoAntes + " agora=" + saldoAtual + ") -- transferencia CONFIRMADA, so a confirmacao visual se perdeu.");
                    Prefs.registrarTransferenciaUsada(ctx, sim);
                    Prefs.setSimAtivo(ctx, sim);
                    callback.onSucesso(sim, saldoAtual);
                    return;
                }
                AppLog.add(ctx, UssdTransferManager.TAG, "Reconciliacao SIM " + sim + ": saldo nao baixou como esperado (antes=" + saldoAntes + " agora=" + saldoAtual + ") -- transferencia falhou de verdade.");
                callback.onErro(UssdTransferManager.combinarMotivos(motivoSimAnterior, "SIM " + sim + ": " + motivoOriginal + " (confirmado que nao saiu -- saldo atual " + saldoAtual + "MB)"));
            }

            @Override // com.kreysam.autosistematransfer.UssdTransferManager.SaldoCallback
            public void onErro(int simRetornado, String motivoConsulta) {
                String motivoBaixo = motivoConsulta == null ? "" : motivoConsulta.toLowerCase();
                if (motivoBaixo.contains("limite") && motivoBaixo.contains("atingido")) {
                    AppLog.add(ctx, UssdTransferManager.TAG, "Reconciliacao SIM " + sim + ": consulta retornou limite diario atingido -- transferencia CONFIRMADA (so bateu o limite porque ela contou).");
                    Prefs.registrarTransferenciaUsada(ctx, sim);
                    Prefs.setSimAtivo(ctx, sim);
                    callback.onSucesso(sim, -1);
                    return;
                }
                AppLog.add(ctx, UssdTransferManager.TAG, "Reconciliacao SIM " + sim + ": falha ao consultar saldo (" + motivoConsulta + ") -- reportando erro original.");
                callback.onErro(UssdTransferManager.combinarMotivos(motivoSimAnterior, "SIM " + sim + ": " + motivoOriginal + " (nao foi possivel confirmar via consulta: " + motivoConsulta + ")"));
            }
        });
    }

    public static boolean simEhVodacom(Context ctx, int sim) {
        List<SubscriptionInfo> infos;
        try {
            SubscriptionManager sm = (SubscriptionManager) ctx.getSystemService("telephony_subscription_service");
            if (sm == null || (infos = sm.getActiveSubscriptionInfoList()) == null) {
                return true;
            }
            for (SubscriptionInfo info : infos) {
                if (info.getSimSlotIndex() == sim - 1) {
                    CharSequence nomeOperadora = info.getCarrierName();
                    boolean ehVodacom = nomeOperadora != null && nomeOperadora.toString().toLowerCase().contains("vodacom");
                    if (!ehVodacom) {
                        Log.w(TAG, "SIM " + sim + " nao e Vodacom (operadora: " + ((Object) nomeOperadora) + ") -- vai ser ignorado para USSD.");
                    }
                    return ehVodacom;
                }
            }
            return true;
        } catch (SecurityException e) {
            Log.w(TAG, "Sem permissao para verificar operadora do SIM " + sim + " -- deixando tentar normalmente.");
            return true;
        }
    }

    public static void consultarSaldoCredito(final Context ctx, final int sim, final SaldoCreditoCallback callback) {
        if (temPermissoesENecessario(ctx, new ErroSimples() { // from class: com.kreysam.autosistematransfer.UssdTransferManager$$ExternalSyntheticLambda2
            @Override // com.kreysam.autosistematransfer.UssdTransferManager.ErroSimples
            public final void onErro(String str) {
                callback.onErro(sim, str);
            }
        })) {
            TelaHelper.ligar(ctx);
            TtsHelper.falar("A consultar saldo de credito do Sim " + sim + ".");
            String erroDiscagem = discar(ctx, USSD_CREDITO_SALDO, sim);
            if (erroDiscagem != null) {
                TelaHelper.desligar();
                callback.onErro(sim, "Falha ao discar *100# no SIM " + sim + ": " + erroDiscagem);
            } else {
                handler.postDelayed(new Runnable() { // from class: com.kreysam.autosistematransfer.UssdTransferManager$$ExternalSyntheticLambda3
                    @Override // java.lang.Runnable
                    public final void run() {
                        UssdAccessibilityService.iniciarConsultaSaldoCredito(new UssdAccessibilityService.CreditoCallback() { // from class: com.kreysam.autosistematransfer.UssdTransferManager.7
                            @Override // com.kreysam.autosistematransfer.UssdAccessibilityService.CreditoCallback
                            public void onSaldoLido(double saldoMT) {
                                TelaHelper.desligar();
                                TtsHelper.falar("Saldo de credito do Sim " + i + ": " + saldoMT + " meticais.");
                                AppLog.add(context, UssdTransferManager.TAG, "Saldo credito SIM " + i + ": " + saldoMT + "MT");
                                saldoCreditoCallback.onSaldoLido(i, saldoMT);
                            }

                            @Override // com.kreysam.autosistematransfer.UssdAccessibilityService.CreditoCallback
                            public void onSucesso() {
                            }

                            @Override // com.kreysam.autosistematransfer.UssdAccessibilityService.CreditoCallback
                            public void onErro(String motivo) {
                                TelaHelper.desligar();
                                AppLog.add(context, UssdTransferManager.TAG, "Erro ao consultar saldo credito SIM " + i + ": " + motivo);
                                saldoCreditoCallback.onErro(i, motivo);
                            }
                        });
                    }
                }, 1200L);
            }
        }
    }

    public static void transferirCredito(Context ctx, String valorMT, String numero, final ResultadoCreditoCallback callback) {
        String numeroLimpo = limparNumero(numero);
        if (numeroLimpo == null) {
            callback.onErro("Numero invalido: " + numero + " -- deve ter 9 digitos e comecar com 84 ou 85.");
            return;
        }
        try {
            double valor = Double.parseDouble(valorMT.replace(",", "."));
            int simDisponivel = Prefs.getSimCreditoDisponivel(ctx);
            if (simDisponivel == 0) {
                AppLog.add(ctx, TAG, "App indisponivel para transferir credito (toggle definido para Nenhum).");
                ApiClient.enviarHeartbeat(ctx);
                callback.onErro("App indisponivel para transferir credito (toggle definido para Nenhum).");
            } else {
                Objects.requireNonNull(callback);
                if (temPermissoesENecessario(ctx, new ErroSimples() { // from class: com.kreysam.autosistematransfer.UssdTransferManager$$ExternalSyntheticLambda5
                    @Override // com.kreysam.autosistematransfer.UssdTransferManager.ErroSimples
                    public final void onErro(String str) {
                        callback.onErro(str);
                    }
                })) {
                    precheckEtentarCredito(ctx, valorMT, valor, numeroLimpo, simDisponivel, callback);
                }
            }
        } catch (NumberFormatException e) {
            callback.onErro("Valor de credito invalido: " + valorMT);
        }
    }

    private static void precheckEtentarCredito(final Context ctx, final String valorMT, final double valor, final String numero, final int sim, final ResultadoCreditoCallback callback) {
        TelaHelper.ligar(ctx);
        String erroDisc = discar(ctx, USSD_CREDITO_SALDO, sim);
        if (erroDisc != null) {
            TelaHelper.desligar();
            callback.onErro("SIM " + sim + ": falha ao discar *100# (" + erroDisc + ")");
        } else {
            handler.postDelayed(new Runnable() { // from class: com.kreysam.autosistematransfer.UssdTransferManager$$ExternalSyntheticLambda10
                @Override // java.lang.Runnable
                public final void run() {
                    UssdAccessibilityService.iniciarConsultaSaldoCredito(new UssdAccessibilityService.CreditoCallback() { // from class: com.kreysam.autosistematransfer.UssdTransferManager.8
                        @Override // com.kreysam.autosistematransfer.UssdAccessibilityService.CreditoCallback
                        public void onSaldoLido(double saldoMT) {
                            TelaHelper.desligar();
                            AppLog.add(context, UssdTransferManager.TAG, "Pre-check credito SIM " + i + ": saldo=" + saldoMT + "MT, pedido=" + d + "MT");
                            if (saldoMT < d) {
                                String meuMotivo = "SIM " + i + ": saldo insuficiente (" + saldoMT + "MT < " + d + "MT)";
                                TtsHelper.falar("Saldo insuficiente no Sim " + i + ": " + saldoMT + " meticais.");
                                AppLog.add(context, UssdTransferManager.TAG, meuMotivo + " -- sem fallback para credito, devolvendo falha ao painel.");
                                resultadoCreditoCallback.onErro(meuMotivo);
                                return;
                            }
                            Prefs.setSaldoCredito(context, i, saldoMT);
                            TtsHelper.falar("A transferir " + str + " meticais para " + TtsHelper.numeroTelefonePorExtenso(str) + ".");
                            UssdTransferManager.executarTransferenciaCredito(context, str, d, str, i, resultadoCreditoCallback);
                        }

                        @Override // com.kreysam.autosistematransfer.UssdAccessibilityService.CreditoCallback
                        public void onSucesso() {
                        }

                        @Override // com.kreysam.autosistematransfer.UssdAccessibilityService.CreditoCallback
                        public void onErro(String motivo) {
                            TelaHelper.desligar();
                            String meuMotivo = "SIM " + i + ": erro ao verificar saldo (" + motivo + ")";
                            AppLog.add(context, UssdTransferManager.TAG, meuMotivo);
                            resultadoCreditoCallback.onErro(meuMotivo);
                        }
                    });
                }
            }, 1200L);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void executarTransferenciaCredito(final Context ctx, final String valorMT, final double valor, final String numero, final int sim, final ResultadoCreditoCallback callback) {
        TelaHelper.ligar(ctx);
        String erroDisc = discar(ctx, USSD_CREDITO_MENU, sim);
        if (erroDisc != null) {
            TelaHelper.desligar();
            callback.onErro("SIM " + sim + ": falha ao discar *111# (" + erroDisc + ")");
        } else {
            handler.postDelayed(new Runnable() { // from class: com.kreysam.autosistematransfer.UssdTransferManager$$ExternalSyntheticLambda9
                @Override // java.lang.Runnable
                public final void run() {
                    String str = valorMT;
                    String str2 = numero;
                    UssdAccessibilityService.iniciarTransferenciaCredito(str, str2, new UssdAccessibilityService.CreditoCallback() { // from class: com.kreysam.autosistematransfer.UssdTransferManager.9
                        @Override // com.kreysam.autosistematransfer.UssdAccessibilityService.CreditoCallback
                        public void onSaldoLido(double saldoMT) {
                        }

                        @Override // com.kreysam.autosistematransfer.UssdAccessibilityService.CreditoCallback
                        public void onSucesso() {
                            TelaHelper.desligar();
                            TtsHelper.falar("Credito transferido com sucesso.");
                            AppLog.add(context, UssdTransferManager.TAG, "Credito " + str + "MT para " + str2 + " via SIM " + i + " -- sucesso.");
                            resultadoCreditoCallback.onSucesso(i);
                        }

                        @Override // com.kreysam.autosistematransfer.UssdAccessibilityService.CreditoCallback
                        public void onErro(String motivo) {
                            TelaHelper.desligar();
                            if ("MMI_INVALIDO_POS_NUMERO".equals(motivo) || motivo.startsWith("ERRO_POS_NUMERO:")) {
                                AppLog.add(context, UssdTransferManager.TAG, "Credito SIM " + i + ": erro ambiguo apos enviar numero (" + motivo + ") -- a consultar saldo *100# para confirmar.");
                                UssdTransferManager.reconciliarCreditoAposErro(context, str, d, str2, i, motivo, resultadoCreditoCallback);
                            } else {
                                String meuMotivo = "SIM " + i + ": " + motivo;
                                AppLog.add(context, UssdTransferManager.TAG, "Erro transferencia credito: " + meuMotivo);
                                resultadoCreditoCallback.onErro(meuMotivo);
                            }
                        }
                    });
                }
            }, 1200L);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void reconciliarCreditoAposErro(final Context ctx, String valorMT, final double valor, String numero, final int sim, final String motivoOriginal, final ResultadoCreditoCallback callback) {
        final double saldoAntes = Prefs.getSaldoCredito(ctx, sim);
        TelaHelper.ligar(ctx);
        String erroDisc = discar(ctx, USSD_CREDITO_SALDO, sim);
        if (erroDisc != null) {
            TelaHelper.desligar();
            AppLog.add(ctx, TAG, "Reconciliacao credito: falha ao discar *100# -- reportando erro original.");
            callback.onErro("SIM " + sim + ": " + motivoOriginal + " (nao foi possivel confirmar via *100#)");
            return;
        }
        handler.postDelayed(new Runnable() { // from class: com.kreysam.autosistematransfer.UssdTransferManager$$ExternalSyntheticLambda4
            @Override // java.lang.Runnable
            public final void run() {
                UssdAccessibilityService.iniciarConsultaSaldoCredito(new UssdAccessibilityService.CreditoCallback() { // from class: com.kreysam.autosistematransfer.UssdTransferManager.10
                    @Override // com.kreysam.autosistematransfer.UssdAccessibilityService.CreditoCallback
                    public void onSaldoLido(double saldoAtual) {
                        TelaHelper.desligar();
                        double d = d;
                        if (d >= 0.0d && saldoAtual <= d - d) {
                            AppLog.add(context, UssdTransferManager.TAG, "Reconciliacao credito SIM " + i + ": saldo baixou de " + d + "MT para " + saldoAtual + "MT -- transferencia CONFIRMADA.");
                            TtsHelper.falar("Credito transferido com sucesso.");
                            resultadoCreditoCallback.onSucesso(i);
                        } else {
                            AppLog.add(context, UssdTransferManager.TAG, "Reconciliacao credito SIM " + i + ": saldo nao baixou como esperado (antes=" + d + "MT agora=" + saldoAtual + "MT) -- falhou de verdade.");
                            resultadoCreditoCallback.onErro("SIM " + i + ": " + str + " (confirmado que nao saiu -- saldo atual " + saldoAtual + "MT)");
                        }
                    }

                    @Override // com.kreysam.autosistematransfer.UssdAccessibilityService.CreditoCallback
                    public void onSucesso() {
                    }

                    @Override // com.kreysam.autosistematransfer.UssdAccessibilityService.CreditoCallback
                    public void onErro(String motivoConsulta) {
                        TelaHelper.desligar();
                        AppLog.add(context, UssdTransferManager.TAG, "Reconciliacao credito SIM " + i + ": falha ao consultar saldo -- reportando erro original.");
                        resultadoCreditoCallback.onErro("SIM " + i + ": " + str + " (nao foi possivel confirmar: " + motivoConsulta + ")");
                    }
                });
            }
        }, 1200L);
    }

    private static String discar(Context ctx, String codigo, int sim) {
        try {
            Intent intent = new Intent("android.intent.action.CALL");
            intent.setData(Uri.parse("tel:" + Uri.encode(codigo)));
            intent.addFlags(268435456);
            TelecomManager tm = (TelecomManager) ctx.getSystemService("telecom");
            if (tm == null) {
                return "TelecomManager indisponível no sistema.";
            }
            List<PhoneAccountHandle> todas = tm.getCallCapablePhoneAccounts();
            List<PhoneAccountHandle> simsDisponiveis = new ArrayList<>();
            for (PhoneAccountHandle h : todas) {
                PhoneAccount pa = tm.getPhoneAccount(h);
                if (pa != null && pa.hasCapabilities(4)) {
                    simsDisponiveis.add(h);
                }
            }
            if (simsDisponiveis.isEmpty()) {
                return "Nenhum SIM com capacidade de chamada foi detectado (todas=" + todas.size() + ").";
            }
            int idx = sim - 1;
            if (simsDisponiveis.size() > idx && idx >= 0) {
                intent.putExtra("android.telecom.extra.PHONE_ACCOUNT_HANDLE", simsDisponiveis.get(idx));
            } else {
                intent.putExtra("android.telecom.extra.PHONE_ACCOUNT_HANDLE", simsDisponiveis.get(0));
            }
            ctx.startActivity(intent);
            return null;
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException ao discar: " + e.getMessage(), e);
            return "SecurityException — permissão CALL_PHONE não concedida em tempo real: " + e.getMessage();
        } catch (Exception e2) {
            Log.e(TAG, "Erro ao discar: " + e2.getMessage(), e2);
            return e2.getClass().getSimpleName() + ": " + e2.getMessage();
        }
    }
}
