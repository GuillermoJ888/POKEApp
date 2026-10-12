package com.example.pokeapp;

import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.pokeapp.battle.BattlePokemon;
import com.example.pokeapp.data.CargadorPokemon;
import com.example.pokeapp.data.UsuarioManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * ¿Quién es ese Pokémon? (módulo E): se muestra la silueta de un Pokémon al azar y hay que
 * elegir su nombre entre 4 opciones; al responder se revela.
 *
 * - Puntos: 100 por acierto, menos lo que cuesten las pistas usadas (tipo −20, stats −20, cry −10).
 * - 3 vidas: se pierde una al fallar. La racha son los aciertos seguidos.
 * - Se guardan en Firestore el mejor puntaje (recordQuien) y la mejor racha (rachaQuien).
 */
public class QuienEsActivity extends AppCompatActivity {

    private static final String RECORD = "recordQuien", RACHA = "rachaQuien";
    private static final int VIDAS = 3, OPCIONES = 4, PUNTOS = 100;
    private static final int COSTO_TIPO = 20, COSTO_STATS = 20, COSTO_CRY = 10;
    private static final int COLOR_SILUETA = 0xFF1B1B2F;
    private static final long PAUSA = 1600;

    private final Random random = new Random();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer cry;

    private int puntos, vidas, racha, mejorRacha, record, valorPregunta;
    private boolean jugando, esperandoRespuesta, pistaTipo, pistaStats, pistaCry;
    private BattlePokemon secreto;

