 package com.lacoste.auto;

import android.content.Context;

public class CommandParser {

    private CommandParser() {}

    /**
     * Captura comandos vindos de notificações do WhatsApp:
     *.enviar 100 84xxxxxxx
     *.enviar 100mb 84xxxxxxx
     *.saldo 100 84xxxxxxx
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

            String comando = partes[0];
            String valorTexto = partes[1].replaceAll("(?i)mb|(?i)mt", "").trim();
            String numero = partes[2].replaceAll("[^0-9]", "");

            int valor;
            try {
                valor = Integer.parseInt(valorTexto);
            } catch (NumberFormatException e) {
                AppLog.add(context, "CommandParser", "Valor inválido: " + valorTexto);
                return false;
            }

            if (!numero.matches("^(84|85)\\d{7}$")) {
                AppLog.add(context, "CommandParser", "Número inválido: " + numero);
                return false;
            }

            // CASO 1:.ENVIAR
            if (comando.equalsIgnoreCase(".enviar")) {

                if (valor < UssdTransferManager.MIN_MB_TRANSFERENCIA
                        || valor > UssdTransferManager.MAX_MB_TRANSFERENCIA) {
                    AppLog.add(context, "CommandParser",
                            "Quantidade fora do limite: " + valor);
                    return false;
                }

                AppLog.add(context, "CommandParser",
                        "Comando.enviar capturado: " + valor + "MB para " + numero);

                UssdTransferManager.transferir(
                        context,
                        valor,
                        numero,
                        new UssdTransferManager.ResultadoCallback() {

                            @Override
                            public void onSucesso(int sim, int saldoRestanteMB) {
                                AppLog.add(context, "CommandParser",
                                        "Comando.enviar concluído. SIM " + sim);
                            }

                            @Override
                            public void onFalhaSaldoInsuficiente(String detalhes) {
                                AppLog.add(context, "CommandParser",
                                        "Falha por saldo insuficiente: " + detalhes);
                            }

                            @Override
                            public void onErro(String motivo) {
                                AppLog.add(context, "CommandParser",
                                        "Erro no.enviar: " + motivo);
                            }
                        }
                );
                return true;
            }

            // CASO 2:.SALDO
            if (comando.equalsIgnoreCase(".saldo")) {

                if (valor <= 0 || valor > 5000) {
                    AppLog.add(context, "CommandParser",
                            "Valor de saldo inválido: " + valor);
                    return false;
                }

                AppLog.add(context, "CommandParser",
                        "Comando.saldo capturado: " + valor + "MT para " + numero);

                // .saldo é transferência de crédito em MT.
                // Não passa 0 MB para o sistema de megas.
                UssdTransferManager.transferirCredito(
                        context,
                        String.valueOf(valor),
                        numero,
                        new UssdTransferManager.ResultadoCreditoCallback() {

                            @Override
                            public void onSucesso(int sim) {
                                AppLog.add(context, "CommandParser",
                                        "Comando.saldo concluído. SIM " + sim);
                            }

                            @Override
                            public void onErro(String motivo) {
                                AppLog.add(context, "CommandParser",
                                        "Erro no.saldo: " + motivo);
                            }
                        }
                );
                return true;
            }

            return false;

        } catch (Exception e) {
            AppLog.add(context, "CommandParser",
                    "Erro ao interpretar comando: " + e.getMessage());
            return false;
        }
    }
}