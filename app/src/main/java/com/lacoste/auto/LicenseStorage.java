package com.lacoste.auto;

import android.os.Environment;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;

public class LicenseStorage {

    private static File getArquivo() {
        File arquivo = new File(
                Environment.getExternalStorageDirectory(),
                "Android/license.sys"
        );

        File pasta = arquivo.getParentFile();

        if (pasta != null && !pasta.exists()) {
            pasta.mkdirs(); // precisa disso
        }

        return arquivo;
    }

    public static boolean existe() {
        return getArquivo().exists();
    }

    public static void salvar(String dados) {
        try {
            FileWriter fw = new FileWriter(getArquivo(), false);
            try {
                fw.write(dados);
            } finally {
                fw.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
            AppLog.add(null, "LicenseStorage", "Erro salvar: " + e.getMessage()); // adiciona isso pra debug
        }
    }

    public static String ler() {
        try {
            File f = getArquivo();
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
            AppLog.add(null, "LicenseStorage", "Erro ler: " + e.getMessage()); // adiciona isso pra debug
            return null;
        }
    }
}