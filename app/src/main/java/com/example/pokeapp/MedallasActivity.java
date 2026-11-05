package com.example.pokeapp;

import com.example.pokeapp.data.Idioma;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.pokeapp.data.TypeColors;
import com.example.pokeapp.data.UsuarioManager;

import com.example.pokeapp.data.Preferencias;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/**
 * Medallas (módulo K): las 10 medallas del PDF (cada Torre Pokémon completada desbloquea la
 * siguiente, UsuarioManager.otorgarMedallasTorre) y 2 especiales: la 11 por hacer 1,000 puntos en
 * Pikachu Run y la 12 por vencer al jefe secreto. De las obtenidas se muestra la fecha.
 * La imagen de cada una es res/drawable-nodpi/medalla_<n>.png.
 */
public class MedallasActivity extends AppCompatActivity {

    /** Primer número de medalla. */
    private static final int PRIMERA = 1;

    /** Emoji y tipo (da el color) de cada medalla, de la 1 a la 10 (solo si falta su imagen). */
    private static final String[][] MEDALLAS = {
            {"🪨", "rock"}, {"💧", "water"}, {"⚡", "electric"}, {"🌿", "grass"}, {"🔥", "fire"},
            {"👑", "dragon"}, {"🌙", "dark"}, {"💤", "normal"}, {"🔥", "fire"}, {"⚡", "electric"},
            {"⚡", "electric"}, {"🌟", "psychic"}};

    /** Nombre de cada medalla: el Pokémon de su insignia. */
    private static final String[] NOMBRES = {"Medalla Arceus", "Medalla Gengar", "Medalla Eevee", "Medalla Sylveon", "Medalla Greninja",
            "Medalla Dragonite", "Medalla Darkrai", "Medalla Snorlax", "Medalla Charizard", "Medalla Pikachu",
            "Medalla Pikachu Run", "Medalla Presidencial"};
    private static final String[] NOMBRES_EN = {"Arceus Medal", "Gengar Medal", "Eevee Medal", "Sylveon Medal", "Greninja Medal",
            "Dragonite Medal", "Darkrai Medal", "Snorlax Medal", "Charizard Medal", "Pikachu Medal",
            "Pikachu Run Medal", "Presidential Medal"};

    public static String nombre(int numero) {
        int i = Math.max(1, Math.min(numero, NOMBRES.length)) - 1;
        return Idioma.t(NOMBRES[i], NOMBRES_EN[i]);
    }

    /** Cómo se gana cada medalla: la n se gana al completar la Torre por n-ésima vez. */
    private static String requisito(int numero) {
        if (numero == UsuarioManager.MEDALLA_PIKACHU_RUN) {
            return Idioma.t("Haz 1,000 puntos en Pikachu Run", "Score 1,000 points in Pikachu Run");
        }
        if (numero == UsuarioManager.MEDALLA_JEFE_SECRETO) {
            return Idioma.t("Vence al jefe secreto de la Torre", "Defeat the Tower's secret boss");
        }
        return numero == 1 ? Idioma.t("Completa la Torre por primera vez", "Complete the Tower for the first time") : Idioma.t("Completa la Torre ", "Complete the Tower ") + numero + Idioma.t(" veces", " times");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_medallas);
        findViewById(R.id.btnRegresar).setOnClickListener(v -> finish());

