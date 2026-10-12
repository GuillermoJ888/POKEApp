package com.example.pokeapp;

import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.pokeapp.battle.BattlePokemon;
import com.example.pokeapp.data.CargadorPokemon;
import com.example.pokeapp.data.PokemonMini;
import com.example.pokeapp.data.UsuarioManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Safari Pokémon (módulo F): aparecen Pokémon al azar y se intenta capturarlos.
 *
 * - Tres Pokéball con distinta probabilidad: Poké Ball 35 %, Super Ball 55 %, Ultra Ball 80 %.
 *   A los Pokémon fuertes (suma de stats alta) se les resta hasta 25 puntos.
 * - Cada safari empieza con 8 Poké Ball, 4 Super Ball y 2 Ultra Ball; termina al acabarse.
 * - Lanzamiento estilo Pokémon GO: se elige la Pokéball con su botón y se desliza con el dedo hacia
 *   el Pokémon. Si va floja o desviada, se pierde. Bola curva (trazo en arco): +10 %; tiro al centro: +5 %.
 *   Tocar otra vez el botón de la bola elegida la lanza recta, sin bonos.
 * - Si no queda atrapado, el Pokémon puede escapar (25 %). También se puede huir del encuentro.
 * - Los capturados se guardan en la colección del usuario (Firestore: usuarios/{uid}.capturados).
 */
public class SafariActivity extends AppCompatActivity {

    private static final String[] BOLAS = {"Poké Ball", "Super Ball", "Ultra Ball"};
    private static final int[] PROBABILIDAD = {35, 55, 80};
    private static final int[] INICIALES = {8, 4, 2};
    private static final int[] COLOR = {0xFFE3350D, 0xFF1D4ED8, 0xFFB8860B};
    private static final int PROBABILIDAD_ESCAPE = 25;
    private static final int BONO_CURVA = 10, BONO_CENTRO = 5;

    // Lanzamiento con el dedo (medidas en dp)
    private static final float ARRASTRE_MINIMO = 40f;      // menos que esto no cuenta como lanzamiento
    private static final float VELOCIDAD_MINIMA = 250f;    // dp/s hacia arriba para soltar la bola
    private static final float VELOCIDAD_ALCANCE = 600f;   // dp/s para que llegue hasta el Pokémon
    private static final float DESVIO_CURVA = 22f;         // cuánto se arquea el trazo para ser bola curva

    private final Random random = new Random();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final int[] bolas = new int[BOLAS.length];
    private BattlePokemon salvaje;
    private int dificultad;            // puntos que se restan a la probabilidad (0–25)
    private boolean ocupado = true;    // cargando o a media animación
    private int seleccionada;          // Pokéball lista para lanzar
    private float dp;

    // Recorrido del dedo mientras arrastra la bola (para sacar velocidad y curva)
    private final List<float[]> trazo = new ArrayList<>();   // {x, y, milisegundos}
    private int capturadosHoy;
    private List<PokemonMini> coleccion = new ArrayList<>();

