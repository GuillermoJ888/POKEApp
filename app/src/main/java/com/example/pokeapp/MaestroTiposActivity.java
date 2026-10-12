package com.example.pokeapp;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.pokeapp.battle.BattlePokemon;
import com.example.pokeapp.battle.TypeChart;
import com.example.pokeapp.data.CargadorPokemon;
import com.example.pokeapp.data.TypeColors;
import com.example.pokeapp.data.UsuarioManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Maestro de Tipos (módulo G): aparece un Pokémon al azar y hay que tocar, contra reloj,
 * el tipo de ataque que es súper eficaz contra él (misma tabla que las batallas: TypeChart).
 *
 * - Puntos: 50 por acierto + hasta 150 según lo rápido que se contestó.
 * - Dificultad progresiva: cada 3 aciertos sube el nivel y hay 1 segundo menos (de 10 s a 4 s).
 * - 3 vidas: se pierde una al fallar o al acabarse el tiempo.
 * - La mejor puntuación se guarda en Firestore (usuarios/{uid}.recordTipos).
 */
public class MaestroTiposActivity extends AppCompatActivity {

    private static final String RECORD = "recordTipos";
    private static final List<String> TIPOS = Arrays.asList("normal", "fire", "water", "electric", "grass",
            "ice", "fighting", "poison", "ground", "flying", "psychic", "bug", "rock", "ghost", "dragon",
            "dark", "steel", "fairy");
    private static final int VIDAS = 3, OPCIONES = 4;
    private static final long TIEMPO_INICIAL = 10000, TIEMPO_MINIMO = 4000, PAUSA = 1100;

    private final Random random = new Random();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private CountDownTimer reloj;

    private int puntos, vidas, aciertos, record;
    private boolean jugando, esperandoRespuesta;
    private long tiempoPregunta, tiempoRestante;
    private List<String> tiposRival;
    private String correcta;

