package com.example.pokeapp;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.bumptech.glide.Glide;
import com.example.pokeapp.battle.BattleEngine;
import com.example.pokeapp.battle.BattlePokemon;
import com.example.pokeapp.battle.BattleTurn;
import com.example.pokeapp.battle.Escenario;
import com.example.pokeapp.battle.Movimiento;
import com.example.pokeapp.data.MovimientoManager;
import com.example.pokeapp.data.TypeColors;
import com.example.pokeapp.data.HistorialManager;
import com.example.pokeapp.data.Pokemon;
import com.example.pokeapp.red.ConexionBatalla;
import com.example.pokeapp.red.Protocolo;
import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Arena de batalla a pantalla completa, compartida por Battle Emulator, Battle Versus
 * y Torre Pokémon (todas usan el mismo BattleEngine).
 *
 * - Cada lado tiene un equipo (1 Pokémon en el Emulator, 3 en Versus, 1–4 en la Torre).
 * - Cada lado puede ser "manual" (un jugador toca Atacar y elige quién entra al caer uno)
 *   o automático (el sistema ataca solo y manda al siguiente vivo).
 * - Empieza el Pokémon con mayor Speed; también al entrar un reemplazo.
 * - Al terminar guarda el resumen en el Historial y regresa con el resultado.
 *
 * Batalla entre dispositivos (dos celulares, ver red/Protocolo): el lado A siempre es
 * el jugador de este celular. El ANFITRIÓN pelea igual que en Versus, pero las decisiones
 * del lado B llegan por la red y cada cosa que pasa se le avisa al otro celular.
 * El INVITADO ("espejo") no calcula nada: solo dibuja lo que el anfitrión le manda.
 */
public class BattleArenaActivity extends AppCompatActivity {

    private static final String EXTRA_EQUIPO_A = "extra_equipo_a";
    private static final String EXTRA_EQUIPO_B = "extra_equipo_b";
    private static final String EXTRA_NOMBRE_A = "extra_nombre_a";
    private static final String EXTRA_NOMBRE_B = "extra_nombre_b";
    private static final String EXTRA_MANUAL_A = "extra_manual_a";
    private static final String EXTRA_MANUAL_B = "extra_manual_b";
    private static final String EXTRA_MODO = "extra_modo";
    private static final String EXTRA_ESCENARIO = "extra_escenario";
    private static final String EXTRA_RED = "extra_red";
    /** Opcional: imagen (drawable) del entrenador rival, que se ve junto a su Pokémon. */
    public static final String EXTRA_ENTRENADOR_B = "extra_entrenador_b";
    /** true = esa imagen es un personaje de cuerpo completo (PNG sin fondo); false = foto en marco redondo. */
    public static final String EXTRA_ENTRENADOR_CUERPO = "extra_entrenador_cuerpo";
    /** Opcional: frase con la que se presenta el entrenador rival, en vez de "¡A luchar contra …!". */
    public static final String EXTRA_FRASE_ENTRENADOR = "extra_frase_entrenador";
    /** Tiempo extra de presentación cuando hay entrenador: sale él, se hace a un lado y entra su Pokémon. */
    private static final long PAUSA_ENTRENADOR = 1500;

    private static final int RED_NO = 0, RED_ANFITRION = 1, RED_INVITADO = 2;

    // Resultado que reciben las pantallas que abren la arena
    public static final String RESULTADO_GANO_A = "resultado_gano_a";
    public static final String RESULTADO_HP_GANADOR = "resultado_hp_ganador";
    public static final String RESULTADO_TURNOS = "resultado_turnos";
    /** Posición, dentro de su equipo, del Pokémon que dio el golpe final. */
    public static final String RESULTADO_INDICE_GANADOR = "resultado_indice_ganador";

    private static final long PAUSA_INICIO = 1500;
    private static final long PAUSA_TURNO = 1300;
    private static final long PAUSA_REEMPLAZO = 900;
    private static final long PAUSA_REGRESO = 2000;

    private static final Gson gson = new Gson();
    private static final Random random = new Random();

    private ImageView imgA, imgB;
    private TextView tvNombreA, tvNombreB, tvTiposA, tvTiposB, tvHpA, tvHpB, tvDuenoA, tvDuenoB;
    private TextView tvTurno, tvBanner, tvBitacora, tvTurnoDe;
    private ProgressBar barraHpA, barraHpB;
    private FrameLayout contenedorA, contenedorB;
    private ImageView imgEntrenadorB;
    private boolean hayEntrenador, entrenadorDeCuerpo;
    private LinearLayout puntosA, puntosB;
    private ScrollView scrollBitacora;
    private View barraAccion, btnCambiar;
    private GridLayout gridAtaques;

    private BattleEngine motor;
    private final List<BattlePokemon> equipoA = new ArrayList<>();
    private final List<BattlePokemon> equipoB = new ArrayList<>();
    private int activoA = 0, activoB = 0;
    private String nombreA, nombreB, modo;
    private boolean manualA, manualB;

    private boolean turnoDeA;
    private boolean esperandoJugador = false;
    private boolean batallaTerminada = false;
    private boolean ataquesListos = false, pausaInicioLista = false, batallaIniciada = false;
    private int indiceEscenario = -1;

    // Batalla entre dispositivos (red == null en los demás modos)
    private ConexionBatalla red;
    private boolean soyAnfitrion;
    private boolean espejo;                 // invitado: solo dibuja lo que manda el anfitrión
    private boolean rivalListo;             // (anfitrión) el invitado ya dibujó la batalla
    private boolean esperandoRival;         // (anfitrión) falta la decisión del invitado
    private boolean rivalEligeReemplazo;    // …y esa decisión es quién entra por el que cayó
    private Pokemon[] datosA, datosB;
    private final List<BattleTurn> turnosEspejo = new ArrayList<>();

    private final Handler handler = new Handler(Looper.getMainLooper());

    /** Battle Emulator: un Pokémon por lado y todo automático. */
    public static Intent crearIntent(Context context, Pokemon a, Pokemon b) {
        return crearIntentEquipos(context, Collections.singletonList(a), Collections.singletonList(b),
                null, null, false, false, HistorialManager.MODO_EMULATOR, -1);
    }

    /**
     * Batalla por equipos.
     *
     * @param nombreA   nombre del dueño del equipo A ("Jugador 1", "Tú"); null = sin dueño
     * @param manualA   true = un jugador toca Atacar y elige reemplazos del equipo A
     * @param escenario índice en Escenario.TODOS, o -1 para uno al azar
     */
    public static Intent crearIntentEquipos(Context context, List<Pokemon> a, List<Pokemon> b,
                                            String nombreA, String nombreB,
                                            boolean manualA, boolean manualB,
                                            String modo, int escenario) {
        return new Intent(context, BattleArenaActivity.class)
                .putExtra(EXTRA_EQUIPO_A, gson.toJson(a))
                .putExtra(EXTRA_EQUIPO_B, gson.toJson(b))
                .putExtra(EXTRA_NOMBRE_A, nombreA)
                .putExtra(EXTRA_NOMBRE_B, nombreB)
                .putExtra(EXTRA_MANUAL_A, manualA)
                .putExtra(EXTRA_MANUAL_B, manualB)
                .putExtra(EXTRA_MODO, modo)
                .putExtra(EXTRA_ESCENARIO, escenario);
    }

