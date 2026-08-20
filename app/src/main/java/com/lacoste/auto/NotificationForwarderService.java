package com.lacoste.auto;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

public class NotificationForwarderService extends NotificationListenerService {

    private static final String TAG = "AutosistemaNotifService";

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {

        if (sbn == null) return;

        try {
            String pacote = sbn.getPackageName();

            if(pacote.equals("com.whatsapp") || pacote.equals("com.whatsapp.w4b")){

                String titulo = sbn.getNotification().extras.getString("android.title");
                String texto = sbn.getNotification().extras.getString("android.text");
                String bigText = sbn.getNotification().extras.getString("android.bigText");

                String mensagem = bigText!= null? bigText : texto;
                if(mensagem == null) mensagem = "";

                // Tira "Nome: " se vier de grupo
                if (mensagem.contains(": ")) {
                    mensagem = mensagem.split(": ", 2)[1];
                }

                mensagem = mensagem.trim();
                String mensagemLower = mensagem.toLowerCase();

                // Só dispara se começar com.enviar ou.saldo
                if(mensagemLower.startsWith(".enviar") || mensagemLower.startsWith(".saldo")){
                    Log.i(TAG, "Msg recebida: " + mensagem);
                    if (LicenseManager.estaAtivado(this)) {
                        WhatsAppCommandService.processarMensagem(this, mensagem);
                    } else {
                        Log.w(TAG, "Comando ignorado: licença expirada.");
                    }
                    return; // já capturou
                }

                // Se não for comando, continua sendo pagamento
                String remetente = (titulo == null? "" : titulo) + " + pacote;
                String numeroExtra = Prefs.getNumeroExtra(getApplicationContext());

                if (SmsFilter.deveEncaminhar(remetente, mensagem, numeroExtra)) {
                    Log.i(TAG, "Notificação de pagamento detetada — a enviar ao painel.");
                    ApiClient.enviarParaPainel(getApplicationContext(), mensagem);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Erro a processar notificação", e);
        }
    }
}