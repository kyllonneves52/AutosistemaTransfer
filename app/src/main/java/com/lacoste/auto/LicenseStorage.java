package com.lacoste.auto;

import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public class LicenseStorage {

    private static final String PASTA = "license";
    private static final String ARQUIVO = "license.sys";

    private static File getArquivo(Context ctx) {
        File pasta = new File(ctx.getFilesDir(), PASTA);
        if (!pasta.exists()) {
            pasta.mkdirs(); // cria /data/data/com.lacoste.auto/files/license/
        }
        return new File(pasta, ARQUIVO);
    }

    public static boolean existe(Context ctx) {
        return getArquivo(ctx).exists();
    }

    public static void salvar(Context ctx, String dados) {
        try {
            FileOutputStream fos = new FileOutputStream(getArquivo(ctx), false);
            try {
                fos.write(dados.getBytes(StandardCharsets.UTF_8));
            } finally {
                fos.close();
            }
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

            try {
                fis.read(dados);
            } finally {
                fis.close();
            }

            return new String(dados, StandardCharsets.UTF_8);

        } catch (Exception e) {
            AppLog.add(ctx, "LicenseStorage", "Erro ler: " + e.getMessage());
            return null;
        }
    }
}