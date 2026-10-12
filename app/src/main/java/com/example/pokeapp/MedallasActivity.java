package com.example.pokeapp;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.pokeapp.data.TypeColors;
import com.example.pokeapp.data.UsuarioManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/**
 * Medallas (módulo K): las 10 medallas del proyecto, obtenidas o bloqueadas.
 * Cada Torre Pokémon completada desbloquea la siguiente (UsuarioManager.otorgarMedalla);
 * de las obtenidas se muestra la fecha en que se ganó.
 */
public class MedallasActivity extends AppCompatActivity {

    /** Nombre, emoji y tipo (da el color) de cada medalla, en el orden en que se ganan. */
    private static final String[][] MEDALLAS = {
            {"Roca", "🪨", "rock"}, {"Cascada", "💧", "water"}, {"Trueno", "⚡", "electric"},
            {"Arcoíris", "🌈", "grass"}, {"Alma", "💜", "poison"}, {"Pantano", "🔮", "psychic"},
            {"Volcán", "🌋", "fire"}, {"Tierra", "🌎", "ground"}, {"Glaciar", "❄️", "ice"},
            {"Dragón", "🐉", "dragon"}};

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
        ((TextView) findViewById(R.id.tvProgresoMedallas)).setText(obtenidas + " de " + MEDALLAS.length
                + (obtenidas >= MEDALLAS.length ? " · ¡Colección completa! 🏆"
                : " · Completa la Torre Pokémon para ganar la siguiente"));
        ProgressBar barra = findViewById(R.id.barraMedallas);
        barra.setMax(MEDALLAS.length);
        barra.setProgress(obtenidas);

        GridLayout grid = findViewById(R.id.gridMedallas);
        grid.removeAllViews();
        float dp = getResources().getDisplayMetrics().density;
        SimpleDateFormat formato = new SimpleDateFormat("d MMM yyyy", new Locale("es", "MX"));

        for (int i = 0; i < MEDALLAS.length; i++) {
            boolean ganada = i < obtenidas;
            int color = ganada ? TypeColors.obtenerColor(MEDALLAS[i][2]) : 0xFFB8B8B8;

            LinearLayout tarjeta = new LinearLayout(this);
            tarjeta.setOrientation(LinearLayout.VERTICAL);
            tarjeta.setGravity(Gravity.CENTER_HORIZONTAL);
            tarjeta.setBackgroundResource(R.drawable.bg_card_menu);
            tarjeta.setPadding(Math.round(10 * dp), Math.round(14 * dp), Math.round(10 * dp), Math.round(12 * dp));
            tarjeta.setAlpha(ganada ? 1f : 0.6f);

            // La medalla: círculo del color de su tipo con borde dorado (gris si está bloqueada)
            GradientDrawable circulo = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{0xFFFFFFFF, color});
            circulo.setShape(GradientDrawable.OVAL);
            circulo.setStroke(Math.round(4 * dp), ganada ? 0xFFFACC15 : 0xFF8A8A8A);
            TextView medalla = new TextView(this);
            medalla.setBackground(circulo);
            medalla.setGravity(Gravity.CENTER);
            medalla.setText(ganada ? MEDALLAS[i][1] : "🔒");
            medalla.setTextSize(32);
            tarjeta.addView(medalla, new LinearLayout.LayoutParams(Math.round(84 * dp), Math.round(84 * dp)));

            TextView nombre = new TextView(this);
            nombre.setText((i + 1) + ". Medalla " + MEDALLAS[i][0]);
            nombre.setTextColor(PokeApp.color(this, R.attr.pkTexto));
            nombre.setTextSize(14);
            nombre.setTypeface(null, Typeface.BOLD);
            nombre.setGravity(Gravity.CENTER);
            nombre.setPadding(0, Math.round(8 * dp), 0, 0);
            tarjeta.addView(nombre);

            Date fecha = fechas != null ? fechas.get(i + 1) : null;
            TextView estado = new TextView(this);
            estado.setText(!ganada ? "Bloqueada" : fecha != null ? "Obtenida el " + formato.format(fecha) : "Obtenida");
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
