package com.kreysam.autosistematransfer;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/* JADX INFO: loaded from: classes3.dex */
public class AppLog {
    private static final SimpleDateFormat FORMATO_HORA = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private static final String NOME_FICHEIRO = "autosistema_transfer_log.txt";
    private static final long TAMANHO_MAX_BYTES = 300000;

    public static synchronized void add(Context ctx, String tag, String msg) {
        File f;
        String linha;
        FileWriter fw;
        Log.d(tag, msg);
        try {
            f = ficheiro(ctx);
            linha = FORMATO_HORA.format(new Date()) + " [" + tag + "] " + msg + "\n";
            fw = new FileWriter(f, true);
        } catch (Exception e) {
            Log.e("AppLog", "Falha ao escrever log: " + e.getMessage());
        }
        try {
            fw.write(linha);
            fw.close();
            cortarSeNecessario(f);
        } catch (Throwable th) {
            try {
                fw.close();
            } catch (Throwable th2) {
                th.addSuppressed(th2);
            }
            throw th;
        }
    }

    public static synchronized String ler(Context ctx) {
        try {
            File f = ficheiro(ctx);
            if (!f.exists()) {
                return "(sem registos ainda)";
            }
            byte[] dados = new byte[(int) f.length()];
            FileInputStream fis = new FileInputStream(f);
            try {
                fis.read(dados);
                fis.close();
                return new String(dados, "UTF-8");
            } catch (Throwable th) {
                try {
                    fis.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
                throw th;
            }
        } catch (Exception e) {
            return "Erro ao ler log: " + e.getMessage();
        }
    }

    public static synchronized void limpar(Context ctx) {
        try {
            File f = ficheiro(ctx);
            if (f.exists()) {
                f.delete();
            }
        } catch (Exception e) {
        }
    }

    private static File ficheiro(Context ctx) {
        return new File(ctx.getApplicationContext().getFilesDir(), NOME_FICHEIRO);
    }

    private static void cortarSeNecessario(File f) {
        try {
            if (f.length() <= TAMANHO_MAX_BYTES) {
                return;
            }
            RandomAccessFile raf = new RandomAccessFile(f, "r");
            long cortarAte = f.length() - 150000;
            raf.seek(cortarAte);
            byte[] resto = new byte[(int) (f.length() - cortarAte)];
            raf.readFully(resto);
            raf.close();
            FileOutputStream fos = new FileOutputStream(f, false);
            try {
                fos.write(resto);
                fos.close();
            } finally {
            }
        } catch (Exception e) {
        }
    }
}
