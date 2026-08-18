package com.lacoste.auto;

import android.content.Context;
import java.util.Locale;

public class LicenseManager {

    // Mantido igual ao sistema do auto1.
    private static final String BUILD_PERMITIDO = "SP1A.210812.017";
    private static final String ID_LICENCA = "DF7A2792";
    private static final int DIAS_PLANO = 3;
    private static final String CODIGO = "AB96";

    private static volatile String ultimoMotivo = "Chave inválida";

    public static String getUltimoMotivo() {
        return ultimoMotivo;
    }

    public static boolean estaAtivado(Context context) {
        if (!LicenseStorage.existe()) {
            return false;
        }

        try {
            String dados = LicenseStorage.ler();
            if (dados == null) return false;

            String buildAtual =
                    android.os.Build.ID.toUpperCase(Locale.ROOT);

            String buildGuardado = pegar(dados, "BUILD_ID");

            if (!buildAtual.equals(buildGuardado)) {
                AppLog.add(context, "LicenseManager",
                        "BUILD mudou. Bloqueado.");
                return false;
            }

            long dataFinal =
                    Long.parseLong(pegar(dados, "DATA_FINAL"));

            if (System.currentTimeMillis() >= dataFinal) {
                AppLog.add(context, "LicenseManager",
                        "Licença expirada.");
                return false;
            }

            return true;

        } catch (Exception e) {
            AppLog.add(context, "LicenseManager",
                    "Erro ao verificar licença: " + e.getMessage());
            return false;
        }
    }

    public static boolean ativar(Context context, String chave) {
        try {
            ultimoMotivo = "Chave inválida";

            String buildTelefone =
                    android.os.Build.ID.toUpperCase(Locale.ROOT);

            AppLog.add(context, "LicenseManager",
                    "BUILD telefone: " + buildTelefone);

            if (!buildTelefone.equals(BUILD_PERMITIDO)) {
                ultimoMotivo = "Dispositivo não autorizado.";
                AppLog.add(context, "LicenseManager",
                        "Falhou: BUILD diferente.");
                return false;
            }

            // Impede reutilização da mesma chave neste dispositivo.
            if (LicenseStorage.existe()) {
                String dados = LicenseStorage.ler();

                if (dados != null) {
                    String chaveSalva = pegar(dados, "CHAVE");

                    if (!chaveSalva.isEmpty()
                            && chave.equalsIgnoreCase(chaveSalva)) {
                        ultimoMotivo = "Esta chave já foi utilizada.";
                        AppLog.add(context, "LicenseManager",
                                "Tentativa de reutilização da chave.");
                        return false;
                    }
                }
            }

            String[] partes = chave.split("-");

            if (partes.length != 4) {
                ultimoMotivo = "Formato de chave inválido.";
                return false;
            }

            if (!partes[0].equals("RCBD")) {
                ultimoMotivo = "Prefixo de chave inválido.";
                return false;
            }

            String id = partes[1];
            String dias = partes[2].replace("D", "");
            String codigo = partes[3];

            if (!id.equals(ID_LICENCA)) {
                ultimoMotivo = "ID da licença inválido.";
                return false;
            }

            if (!dias.equals(String.valueOf(DIAS_PLANO))) {
                ultimoMotivo = "Plano da licença inválido.";
                return false;
            }

            if (!codigo.equals(CODIGO)) {
                ultimoMotivo = "Código da licença inválido.";
                return false;
            }

            long inicio = System.currentTimeMillis();

            // Duração real do plano: DIAS_PLANO dias.
            long duracaoMs =
                    DIAS_PLANO * 24L * 60L * 60L * 1000L;
            long finalizacao = inicio + duracaoMs;

            String dados =
                    "CHAVE=" + chave + "\n" +
                    "BUILD_ID=" + buildTelefone + "\n" +
                    "DATA_INICIO=" + inicio + "\n" +
                    "DATA_FINAL=" + finalizacao;

            LicenseStorage.salvar(dados);

            ultimoMotivo = "Licença ativada.";
            AppLog.add(context, "LicenseManager",
                    "Licença ativada com sucesso.");
            return true;

        } catch (Exception e) {
            ultimoMotivo = "Erro ao ativar licença.";
            AppLog.add(context, "LicenseManager",
                    "Erro licença: " + e.getMessage());
            return false;
        }
    }

    public static long millisRestantes(Context context) {
        try {
            String dados = LicenseStorage.ler();
            if (dados == null) return 0L;
            long fim = Long.parseLong(pegar(dados, "DATA_FINAL"));
            return Math.max(0L, fim - System.currentTimeMillis());
        } catch (Exception e) {
            return 0L;
        }
    }

    public static String tempoRestante(Context context) {
        try {
            String dados = LicenseStorage.ler();
            if (dados == null) return "ERRO";

            long fim =
                    Long.parseLong(pegar(dados, "DATA_FINAL"));

            long restante =
                    fim - System.currentTimeMillis();

            if (restante <= 0) return "EXPIRADO";

            long dias = restante / 86400000L;
            long horas =
                    (restante % 86400000L) / 3600000L;
            long minutos =
                    (restante % 3600000L) / 60000L;

            return dias + " dias "
                    + horas + " horas "
                    + minutos + " minutos";

        } catch (Exception e) {
            return "ERRO";
        }
    }

    private static String pegar(String texto, String chave) {
        if (texto == null) return "";

        for (String linha : texto.split("\\n")) {
            if (linha.startsWith(chave + "=")) {
                return linha.substring((chave + "=").length()).trim();
            }
        }

        return "";
    }
}
