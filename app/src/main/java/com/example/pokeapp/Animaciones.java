package com.example.pokeapp;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Animaciones que se reutilizan en varias pantallas. */
public final class Animaciones {

    private Animaciones() { }

    /** La vista sube y baja suavemente sin parar (dibujo de las pantallas vacías). */
    public static void flotar(View vista) {
        float dp = vista.getResources().getDisplayMetrics().density;
        ObjectAnimator flotar = ObjectAnimator.ofFloat(vista, View.TRANSLATION_Y, 0f, -10 * dp);
        flotar.setDuration(1300);
        flotar.setRepeatCount(ValueAnimator.INFINITE);
        flotar.setRepeatMode(ValueAnimator.REVERSE);
        flotar.start();
    }

    /**
     * Pantalla de "¡Nueva medalla!": la medalla aparece girando sobre rayos dorados, luego late.
     * Al tocar en cualquier parte se cierra y se ejecuta "despues".
     */
    public static void medallaGanada(Activity activity, int numero, Runnable despues) {
        float dp = activity.getResources().getDisplayMetrics().density;
        Dialog dialogo = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);

        FrameLayout raiz = new FrameLayout(activity);
        raiz.setBackgroundColor(0xE6070706);

        ImageView rayos = new ImageView(activity);
        rayos.setImageResource(R.drawable.ic_rayos_medalla);
        raiz.addView(rayos, new FrameLayout.LayoutParams(Math.round(380 * dp), Math.round(380 * dp), Gravity.CENTER));

        LinearLayout columna = new LinearLayout(activity);
        columna.setOrientation(LinearLayout.VERTICAL);
        columna.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView titulo = new TextView(activity);
        titulo.setText("¡NUEVA MEDALLA!");
        titulo.setTextColor(0xFFE8D08A);
        titulo.setTextSize(24);
        titulo.setTypeface(Typeface.SERIF, Typeface.BOLD);
        titulo.setLetterSpacing(0.12f);
        columna.addView(titulo);

        ImageView medalla = new ImageView(activity);
        int imagen = activity.getResources().getIdentifier("medalla_" + numero, "drawable", activity.getPackageName());
        medalla.setImageResource(imagen != 0 ? imagen : R.drawable.ic_mod_medallas);
        medalla.setCameraDistance(9000 * dp);   // que el giro en 3D no se deforme
        LinearLayout.LayoutParams lpMedalla = new LinearLayout.LayoutParams(Math.round(210 * dp), Math.round(210 * dp));
        lpMedalla.topMargin = Math.round(20 * dp);
        lpMedalla.bottomMargin = Math.round(18 * dp);
        columna.addView(medalla, lpMedalla);

        TextView nombre = new TextView(activity);
        nombre.setText(MedallasActivity.nombre(numero));
        nombre.setTextColor(Color.WHITE);
        nombre.setTextSize(22);
        nombre.setTypeface(null, Typeface.BOLD);
        columna.addView(nombre);

        TextView aviso = new TextView(activity);
        aviso.setText("Toca para continuar");
        aviso.setTextColor(0xFFA39373);
        aviso.setTextSize(13);
        aviso.setPadding(0, Math.round(26 * dp), 0, 0);
        columna.addView(aviso);

        raiz.addView(columna, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        dialogo.setContentView(raiz);
        if (dialogo.getWindow() != null) {
            dialogo.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialogo.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }

        // Rayos girando sin parar
        ObjectAnimator giroRayos = ObjectAnimator.ofFloat(rayos, View.ROTATION, 0f, 360f);
        giroRayos.setDuration(14000);
        giroRayos.setInterpolator(new LinearInterpolator());
        giroRayos.setRepeatCount(ValueAnimator.INFINITE);

        // Latido de la medalla, después de que termina de girar
        ObjectAnimator latido = ObjectAnimator.ofPropertyValuesHolder(medalla,
                android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.06f),
                android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.06f));
        latido.setDuration(700);
        latido.setRepeatCount(ValueAnimator.INFINITE);
        latido.setRepeatMode(ValueAnimator.REVERSE);

        rayos.setAlpha(0f);
        titulo.setAlpha(0f);
        titulo.setTranslationY(-20 * dp);
        nombre.setAlpha(0f);
        aviso.setAlpha(0f);
        medalla.setScaleX(0f);
        medalla.setScaleY(0f);

        dialogo.setOnShowListener(d -> {
            giroRayos.start();
            rayos.animate().alpha(1f).setDuration(500).start();
            titulo.animate().alpha(1f).translationY(0f).setDuration(450).start();
            medalla.animate().scaleX(1f).scaleY(1f).rotationY(720f).setDuration(1000)
                    .setInterpolator(new OvershootInterpolator(1.2f))
                    .withEndAction(latido::start).start();
            nombre.animate().alpha(1f).setStartDelay(700).setDuration(400).start();
            aviso.animate().alpha(1f).setStartDelay(1300).setDuration(400).start();
            Efectos.victoria(activity);
        });
        raiz.setOnClickListener(v -> {
            if (aviso.getAlpha() > 0.5f) dialogo.dismiss();   // no se cierra antes de verla
        });
        dialogo.setOnDismissListener(d -> {
            giroRayos.cancel();
            latido.cancel();
            if (despues != null) despues.run();
        });
        dialogo.show();
    }
}
