package com.lacoste.auto;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

public class WhatsAppCommandService extends Service {

    private static final String CHANNEL_ID = "LacosteAutoService";

    @Override
    public void onCreate() {
        super.onCreate();
        criarCanal();

        Notification notification;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notification = new Notification.Builder(this, CHANNEL_ID)
              .setContentTitle("LacosteAuto Rodando")
              .setContentText("Monitorando.enviar e.saldo")
              .setSmallIcon(android.R.drawable.ic_dialog_info)
              .build();
        } else {
            notification = new Notification.Builder(this)
              .setContentTitle("LacosteAuto Rodando")
              .setContentText("Monitorando.enviar e.saldo")
              .setSmallIcon(android.R.drawable.ic_dialog_info)
              .build();
        }

        startForeground(1, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    // CHAMADO PELO NotificationForwarderService
    public static void processarMensagem(Context context, String mensagem) {
        // Aqui usamos o teu CommandParser que já tem toda lógica de MB/MT
        boolean sucesso = CommandParser.processar(context, mensagem);

        if(sucesso){
            AppLog.add(context, "WhatsAppCommandService", "Comando processado com sucesso: " + mensagem);
        } else {
            AppLog.add(context, "WhatsAppCommandService", "Comando invalido: " + mensagem);
        }
    }

    private void criarCanal() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "LacosteAuto Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}