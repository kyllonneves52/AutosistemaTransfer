package com.kreysam.autosistematransfer;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

/* JADX INFO: loaded from: classes3.dex */
public class TelaHelper {
    private static final String TAG = "TelaHelper";
    private static final long TIMEOUT_SEGURANCA_MS = 90000;
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static Runnable timeoutRunnable;
    private static PowerManager.WakeLock wakeLockAtual;

    public static synchronized void ligar(Context ctx) {
        PowerManager.WakeLock wakeLock;
        try {
            wakeLock = wakeLockAtual;
        } catch (Exception e) {
            Log.e(TAG, "Falha ao ligar a tela: " + e.getMessage());
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            reagendarTimeout(ctx);
            return;
        }
        PowerManager pm = (PowerManager) ctx.getApplicationContext().getSystemService("power");
        if (pm == null) {
            return;
        }
        PowerManager.WakeLock wakeLockNewWakeLock = pm.newWakeLock(805306378, "AutosistemaTransfer:UssdTela");
        wakeLockAtual = wakeLockNewWakeLock;
        wakeLockNewWakeLock.acquire(TIMEOUT_SEGURANCA_MS);
        Log.i(TAG, "Tela ligada para operacao USSD.");
        AppLog.add(ctx, TAG, "Tela ligada para operacao USSD.");
        reagendarTimeout(ctx);
    }

    public static synchronized void desligar() {
        try {
            try {
                Runnable runnable = timeoutRunnable;
                if (runnable != null) {
                    handler.removeCallbacks(runnable);
                    timeoutRunnable = null;
                }
                PowerManager.WakeLock wakeLock = wakeLockAtual;
                if (wakeLock != null && wakeLock.isHeld()) {
                    wakeLockAtual.release();
                    Log.i(TAG, "Tela libertada -- operacao USSD terminou.");
                }
            } catch (Exception e) {
                Log.e(TAG, "Falha ao libertar a tela: " + e.getMessage());
            }
        } finally {
            wakeLockAtual = null;
        }
    }

    private static void reagendarTimeout(final Context ctx) {
        Runnable runnable = timeoutRunnable;
        if (runnable != null) {
            handler.removeCallbacks(runnable);
        }
        Runnable runnable2 = new Runnable() { // from class: com.kreysam.autosistematransfer.TelaHelper$$ExternalSyntheticLambda0
            @Override // java.lang.Runnable
            public final void run() {
                TelaHelper.lambda$reagendarTimeout$0(ctx);
            }
        };
        timeoutRunnable = runnable2;
        handler.postDelayed(runnable2, TIMEOUT_SEGURANCA_MS);
    }

    static /* synthetic */ void lambda$reagendarTimeout$0(Context ctx) {
        Log.w(TAG, "Timeout de seguranca (90s) -- libertando a tela mesmo sem confirmacao de fim.");
        AppLog.add(ctx, TAG, "Timeout de seguranca (90s) -- libertando a tela mesmo sem confirmacao de fim.");
        desligar();
    }
}
