package com.example.pokeapp;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Configuración (módulo M): por ahora solo se muestran las opciones del proyecto
 * (idioma, tema, sonido, vibración, reiniciar progreso y cerrar sesión), DESHABILITADAS.
 * Cada una se activará cuando se programe su función.
 */
public class ConfiguracionActivity extends AppCompatActivity {

    /** Emoji, título, descripción y valor que se muestra a la derecha. */
    private static final String[][] OPCIONES = {
            {"🌐", "Idioma", "Idioma de los textos de la app", "Español"},
            {"🎨", "Tema", "Claro, oscuro o Pokédex", "Claro"},
            {"🔊", "Sonido", "Efectos y cry de los Pokémon", "Activado"},
            {"📳", "Vibración", "Vibrar en golpes y capturas", "Desactivada"},
            {"♻️", "Reiniciar progreso", "Borra victorias, medallas, récords y colección", ""},
            {"🚪", "Cerrar sesión", "Salir de tu cuenta en este celular", ""}};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_configuracion);
        findViewById(R.id.btnRegresar).setOnClickListener(v -> finish());

        LinearLayout lista = findViewById(R.id.listaConfiguracion);
        float dp = getResources().getDisplayMetrics().density;

        for (String[] opcion : OPCIONES) {
            LinearLayout fila = new LinearLayout(this);
            fila.setOrientation(LinearLayout.HORIZONTAL);
            fila.setGravity(Gravity.CENTER_VERTICAL);
            fila.setBackgroundResource(R.drawable.bg_card_menu);
            fila.setPadding(Math.round(14 * dp), Math.round(14 * dp), Math.round(14 * dp), Math.round(14 * dp));
            fila.setAlpha(0.55f);      // deshabilitada
            fila.setEnabled(false);

            TextView icono = new TextView(this);
            icono.setText(opcion[0]);
            icono.setTextSize(24);
            fila.addView(icono);

            LinearLayout textos = new LinearLayout(this);
            textos.setOrientation(LinearLayout.VERTICAL);
            textos.setPadding(Math.round(12 * dp), 0, Math.round(8 * dp), 0);

            TextView titulo = new TextView(this);
            titulo.setText(opcion[1]);
            titulo.setTextColor(getColor(R.color.menu_text));
            titulo.setTextSize(16);
            titulo.setTypeface(null, Typeface.BOLD);
            textos.addView(titulo);

            TextView descripcion = new TextView(this);
            descripcion.setText(opcion[2]);
            descripcion.setTextColor(getColor(R.color.menu_text_soft));
            descripcion.setTextSize(12);
            textos.addView(descripcion);
            fila.addView(textos, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView valor = new TextView(this);
            valor.setText(opcion[3].isEmpty() ? "🔒" : opcion[3] + " 🔒");
            valor.setTextColor(getColor(R.color.menu_text_soft));
            valor.setTextSize(13);
            valor.setTypeface(null, Typeface.BOLD);
            fila.addView(valor);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = Math.round(10 * dp);
            lista.addView(fila, lp);
        }
    }
}
