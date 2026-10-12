package com.example.pokeapp;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.pokeapp.data.Preferencias;
import com.example.pokeapp.data.UsuarioManager;
import com.google.firebase.auth.FirebaseAuth;

/**
 * Configuración (módulo M).
 *  - Sonido y Vibración: interruptores guardados en el celular (Preferencias).
 *  - Reiniciar progreso: con confirmación; borra victorias, medallas, récords, colección e historial.
 *  - Cerrar sesión: con confirmación.
 *  - Idioma: alterna Español / English (toda la app se redibuja).
 *  - Tema: alterna Claro / Oscuro / Según el celular.
 */
public class ConfiguracionActivity extends AppCompatActivity {

    private LinearLayout lista;
    private float dp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_configuracion);
        findViewById(R.id.btnRegresar).setOnClickListener(v -> finish());

        lista = findViewById(R.id.listaConfiguracion);
        dp = getResources().getDisplayMetrics().density;

        // Interruptores (se guardan solo en este celular)
        TextView[] valorSonido = new TextView[1];
        valorSonido[0] = agregarFila("🔊", getString(R.string.cfg_sonido), getString(R.string.cfg_sonido_desc),
                textoInterruptor(Preferencias.sonido(this)), () -> {
                    boolean nuevo = !Preferencias.sonido(this);
                    Preferencias.setSonido(this, nuevo);
                    valorSonido[0].setText(textoInterruptor(nuevo));
                    if (nuevo) Efectos.golpe(this, false);   // muestra de cómo suena
                });

        TextView[] valorVibracion = new TextView[1];
        valorVibracion[0] = agregarFila("📳", getString(R.string.cfg_vibracion), getString(R.string.cfg_vibracion_desc),
                textoInterruptor(Preferencias.vibracion(this)), () -> {
                    boolean nueva = !Preferencias.vibracion(this);
                    Preferencias.setVibracion(this, nueva);
                    valorVibracion[0].setText(textoInterruptor(nueva));
                    if (nueva) Efectos.vibrar(this, 120);    // muestra
                });

        // Al cambiar idioma o tema, Android vuelve a dibujar las pantallas abiertas
        boolean ingles = Preferencias.esIngles();
        agregarFila("🌐", getString(R.string.cfg_idioma), getString(R.string.cfg_idioma_desc),
                ingles ? "English" : "Español",
                () -> Preferencias.setIdioma(ingles ? "es" : "en"));

        agregarFila("🎨", getString(R.string.cfg_tema), getString(R.string.cfg_tema_desc),
                textoTema(Preferencias.tema(this)),
                () -> Preferencias.setTema(this, siguienteTema(Preferencias.tema(this))));

        agregarFila("♻️", getString(R.string.cfg_reiniciar), getString(R.string.cfg_reiniciar_desc),
                "›", this::confirmarReinicio);

        agregarFila("🚪", getString(R.string.cfg_cerrar_sesion), getString(R.string.cfg_cerrar_sesion_desc),
                "›", this::confirmarCerrarSesion);
    }

    private String textoTema(String tema) {
        if (Preferencias.TEMA_OSCURO.equals(tema)) return getString(R.string.cfg_tema_oscuro);
        if (Preferencias.TEMA_SISTEMA.equals(tema)) return getString(R.string.cfg_tema_sistema);
        return getString(R.string.cfg_tema_claro);
    }

    /** Claro → Oscuro → Según el celular → Claro... */
    private String siguienteTema(String tema) {
        if (Preferencias.TEMA_CLARO.equals(tema)) return Preferencias.TEMA_OSCURO;
        if (Preferencias.TEMA_OSCURO.equals(tema)) return Preferencias.TEMA_SISTEMA;
        return Preferencias.TEMA_CLARO;
    }

    private String textoInterruptor(boolean activo) {
        return getString(activo ? R.string.cfg_activado : R.string.cfg_desactivado);
    }

    /**
     * Agrega una opción a la lista. alTocar = null la deja bloqueada (gris).
     * Devuelve el texto de la derecha, para poder actualizarlo.
     */
    private TextView agregarFila(String emoji, String titulo, String descripcion, String valor, Runnable alTocar) {
        LinearLayout fila = new LinearLayout(this);
        fila.setOrientation(LinearLayout.HORIZONTAL);
        fila.setGravity(Gravity.CENTER_VERTICAL);
        fila.setBackgroundResource(R.drawable.bg_card_menu);
        fila.setPadding(Math.round(14 * dp), Math.round(14 * dp), Math.round(14 * dp), Math.round(14 * dp));

        if (alTocar != null) {
            fila.setClickable(true);
            fila.setFocusable(true);
            fila.setOnClickListener(v -> alTocar.run());
        } else {
            fila.setAlpha(0.55f);   // bloqueada
        }

        TextView icono = new TextView(this);
        icono.setText(emoji);
        icono.setTextSize(24);
        fila.addView(icono);

        LinearLayout textos = new LinearLayout(this);
        textos.setOrientation(LinearLayout.VERTICAL);
        textos.setPadding(Math.round(12 * dp), 0, Math.round(8 * dp), 0);

        TextView tvTitulo = new TextView(this);
        tvTitulo.setText(titulo);
        tvTitulo.setTextColor(getColor(R.color.menu_text));
        tvTitulo.setTextSize(16);
        tvTitulo.setTypeface(null, Typeface.BOLD);
        textos.addView(tvTitulo);

        TextView tvDescripcion = new TextView(this);
        tvDescripcion.setText(descripcion);
        tvDescripcion.setTextColor(getColor(R.color.menu_text_soft));
        tvDescripcion.setTextSize(12);
        textos.addView(tvDescripcion);
        fila.addView(textos, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvValor = new TextView(this);
        tvValor.setText(valor);
        tvValor.setTextColor(getColor(alTocar != null ? R.color.menu_red : R.color.menu_text_soft));
        tvValor.setTextSize(13);
        tvValor.setTypeface(null, Typeface.BOLD);
        fila.addView(tvValor);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Math.round(10 * dp);
        lista.addView(fila, lp);
        return tvValor;
    }

    private void confirmarReinicio() {
        Dialogos.confirmar(this, "♻️", getString(R.string.cfg_reiniciar_pregunta),
                getString(R.string.cfg_reiniciar_aviso),
                getString(R.string.cfg_reiniciar_si), getString(R.string.cfg_cancelar),
                () -> UsuarioManager.reiniciarProgreso(ok -> {
                    if (isFinishing() || isDestroyed()) return;
                    Toast.makeText(this, Boolean.TRUE.equals(ok)
                                    ? R.string.cfg_reiniciado
                                    : R.string.cfg_reiniciar_error,
                            Toast.LENGTH_LONG).show();
                }), null);
    }

    private void confirmarCerrarSesion() {
        Dialogos.confirmar(this, "🚪", getString(R.string.cfg_cerrar_pregunta),
                getString(R.string.cfg_cerrar_aviso),
                getString(R.string.cfg_cerrar_sesion), getString(R.string.cfg_cancelar),
                () -> {
                    FirebaseAuth.getInstance().signOut();
                    Intent intent = new Intent(this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                }, null);
    }
}
