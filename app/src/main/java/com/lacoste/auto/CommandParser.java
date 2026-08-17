package com.lacoste.auto;

import android.content.Context;

public class CommandParser {

    private CommandParser() {}

    /**
     * Captura comandos vindos de notificações do WhatsApp:
     * .enviar 100 84xxxxxxx
     * .enviar 100mb 84xxxxxxx
     */
    public static boolean processar(
            final Context context,
            String mensagem) {

        if (context == null || mensagem == null) {
            return false;
        }

        try {
            String texto = mensagem.trim();

            if (texto.isEmpty()) return false;

            String[] partes = texto.split("\\s+");

            if (partes.length < 3) {
                return false;
            }

            if (!partes[0].equalsIgnoreCase(".enviar")) {
                return false;
            }

            String mbTexto =
                    partes[1].replaceAll("(?i)mb", "").trim();

            String numero =
                    partes[2].replaceAll("[^0-9]", "");

            int mb = Integer.parseInt(mbTexto);

            if (mb < UssdTransferManager.MIN_MB_TRANSFERENCIA
                    || mb > UssdTransferManager.MAX_MB_TRANSFERENCIA) {
                AppLog.add(context, "CommandParser",
                        "Quantidade fora do limite: " + mb);
                return false;
            }

            if (!numero.matches("^(84|85)\\d{7}$")) {
                AppLog.add(context, "CommandParser",
                        "Número inválido: " + numero);
                return false;
            }

            AppLog.add(context, "CommandParser",
                    "Comando .enviar capturado: "
                            + mb + "MB para " + numero);

            UssdTransferManager.transferir(
                    context,
                    mb,
                    numero,
                    new UssdTransferManager.ResultadoCallback() {

                        @Override
                        public void onSucesso(
                                int sim,
                                int saldoRestanteMB) {
                            AppLog.add(context, "CommandParser",
                                    "Comando .enviar concluído. SIM "
                                            + sim
                                            + ", saldo "
                                            + saldoRestanteMB + "MB");
                        }

                        @Override
                        public void onFalhaSaldoInsuficiente(
                                String detalhes) {
                            AppLog.add(context, "CommandParser",
                                    "Falha por saldo insuficiente: "
                                            + detalhes);
                        }

                        @Override
                        public void onErro(String motivo) {
                            AppLog.add(context, "CommandParser",
                                    "Erro no .enviar: " + motivo);
                        }
                    }
            );

            return true;

        } catch (Exception e) {
            AppLog.add(context, "CommandParser",
                    "Erro ao interpretar .enviar: "
                            + e.getMessage());
            return false;
        }
    }
}
