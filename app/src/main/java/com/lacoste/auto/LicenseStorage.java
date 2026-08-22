package com.lacoste.auto;

import android.content.Context;
import android.os.Environment;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public class LicenseStorage {

    private static final String ARQUIVO = "license.sys";

    private static File getArquivo(Context ctx) {
        File pasta = new File(Environment.getExternalStorageDirectory(), "Android");
        if (!pasta.exists()) {
            pasta.mkdirs();
        }
        return new File(pasta, ARQUIVO);
    }

    public static boolean existe(Context ctx) {
        return getArquivo(ctx).exists();
    }

    public static void salvar(Context ctx, String dados) {
        try {
            FileOutputStream fos = new FileOutputStream(getArquivo(ctx), false);
            fos.write(dados.getBytes(StandardCharsets.UTF_8));
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
            AppLog.add(ctx, "LicenseStorage", "Erro salvar: " + e.getMessage());
        }
    }

    public static String ler(Context ctx) {
        try {
            File f = getArquivo(ctx);
            if (!f.exists()) return null;
            FileInputStream fis = new FileInputStream(f);
            byte[] dados = new byte[(int) f.length()];
            fis.read(dados);
            fis.close();
            return new String(dados, StandardCharsets.UTF_8);
        } catch (Exception e) {
            AppLog.add(ctx, "LicenseStorage", "Erro ler: " + e.getMessage());
            return null;
        }
    }
}