package com.kreysam.autosistematransfer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/* JADX INFO: loaded from: classes3.dex */
public class BootReceiver extends BroadcastReceiver {
    @Override // android.content.BroadcastReceiver
    public void onReceive(Context context, Intent intent) {
        Intent servico = new Intent(context, (Class<?>) MonitorService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            context.startForegroundService(servico);
        } else {
            context.startService(servico);
        }
    }
}