    private TextView tvPuntos, tvVidas, tvNivel, tvRecord, tvNombre, tvTipos, tvPregunta;
    private ImageView img;
    private ProgressBar barraTiempo;
    private GridLayout gridOpciones;
    private View btnJugar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maestro_tipos);

        tvPuntos = findViewById(R.id.tvPuntosTipos);
        tvVidas = findViewById(R.id.tvVidasTipos);
        tvNivel = findViewById(R.id.tvNivelTipos);
        tvRecord = findViewById(R.id.tvRecordTipos);
        tvNombre = findViewById(R.id.tvNombreTipos);
        tvTipos = findViewById(R.id.tvTiposRival);
        tvPregunta = findViewById(R.id.tvPreguntaTipos);
        img = findViewById(R.id.imgTipos);
        barraTiempo = findViewById(R.id.barraTiempoTipos);
        gridOpciones = findViewById(R.id.gridOpcionesTipos);
        btnJugar = findViewById(R.id.btnJugarTipos);

        findViewById(R.id.btnRegresar).setOnClickListener(v -> finish());
        btnJugar.setOnClickListener(v -> empezar());

        UsuarioManager.obtenerRecord(RECORD, valor -> {
            record = Math.max(record, valor);
            pintarMarcador();
        });
        pintarMarcador();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (reloj != null) reloj.cancel();
        handler.removeCallbacksAndMessages(null);
    }

    /** Salir de la pantalla a media partida la termina (el reloj no sigue corriendo a escondidas). */
    @Override
    protected void onStop() {
        super.onStop();
        if (jugando) terminar("Partida interrumpida");
    }

    private int nivel() {
        return 1 + aciertos / 3;
    }

    private void pintarMarcador() {
        tvPuntos.setText("⭐ " + puntos);
        tvVidas.setText(vidas > 0 ? new String(new char[vidas]).replace("\0", "❤️") : "💔");
        tvNivel.setText("Nivel " + nivel());
        tvRecord.setText("🏆 Récord: " + record);
    }

    private void empezar() {
        puntos = 0;
        aciertos = 0;
        vidas = VIDAS;
        jugando = true;
        btnJugar.setVisibility(View.GONE);
        pintarMarcador();
        siguientePregunta();
    }

    private void siguientePregunta() {
        if (!jugando || isFinishing() || isDestroyed()) return;
        gridOpciones.removeAllViews();
        tvPregunta.setText("Buscando un Pokémon salvaje…");
        barraTiempo.setProgress(barraTiempo.getMax());

        int id = CargadorPokemon.idsAleatorios(1, false, random).get(0);
        CargadorPokemon.cargar(Collections.singletonList(id), lista -> {
            if (!jugando || isFinishing() || isDestroyed()) return;
            if (lista != null && !lista.isEmpty()) {
                BattlePokemon p = new BattlePokemon(lista.get(0));
                Glide.with(this).load(p.imagen).into(img);
                preguntar(p.nombre, p.tipos);
            } else {
                // Sin internet: se pregunta por un tipo, sin Pokémon
                String tipo = TIPOS.get(random.nextInt(TIPOS.size()));
                img.setImageResource(R.drawable.ic_pokeball_logo);
                preguntar("Pokémon de tipo " + TypeColors.traducir(tipo), Collections.singletonList(tipo));
            }
        });
    }

    private void preguntar(String nombre, List<String> tipos) {
        // Tipos de ataque súper eficaces (×2 o ×4) y los que no lo son
        List<String> buenas = new ArrayList<>(), malas = new ArrayList<>();
        for (String t : TIPOS) (TypeChart.efectividad(t, tipos) >= 2f ? buenas : malas).add(t);
        if (buenas.isEmpty()) {
            siguientePregunta();
            return;
        }
        Collections.shuffle(malas, random);
        correcta = buenas.get(random.nextInt(buenas.size()));
        List<String> opciones = new ArrayList<>(malas.subList(0, OPCIONES - 1));
        opciones.add(correcta);
        Collections.shuffle(opciones, random);

        tiposRival = tipos;
        tvNombre.setText(nombre);
        StringBuilder texto = new StringBuilder();
        for (String t : tipos) texto.append(texto.length() > 0 ? " · " : "").append(TypeColors.traducir(t));
        tvTipos.setText(texto);
        tvPregunta.setText("¿Qué tipo de ataque es súper eficaz contra " + nombre + "?");

        float dp = getResources().getDisplayMetrics().density;
        gridOpciones.removeAllViews();
        for (String tipo : opciones) {
            TextView boton = new TextView(this);
            boton.setText(TypeColors.traducir(tipo));
            boton.setTextColor(0xFFFFFFFF);
            boton.setTextSize(17);
            boton.setTypeface(null, Typeface.BOLD);
            boton.setGravity(Gravity.CENTER);
            boton.setShadowLayer(3 * dp, 0, dp, 0x99000000);
            boton.setBackground(fondo(TypeColors.obtenerColor(tipo), 0xCCFFFFFF));
            boton.setTag(tipo);
            boton.setOnClickListener(v -> responder(tipo));

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
            lp.width = 0;
            lp.height = Math.round(64 * dp);
            lp.setMargins(Math.round(5 * dp), Math.round(5 * dp), Math.round(5 * dp), Math.round(5 * dp));
            gridOpciones.addView(boton, lp);
        }

        iniciarReloj();
    }

    private GradientDrawable fondo(int color, int borde) {
        float dp = getResources().getDisplayMetrics().density;
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(16 * dp);
        d.setColor(color);
        d.setStroke(Math.round(3 * dp), borde);
        return d;
    }

    /** Dificultad progresiva: cada nivel da 1 segundo menos, hasta el mínimo. */
    private void iniciarReloj() {
        tiempoPregunta = Math.max(TIEMPO_MINIMO, TIEMPO_INICIAL - (nivel() - 1) * 1000L);
        tiempoRestante = tiempoPregunta;
        barraTiempo.setMax((int) tiempoPregunta);
        barraTiempo.setProgress((int) tiempoPregunta);
        esperandoRespuesta = true;

        if (reloj != null) reloj.cancel();
        reloj = new CountDownTimer(tiempoPregunta, 40) {
            @Override
            public void onTick(long restante) {
                tiempoRestante = restante;
                barraTiempo.setProgress((int) restante);
            }

            @Override
            public void onFinish() {
                barraTiempo.setProgress(0);
                tiempoRestante = 0;
                responder(null);   // se acabó el tiempo
            }
        }.start();
    }

    /** tipo == null: se acabó el tiempo. */
    private void responder(String tipo) {
        if (!esperandoRespuesta) return;
        esperandoRespuesta = false;
        if (reloj != null) reloj.cancel();

        boolean acierto = correcta.equals(tipo);
        if (acierto) {
            int ganados = 50 + Math.round(150f * tiempoRestante / tiempoPregunta);
            puntos += ganados;
            aciertos++;
            tvPregunta.setText("✅ ¡Correcto! +" + ganados + " puntos");
        } else {
            vidas--;
            float veces = TypeChart.efectividad(correcta, tiposRival);
            tvPregunta.setText((tipo == null ? "⏰ ¡Se acabó el tiempo! " : "❌ ")
                    + "Era " + TypeColors.traducir(correcta) + " (×" + (int) veces + ")");
        }

        // Se marca la correcta en verde y, si falló, la que tocó en rojo
        for (int i = 0; i < gridOpciones.getChildCount(); i++) {
            View boton = gridOpciones.getChildAt(i);
            boton.setClickable(false);
            Object suTipo = boton.getTag();
            if (correcta.equals(suTipo)) boton.setBackground(fondo(0xFF2E8B57, 0xFFFFFFFF));
            else if (suTipo.equals(tipo)) boton.setBackground(fondo(0xFFB91C1C, 0xFFFFFFFF));
            else boton.setAlpha(0.45f);
        }
        pintarMarcador();

        if (vidas <= 0) handler.postDelayed(() -> terminar("¡Fin del juego!"), PAUSA);
        else handler.postDelayed(this::siguientePregunta, PAUSA);
    }

    private void terminar(String titulo) {
        if (!jugando) return;
        jugando = false;
        esperandoRespuesta = false;
        if (reloj != null) reloj.cancel();
        handler.removeCallbacksAndMessages(null);

        boolean nuevoRecord = puntos > record;
        if (nuevoRecord) {
            record = puntos;
            UsuarioManager.guardarRecord(RECORD, puntos);
        }
        gridOpciones.removeAllViews();
        tvPregunta.setText(titulo + "\n" + puntos + " puntos · " + aciertos + " aciertos · nivel " + nivel()
                + (nuevoRecord ? "\n🏆 ¡Nuevo récord!" : ""));
        ((TextView) btnJugar).setText("🔁 Jugar otra vez");
        btnJugar.setVisibility(View.VISIBLE);
        pintarMarcador();
    }
}
