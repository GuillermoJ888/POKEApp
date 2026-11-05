package com.example.pokeapp;

import com.example.pokeapp.data.Idioma;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
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
 * - Evento especial: cada 10 de racha, si te falta algún corazón, la siguiente ronda muestra solo
 *   un pedazo de la silueta y sin pistas; si aciertas recuperas 1 corazón (fallar ahí no quita vida).
 *   La mitad de las veces, en vez de un Pokémon sale la silueta de un entrenador de la Torre.
 * - Se guardan en Firestore el mejor puntaje (recordQuien) y la mejor racha (rachaQuien).
 */
public class QuienEsActivity extends AppCompatActivity {

    private static final String RECORD = "recordQuien", RACHA = "rachaQuien";
    private static final int VIDAS = 3, OPCIONES = 4, PUNTOS = 100;
    private static final int COSTO_TIPO = 20, COSTO_STATS = 20, COSTO_CRY = 10;
    private static final int COLOR_SILUETA = 0xFF1B1B2F;
    private static final long PAUSA = 1600;
    private static final int RACHA_EVENTO = 10;   // cada cuántos aciertos seguidos puede salir el evento

    private final Random random = new Random();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer cry;

    private int puntos, vidas, racha, mejorRacha, record, valorPregunta;
    private boolean jugando, esperandoRespuesta, pistaTipo, pistaStats, pistaCry;
    private boolean eventoPendiente, eventoEspecial;   // ronda de "solo un pedazo" para recuperar un corazón
    private BattlePokemon secreto;
    private String entrenadorSecreto;   // en el evento especial: nombre del entrenador a adivinar (null = es un Pokémon)

    private TextView tvPuntos, tvRacha, tvVidas, tvRecord, tvPregunta, tvPistas;
    private TextView btnTipo, btnStats, btnCry;
    private ImageView img;
    private GridLayout gridOpciones;
    private View btnJugar, filaPistas;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Efectos.precargar(this);
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

        btnTipo.setText(Idioma.t("🏷 Tipo −", "🏷 Type −") + COSTO_TIPO);
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
    protected void onStop() {
        super.onStop();
        soltarCry();
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
        tvRacha.setText(Idioma.t("🔥 Racha ", "🔥 Streak ") + racha);
        tvVidas.setText(vidas > 0 ? new String(new char[vidas]).replace("\0", "❤️") : "💔");
        tvRecord.setText(Idioma.t("🏆 Récord: ", "🏆 Record: ") + record + Idioma.t(" · Mejor racha: ", " · Best streak: ") + mejorRacha);
    }