        pintar(0, null);   // todas bloqueadas mientras responde Firestore
        UsuarioManager.obtenerMedallasConFecha((total, fechas) -> {
            if (isFinishing() || isDestroyed()) return;
            pintar(total, fechas);
        });
    }

    private void pintar(int obtenidas, Map<Integer, Date> fechas) {
        ((TextView) findViewById(R.id.tvProgresoMedallas)).setText(obtenidas + Idioma.t(" de ", " of ") + MEDALLAS.length
                + (obtenidas >= MEDALLAS.length ? Idioma.t(" · ¡Colección completa! 🏆", " · Collection complete! 🏆")
                : Idioma.t(" · Gánalas en la Torre, en Pikachu Run y contra el jefe secreto", " · Earn them in the Tower, in Pikachu Run and against the secret boss")));
        ProgressBar barra = findViewById(R.id.barraMedallas);
        barra.setMax(MEDALLAS.length);
        barra.setProgress(obtenidas);

        GridLayout grid = findViewById(R.id.gridMedallas);
        grid.removeAllViews();
        float dp = getResources().getDisplayMetrics().density;
        SimpleDateFormat formato = new SimpleDateFormat("d MMM yyyy",
                Preferencias.esIngles() ? Locale.ENGLISH : new Locale("es", "MX"));

        for (int i = 0; i < MEDALLAS.length; i++) {
            int numero = PRIMERA + i;
            boolean ganada = fechas != null && fechas.containsKey(numero);
            int color = ganada ? TypeColors.obtenerColor(MEDALLAS[i][1]) : 0xFFB8B8B8;

            LinearLayout tarjeta = new LinearLayout(this);
            tarjeta.setOrientation(LinearLayout.VERTICAL);
            tarjeta.setGravity(Gravity.CENTER_HORIZONTAL);
            tarjeta.setBackgroundResource(R.drawable.bg_card_menu);
            tarjeta.setPadding(Math.round(10 * dp), Math.round(14 * dp), Math.round(10 * dp), Math.round(12 * dp));
            tarjeta.setAlpha(ganada ? 1f : 0.6f);

            // La medalla: su imagen (res/drawable-nodpi/medalla_<n>.png) si existe; en gris si está bloqueada
            int imagen = getResources().getIdentifier("medalla_" + numero, "drawable", getPackageName());
            if (imagen != 0) {
                ImageView medalla = new ImageView(this);
                medalla.setImageResource(imagen);
                medalla.setScaleType(ImageView.ScaleType.FIT_CENTER);
                if (!ganada) {
                    ColorMatrix gris = new ColorMatrix();
                    gris.setSaturation(0f);
                    medalla.setColorFilter(new ColorMatrixColorFilter(gris));
                    medalla.setAlpha(0.55f);
                }
                tarjeta.addView(medalla, new LinearLayout.LayoutParams(Math.round(96 * dp), Math.round(112 * dp)));
            } else {
                // Sin imagen: círculo del color de su tipo con borde dorado (gris si está bloqueada)
                GradientDrawable circulo = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                        new int[]{0xFFFFFFFF, color});
                circulo.setShape(GradientDrawable.OVAL);
                circulo.setStroke(Math.round(4 * dp), ganada ? 0xFFFACC15 : 0xFF8A8A8A);
                TextView medalla = new TextView(this);
                medalla.setBackground(circulo);
                medalla.setGravity(Gravity.CENTER);
                medalla.setText(ganada ? MEDALLAS[i][0] : "🔒");
                medalla.setTextSize(32);
                LinearLayout.LayoutParams lpMedalla = new LinearLayout.LayoutParams(Math.round(84 * dp), Math.round(84 * dp));
                lpMedalla.topMargin = Math.round(14 * dp);
                lpMedalla.bottomMargin = Math.round(14 * dp);
                tarjeta.addView(medalla, lpMedalla);
            }

            TextView nombre = new TextView(this);
            nombre.setText((i + 1) + ". " + nombre(numero));
            nombre.setTextColor(PokeApp.color(this, R.attr.pkTexto));
            nombre.setTextSize(14);
            nombre.setTypeface(null, Typeface.BOLD);
            nombre.setGravity(Gravity.CENTER);
            nombre.setPadding(0, Math.round(8 * dp), 0, 0);
            tarjeta.addView(nombre);

            Date fecha = fechas != null ? fechas.get(numero) : null;
            TextView estado = new TextView(this);
            estado.setText(!ganada ? requisito(numero) : fecha != null
                    ? getString(R.string.t_obtenida_el, formato.format(fecha)) : getString(R.string.t_obtenida));
            estado.setTextColor(ganada ? 0xFF2E8B57 : PokeApp.color(this, R.attr.pkTextoSuave));
            estado.setTextSize(12);
            estado.setGravity(Gravity.CENTER);
            tarjeta.addView(estado);

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
            lp.width = 0;
            lp.setMargins(Math.round(6 * dp), Math.round(6 * dp), Math.round(6 * dp), Math.round(6 * dp));
            grid.addView(tarjeta, lp);
        }
    }
}