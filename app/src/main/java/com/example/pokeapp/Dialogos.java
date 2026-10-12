package com.example.pokeapp;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

/** Ventanas de confirmación con el estilo de la app (en lugar de las grises de Android). */
public final class Dialogos {

    private Dialogos() { }

    /**
     * Pregunta antes de hacer algo importante.
     *
     * @param emoji         dibujo de arriba, por ejemplo "🗑️"
     * @param textoAceptar  texto del botón que confirma ("Sí, reiniciar")
     * @param textoCancelar texto del botón que cierra sin hacer nada ("Cancelar")
     * @param alAceptar     qué hacer si confirma
     * @param alCancelar    qué hacer si cancela (puede ser null)
     */
    public static void confirmar(Activity activity, String emoji, String titulo, String mensaje,
                                 String textoAceptar, String textoCancelar,
                                 Runnable alAceptar, Runnable alCancelar) {
        View vista = LayoutInflater.from(activity).inflate(R.layout.dialog_confirmar, null);
        ((TextView) vista.findViewById(R.id.tvEmojiConfirmar)).setText(emoji);
        ((TextView) vista.findViewById(R.id.tvTituloConfirmar)).setText(titulo);
        ((TextView) vista.findViewById(R.id.tvMensajeConfirmar)).setText(mensaje);

        TextView btnAceptar = vista.findViewById(R.id.btnAceptarConfirmar);
        TextView btnCancelar = vista.findViewById(R.id.btnCancelarConfirmar);
        btnAceptar.setText(textoAceptar);
        btnCancelar.setText(textoCancelar);

        AlertDialog dialogo = new AlertDialog.Builder(activity).setView(vista).create();
        btnAceptar.setOnClickListener(v -> {
            dialogo.dismiss();
            if (alAceptar != null) alAceptar.run();
        });
        btnCancelar.setOnClickListener(v -> {
            dialogo.dismiss();
            if (alCancelar != null) alCancelar.run();
        });

        dialogo.show();
        if (dialogo.getWindow() != null) {
            dialogo.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // Entrada con un pequeño rebote
        vista.setScaleX(0.85f);
        vista.setScaleY(0.85f);
        vista.setAlpha(0f);
        vista.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(260)
                .setInterpolator(new OvershootInterpolator()).start();
    }
}