    /**
     * Batalla entre dispositivos: la conexión (ConexionBatalla) ya debe estar abierta.
     * "mio" siempre se ve del lado A; quien creó la sala es el anfitrión.
     */
    public static Intent crearIntentRed(Context context, List<Pokemon> mio, List<Pokemon> rival,
                                        String miNombre, String nombreRival, boolean soyAnfitrion) {
        return crearIntentEquipos(context, mio, rival, miNombre, nombreRival, true, false,
                HistorialManager.MODO_DISPOSITIVOS, -1)
                .putExtra(EXTRA_RED, soyAnfitrion ? RED_ANFITRION : RED_INVITADO);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_battle_arena);
        pantallaCompleta();
        enlazarVistas();

        Pokemon[] a = leerEquipo(EXTRA_EQUIPO_A);
        Pokemon[] b = leerEquipo(EXTRA_EQUIPO_B);
        if (a == null || b == null || a.length == 0 || b.length == 0) {
            Toast.makeText(this, "No se pudieron cargar los Pokémon", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        nombreA = getIntent().getStringExtra(EXTRA_NOMBRE_A);
        nombreB = getIntent().getStringExtra(EXTRA_NOMBRE_B);
        manualA = getIntent().getBooleanExtra(EXTRA_MANUAL_A, false);
        manualB = getIntent().getBooleanExtra(EXTRA_MANUAL_B, false);
        modo = getIntent().getStringExtra(EXTRA_MODO);
        if (modo == null) modo = HistorialManager.MODO_EMULATOR;

        findViewById(R.id.btnSalirArena).setOnClickListener(v -> finish());
        btnCambiar.setOnClickListener(v -> cambiarPorDecision());

        int tipoRed = getIntent().getIntExtra(EXTRA_RED, RED_NO);
        if (tipoRed != RED_NO) {
            red = ConexionBatalla.obtener(this);
            soyAnfitrion = tipoRed == RED_ANFITRION;
            espejo = !soyAnfitrion;
            if (!red.estaConectado()) {
                red = null;
                Toast.makeText(this, "Se perdió la conexión con el otro celular", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        }
        datosA = a;
        datosB = b;

        prepararBatalla(a, b, getIntent().getIntExtra(EXTRA_ESCENARIO, -1));

        if (red != null) red.setEscucha(escuchaRed);

        // Al invitado, los ataques y el HP le llegan del anfitrión en el mensaje "inicio"
        if (!espejo) {
            // Mientras se ve "¡A vs B!" se cargan los 4 ataques reales de cada Pokémon
            List<BattlePokemon> todos = new ArrayList<>(equipoA);
            todos.addAll(equipoB);
            MovimientoManager.asignar(todos, () -> {
                ataquesListos = true;
                if (red != null) {
                    red.enviar(Protocolo.inicio(indiceEscenario, describirEquipo(equipoA), describirEquipo(equipoB)));
                }
                intentarComenzar();
            });
        }
        handler.postDelayed(() -> {
            pausaInicioLista = true;
            if (!ataquesListos) mostrarBanner("Preparando ataques…");
            intentarComenzar();
        }, PAUSA_INICIO + (hayEntrenador ? PAUSA_ENTRENADOR : 0));
    }

    /** Salir a media batalla entre dispositivos corta la conexión: el otro celular se entera y también sale. */
    @Override
    public void finish() {
        if (red != null && !batallaTerminada) red.desconectar();
        super.finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (red != null) {
            red.quitarEscucha(escuchaRed);
            if (!batallaTerminada) red.desconectar();
        }
    }

    private Pokemon[] leerEquipo(String extra) {
        String json = getIntent().getStringExtra(extra);
        if (json == null) return null;
        try {
            return gson.fromJson(json, Pokemon[].class);
        } catch (Exception e) {
            return null;
        }
    }

    /** Oculta barra de estado y de navegación (se ven deslizando desde el borde). */
    private void pantallaCompleta() {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    private void enlazarVistas() {
        imgA = findViewById(R.id.imgA);
        imgB = findViewById(R.id.imgB);
        imgEntrenadorB = findViewById(R.id.imgEntrenadorB);
        int entrenador = getIntent().getIntExtra(EXTRA_ENTRENADOR_B, 0);
        if (entrenador != 0) {
            hayEntrenador = true;
            entrenadorDeCuerpo = getIntent().getBooleanExtra(EXTRA_ENTRENADOR_CUERPO, false);
            imgEntrenadorB.setImageResource(entrenador);
            if (entrenadorDeCuerpo) {
                // Personaje completo, como los entrenadores de los juegos: sin marco y más grande
                float dp = getResources().getDisplayMetrics().density;
                imgEntrenadorB.setBackground(null);
                imgEntrenadorB.setPadding(0, 0, 0, 0);
                imgEntrenadorB.setScaleType(ImageView.ScaleType.FIT_CENTER);
                imgEntrenadorB.getLayoutParams().width = Math.round(112 * dp);
                imgEntrenadorB.getLayoutParams().height = Math.round(140 * dp);
                // Pixel art (imágenes chicas): sin suavizado para que no se vea borroso
                android.graphics.drawable.Drawable dibujo = imgEntrenadorB.getDrawable();
                if (dibujo instanceof android.graphics.drawable.BitmapDrawable
                        && ((android.graphics.drawable.BitmapDrawable) dibujo).getBitmap().getWidth() <= 256) {
                    dibujo.mutate();
                    ((android.graphics.drawable.BitmapDrawable) dibujo).setFilterBitmap(false);
                }
            } else {
                imgEntrenadorB.setClipToOutline(true);   // foto con recorte redondo
            }
            imgEntrenadorB.setVisibility(View.INVISIBLE);   // aparece en la presentación
        }
        tvNombreA = findViewById(R.id.tvNombreA);
        tvNombreB = findViewById(R.id.tvNombreB);
        tvTiposA = findViewById(R.id.tvTiposA);
        tvTiposB = findViewById(R.id.tvTiposB);
        tvHpA = findViewById(R.id.tvHpA);
        tvHpB = findViewById(R.id.tvHpB);
        tvDuenoA = findViewById(R.id.tvDuenoA);
        tvDuenoB = findViewById(R.id.tvDuenoB);
        barraHpA = findViewById(R.id.barraHpA);
        barraHpB = findViewById(R.id.barraHpB);
        contenedorA = findViewById(R.id.contenedorA);
        contenedorB = findViewById(R.id.contenedorB);
        puntosA = findViewById(R.id.equipoA);
        puntosB = findViewById(R.id.equipoB);
        tvTurno = findViewById(R.id.tvTurno);
        tvBanner = findViewById(R.id.tvBanner);
        tvBitacora = findViewById(R.id.tvBitacora);
        scrollBitacora = findViewById(R.id.scrollBitacora);
        barraAccion = findViewById(R.id.barraAccion);
        btnCambiar = findViewById(R.id.btnCambiar);
        gridAtaques = findViewById(R.id.gridAtaques);
        tvTurnoDe = findViewById(R.id.tvTurnoDe);
    }

    // ------------------------------------------------------------------
    // Ayudas por lado (A = abajo a la izquierda, B = arriba a la derecha)
    // ------------------------------------------------------------------

    private List<BattlePokemon> equipo(boolean ladoA) { return ladoA ? equipoA : equipoB; }

    private BattlePokemon actual(boolean ladoA) { return ladoA ? equipoA.get(activoA) : equipoB.get(activoB); }

    private boolean esManual(boolean ladoA) { return ladoA ? manualA : manualB; }

    private FrameLayout contenedor(boolean ladoA) { return ladoA ? contenedorA : contenedorB; }

    /** "Jugador 1" si el lado tiene dueño; si no, el nombre del Pokémon. */
    private String dueno(boolean ladoA) {
        String nombre = ladoA ? nombreA : nombreB;
        return nombre != null ? nombre : actual(ladoA).nombre;
    }

    private boolean quedanVivos(boolean ladoA) {
        for (BattlePokemon p : equipo(ladoA)) if (!p.estaDerrotado()) return true;
        return false;
    }

    private boolean esPorEquipos() {
        return equipoA.size() > 1 || equipoB.size() > 1 || nombreA != null || nombreB != null;
    }

    // ------------------------------------------------------------------
    // Preparación
    // ------------------------------------------------------------------

    private void prepararBatalla(Pokemon[] a, Pokemon[] b, int indiceEscenario) {
        motor = new BattleEngine(random);
        for (Pokemon p : a) equipoA.add(new BattlePokemon(p));
        for (Pokemon p : b) equipoB.add(new BattlePokemon(p));

        if (indiceEscenario < 0 || indiceEscenario >= Escenario.TODOS.size()) {
            indiceEscenario = random.nextInt(Escenario.TODOS.size());
        }
        this.indiceEscenario = indiceEscenario;
        Escenario escenario = Escenario.TODOS.get(indiceEscenario);
        tvTurno.setText("📍 " + escenario.nombre);
        acomodarEscenario(escenario);

        mostrarDueno(tvDuenoA, nombreA);
        mostrarDueno(tvDuenoB, nombreB);
        pintarPokemon(true);
        pintarPokemon(false);

        if (hayEntrenador && nombreB != null) {
            // "Nivel 3 · Edgar" → "¡A luchar contra Edgar!"
            String rival = nombreB.contains(" · ") ? nombreB.substring(nombreB.indexOf(" · ") + 3) : nombreB;
            String frase = getIntent().getStringExtra(EXTRA_FRASE_ENTRENADOR);
            mostrarBanner(frase != null ? frase : "¡A luchar contra " + rival + "!");
        } else if (esPorEquipos()) {
            mostrarBanner("¡" + dueno(true) + " vs " + dueno(false) + "!");
        } else {
            mostrarBanner("¡" + actual(true).nombre + " vs " + actual(false).nombre + "!");
        }
    }

    private void mostrarDueno(TextView tv, String nombre) {
        tv.setVisibility(nombre != null ? View.VISIBLE : View.GONE);
        if (nombre != null) tv.setText(nombre.toUpperCase(Locale.ROOT));
    }

    /** Imagen, nombre, tipos, HP y puntitos del equipo del Pokémon activo de un lado. */
    private void pintarPokemon(boolean ladoA) {
        BattlePokemon p = actual(ladoA);
        Glide.with(this).load(p.imagen).into(ladoA ? imgA : imgB);
        (ladoA ? tvNombreA : tvNombreB).setText(p.nombre);
        (ladoA ? tvTiposA : tvTiposB).setText(p.tiposTexto());
        actualizarBarraHp(ladoA ? barraHpA : barraHpB, ladoA ? tvHpA : tvHpB, p, false);
        actualizarPuntos(ladoA);
    }

    /** Un puntito por Pokémon del equipo: rojo vivo, gris debilitado; el activo con borde. */
    private void actualizarPuntos(boolean ladoA) {
        LinearLayout puntos = ladoA ? puntosA : puntosB;
        List<BattlePokemon> equipo = equipo(ladoA);
        puntos.removeAllViews();
        if (equipo.size() <= 1) {
            puntos.setVisibility(View.GONE);
            return;
        }

        float dp = getResources().getDisplayMetrics().density;
        int activo = ladoA ? activoA : activoB;
        for (int i = 0; i < equipo.size(); i++) {
            GradientDrawable circulo = new GradientDrawable();
            circulo.setShape(GradientDrawable.OVAL);
            circulo.setColor(equipo.get(i).estaDerrotado() ? 0xFFB8B8B8 : 0xFFE3350D);
            if (i == activo && !equipo.get(i).estaDerrotado()) {
                circulo.setStroke(Math.round(2 * dp), 0xFF3A1410);
            }

            View punto = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.round(11 * dp), Math.round(11 * dp));
            lp.setMarginEnd(Math.round(5 * dp));
            punto.setLayoutParams(lp);
            punto.setBackground(circulo);
            puntos.addView(punto);
        }
        puntos.setVisibility(View.VISIBLE);
    }

    /**
     * Pone el fondo y, cuando la arena ya tiene tamaño, coloca plataformas, Pokémon
     * y la tarjeta de A según las posiciones del escenario. Las posiciones vienen como
     * fracción de la imagen, así que se convierten tomando en cuenta el recorte centerCrop.
     */
    private void acomodarEscenario(Escenario escenario) {
        ImageView imgFondo = findViewById(R.id.imgFondo);
        View arena = findViewById(R.id.arena);
        View plataformaA = findViewById(R.id.plataformaA);
        View plataformaB = findViewById(R.id.plataformaB);
        View hudA = findViewById(R.id.hudA);

        imgFondo.setImageResource(escenario.fondo);
        if (!escenario.plataformasDibujadas) {
            plataformaA.setBackground(escenario.crearPlataforma(this));
            plataformaB.setBackground(escenario.crearPlataforma(this));
        }

        arena.post(() -> {
            if (isFinishing() || isDestroyed()) return;

            // Tamaño de los Pokémon según el escenario (una sola vez)
            escalar(contenedorB, escenario.escalaPokemon);
            escalar(contenedorA, escenario.escalaPokemon);

            posicionar(escenario);

            // Si la arena cambia de alto (por ejemplo, aparece algo abajo), se vuelve a acomodar
            arena.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
                if ((b - t) != (ob - ot) || (r - l) != (or - ol)) v.post(() -> posicionar(escenario));
            });

            // Entrada: cada Pokémon llega desde su lado
            float ancho = arena.getWidth();
            contenedorA.setVisibility(View.VISIBLE);
            contenedorB.setVisibility(View.VISIBLE);
            contenedorA.setTranslationX(-ancho);
            contenedorB.setTranslationX(ancho);
            contenedorA.animate().translationX(0f).setDuration(600)
                    .setInterpolator(new OvershootInterpolator()).start();
            contenedorB.animate().translationX(0f).setDuration(600)
                    .setStartDelay(hayEntrenador ? PAUSA_ENTRENADOR : 150)
                    .setInterpolator(new OvershootInterpolator()).start();
            if (hayEntrenador) {
                // Como en los juegos: primero se planta el entrenador en la plataforma del rival,
                // luego se hace a un lado y entra su Pokémon
                FrameLayout.LayoutParams lpPokemon = (FrameLayout.LayoutParams) contenedorB.getLayoutParams();
                FrameLayout.LayoutParams lpEntrenador = (FrameLayout.LayoutParams) imgEntrenadorB.getLayoutParams();
                float alCentro = (lpPokemon.leftMargin + lpPokemon.width / 2f)
                        - (lpEntrenador.leftMargin + lpEntrenador.width / 2f);
                imgEntrenadorB.setVisibility(View.VISIBLE);
                imgEntrenadorB.setTranslationX(ancho);
                imgEntrenadorB.animate().translationX(alCentro).setDuration(500).withEndAction(() ->
                        imgEntrenadorB.animate().translationX(0f).setStartDelay(PAUSA_ENTRENADOR - 600)
                                .setDuration(450).start()).start();
            }
        });
    }

    /** Coloca plataformas, Pokémon y la tarjeta de A según el tamaño actual de la arena. */
    private void posicionar(Escenario escenario) {
        if (isFinishing() || isDestroyed()) return;

        ImageView imgFondo = findViewById(R.id.imgFondo);
        View arena = findViewById(R.id.arena);
        View plataformaA = findViewById(R.id.plataformaA);
        View plataformaB = findViewById(R.id.plataformaB);
        View hudA = findViewById(R.id.hudA);

        float ancho = arena.getWidth(), alto = arena.getHeight();
        if (ancho == 0 || alto == 0 || imgFondo.getDrawable() == null) return;
        float imgAncho = imgFondo.getDrawable().getIntrinsicWidth();
        float imgAlto = imgFondo.getDrawable().getIntrinsicHeight();

        // Misma cuenta que hace centerCrop: escala para cubrir y centra lo que sobra
        float escala = Math.max(ancho / imgAncho, alto / imgAlto);
        float despX = (ancho - imgAncho * escala) / 2f;
        float despY = (alto - imgAlto * escala) / 2f;

        float bx = despX + escenario.bX * imgAncho * escala;
        float by = despY + escenario.bY * imgAlto * escala;
        float ax = despX + escenario.aX * imgAncho * escala;
        float ay = despY + escenario.aY * imgAlto * escala;

        float dp = getResources().getDisplayMetrics().density;

        if (!escenario.plataformasDibujadas) {
            colocar(plataformaB, bx, by + plataformaB.getLayoutParams().height / 2f);
            colocar(plataformaA, ax, ay + plataformaA.getLayoutParams().height / 2f);
            plataformaA.setVisibility(View.VISIBLE);
            plataformaB.setVisibility(View.VISIBLE);
        }

        // Los pies del Pokémon quedan un poco abajo del centro de su plataforma,
        // sin salirse por abajo de la arena
        colocar(contenedorB, bx, by + 6 * dp);

        // El entrenador rival queda a un lado y un poco atrás de su Pokémon, sin salirse de la pantalla
        if (imgEntrenadorB.getVisibility() != View.GONE) {
            float mitad = imgEntrenadorB.getLayoutParams().width / 2f;
            float x = bx + contenedorB.getLayoutParams().width * 0.42f + mitad;
            // El personaje completo pisa el mismo suelo que su Pokémon; la foto va un poco más arriba
            colocar(imgEntrenadorB, Math.min(x, ancho - mitad - 2 * dp),
                    entrenadorDeCuerpo ? by + 6 * dp : by - 30 * dp);
        }
        colocar(contenedorA, ax, Math.min(ay + 6 * dp, alto - 4 * dp));

        // Tarjeta de A: si A está hasta abajo, va a media altura (bajo B);
        // si A está más arriba, va abajo a la derecha para no tapar a B
        FrameLayout.LayoutParams lpHud = (FrameLayout.LayoutParams) hudA.getLayoutParams();
        lpHud.topMargin = escenario.aY > 0.8f
                ? Math.round(alto * 0.53f)
                : Math.round(alto - hudA.getHeight() - 16 * dp);
        hudA.setLayoutParams(lpHud);
        hudA.setVisibility(View.VISIBLE);
    }

    private void escalar(View vista, float factor) {
        if (factor == 1f) return;
        ViewGroup.LayoutParams lp = vista.getLayoutParams();
        lp.width = Math.round(lp.width * factor);
        lp.height = Math.round(lp.height * factor);
        vista.setLayoutParams(lp);
    }

    /** Coloca una vista con su centro horizontal en x y su borde inferior en yInferior. */
    private void colocar(View vista, float x, float yInferior) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) vista.getLayoutParams();
        lp.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
        lp.leftMargin = Math.round(x - lp.width / 2f);
        lp.topMargin = Math.round(yInferior - lp.height);
        vista.setLayoutParams(lp);
    }

    // ------------------------------------------------------------------
    // Turnos
    // ------------------------------------------------------------------

    /** Empieza cuando ya pasó la presentación y ya se cargaron los ataques. */
    private void intentarComenzar() {
        if (!ataquesListos || !pausaInicioLista || batallaIniciada) return;
        if (red != null && soyAnfitrion && !rivalListo) {
            mostrarBanner("Esperando a " + dueno(false) + "…");
            return;
        }
        batallaIniciada = true;
        comenzarBatalla();
    }

    private void comenzarBatalla() {
        if (batallaTerminada || isFinishing() || isDestroyed()) return;

        ocultarBanner();
        Efectos.cry(this, actual(true).id);   // grito del Pokémon de abajo al empezar
        if (espejo) {
            // El anfitrión decide quién empieza y lo avisa con el primer "turno"
            agregarBitacora("¡" + dueno(true) + " vs " + dueno(false) + "!");
            tvTurno.setText("⏳ Esperando a " + dueno(false) + "…");
            red.enviar(Protocolo.listo());
            return;
        }
        agregarBitacora("¡" + dueno(true) + " vs " + dueno(false) + "!\n" + decidirQuienEmpieza());
        handler.postDelayed(this::siguienteTurno, 700);
    }

    /** Regla del proyecto: empieza el Pokémon activo con mayor Speed (empate: al azar). */
    private String decidirQuienEmpieza() {
        BattlePokemon a = actual(true), b = actual(false);
        turnoDeA = motor.quienEmpieza(a, b) == a;
        BattlePokemon primero = actual(turnoDeA);
        return "Empieza " + primero.nombre + (a.velocidad != b.velocidad
                ? " por tener mayor Speed (" + primero.velocidad + ")."
                : " por sorteo (misma Speed).");
    }

    private void siguienteTurno() {
        if (batallaTerminada || isFinishing() || isDestroyed()) return;

        if (red != null) {
            red.enviar(Protocolo.turno(ladoRed(turnoDeA), Protocolo.PIDE_ACCION));
            if (!turnoDeA) {
                esperarRival(false);   // el invitado elige en su celular
                return;
            }
        }

        if (esManual(turnoDeA)) {
            mostrarPanelAtaques(turnoDeA);   // espera a que el jugador elija ataque o cambie
        } else {
            ejecutarAtaque(turnoDeA, null);  // el sistema elige su mejor ataque
        }
    }

    /** Caja de ataques estilo juego: "¿Qué hará Pikachu?" + 4 botones del color de su tipo. */
    private void mostrarPanelAtaques(boolean ladoA) {
        esperandoJugador = true;
        BattlePokemon p = actual(ladoA);
        String quien = ladoA ? nombreA : nombreB;
        tvTurnoDe.setText((quien != null ? quien + "\n" : "") + "¿Qué hará " + p.nombre + "?");

        float dp = getResources().getDisplayMetrics().density;
        gridAtaques.removeAllViews();
        for (Movimiento m : p.getMovimientos()) {
            LinearLayout boton = new LinearLayout(this);
            boton.setOrientation(LinearLayout.VERTICAL);
            boton.setPadding(Math.round(12 * dp), Math.round(8 * dp), Math.round(12 * dp), Math.round(8 * dp));

            int color = m.tipo != null ? TypeColors.obtenerColor(m.tipo) : 0xFF8A93A6;
            GradientDrawable fondo = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{color, oscurecer(color)});
            fondo.setCornerRadius(14 * dp);
            fondo.setStroke(Math.round(2 * dp), 0xCCFFFFFF);
            boton.setBackground(fondo);

            TextView nombre = new TextView(this);
            nombre.setText(m.nombre);
            nombre.setTextColor(0xFFFFFFFF);
            nombre.setTextSize(15);
            nombre.setTypeface(null, android.graphics.Typeface.BOLD);
            nombre.setMaxLines(1);
            nombre.setShadowLayer(3 * dp, 0, dp, 0x99000000);
            boton.addView(nombre);

            // Etiqueta del tipo (más oscura que el botón) + potencia y precisión
            LinearLayout filaTipo = new LinearLayout(this);
            filaTipo.setOrientation(LinearLayout.HORIZONTAL);
            filaTipo.setGravity(android.view.Gravity.CENTER_VERTICAL);

            TextView chipTipo = new TextView(this);
            chipTipo.setText(m.tipoTexto().toUpperCase(Locale.ROOT));
            chipTipo.setTextColor(0xFFFFFFFF);
            chipTipo.setTextSize(9);
            chipTipo.setTypeface(null, android.graphics.Typeface.BOLD);
            chipTipo.setPadding(Math.round(6 * dp), Math.round(1 * dp), Math.round(6 * dp), Math.round(1 * dp));
            GradientDrawable pastilla = new GradientDrawable();
            pastilla.setCornerRadius(8 * dp);
            pastilla.setColor(oscurecer(oscurecer(color)));
            chipTipo.setBackground(pastilla);
            filaTipo.addView(chipTipo);

            TextView detalle = new TextView(this);
            detalle.setText("  Pot. " + m.potencia + " · " + (m.precision > 0 ? m.precision + "%" : "—"));
            detalle.setTextColor(0xE6FFFFFF);
            detalle.setTextSize(11);
            detalle.setShadowLayer(2 * dp, 0, dp, 0x99000000);
            filaTipo.addView(detalle);
            boton.addView(filaTipo);

            // Daño base contra el rival actual (sin contar súper eficaz ni críticos)
            int[] rango = BattleEngine.rangoDanioBase(m, p, actual(!ladoA));
            TextView danio = new TextView(this);
            danio.setText("💥 Daño " + (rango[0] == rango[1] ? String.valueOf(rango[0]) : rango[0] + "–" + rango[1]));
            danio.setTextColor(0xFFFFFFFF);
            danio.setTextSize(12);
            danio.setTypeface(null, android.graphics.Typeface.BOLD);
            danio.setShadowLayer(2 * dp, 0, dp, 0x99000000);
            danio.setPadding(0, Math.round(2 * dp), 0, 0);
            boton.addView(danio);

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
            lp.width = 0;
            lp.setMargins(Math.round(4 * dp), Math.round(4 * dp), Math.round(4 * dp), Math.round(4 * dp));
            boton.setOnClickListener(v -> usarMovimiento(m));
            gridAtaques.addView(boton, lp);
        }

        btnCambiar.setVisibility(otrosVivos(ladoA).isEmpty() ? View.GONE : View.VISIBLE);
        barraAccion.setVisibility(View.VISIBLE);
    }

    private int oscurecer(int color) {
        int r = (int) (((color >> 16) & 0xFF) * 0.7f);
        int g = (int) (((color >> 8) & 0xFF) * 0.7f);
        int b = (int) ((color & 0xFF) * 0.7f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private void usarMovimiento(Movimiento m) {
        if (!esperandoJugador || batallaTerminada) return;
        esperandoJugador = false;
        barraAccion.setVisibility(View.GONE);
        if (espejo) {
            // El invitado solo avisa qué eligió; el resultado regresa como "ataque"
            red.enviar(Protocolo.atacar(actual(true).getMovimientos().indexOf(m)));
            tvTurno.setText("⏳ " + m.nombre + "…");
            return;
        }
        ejecutarAtaque(turnoDeA, m);
    }

    /** Ataca con el movimiento indicado (null = el sistema elige el mejor). */
    private void ejecutarAtaque(boolean ladoA, Movimiento movimiento) {
        BattlePokemon atacante = actual(ladoA);
        BattlePokemon defensor = actual(!ladoA);

        BattleTurn turno = motor.atacar(atacante, defensor, movimiento);
        if (red != null) {
            Movimiento m = turno.movimiento;
            red.enviar(Protocolo.ataque(ladoRed(ladoA), turno.numero, m.nombre, m.tipo, m.potencia,
                    m.precision, turno.danio, turno.efectividad, turno.critico, turno.fallo,
                    turno.hpRestante));
        }
        mostrarAtaque(ladoA, turno);

        if (turno.derrotado) {
            if (!quedanVivos(!ladoA)) {
                terminarBatalla(ladoA);
            } else {
                handler.postDelayed(() -> pedirReemplazo(!ladoA), PAUSA_REEMPLAZO);
            }
            return;
        }

        turnoDeA = !ladoA;
        handler.postDelayed(this::siguienteTurno, PAUSA_TURNO);
    }

    /** Dibuja un ataque ya calculado: animaciones, barra de vida y bitácora. */
    private void mostrarAtaque(boolean ladoA, BattleTurn turno) {
        BattlePokemon defensor = turno.defensor;
        tvTurno.setText("Turno " + turno.numero + " · " + turno.movimiento.nombre);

        animarAtaque(contenedor(ladoA), ladoA ? 40f : -40f);
        if (turno.fallo) {
            textoFlotante(contenedor(!ladoA), "¡Falló!", 0xFF8A93A6, 22);
            Efectos.fallo(this);
        } else if (turno.sinEfecto()) {
            textoFlotante(contenedor(!ladoA), "No le afecta", 0xFF8A93A6, 20);
            Efectos.fallo(this);
        } else {
            animarGolpe(contenedor(!ladoA), turno);
            Efectos.golpe(this, turno.critico);   // sonido y vibración (según Configuración)
        }
        actualizarBarraHp(ladoA ? barraHpB : barraHpA, ladoA ? tvHpB : tvHpA, defensor, true);
        agregarBitacora(turno.descripcion());

        if (turno.derrotado) {
            actualizarPuntos(!ladoA);
            contenedor(!ladoA).animate().translationY(80f).alpha(0f).setDuration(700).setStartDelay(300).start();
        }
    }

    /** Índices de los Pokémon vivos del lado, sin contar al que está peleando. */
    private List<Integer> otrosVivos(boolean ladoA) {
        List<Integer> vivos = new ArrayList<>();
        List<BattlePokemon> equipo = equipo(ladoA);
        int activo = ladoA ? activoA : activoB;
        for (int i = 0; i < equipo.size(); i++) {
            if (i != activo && !equipo.get(i).estaDerrotado()) vivos.add(i);
        }
        return vivos;
    }

    /** Botón "Cambiar": manda a otro de su equipo y el rival aprovecha el turno. */
    private void cambiarPorDecision() {
        if (!esperandoJugador || batallaTerminada) return;
        boolean lado = turnoDeA;
        if (otrosVivos(lado).isEmpty()) return;

        mostrarVentanaEquipo(lado, "Cambiar Pokémon", true, indice -> {
            esperandoJugador = false;
            barraAccion.setVisibility(View.GONE);
            if (espejo) {
                red.enviar(Protocolo.cambiar(indice));   // regresa como "entra"
                return;
            }
            entrarPokemon(lado, indice, true);
        });
    }

    /** El lado que perdió a su Pokémon manda otro: el jugador lo elige, el sistema toma el siguiente. */
    private void pedirReemplazo(boolean ladoA) {
        if (batallaTerminada || isFinishing() || isDestroyed()) return;

        List<Integer> vivos = otrosVivos(ladoA);
        if (red != null && vivos.size() > 1) {
            red.enviar(Protocolo.turno(ladoRed(ladoA), Protocolo.PIDE_REEMPLAZO));
            if (!ladoA) {
                esperarRival(true);   // el invitado elige en su celular
                return;
            }
        }
        if (!esManual(ladoA) || vivos.size() == 1) {
            entrarPokemon(ladoA, vivos.get(0), false);
            return;
        }
        mostrarVentanaEquipo(ladoA, "Elige tu siguiente Pokémon", false,
                indice -> entrarPokemon(ladoA, indice, false));
    }

    private interface AlElegir {
        void elegido(int indice);
    }

    /** Ventana decorada con el equipo: sprite, tipos y barra de vida; los debilitados no se pueden elegir. */
    private void mostrarVentanaEquipo(boolean ladoA, String titulo, boolean cancelable, AlElegir alElegir) {
        View vista = LayoutInflater.from(this).inflate(R.layout.dialog_elegir_pokemon_batalla, null);
        String quien = ladoA ? nombreA : nombreB;
        TextView tvDueno = vista.findViewById(R.id.tvDuenoDialogo);
        tvDueno.setText(quien != null ? quien.toUpperCase(Locale.ROOT) : "");
        tvDueno.setVisibility(quien != null ? View.VISIBLE : View.GONE);
        ((TextView) vista.findViewById(R.id.tvTituloDialogo)).setText(titulo);

        AlertDialog dialogo = new AlertDialog.Builder(this).setView(vista).setCancelable(cancelable).create();

        LinearLayout lista = vista.findViewById(R.id.listaEquipoDialogo);
        List<BattlePokemon> equipo = equipo(ladoA);
        int activo = ladoA ? activoA : activoB;
        float dp = getResources().getDisplayMetrics().density;

        for (int i = 0; i < equipo.size(); i++) {
            BattlePokemon p = equipo.get(i);
            View tarjeta = LayoutInflater.from(this).inflate(R.layout.item_pokemon_batalla, lista, false);
            Glide.with(this).load(p.imagen).into((ImageView) tarjeta.findViewById(R.id.imgPokemonBatalla));
            ((TextView) tarjeta.findViewById(R.id.tvNombrePokemonBatalla)).setText(p.nombre);

            // Fondo del sprite con el color de su tipo
            GradientDrawable fondoSprite = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{TypeColors.obtenerColor(p.tipos.get(0)), 0xFFFFFFFF});
            fondoSprite.setCornerRadius(14 * dp);
            tarjeta.findViewById(R.id.fondoSpriteBatalla).setBackground(fondoSprite);

            LinearLayout chips = tarjeta.findViewById(R.id.chipsPokemonBatalla);
            for (String tipo : p.tipos) {
                TextView chip = new TextView(this);
                chip.setText(TypeColors.traducir(tipo));
                chip.setTextColor(0xFFFFFFFF);
                chip.setTextSize(10);
                chip.setTypeface(null, android.graphics.Typeface.BOLD);
                chip.setPadding(Math.round(8 * dp), Math.round(2 * dp), Math.round(8 * dp), Math.round(2 * dp));
                GradientDrawable pastilla = new GradientDrawable();
                pastilla.setCornerRadius(10 * dp);
                pastilla.setColor(TypeColors.obtenerColor(tipo));
                chip.setBackground(pastilla);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.setMarginEnd(Math.round(4 * dp));
                chips.addView(chip, lp);
            }

            actualizarBarraHp(tarjeta.findViewById(R.id.barraPokemonBatalla),
                    tarjeta.findViewById(R.id.tvHpPokemonBatalla), p, false);

            TextView estado = tarjeta.findViewById(R.id.tvEstadoPokemonBatalla);
            boolean enBatalla = i == activo && !p.estaDerrotado();
            if (p.estaDerrotado()) {
                estado.setText("Debilitado");
                estado.setVisibility(View.VISIBLE);
                tarjeta.setAlpha(0.45f);
                tarjeta.setClickable(false);
            } else if (enBatalla) {
                estado.setText("En batalla");
                estado.setVisibility(View.VISIBLE);
                tarjeta.setAlpha(0.6f);
                tarjeta.setClickable(false);
            } else {
                final int indice = i;
                tarjeta.setOnClickListener(v -> {
                    dialogo.dismiss();
                    alElegir.elegido(indice);
                });
            }
            lista.addView(tarjeta);
        }

        View cancelar = vista.findViewById(R.id.btnCancelarDialogo);
        cancelar.setVisibility(cancelable ? View.VISIBLE : View.GONE);
        cancelar.setOnClickListener(v -> dialogo.dismiss());

        dialogo.show();
        if (dialogo.getWindow() != null) {
            dialogo.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
    }

    /**
     * Entra un Pokémon. Si fue por decisión (botón Cambiar) el turno pasa al rival;
     * si fue porque el anterior cayó, empieza el más rápido (regla de Speed).
     */
    private void entrarPokemon(boolean ladoA, int indice, boolean porDecision) {
        if (red != null) red.enviar(Protocolo.entra(ladoRed(ladoA), indice, porDecision));
        mostrarEntrada(ladoA, indice);

        String quien = ladoA ? nombreA : nombreB;
        if (porDecision) {
            agregarBitacora((quien != null ? quien : "Entra") + " cambia a " + actual(ladoA).nombre + ".");
            turnoDeA = !ladoA;
        } else {
            agregarBitacora((quien != null ? quien : "Entra") + " envía a " + actual(ladoA).nombre
                    + ".\n" + decidirQuienEmpieza());
        }
        handler.postDelayed(this::siguienteTurno, PAUSA_TURNO);
    }

    /** Pone al Pokémon "indice" como activo de su lado y lo hace entrar desde su orilla. */
    private void mostrarEntrada(boolean ladoA, int indice) {
        if (ladoA) activoA = indice;
        else activoB = indice;

        pintarPokemon(ladoA);
        Efectos.cry(this, actual(ladoA).id);   // su grito al entrar

        View vista = contenedor(ladoA);
        vista.animate().cancel();
        vista.setAlpha(1f);
        vista.setTranslationY(0f);
        vista.setTranslationX(ladoA ? -vista.getWidth() * 2f : vista.getWidth() * 2f);
        vista.animate().translationX(0f).setDuration(500).setStartDelay(0)
                .setInterpolator(new OvershootInterpolator()).start();
    }

    private void terminarBatalla(boolean ganoA) {
        batallaTerminada = true;
        esperandoJugador = false;
        barraAccion.setVisibility(View.GONE);

        int totalTurnos = turnos().size();
        if (red != null) {
            if (soyAnfitrion) red.enviar(Protocolo.fin(ladoRed(ganoA), totalTurnos));
            // Lo que llegue desde ahora (por ejemplo, una revancha) es para la sala
            red.quitarEscucha(escuchaRed);
        }

        String ganador = esPorEquipos() ? dueno(ganoA) : actual(ganoA).nombre;
        agregarBitacora("🏆 ¡" + ganador + " gana la batalla en " + totalTurnos + " turnos!");

        tvTurno.setText("Fin · " + totalTurnos + " turnos");
        mostrarBanner("🏆 ¡" + ganador + " gana!");
        Efectos.victoria(this);
        Efectos.cry(this, actual(ganoA).id);

        guardarEnHistorial(ganoA);

        Intent resultado = new Intent()
                .putExtra(RESULTADO_GANO_A, ganoA)
                .putExtra(RESULTADO_HP_GANADOR, actual(ganoA).getHpActual())
                .putExtra(RESULTADO_TURNOS, totalTurnos)
                .putExtra(RESULTADO_INDICE_GANADOR, ganoA ? activoA : activoB);
        setResult(RESULT_OK, resultado);

        handler.postDelayed(() -> {
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }, PAUSA_REGRESO);
    }

    private void guardarEnHistorial(boolean ganoA) {
        List<String> movimientos = new ArrayList<>();
        if (esPorEquipos()) {
            movimientos.add(dueno(true) + ": " + nombresEquipo(equipoA) + "\n"
                    + dueno(false) + ": " + nombresEquipo(equipoB));
        }
        for (BattleTurn t : turnos()) movimientos.add(t.descripcion());

        // Por equipos se guarda el nombre de cada jugador y el primer Pokémon de cada equipo
        List<String> lados = esPorEquipos()
                ? Arrays.asList(dueno(true), dueno(false))
                : Arrays.asList(actual(true).nombre, actual(false).nombre);
        List<Integer> ids = Arrays.asList(equipoA.get(0).id, equipoB.get(0).id);
        String ganador = esPorEquipos() ? dueno(ganoA) : actual(ganoA).nombre;

        // Se usa el contexto de la aplicación: la arena puede cerrarse antes de que responda
        Context app = getApplicationContext();
        HistorialManager.guardarBatalla(modo, lados, ids, ganador, movimientos, guardado -> {
            if (!Boolean.TRUE.equals(guardado)) {
                Toast.makeText(app, "No se pudo guardar la batalla en el Historial", Toast.LENGTH_LONG).show();
            }
        });
    }

    // ------------------------------------------------------------------
    // Batalla entre dispositivos
    // ------------------------------------------------------------------

    /** Turnos jugados: los del motor, o los que mandó el anfitrión si este celular es el invitado. */
    private List<BattleTurn> turnos() {
        return espejo ? turnosEspejo : motor.getTurnos();
    }

    /** Nombre de un lado en los mensajes: el A siempre es este celular. */
    private String ladoRed(boolean ladoA) {
        return Protocolo.lado(ladoA == soyAnfitrion);
    }

    /** true si el lado que nombra el mensaje es el A (este celular). */
    private boolean esLadoA(JSONObject mensaje, String clave) {
        return Protocolo.lado(soyAnfitrion).equals(mensaje.optString(clave));
    }

    /** (Anfitrión) Queda en pausa hasta que llegue la "accion" del invitado. */
    private void esperarRival(boolean reemplazo) {
        esperandoRival = true;
        rivalEligeReemplazo = reemplazo;
        tvTurno.setText("⏳ Esperando a " + dueno(false) + "…");
    }

    /** (Anfitrión) Un equipo para el mensaje "inicio": ID, HP y ataques de cada Pokémon. */
    private JSONArray describirEquipo(List<BattlePokemon> equipo) {
        JSONArray lista = new JSONArray();
        for (BattlePokemon p : equipo) {
            JSONArray ataques = new JSONArray();
            for (Movimiento m : p.getMovimientos()) {
                ataques.put(Protocolo.ataqueDe(m.nombre, m.tipo, m.potencia, m.precision));
            }
            lista.put(Protocolo.pokemon(p.id, p.hpMaxima, ataques));
        }
        return lista;
    }

    private final ConexionBatalla.Escucha escuchaRed = new ConexionBatalla.Escucha() {
        @Override
        public void alRecibir(JSONObject mensaje) {
            if (batallaTerminada || isFinishing() || isDestroyed()) return;
            if (soyAnfitrion) recibirComoAnfitrion(mensaje);
            else recibirComoInvitado(mensaje);
        }

        @Override
        public void alDesconectar() {
            if (batallaTerminada || isFinishing() || isDestroyed()) return;
            Toast.makeText(getApplicationContext(), "El otro celular se desconectó: batalla cancelada",
                    Toast.LENGTH_LONG).show();
            finish();
        }
    };

    private void recibirComoAnfitrion(JSONObject mensaje) {
        String tipo = Protocolo.tipo(mensaje);
        if (Protocolo.LISTO.equals(tipo)) {
            rivalListo = true;
            intentarComenzar();
            return;
        }
        // Solo se acepta la decisión del invitado cuando de verdad le toca (y si es válida)
        if (!Protocolo.ACCION.equals(tipo) || !esperandoRival) return;

        int indice = mensaje.optInt("indice", -1);
        String hace = mensaje.optString("hace");
        if (Protocolo.HACE_ATACAR.equals(hace) && !rivalEligeReemplazo) {
            List<Movimiento> ataques = actual(false).getMovimientos();
            if (indice < 0 || indice >= ataques.size()) return;
            esperandoRival = false;
            ejecutarAtaque(false, ataques.get(indice));
        } else if (Protocolo.HACE_CAMBIAR.equals(hace)) {
            if (!otrosVivos(false).contains(indice)) return;
            esperandoRival = false;
            entrarPokemon(false, indice, !rivalEligeReemplazo);
        }
    }

    private void recibirComoInvitado(JSONObject mensaje) {
        switch (Protocolo.tipo(mensaje)) {
            case Protocolo.INICIO:
                aplicarInicio(mensaje);
                break;
            case Protocolo.TURNO:
                turnoDeA = esLadoA(mensaje, "lado");
                if (!turnoDeA) {
                    tvTurno.setText("⏳ Esperando a " + dueno(false) + "…");
                } else if (Protocolo.PIDE_REEMPLAZO.equals(mensaje.optString("pide"))) {
                    mostrarVentanaEquipo(true, "Elige tu siguiente Pokémon", false,
                            indice -> red.enviar(Protocolo.cambiar(indice)));
                } else {
                    mostrarPanelAtaques(true);
                }
                break;
            case Protocolo.ATAQUE:
                aplicarAtaque(mensaje);
                break;
            case Protocolo.ENTRA: {
                boolean ladoA = esLadoA(mensaje, "lado");
                int indice = mensaje.optInt("indice", -1);
                if (indice < 0 || indice >= equipo(ladoA).size()) return;
                mostrarEntrada(ladoA, indice);
                agregarBitacora(dueno(ladoA) + (mensaje.optBoolean("porDecision") ? " cambia a " : " envía a ")
                        + actual(ladoA).nombre + ".");
                break;
            }
            case Protocolo.FIN:
                terminarBatalla(esLadoA(mensaje, "gana"));
                break;
            default:
                break;
        }
    }

    /** (Invitado) HP y ataques que dictó el anfitrión para los dos equipos. */
    private void aplicarInicio(JSONObject mensaje) {
        if (ataquesListos) return;
        aplicarEquipo(mensaje.optJSONArray(Protocolo.lado(soyAnfitrion)), equipoA, datosA);
        aplicarEquipo(mensaje.optJSONArray(Protocolo.lado(!soyAnfitrion)), equipoB, datosB);
        pintarPokemon(true);
        pintarPokemon(false);
        ataquesListos = true;
        intentarComenzar();
    }

    private void aplicarEquipo(JSONArray lista, List<BattlePokemon> equipo, Pokemon[] datos) {
        if (lista == null) return;
        for (int i = 0; i < equipo.size() && i < lista.length(); i++) {
            JSONObject p = lista.optJSONObject(i);
            if (p == null) continue;

            int hp = p.optInt("hp", 0);
            if (hp > 0 && hp != equipo.get(i).hpMaxima) equipo.set(i, new BattlePokemon(datos[i], hp));

            List<Movimiento> ataques = new ArrayList<>();
            JSONArray dictados = p.optJSONArray("ataques");
            for (int j = 0; dictados != null && j < dictados.length(); j++) {
                JSONObject a = dictados.optJSONObject(j);
                if (a != null) ataques.add(leerMovimiento(a));
            }
            equipo.get(i).setMovimientos(ataques);
        }
    }

    private Movimiento leerMovimiento(JSONObject o) {
        String tipo = o.optString("tipo", "");
        return new Movimiento(o.optString("nombre", "Ataque"), tipo.isEmpty() ? null : tipo,
                o.optInt("potencia", 0), o.optInt("precision", 0));
    }

    /** (Invitado) Dibuja un ataque que ya calculó el anfitrión. */
    private void aplicarAtaque(JSONObject mensaje) {
        boolean ladoA = esLadoA(mensaje, "lado");
        BattlePokemon atacante = actual(ladoA), defensor = actual(!ladoA);
        defensor.fijarHp(mensaje.optInt("hp", defensor.getHpActual()));

        BattleTurn turno = BattleTurn.remoto(mensaje.optInt("numero", turnosEspejo.size() + 1),
                atacante, defensor, leerMovimiento(mensaje), mensaje.optInt("danio", 0),
                (float) mensaje.optDouble("efectividad", 1.0),
                mensaje.optBoolean("critico"), mensaje.optBoolean("fallo"));
        turnosEspejo.add(turno);
        mostrarAtaque(ladoA, turno);
    }

    private String nombresEquipo(List<BattlePokemon> equipo) {
        StringBuilder sb = new StringBuilder();
        for (BattlePokemon p : equipo) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(p.nombre);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Animaciones, barras y bitácora
    // ------------------------------------------------------------------

    private void mostrarBanner(String texto) {
        tvBanner.setText(texto);
        tvBanner.setVisibility(View.VISIBLE);
        tvBanner.setScaleX(0.6f);
        tvBanner.setScaleY(0.6f);
        tvBanner.setAlpha(0f);
        tvBanner.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(350)
                .setInterpolator(new OvershootInterpolator()).start();
    }

    private void ocultarBanner() {
        tvBanner.animate().alpha(0f).setDuration(250)
                .withEndAction(() -> tvBanner.setVisibility(View.GONE)).start();
    }

    private void animarAtaque(View vista, float desplazamientoDp) {
        float px = desplazamientoDp * getResources().getDisplayMetrics().density;
        ObjectAnimator salto = ObjectAnimator.ofFloat(vista, "translationX", 0f, px, 0f);
        salto.setDuration(300);
        salto.setInterpolator(new AccelerateDecelerateInterpolator());
        salto.start();
    }

    private void animarGolpe(FrameLayout contenedor, BattleTurn turno) {
        // Sacudida y parpadeo del Pokémon golpeado
        ObjectAnimator sacudida = ObjectAnimator.ofFloat(contenedor, "translationX", 0f, -22f, 22f, -12f, 0f);
        sacudida.setDuration(340);
        sacudida.setStartDelay(150);
        sacudida.start();

        ValueAnimator flash = ValueAnimator.ofFloat(1f, 0.25f, 1f, 0.25f, 1f);
        flash.setDuration(400);
        flash.setStartDelay(150);
        flash.addUpdateListener(a -> contenedor.setAlpha((float) a.getAnimatedValue()));
        flash.start();

        // Texto de daño flotante
        TextView tvDanio = new TextView(this);
        String texto = "-" + turno.danio;
        if (turno.critico) texto += " ¡Crítico!";
        else if (turno.efectividad >= 2f) texto += " ¡Súper eficaz!";
        tvDanio.setText(texto);
        tvDanio.setTextColor(turno.critico ? 0xFFF59E0B : 0xFFE3350D);
        tvDanio.setTextSize(turno.critico || turno.efectividad >= 2f ? 22 : 26);
        tvDanio.setTypeface(null, android.graphics.Typeface.BOLD);
        tvDanio.setShadowLayer(6f, 0f, 2f, 0xFFFFFFFF);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = android.view.Gravity.CENTER;
        contenedor.addView(tvDanio, params);

        tvDanio.animate()
                .translationY(-120f)
                .alpha(0f)
                .setStartDelay(150)
                .setDuration(900)
                .withEndAction(() -> contenedor.removeView(tvDanio))
                .start();
    }

    private void textoFlotante(FrameLayout contenedor, String texto, int color, int tamanio) {
        TextView tv = new TextView(this);
        tv.setText(texto);
        tv.setTextColor(color);
        tv.setTextSize(tamanio);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setShadowLayer(6f, 0f, 2f, 0xFFFFFFFF);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = android.view.Gravity.CENTER;
        contenedor.addView(tv, params);
        tv.animate().translationY(-110f).alpha(0f).setStartDelay(150).setDuration(900)
                .withEndAction(() -> contenedor.removeView(tv)).start();
    }

    /** Barra con % de vida: verde > 50 %, amarilla > 20 %, roja el resto. */
    private void actualizarBarraHp(ProgressBar barra, TextView texto, BattlePokemon pokemon, boolean animar) {
        int porcentaje = Math.round(pokemon.getHpActual() * 100f / pokemon.hpMaxima);

        barra.setMax(pokemon.hpMaxima);
        if (animar) {
            ObjectAnimator.ofInt(barra, "progress", barra.getProgress(), pokemon.getHpActual())
                    .setDuration(500)
                    .start();
        } else {
            barra.setProgress(pokemon.getHpActual());
        }

        int color = porcentaje > 50 ? 0xFF39C06B : porcentaje > 20 ? 0xFFF5C518 : 0xFFE3350D;
        barra.setProgressTintList(ColorStateList.valueOf(color));

        texto.setText(String.format(Locale.ROOT, "%d / %d HP · %d%%",
                pokemon.getHpActual(), pokemon.hpMaxima, porcentaje));
    }

    /** Cada bloque (inicio, turno, resultado) separado por una línea en blanco. */
    private void agregarBitacora(String bloque) {
        tvBitacora.append((tvBitacora.length() == 0 ? "" : "\n\n") + bloque);
        scrollBitacora.post(() -> scrollBitacora.scrollTo(0, tvBitacora.getBottom()));
    }
}
