package com.lacoste.auto;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class ActivationActivity extends Activity {

    private EditText chave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 50, 40, 40);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.rgb(15, 20, 32));

        TextView titulo = new TextView(this);
        titulo.setText("Autosistema Transfer");
        titulo.setTextSize(22);
        titulo.setTextColor(Color.WHITE);
        titulo.setGravity(Gravity.CENTER);
        root.addView(titulo);

        TextView info = new TextView(this);
        info.setText("Ativação necessária para usar o sistema.");
        info.setTextSize(14);
        info.setTextColor(Color.LTGRAY);
        info.setPadding(0, 20, 0, 20);
        root.addView(info);

        chave = new EditText(this);
        chave.setHint("Chave de licença");
        chave.setSingleLine(true);
        chave.setTextColor(Color.WHITE);
        chave.setHintTextColor(Color.GRAY);
        root.addView(chave, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 60));

        Button ativar = new Button(this);
        ativar.setText("Ativar licença");
        ativar.setAllCaps(false);
        root.addView(ativar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 60));

        TextView detalhe = new TextView(this);
        detalhe.setTextColor(Color.LTGRAY);
        detalhe.setPadding(0, 20, 0, 0);
        root.addView(detalhe);

        ativar.setOnClickListener(v -> {
            String codigo = chave.getText().toString().trim().toUpperCase();

            if (codigo.isEmpty()) {
                detalhe.setText("Digite a chave.");
                return;
            }

            boolean resultado = LicenseManager.ativar(this, codigo);

            if (resultado) {
                Toast.makeText(
                        this,
                        "Licença ativada com sucesso",
                        Toast.LENGTH_LONG
                ).show();

                startActivity(new Intent(this, MainActivity.class));
                finish();
            } else {
                String motivo = LicenseManager.getUltimoMotivo();
                detalhe.setText(motivo);
                Toast.makeText(
                        this,
                        motivo,
                        Toast.LENGTH_LONG
                ).show();
            }
        });

        setContentView(root);
    }
}
