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
import com.google.gson.Gson;

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

        prepararBatalla(a, b, getIntent().getIntExtra(EXTRA_ESCENARIO, -1));

        // Mientras se ve "¡A vs B!" se cargan los 4 ataques reales de cada Pokémon
        List<BattlePokemon> todos = new ArrayList<>(equipoA);
        todos.addAll(equipoB);
        MovimientoManager.asignar(todos, () -> {
            ataquesListos = true;
            intentarComenzar();
        });
        handler.postDelayed(() -> {
            pausaInicioLista = true;
            if (!ataquesListos) mostrarBanner("Preparando ataques…");
            intentarComenzar();
        }, PAUSA_INICIO);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
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

        Escenario escenario = indiceEscenario >= 0 && indiceEscenario < Escenario.TODOS.size()
                ? Escenario.TODOS.get(indiceEscenario)
                : Escenario.aleatorio(random);
        tvTurno.setText("📍 " + escenario.nombre);
        acomodarEscenario(escenario);

        mostrarDueno(tvDuenoA, nombreA);
        mostrarDueno(tvDuenoB, nombreB);
        pintarPokemon(true);
        pintarPokemon(false);

        if (esPorEquipos()) {
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
            contenedorB.animate().translationX(0f).setDuration(600).setStartDelay(150)
                    .setInterpolator(new OvershootInterpolator()).start();
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
        batallaIniciada = true;
        comenzarBatalla();
    }

    private void comenzarBatalla() {
        if (batallaTerminada || isFinishing() || isDestroyed()) return;

        ocultarBanner();
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
        ejecutarAtaque(turnoDeA, m);
    }

    /** Ataca con el movimiento indicado (null = el sistema elige el mejor). */
    private void ejecutarAtaque(boolean ladoA, Movimiento movimiento) {
        BattlePokemon atacante = actual(ladoA);
        BattlePokemon defensor = actual(!ladoA);

        BattleTurn turno = motor.atacar(atacante, defensor, movimiento);
        tvTurno.setText("Turno " + turno.numero + " · " + turno.movimiento.nombre);

        animarAtaque(contenedor(ladoA), ladoA ? 40f : -40f);
        if (turno.fallo) {
            textoFlotante(contenedor(!ladoA), "¡Falló!", 0xFF8A93A6, 22);
        } else if (turno.sinEfecto()) {
            textoFlotante(contenedor(!ladoA), "No le afecta", 0xFF8A93A6, 20);
        } else {
            animarGolpe(contenedor(!ladoA), turno);
        }
        actualizarBarraHp(ladoA ? barraHpB : barraHpA, ladoA ? tvHpB : tvHpA, defensor, true);
        agregarBitacora(turno.descripcion());

        if (turno.derrotado) {
            actualizarPuntos(!ladoA);
            contenedor(!ladoA).animate().translationY(80f).alpha(0f).setDuration(700).setStartDelay(300).start();

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
            entrarPokemon(lado, indice, true);
        });
    }

    /** El lado que perdió a su Pokémon manda otro: el jugador lo elige, el sistema toma el siguiente. */
    private void pedirReemplazo(boolean ladoA) {
        if (batallaTerminada || isFinishing() || isDestroyed()) return;

        List<Integer> vivos = otrosVivos(ladoA);
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
        if (ladoA) activoA = indice;
        else activoB = indice;

        pintarPokemon(ladoA);

        View vista = contenedor(ladoA);
        vista.animate().cancel();
        vista.setAlpha(1f);
        vista.setTranslationY(0f);
        vista.setTranslationX(ladoA ? -vista.getWidth() * 2f : vista.getWidth() * 2f);
        vista.animate().translationX(0f).setDuration(500).setStartDelay(0)
                .setInterpolator(new OvershootInterpolator()).start();

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

    private void terminarBatalla(boolean ganoA) {
        batallaTerminada = true;
        esperandoJugador = false;
        barraAccion.setVisibility(View.GONE);

        int totalTurnos = motor.getTurnos().size();
        String ganador = esPorEquipos() ? dueno(ganoA) : actual(ganoA).nombre;
        agregarBitacora("🏆 ¡" + ganador + " gana la batalla en " + totalTurnos + " turnos!");

        tvTurno.setText("Fin · " + totalTurnos + " turnos");
        mostrarBanner("🏆 ¡" + ganador + " gana!");

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
        for (BattleTurn t : motor.getTurnos()) movimientos.add(t.descripcion());

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