    private void empezar() {
        puntos = 0;
        racha = 0;
        vidas = VIDAS;
        eventoPendiente = eventoEspecial = false;
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
        eventoEspecial = eventoPendiente;
        eventoPendiente = false;
        tvPregunta.setText(eventoEspecial ? Idioma.t("🎁 ¡EVENTO ESPECIAL!", "🎁 SPECIAL EVENT!") + "\n" + Idioma.t("Adivina con solo un pedazo y recupera un ❤️", "Guess from just a piece and recover a ❤️")
                : Idioma.t("Buscando un Pokémon…", "Looking for a Pokémon…"));
        img.setImageResource(R.drawable.ic_pokeball_logo);
        img.clearColorFilter();
        entrenadorSecreto = null;

        if (eventoEspecial && random.nextBoolean() && preguntarEntrenador()) return;

        // El primero es el secreto; los demás solo dan nombres para las otras opciones
        CargadorPokemon.cargar(CargadorPokemon.idsAleatorios(OPCIONES, false, random), lista -> {
            if (!jugando || isFinishing() || isDestroyed()) return;
            if (lista == null) {
                Toast.makeText(this, Idioma.t("No se pudo cargar el Pokémon. Revisa tu internet.", "Could not load the Pokémon. Check your internet."), Toast.LENGTH_LONG).show();
                terminar(Idioma.t("Sin conexión", "No connection"));
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

        img.setColorFilter(COLOR_SILUETA, PorterDuff.Mode.SRC_IN);   // solo la silueta
        if (eventoEspecial) {
            mostrarPedazo();   // sin pistas: solo un pedazo de la silueta
            tvPregunta.setText(Idioma.t("🎁 ¿Quién es? Si aciertas recuperas un ❤️", "🎁 Who is it? Get it right to recover a ❤️"));
        } else {
            Glide.with(this).load(secreto.imagen).into(img);
            tvPregunta.setText(Idioma.t("¿Quién es ese Pokémon?", "Who's that Pokémon?"));
            for (TextView b : new TextView[]{btnTipo, btnStats, btnCry}) b.setAlpha(1f);
            filaPistas.setVisibility(View.VISIBLE);
        }

        pintarOpciones(nombres);
    }

    /** Los 4 botones de respuesta. */
    private void pintarOpciones(List<String> nombres) {
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

    /** Evento especial: recorta un pedazo al azar del Pokémon (alrededor de 40 % de su tamaño) y solo muestra eso. */
    private void mostrarPedazo() {
        BattlePokemon esperado = secreto;
        // Sin "hardware bitmap": hace falta leer sus píxeles para encontrar la parte visible
        Glide.with(this).asBitmap().load(secreto.imagen).disallowHardwareConfig().into(new CustomTarget<Bitmap>() {
            @Override
            public void onResourceReady(@NonNull Bitmap bitmap, Transition<? super Bitmap> transicion) {
                if (isFinishing() || isDestroyed() || secreto != esperado || !esperandoRespuesta) return;
                img.setImageBitmap(recortarPedazo(bitmap));
                img.animate().scaleX(1.08f).scaleY(1.08f).setDuration(250)
                        .withEndAction(() -> img.animate().scaleX(1f).scaleY(1f).setDuration(250).start()).start();
            }

            @Override
            public void onLoadCleared(Drawable sinUso) { }
        });
    }

    /** Cuadro al azar dentro de la parte visible (no transparente) de la imagen. */
    private Bitmap recortarPedazo(Bitmap bitmap) {
        int ancho = bitmap.getWidth(), alto = bitmap.getHeight();
        int minX = ancho, minY = alto, maxX = 0, maxY = 0;
        for (int y = 0; y < alto; y += 4) {
            for (int x = 0; x < ancho; x += 4) {
                if (Color.alpha(bitmap.getPixel(x, y)) > 40) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX <= minX || maxY <= minY) return bitmap;   // imagen rara: se muestra completa
        int lado = Math.max(16, Math.round(Math.max(maxX - minX, maxY - minY) * 0.42f));
        int centroX = minX + random.nextInt(Math.max(1, maxX - minX));
        int centroY = minY + random.nextInt(Math.max(1, maxY - minY));
        int x = Math.max(0, Math.min(ancho - lado, centroX - lado / 2));
        int y = Math.max(0, Math.min(alto - lado, centroY - lado / 2));
        return Bitmap.createBitmap(bitmap, x, y, Math.min(lado, ancho), Math.min(lado, alto));
    }

    /**
     * Evento especial con un entrenador de la Torre (los que ya tienen su personaje, sin el jefe secreto):
     * su silueta completa y 4 nombres de entrenadores. Devuelve false si no hay personajes.
     */
    private boolean preguntarEntrenador() {
        List<Integer> conPersonaje = new ArrayList<>();
        List<String> todos = new ArrayList<>();
        for (int n = 1; n <= TorreActivity.NIVELES_NORMALES; n++) {
            todos.add(TorreActivity.nombreDocente(n));
            if (getResources().getIdentifier("entrenador_" + n, "drawable", getPackageName()) != 0) conPersonaje.add(n);
        }
        if (conPersonaje.isEmpty()) return false;

        int n = conPersonaje.get(random.nextInt(conPersonaje.size()));
        entrenadorSecreto = TorreActivity.nombreDocente(n);
        List<String> nombres = new ArrayList<>();
        nombres.add(entrenadorSecreto);
        Collections.shuffle(todos, random);
        for (String otro : todos) {
            if (nombres.size() < OPCIONES && !otro.equals(entrenadorSecreto)) nombres.add(otro);
        }
        Collections.shuffle(nombres, random);

        esperandoRespuesta = true;
        img.setImageResource(getResources().getIdentifier("entrenador_" + n, "drawable", getPackageName()));
        img.setColorFilter(COLOR_SILUETA, PorterDuff.Mode.SRC_IN);
        tvPregunta.setText(Idioma.t("🎁 ¿Quién es este ENTRENADOR? Si aciertas recuperas un ❤️", "🎁 Who is this TRAINER? Get it right to recover a ❤️"));
        pintarOpciones(nombres);
        return true;
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
            agregarPista(Idioma.t("Tipo: ", "Type: ") + secreto.tiposTexto());
            btnTipo.setAlpha(0.4f);
        } else if (cual == 2 && !pistaStats) {
            pistaStats = true;
            valorPregunta -= COSTO_STATS;
            agregarPista("HP " + secreto.hpMaxima + Idioma.t(" · Ataque ", " · Attack ") + secreto.ataque
                    + Idioma.t(" · Defensa ", " · Defense ") + secreto.defensa + Idioma.t(" · Velocidad ", " · Speed ") + secreto.velocidad);
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
        tvPregunta.setText(Idioma.t("¿Quién es ese Pokémon? (vale ", "Who's that Pokémon? (worth ") + valorPregunta + Idioma.t(" puntos)", " points)"));
    }

    private void agregarPista(String texto) {
        tvPistas.setText(tvPistas.length() == 0 ? texto : tvPistas.getText() + "\n" + texto);
    }

    private void reproducirCry() {
        if (!com.example.pokeapp.data.Preferencias.sonido(this)) {
            Toast.makeText(this, Idioma.t("El sonido está desactivado en Configuración", "Sound is turned off in Settings"), Toast.LENGTH_SHORT).show();
            return;
        }
        soltarCry();
        cry = new MediaPlayer();
        try {
            cry.setDataSource("https://raw.githubusercontent.com/PokeAPI/cries/main/cries/pokemon/latest/"
                    + secreto.id + ".ogg");
            cry.setOnPreparedListener(MediaPlayer::start);
            cry.setOnErrorListener((mp, what, extra) -> {
                Toast.makeText(this, Idioma.t("No se pudo reproducir el cry", "Could not play the cry"), Toast.LENGTH_SHORT).show();
                return true;
            });
            cry.prepareAsync();
        } catch (Exception e) {
            Toast.makeText(this, Idioma.t("No se pudo reproducir el cry", "Could not play the cry"), Toast.LENGTH_SHORT).show();
        }
    }

    private void responder(String nombre) {
        if (!esperandoRespuesta) return;
        esperandoRespuesta = false;
        filaPistas.setVisibility(View.GONE);
        img.clearColorFilter();   // se revela el Pokémon

        String correcto = entrenadorSecreto != null ? entrenadorSecreto : secreto.nombre;
        boolean acierto = correcto.equals(nombre);
        if (eventoEspecial) {
            // Ronda de premio: se ve completo; acertar devuelve un corazón y fallar no quita nada
            if (entrenadorSecreto == null) Glide.with(this).load(secreto.imagen).into(img);
            eventoEspecial = false;
            entrenadorSecreto = null;
            if (acierto) {
                vidas = Math.min(VIDAS, vidas + 1);
                Efectos.sonar(this, R.raw.snd_captura);
                Efectos.vibrar(this, 120);
                tvPregunta.setText(Idioma.t("💖 ¡Es ", "💖 It's ") + correcto + Idioma.t("! Recuperaste un corazón", "! You recovered a heart"));
            } else {
                Efectos.sonar(this, R.raw.snd_error);
                tvPregunta.setText(Idioma.t("❌ ¡Era ", "❌ It was ") + correcto + Idioma.t("! No pierdes vida, sigue jugando.", "! You keep your life, keep playing."));
            }
        } else if (acierto) {
            puntos += valorPregunta;
            racha++;
            Efectos.sonar(this, R.raw.snd_acierto);
            mejorRacha = Math.max(mejorRacha, racha);
            tvPregunta.setText(Idioma.t("✅ ¡Es ", "✅ It's ") + correcto + "! +" + valorPregunta + Idioma.t(" puntos", " points"));
            // Cada 10 de racha, si falta algún corazón, la siguiente ronda es el evento especial
            if (racha % RACHA_EVENTO == 0 && vidas < VIDAS) {
                eventoPendiente = true;
                tvPregunta.setText(tvPregunta.getText() + Idioma.t("\n🎁 ¡Racha de ", "\n🎁 Streak of ") + racha + Idioma.t("! Viene un evento especial", "! A special event is coming"));
            }
        } else {
            vidas--;
            racha = 0;
            Efectos.sonar(this, R.raw.snd_error);
            tvPregunta.setText(Idioma.t("❌ ¡Era ", "❌ It was ") + correcto + "!");
        }

        for (int i = 0; i < gridOpciones.getChildCount(); i++) {
            TextView boton = (TextView) gridOpciones.getChildAt(i);
            boton.setClickable(false);
            String suNombre = boton.getText().toString();
            if (suNombre.equals(correcto)) boton.setBackground(fondo(0xFF2E8B57));
            else if (suNombre.equals(nombre)) boton.setBackground(fondo(0xFFB91C1C));
            else boton.setAlpha(0.45f);
        }
        pintarMarcador();

        if (vidas <= 0) handler.postDelayed(() -> terminar(Idioma.t("¡Fin del juego!", "Game over!")), PAUSA);
        else handler.postDelayed(this::siguiente, PAUSA);
    }

    private void terminar(String titulo) {
        if (!jugando) return;
        jugando = false;
        esperandoRespuesta = false;

        boolean nuevoRecord = puntos > record;
        Efectos.sonar(this, nuevoRecord ? R.raw.snd_record : R.raw.snd_choque);
        if (nuevoRecord) {
            record = puntos;
            UsuarioManager.guardarRecord(RECORD, puntos);
        }
        UsuarioManager.guardarRecord(RACHA, mejorRacha);

        gridOpciones.removeAllViews();
        filaPistas.setVisibility(View.GONE);
        tvPistas.setText("");
        tvPregunta.setText(titulo + "\n" + puntos + Idioma.t(" puntos", " points") + (nuevoRecord ? Idioma.t("\n🏆 ¡Nuevo récord!", "\n🏆 New record!") : ""));
        ((TextView) btnJugar).setText(Idioma.t("🔁 Jugar otra vez", "🔁 Play again"));
        btnJugar.setVisibility(View.VISIBLE);
        pintarMarcador();
    }
}
