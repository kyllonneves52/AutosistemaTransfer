package com.kreysam.autosistematransfer;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/* JADX INFO: loaded from: classes3.dex */
public class TtsHelper {
    private static final String TAG = "TtsHelper";
    private static TextToSpeech tts;
    private static boolean pronto = false;
    private static final Random random = new Random();
    private static final String[] UNIDADES = {"zero", "um", "dois", "tres", "quatro", "cinco", "seis", "sete", "oito", "nove"};
    private static final String[] DEZ_A_DEZANOVE = {"dez", "onze", "doze", "treze", "catorze", "quinze", "dezasseis", "dezassete", "dezoito", "dezanove"};
    private static final String[] DEZENAS = {"", "", "vinte", "trinta", "quarenta", "cinquenta", "sessenta", "setenta", "oitenta", "noventa"};
    private static final String[] CENTENAS = {"", "cem", "duzentos", "trezentos", "quatrocentos", "quinhentos", "seiscentos", "setecentos", "oitocentos", "novecentos"};

    public static synchronized void iniciar(Context ctx) {
        if (tts != null) {
            return;
        }
        tts = new TextToSpeech(ctx.getApplicationContext(), new TextToSpeech.OnInitListener() { // from class: com.kreysam.autosistematransfer.TtsHelper$$ExternalSyntheticLambda0
            @Override // android.speech.tts.TextToSpeech.OnInitListener
            public final void onInit(int i) {
                TtsHelper.lambda$iniciar$0(i);
            }
        });
    }

    static /* synthetic */ void lambda$iniciar$0(int status) {
        if (status != 0) {
            Log.w(TAG, "TTS falhou ao iniciar (status=" + status + ")");
            return;
        }
        int resultado = tts.setLanguage(new Locale("pt", "PT"));
        if (resultado == -1 || resultado == -2) {
            tts.setLanguage(Locale.getDefault());
        }
        pronto = true;
        Log.i(TAG, "TTS pronto.");
    }

    public static synchronized void falar(String texto) {
        if (!pronto || tts == null || texto == null || texto.isEmpty()) {
            return;
        }
        try {
            tts.speak(texto, 1, null, "autosistema_" + System.currentTimeMillis());
        } catch (Exception e) {
            Log.w(TAG, "Falha ao falar: " + e.getMessage());
        }
    }

    public static synchronized void parar() {
        try {
            TextToSpeech textToSpeech = tts;
            if (textToSpeech != null) {
                textToSpeech.stop();
            }
        } catch (Exception e) {
        }
    }

    public static synchronized void destruir() {
        try {
            TextToSpeech textToSpeech = tts;
            if (textToSpeech != null) {
                textToSpeech.stop();
                tts.shutdown();
            }
        } catch (Exception e) {
        }
        tts = null;
        pronto = false;
    }

    public static String numeroTelefonePorExtenso(String digitos) {
        if (digitos == null || digitos.isEmpty()) {
            return "";
        }
        StringBuilder frase = new StringBuilder();
        String prefixo = digitos.length() >= 2 ? digitos.substring(0, 2) : digitos;
        for (int i = 0; i < prefixo.length(); i++) {
            if (i > 0) {
                frase.append(", ");
            }
            frase.append(UNIDADES[prefixo.charAt(i) - '0']);
        }
        int i2 = digitos.length();
        if (i2 > 2) {
            String resto = digitos.substring(2);
            List<String> blocos = particionarAleatorio(resto.length());
            int pos = 0;
            for (String bloco : blocos) {
                int tamanho = bloco.length();
                String grupo = resto.substring(pos, pos + tamanho);
                pos += tamanho;
                frase.append(", ").append(grupoPorExtenso(grupo));
            }
        }
        return frase.toString();
    }

    private static List<String> particionarAleatorio(int totalDigitos) {
        List<String> blocos = new ArrayList<>();
        int restante = totalDigitos;
        while (restante > 0) {
            int max = Math.min(3, restante);
            int tamanho = random.nextInt(max) + 1;
            if (restante - tamanho == 1 && tamanho < max) {
                tamanho++;
            }
            blocos.add(repete("X", tamanho));
            restante -= tamanho;
        }
        return blocos;
    }

    private static String repete(String s, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(s);
        }
        return sb.toString();
    }

    private static String grupoPorExtenso(String grupo) {
        int n = Integer.parseInt(grupo);
        switch (grupo.length()) {
            case 1:
                return UNIDADES[n];
            case 2:
                if (grupo.charAt(0) == '0') {
                    StringBuilder sb = new StringBuilder();
                    String[] strArr = UNIDADES;
                    return sb.append(strArr[0]).append(" ").append(strArr[n]).toString();
                }
                return numeroExtenso2(n);
            case 3:
                if (grupo.charAt(0) == '0') {
                    String doisDigitos = grupo.substring(1);
                    return UNIDADES[0] + " " + grupoPorExtenso(doisDigitos);
                }
                return numeroExtenso3(n);
            default:
                return grupo;
        }
    }

    private static String numeroExtenso2(int n) {
        if (n < 10) {
            return UNIDADES[n];
        }
        if (n < 20) {
            return DEZ_A_DEZANOVE[n - 10];
        }
        int dezena = n / 10;
        int unidade = n % 10;
        return unidade == 0 ? DEZENAS[dezena] : DEZENAS[dezena] + " e " + UNIDADES[unidade];
    }

    private static String numeroExtenso3(int n) {
        if (n < 100) {
            return numeroExtenso2(n);
        }
        int centena = n / 100;
        int resto = n % 100;
        String base = centena == 1 ? "cem" : CENTENAS[centena];
        return resto == 0 ? base : base + " e " + numeroExtenso2(resto);
    }
}