    private TextView tvPuntos, tvRacha, tvVidas, tvRecord, tvPregunta, tvPistas;
    private TextView btnTipo, btnStats, btnCry;
    private ImageView img;
    private GridLayout gridOpciones;
    private View btnJugar, filaPistas;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quien_es);

        tvPuntos = findViewById(R.id.tvPuntosQuien);
        tvRacha = findViewById(R.id.tvRachaQuien);
        tvVidas = findViewById(R.id.tvVidasQuien);
        tvRecord = findViewById(R.id.tvRecordQuien);
        tvPregunta = findViewById(R.id.tvPreguntaQuien);
        tvPistas = findViewById(R.id.tvPistasQuien);
        btnTipo = findViewById(R.id.btnPistaTipo);
        btnStats = findViewById(R.id.btnPistaStats);
        btnCry = findViewById(R.id.btnPistaCry);
        filaPistas = findViewById(R.id.filaPistasQuien);
        img = findViewById(R.id.imgQuien);
        gridOpciones = findViewById(R.id.gridOpcionesQuien);
        btnJugar = findViewById(R.id.btnJugarQuien);

        btnTipo.setText("🏷 Tipo −" + COSTO_TIPO);
        btnStats.setText("📊 Stats −" + COSTO_STATS);
        btnCry.setText("🔊 Cry −" + COSTO_CRY);

        findViewById(R.id.btnRegresar).setOnClickListener(v -> finish());
        btnJugar.setOnClickListener(v -> empezar());
        btnTipo.setOnClickListener(v -> usarPista(1));
        btnStats.setOnClickListener(v -> usarPista(2));
        btnCry.setOnClickListener(v -> usarPista(3));

        UsuarioManager.obtenerRecord(RECORD, valor -> {
            record = Math.max(record, valor);
            pintarMarcador();
        });
        UsuarioManager.obtenerRecord(RACHA, valor -> {
            mejorRacha = Math.max(mejorRacha, valor);
            pintarMarcador();
        });
        pintarMarcador();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        soltarCry();
    }

    private void soltarCry() {
        if (cry != null) {
            cry.release();
            cry = null;
        }
    }

    private void pintarMarcador() {
        tvPuntos.setText("⭐ " + puntos);
        tvRacha.setText("🔥 Racha " + racha);
        tvVidas.setText(vidas > 0 ? new String(new char[vidas]).replace("\0", "❤️") : "💔");
        tvRecord.setText("🏆 Récord: " + record + " · Mejor racha: " + mejorRacha);
    }

    private void empezar() {
        puntos = 0;
        racha = 0;
        vidas = VIDAS;
        jugando = true;
        btnJugar.setVisibility(View.GONE);
        pintarMarcador();
        siguiente();
    }

    private void siguiente() {
        if (!jugando || isFinishing() || isDestroyed()) return;
        gridOpciones.removeAllViews();
        filaPistas.setVisibility(View.GONE);
        tvPistas.setText("");
        tvPregunta.setText("Buscando un Pokémon…");
        img.setImageResource(R.drawable.ic_pokeball_logo);
        img.clearColorFilter();

        // El primero es el secreto; los demás solo dan nombres para las otras opciones
        CargadorPokemon.cargar(CargadorPokemon.idsAleatorios(OPCIONES, false, random), lista -> {
            if (!jugando || isFinishing() || isDestroyed()) return;
            if (lista == null) {
                Toast.makeText(this, "No se pudo cargar el Pokémon. Revisa tu internet.", Toast.LENGTH_LONG).show();
                terminar("Sin conexión");
                return;
            }
            secreto = new BattlePokemon(lista.get(0));
            List<String> nombres = new ArrayList<>();
            for (int i = 0; i < lista.size(); i++) nombres.add(new BattlePokemon(lista.get(i)).nombre);
            Collections.shuffle(nombres, random);
            preguntar(nombres);
        });
    }

    private void preguntar(List<String> nombres) {
        valorPregunta = PUNTOS;
        pistaTipo = pistaStats = pistaCry = false;
        esperandoRespuesta = true;

        Glide.with(this).load(secreto.imagen).into(img);
        img.setColorFilter(COLOR_SILUETA, PorterDuff.Mode.SRC_IN);   // solo la silueta
        tvPregunta.setText("¿Quién es ese Pokémon?");
        for (TextView b : new TextView[]{btnTipo, btnStats, btnCry}) b.setAlpha(1f);
        filaPistas.setVisibility(View.VISIBLE);

        float dp = getResources().getDisplayMetrics().density;
        gridOpciones.removeAllViews();
        for (String nombre : nombres) {
            TextView boton = new TextView(this);
            boton.setText(nombre);
            boton.setTextColor(0xFFFFFFFF);
            boton.setTextSize(16);
            boton.setTypeface(null, Typeface.BOLD);
            boton.setGravity(Gravity.CENTER);
            boton.setMaxLines(2);
            boton.setBackground(fondo(0xFF1D4ED8));
            boton.setOnClickListener(v -> responder(nombre));

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
            lp.width = 0;
            lp.height = Math.round(60 * dp);
            lp.setMargins(Math.round(5 * dp), Math.round(5 * dp), Math.round(5 * dp), Math.round(5 * dp));
            gridOpciones.addView(boton, lp);
        }
    }

    private GradientDrawable fondo(int color) {
        float dp = getResources().getDisplayMetrics().density;
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(16 * dp);
        d.setColor(color);
        d.setStroke(Math.round(2 * dp), 0xCCFFFFFF);
        return d;
    }

    /** Pistas opcionales (1 tipo, 2 stats, 3 cry): cada una se cobra una sola vez por pregunta. */
    private void usarPista(int cual) {
        if (!esperandoRespuesta || secreto == null) return;
        if (cual == 1 && !pistaTipo) {
            pistaTipo = true;
            valorPregunta -= COSTO_TIPO;
            agregarPista("Tipo: " + secreto.tiposTexto());
            btnTipo.setAlpha(0.4f);
        } else if (cual == 2 && !pistaStats) {
            pistaStats = true;
            valorPregunta -= COSTO_STATS;
            agregarPista("HP " + secreto.hpMaxima + " · Ataque " + secreto.ataque
                    + " · Defensa " + secreto.defensa + " · Velocidad " + secreto.velocidad);
            btnStats.setAlpha(0.4f);
        } else if (cual == 3) {
            // Con el sonido apagado no se cobra la pista (reproducirCry avisa por qué no suena)
            if (!pistaCry && com.example.pokeapp.data.Preferencias.sonido(this)) {
                pistaCry = true;
                valorPregunta -= COSTO_CRY;
                btnCry.setAlpha(0.4f);
            }
            reproducirCry();   // se puede volver a escuchar sin costo
        }
        tvPregunta.setText("¿Quién es ese Pokémon? (vale " + valorPregunta + " puntos)");
    }

    private void agregarPista(String texto) {
        tvPistas.setText(tvPistas.length() == 0 ? texto : tvPistas.getText() + "\n" + texto);
    }

    private void reproducirCry() {
        if (!com.example.pokeapp.data.Preferencias.sonido(this)) {
            Toast.makeText(this, "El sonido está desactivado en Configuración", Toast.LENGTH_SHORT).show();
            return;
        }
        soltarCry();
        cry = new MediaPlayer();
        try {
            cry.setDataSource("https://raw.githubusercontent.com/PokeAPI/cries/main/cries/pokemon/latest/"
                    + secreto.id + ".ogg");
            cry.setOnPreparedListener(MediaPlayer::start);
            cry.setOnErrorListener((mp, what, extra) -> {
                Toast.makeText(this, "No se pudo reproducir el cry", Toast.LENGTH_SHORT).show();
                return true;
            });
            cry.prepareAsync();
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo reproducir el cry", Toast.LENGTH_SHORT).show();
        }
    }

    private void responder(String nombre) {
        if (!esperandoRespuesta) return;
        esperandoRespuesta = false;
        filaPistas.setVisibility(View.GONE);
        img.clearColorFilter();   // se revela el Pokémon

        boolean acierto = secreto.nombre.equals(nombre);
        if (acierto) {
            puntos += valorPregunta;
            racha++;
            mejorRacha = Math.max(mejorRacha, racha);
            tvPregunta.setText("✅ ¡Es " + secreto.nombre + "! +" + valorPregunta + " puntos");
        } else {
            vidas--;
            racha = 0;
            tvPregunta.setText("❌ ¡Era " + secreto.nombre + "!");
        }

        for (int i = 0; i < gridOpciones.getChildCount(); i++) {
            TextView boton = (TextView) gridOpciones.getChildAt(i);
            boton.setClickable(false);
            String suNombre = boton.getText().toString();
            if (suNombre.equals(secreto.nombre)) boton.setBackground(fondo(0xFF2E8B57));
            else if (suNombre.equals(nombre)) boton.setBackground(fondo(0xFFB91C1C));
            else boton.setAlpha(0.45f);
        }
        pintarMarcador();

        if (vidas <= 0) handler.postDelayed(() -> terminar("¡Fin del juego!"), PAUSA);
        else handler.postDelayed(this::siguiente, PAUSA);
    }

    private void terminar(String titulo) {
        if (!jugando) return;
        jugando = false;
        esperandoRespuesta = false;

        boolean nuevoRecord = puntos > record;
        if (nuevoRecord) {
            record = puntos;
            UsuarioManager.guardarRecord(RECORD, puntos);
        }
        UsuarioManager.guardarRecord(RACHA, mejorRacha);

        gridOpciones.removeAllViews();
        filaPistas.setVisibility(View.GONE);
        tvPistas.setText("");
        tvPregunta.setText(titulo + "\n" + puntos + " puntos" + (nuevoRecord ? "\n🏆 ¡Nuevo récord!" : ""));
        ((TextView) btnJugar).setText("🔁 Jugar otra vez");
        btnJugar.setVisibility(View.VISIBLE);
        pintarMarcador();
    }
}