    private ImageView imgPokemon, imgBola;
    private TextView tvNombre, tvDatos, tvMensaje, tvColeccion;
    private final TextView[] botones = new TextView[BOLAS.length];
    private View btnHuir, btnNuevo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_safari);

        imgPokemon = findViewById(R.id.imgSafari);
        imgBola = findViewById(R.id.imgBolaSafari);
        tvNombre = findViewById(R.id.tvNombreSafari);
        tvDatos = findViewById(R.id.tvDatosSafari);
        tvMensaje = findViewById(R.id.tvMensajeSafari);
        tvColeccion = findViewById(R.id.tvColeccionSafari);
        botones[0] = findViewById(R.id.btnBola0);
        botones[1] = findViewById(R.id.btnBola1);
        botones[2] = findViewById(R.id.btnBola2);
        btnHuir = findViewById(R.id.btnHuirSafari);
        btnNuevo = findViewById(R.id.btnNuevoSafari);

        findViewById(R.id.btnRegresar).setOnClickListener(v -> finish());
        findViewById(R.id.btnVerColeccion).setOnClickListener(v -> mostrarColeccion());
        for (int i = 0; i < BOLAS.length; i++) {
            final int tipo = i;
            botones[i].setOnClickListener(v -> elegirBola(tipo));

            // Cada botón lleva dibujada su Pokéball
            BolaDrawable icono = new BolaDrawable(i);
            int lado = Math.round(30 * getResources().getDisplayMetrics().density);
            icono.setBounds(0, 0, lado, lado);
            botones[i].setCompoundDrawablesRelative(icono, null, null, null);
            botones[i].setPadding(lado / 3, 0, lado / 4, 0);
        }
        btnHuir.setOnClickListener(v -> {
            if (ocupado || salvaje == null) return;
            tvMensaje.setText("Huiste de " + salvaje.nombre + ".");
            siguienteEncuentro(700);
        });
        btnNuevo.setOnClickListener(v -> nuevoSafari());
        dp = getResources().getDisplayMetrics().density;
        imgBola.setOnTouchListener(this::alTocarBola);

        UsuarioManager.obtenerCapturados(lista -> {
            coleccion = lista;
            pintar();
        });
        nuevoSafari();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }

    private void nuevoSafari() {
        System.arraycopy(INICIALES, 0, bolas, 0, BOLAS.length);
        capturadosHoy = 0;
        btnNuevo.setVisibility(View.GONE);
        siguienteEncuentro(0);
    }

    private int probabilidad(int tipo) {
        return Math.max(5, PROBABILIDAD[tipo] - dificultad);
    }

    private boolean quedanBolas() {
        for (int n : bolas) if (n > 0) return true;
        return false;
    }

    private void pintar() {
        tvColeccion.setText("🎒 Colección: " + coleccion.size() + " · Hoy: " + capturadosHoy);
        boolean hayPokemon = salvaje != null;
        for (int i = 0; i < BOLAS.length; i++) {
            botones[i].setText(BOLAS[i] + "\n×" + bolas[i] + (hayPokemon ? " · " + probabilidad(i) + "%" : ""));
            botones[i].setAlpha(bolas[i] <= 0 || !hayPokemon ? 0.35f : i == seleccionada ? 1f : 0.65f);
            botones[i].getBackground().mutate().setTint(COLOR[i]);
        }
        btnHuir.setVisibility(hayPokemon ? View.VISIBLE : View.INVISIBLE);
    }

    // ------------------------------------------------------------------
    // Encuentros
    // ------------------------------------------------------------------

    private void siguienteEncuentro(long espera) {
        ocupado = true;
        salvaje = null;
        handler.postDelayed(() -> {
            if (isFinishing() || isDestroyed()) return;
            if (!quedanBolas()) {
                terminarSafari();
                return;
            }
            imgPokemon.animate().cancel();
            imgPokemon.setVisibility(View.INVISIBLE);
            imgBola.setVisibility(View.INVISIBLE);
            tvNombre.setText("…");
            tvDatos.setText("");
            tvMensaje.setText("Algo se mueve entre la hierba…");
            pintar();

            int id = CargadorPokemon.idsAleatorios(1, false, random).get(0);
            CargadorPokemon.cargar(Collections.singletonList(id), lista -> {
                if (isFinishing() || isDestroyed()) return;
                if (lista == null) {
                    tvMensaje.setText("No se pudo cargar el Pokémon. Revisa tu internet.");
                    btnNuevo.setVisibility(View.VISIBLE);
                    return;
                }
                aparecer(new BattlePokemon(lista.get(0)));
            });
        }, espera);
    }

    private void aparecer(BattlePokemon p) {
        salvaje = p;
        int total = p.hpMaxima + p.ataque + p.defensa + p.ataqueEspecial + p.defensaEspecial + p.velocidad;
        // De 300 (débil) a 600 (muy fuerte) en stats: de 0 a 25 puntos más difícil
        dificultad = Math.max(0, Math.min(25, (total - 300) / 12));

        Glide.with(this).load(p.imagen).into(imgPokemon);
        imgPokemon.setVisibility(View.VISIBLE);
        imgPokemon.setScaleX(0f);
        imgPokemon.setScaleY(0f);
        imgPokemon.setAlpha(1f);
        imgPokemon.animate().scaleX(1f).scaleY(1f).setDuration(350).start();

        tvNombre.setText(p.nombre + (yaCapturado(p.id) ? " ✔" : ""));
        tvDatos.setText(String.format(Locale.ROOT, "N.º %d · %s\nHP %d · Ataque %d · Defensa %d · Velocidad %d",
                p.id, p.tiposTexto(), p.hpMaxima, p.ataque, p.defensa, p.velocidad));
        tvMensaje.setText("¡Un " + p.nombre + " salvaje apareció!\nDesliza la Pokéball hacia él.");
        ocupado = false;
        prepararBola();
        pintar();
    }

    private boolean yaCapturado(int id) {
        for (PokemonMini m : coleccion) if (m.getId() == id) return true;
        return false;
    }

    // ------------------------------------------------------------------
    // Lanzar la Pokéball
    // ------------------------------------------------------------------

    /** Botón de una Pokéball: la deja lista; si ya era la elegida, la lanza recta (sin bonos). */
    private void elegirBola(int tipo) {
        if (ocupado || salvaje == null || bolas[tipo] <= 0) return;
        if (tipo == seleccionada) {
            lanzar(0f, 1f, 0f, false);
            return;
        }
        seleccionada = tipo;
        prepararBola();
        pintar();
    }

    /** Coloca abajo la Pokéball elegida (o la primera que quede), lista para deslizarla. */
    private void prepararBola() {
        if (bolas[seleccionada] <= 0) {
            for (int i = 0; i < BOLAS.length; i++) {
                if (bolas[i] > 0) {
                    seleccionada = i;
                    break;
                }
            }
        }
        imgBola.animate().cancel();
        imgBola.setImageDrawable(new BolaDrawable(seleccionada));
        imgBola.setTranslationX(0f);
        imgBola.setTranslationY(0f);
        imgBola.setRotation(0f);
        imgBola.setScaleX(1f);
        imgBola.setScaleY(1f);
        imgBola.setAlpha(1f);
        imgBola.setVisibility(bolas[seleccionada] > 0 ? View.VISIBLE : View.INVISIBLE);
    }

    /** El dedo arrastra la bola; al soltarla hacia arriba con impulso, sale lanzada. */
    private boolean alTocarBola(View bola, MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (ocupado || salvaje == null || bolas[seleccionada] <= 0) return false;
                bola.getParent().requestDisallowInterceptTouchEvent(true);   // que la pantalla no se desplace
                trazo.clear();
                trazo.add(new float[]{e.getRawX(), e.getRawY(), e.getEventTime()});
                return true;

            case MotionEvent.ACTION_MOVE:
                if (trazo.isEmpty()) return false;
                trazo.add(new float[]{e.getRawX(), e.getRawY(), e.getEventTime()});
                bola.setTranslationX(e.getRawX() - trazo.get(0)[0]);
                bola.setTranslationY(e.getRawY() - trazo.get(0)[1]);
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (trazo.isEmpty()) return false;
                trazo.add(new float[]{e.getRawX(), e.getRawY(), e.getEventTime()});
                soltarBola(e.getActionMasked() == MotionEvent.ACTION_CANCEL);
                trazo.clear();
                return true;
        }
        return false;
    }

    private void soltarBola(boolean cancelado) {
        float[] inicio = trazo.get(0), fin = trazo.get(trazo.size() - 1);

        // Velocidad del dedo en los últimos 120 ms (dp por segundo)
        int i = trazo.size() - 1;
        while (i > 0 && fin[2] - trazo.get(i - 1)[2] <= 120) i--;
        float segundos = Math.max(1f, fin[2] - trazo.get(i)[2]) / 1000f;
        float vx = (fin[0] - trazo.get(i)[0]) / segundos / dp;
        float vy = (fin[1] - trazo.get(i)[1]) / segundos / dp;

        boolean lanzada = !cancelado && fin[1] - inicio[1] < -ARRASTRE_MINIMO * dp && vy < -VELOCIDAD_MINIMA;
        if (!lanzada) {
            // Solo la movió: regresa a su lugar y no se gasta
            imgBola.animate().translationX(0f).translationY(0f).setDuration(180).start();
            return;
        }

        // Curva: qué tanto se separó el trazo de la línea recta entre el inicio y el final (+ derecha, − izquierda)
        float cx = fin[0] - inicio[0], cy = fin[1] - inicio[1];
        float largo = (float) Math.hypot(cx, cy);
        float desvio = 0f;
        for (float[] p : trazo) {
            float d = (cx * (p[1] - inicio[1]) - cy * (p[0] - inicio[0])) / largo;
            if (Math.abs(d) > Math.abs(desvio)) desvio = d;
        }
        desvio /= dp;

        float inclinacion = Math.max(-1.5f, Math.min(1.5f, vx / -vy));   // hacia dónde apuntaba el dedo al soltar
        float fuerza = Math.min(1f, -vy / VELOCIDAD_ALCANCE);
        lanzar(inclinacion, fuerza, Math.abs(desvio) >= DESVIO_CURVA ? desvio : 0f, true);
    }

    /**
     * 1) La bola vuela desde donde está hasta la altura del Pokémon.
     * inclinacion = cuánto se va de lado por cada tramo que sube; fuerza 0–1 (menos de 1 se queda corta);
     * curva = arco del trazo en dp (0 = recta); conBonos = false en el lanzamiento con botón.
     */
    private void lanzar(float inclinacion, float fuerza, float curva, boolean conBonos) {
        ocupado = true;
        int tipo = seleccionada;
        bolas[tipo]--;
        pintar();

        // Posiciones como desplazamiento de la bola respecto a su lugar de reposo
        float x0 = imgBola.getTranslationX(), y0 = imgBola.getTranslationY();
        float centroPokemonX = imgPokemon.getLeft() + imgPokemon.getWidth() / 2f
                - (imgBola.getLeft() + imgBola.getWidth() / 2f);
        float centroPokemonY = imgPokemon.getTop() + imgPokemon.getHeight() / 2f
                - (imgBola.getTop() + imgBola.getHeight() / 2f);
        float subida = Math.min(0f, centroPokemonY - y0);            // negativo: hacia arriba
        float x1 = conBonos ? x0 + inclinacion * -subida * fuerza : centroPokemonX;
        float y1 = y0 + subida * fuerza;

        float distancia = Math.abs(x1 - centroPokemonX);
        boolean llega = fuerza >= 1f;
        boolean acierta = llega && distancia <= imgPokemon.getWidth() * 0.42f;
        boolean alCentro = conBonos && acierta && distancia <= imgPokemon.getWidth() * 0.14f;
        boolean esCurva = conBonos && curva != 0f;
        float arco = Math.max(-90f, Math.min(90f, curva * 1.6f)) * dp;

        tvMensaje.setText(esCurva ? "¡Bola curva con " + BOLAS[tipo] + "!" : "¡Lanzaste una " + BOLAS[tipo] + "!");

        ValueAnimator vuelo = ValueAnimator.ofFloat(0f, 1f);
        vuelo.setDuration(esCurva ? 620 : 460);
        vuelo.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            imgBola.setTranslationX(x0 + (x1 - x0) * t + arco * (float) Math.sin(Math.PI * t));
            imgBola.setTranslationY(y0 + (y1 - y0) * t);
            imgBola.setRotation((esCurva ? 1440f : 720f) * t);
            imgBola.setScaleX(1f - 0.25f * t);   // se ve más chica al alejarse
            imgBola.setScaleY(1f - 0.25f * t);
        });
        vuelo.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animacion) {
                if (isFinishing() || isDestroyed() || salvaje == null) return;
                imgBola.setRotation(720f);
                if (!acierta) {
                    fallar(llega ? "¡Se fue de lado!" : "¡Se quedó corta!");
                    return;
                }
                int probabilidad = Math.min(95, probabilidad(tipo)
                        + (esCurva ? BONO_CURVA : 0) + (alCentro ? BONO_CENTRO : 0));
                boolean capturado = random.nextInt(100) < probabilidad;
                if (esCurva || alCentro) {
                    tvMensaje.setText((alCentro ? "🎯 ¡Al centro! " : "") + (esCurva ? "🌀 ¡Bola curva! " : "")
                            + "\nProbabilidad: " + probabilidad + " %");
                }
                // 2) El Pokémon entra en la bola
                imgPokemon.animate().scaleX(0f).scaleY(0f).alpha(0f).setDuration(250)
                        .withEndAction(() -> sacudir(3, capturado)).start();
            }
        });
        vuelo.start();
    }

    /** La bola no le dio al Pokémon: se pierde y, si quedan, se prepara otra. */
    private void fallar(String motivo) {
        Efectos.fallo(this);
        imgBola.animate().alpha(0f).setDuration(220).withEndAction(() -> {
            if (isFinishing() || isDestroyed() || salvaje == null) return;
            if (!quedanBolas()) {
                tvMensaje.setText(motivo + " Perdiste la Pokéball.");
                siguienteEncuentro(1200);   // sin bolas: ahí se termina el safari
                return;
            }
            tvMensaje.setText(motivo + " Perdiste la Pokéball.\nDesliza otra hacia " + salvaje.nombre + ".");
            ocupado = false;
            prepararBola();
            pintar();
        }).start();
    }

    /** 3) La bola se sacude; al terminar se sabe si quedó capturado. */
    private void sacudir(int veces, boolean capturado) {
        if (isFinishing() || isDestroyed() || salvaje == null) return;
        if (veces == 0) {
            resolver(capturado);
            return;
        }
        imgBola.animate().rotation(700f).setDuration(140).withEndAction(() ->
                imgBola.animate().rotation(740f).setDuration(140).withEndAction(() ->
                        imgBola.animate().rotation(720f).setDuration(120).setStartDelay(0)
                                .withEndAction(() -> handler.postDelayed(
                                        () -> sacudir(veces - 1, capturado), 220)).start()
                ).start()
        ).start();
    }

    private void resolver(boolean capturado) {
        if (capturado) {
            capturadosHoy++;
            boolean nuevo = !yaCapturado(salvaje.id);
            if (nuevo) coleccion.add(new PokemonMini(salvaje.id, salvaje.nombre));
            UsuarioManager.registrarCaptura(salvaje.id, salvaje.nombre);
            Efectos.victoria(this);   // sonido y vibración (según Configuración)
            tvMensaje.setText("⭐ ¡" + salvaje.nombre + " fue capturado!"
                    + (nuevo ? "\nSe agregó a tu colección." : "\nYa lo tenías en tu colección."));
            imgBola.animate().scaleX(1.25f).scaleY(1.25f).setDuration(200).withEndAction(() ->
                    imgBola.animate().scaleX(1f).scaleY(1f).setDuration(200).start()).start();
            siguienteEncuentro(1700);
            return;
        }

        // Se salió de la bola
        imgBola.setVisibility(View.INVISIBLE);
        imgPokemon.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(250).start();

        if (random.nextInt(100) < PROBABILIDAD_ESCAPE) {
            tvMensaje.setText("¡" + salvaje.nombre + " se liberó y escapó!");
            imgPokemon.animate().translationX(imgPokemon.getWidth() * 2f).setStartDelay(500).setDuration(350)
                    .withEndAction(() -> imgPokemon.setTranslationX(0f)).start();
            siguienteEncuentro(1500);
        } else if (!quedanBolas()) {
            tvMensaje.setText("¡" + salvaje.nombre + " se liberó!");
            siguienteEncuentro(1200);   // sin bolas: ahí se termina el safari
        } else {
            tvMensaje.setText("¡" + salvaje.nombre + " se liberó! Intenta otra vez.");
            ocupado = false;
            prepararBola();
            pintar();
        }
    }

    private void terminarSafari() {
        salvaje = null;
        imgPokemon.setVisibility(View.INVISIBLE);
        imgBola.setVisibility(View.INVISIBLE);
        tvNombre.setText("Safari terminado");
        tvDatos.setText("");
        tvMensaje.setText("Te quedaste sin Pokéball.\nCapturaste " + capturadosHoy
                + (capturadosHoy == 1 ? " Pokémon" : " Pokémon") + " en este safari.");
        btnNuevo.setVisibility(View.VISIBLE);
        pintar();
    }

    // ------------------------------------------------------------------
    // Colección
    // ------------------------------------------------------------------

    private void mostrarColeccion() {
        if (coleccion.isEmpty()) {
            Toast.makeText(this, "Aún no has capturado ningún Pokémon", Toast.LENGTH_SHORT).show();
            return;
        }
        float dp = getResources().getDisplayMetrics().density;
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        grid.setPadding(Math.round(12 * dp), Math.round(12 * dp), Math.round(12 * dp), Math.round(12 * dp));

        for (PokemonMini m : coleccion) {
            LinearLayout celda = new LinearLayout(this);
            celda.setOrientation(LinearLayout.VERTICAL);
            celda.setGravity(Gravity.CENTER_HORIZONTAL);

            ImageView sprite = new ImageView(this);
            Glide.with(this).load(m.getSpriteUrl()).into(sprite);
            celda.addView(sprite, new LinearLayout.LayoutParams(Math.round(72 * dp), Math.round(72 * dp)));

            TextView nombre = new TextView(this);
            nombre.setText(m.getNombre());
            nombre.setTextSize(12);
            nombre.setTypeface(null, Typeface.BOLD);
            nombre.setGravity(Gravity.CENTER);
            nombre.setMaxLines(1);
            celda.addView(nombre);

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
            lp.width = 0;
            lp.bottomMargin = Math.round(8 * dp);
            grid.addView(celda, lp);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(grid);
        new AlertDialog.Builder(this)
                .setTitle("🎒 Mi colección (" + coleccion.size() + ")")
                .setView(scroll)
                .setPositiveButton("Cerrar", null)
                .show();
    }
}
