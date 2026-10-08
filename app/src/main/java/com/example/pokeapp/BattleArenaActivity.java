package com.example.pokeapp;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.bumptech.glide.Glide;
import com.example.pokeapp.battle.BattleEngine;
import com.example.pokeapp.battle.BattlePokemon;
import com.example.pokeapp.battle.BattleTurn;
import com.example.pokeapp.battle.Escenario;
import com.example.pokeapp.data.HistorialManager;
import com.example.pokeapp.data.Pokemon;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Batalla a pantalla completa del Battle Emulator.
 *
 * Recibe los dos Pokémon (como JSON), la pelea corre sola con el motor compartido,
 * guarda el resumen en el Historial y, 2 segundos después de que uno cae,
 * regresa al Emulator con el resultado para mostrar la ventana del ganador.
 */
public class BattleArenaActivity extends AppCompatActivity {

    private static final String EXTRA_POKEMON_A = "extra_pokemon_a";
    private static final String EXTRA_POKEMON_B = "extra_pokemon_b";

    // Resultado que recibe el Emulator
    public static final String RESULTADO_GANO_A = "resultado_gano_a";
    public static final String RESULTADO_HP_GANADOR = "resultado_hp_ganador";
    public static final String RESULTADO_TURNOS = "resultado_turnos";

    private static final long PAUSA_INICIO = 1500;
    private static final long PAUSA_TURNO = 1300;
    private static final long PAUSA_REGRESO = 2000;

    private static final Gson gson = new Gson();
    private static final Random random = new Random();

    private ImageView imgA, imgB;
    private TextView tvNombreA, tvNombreB, tvTiposA, tvTiposB, tvHpA, tvHpB;
    private TextView tvTurno, tvBanner, tvBitacora;
    private ProgressBar barraHpA, barraHpB;
    private FrameLayout contenedorA, contenedorB;
    private ScrollView scrollBitacora;

    private BattleEngine motor;
    private BattlePokemon pokemonA, pokemonB, turnoDe;
    private boolean batallaTerminada = false;

    private final Handler handler = new Handler(Looper.getMainLooper());

    /** Intent para abrir la arena con los dos Pokémon ya cargados. */
    public static Intent crearIntent(Context context, Pokemon a, Pokemon b) {
        return new Intent(context, BattleArenaActivity.class)
                .putExtra(EXTRA_POKEMON_A, gson.toJson(a))
                .putExtra(EXTRA_POKEMON_B, gson.toJson(b));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_battle_arena);
        pantallaCompleta();
        enlazarVistas();

        Pokemon a = leerPokemon(EXTRA_POKEMON_A);
        Pokemon b = leerPokemon(EXTRA_POKEMON_B);
        if (a == null || b == null) {
            Toast.makeText(this, "No se pudieron cargar los Pokémon", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        findViewById(R.id.btnSalirArena).setOnClickListener(v -> finish());

        prepararBatalla(a, b);
        handler.postDelayed(this::comenzarBatalla, PAUSA_INICIO);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }

    private Pokemon leerPokemon(String extra) {
        String json = getIntent().getStringExtra(extra);
        if (json == null) return null;
        try {
            return gson.fromJson(json, Pokemon.class);
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
        barraHpA = findViewById(R.id.barraHpA);
        barraHpB = findViewById(R.id.barraHpB);
        contenedorA = findViewById(R.id.contenedorA);
        contenedorB = findViewById(R.id.contenedorB);
        tvTurno = findViewById(R.id.tvTurno);
        tvBanner = findViewById(R.id.tvBanner);
        tvBitacora = findViewById(R.id.tvBitacora);
        scrollBitacora = findViewById(R.id.scrollBitacora);
    }

    // ------------------------------------------------------------------
    // Batalla
    // ------------------------------------------------------------------

    private void prepararBatalla(Pokemon a, Pokemon b) {
        motor = new BattleEngine(random);
        pokemonA = new BattlePokemon(a);
        pokemonB = new BattlePokemon(b);

        // Escenario al azar: fondo, plataformas y posición de cada Pokémon
        Escenario escenario = Escenario.aleatorio(random);
        tvTurno.setText("📍 " + escenario.nombre);
        acomodarEscenario(escenario);

        tvNombreA.setText(pokemonA.nombre);
        tvNombreB.setText(pokemonB.nombre);
        tvTiposA.setText(pokemonA.tiposTexto());
        tvTiposB.setText(pokemonB.tiposTexto());
        Glide.with(this).load(pokemonA.imagen).into(imgA);
        Glide.with(this).load(pokemonB.imagen).into(imgB);

        actualizarBarraHp(barraHpA, tvHpA, pokemonA, false);
        actualizarBarraHp(barraHpB, tvHpB, pokemonB, false);

        mostrarBanner("¡" + pokemonA.nombre + " vs " + pokemonB.nombre + "!");
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

            float ancho = arena.getWidth(), alto = arena.getHeight();
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

            // Tamaño de los Pokémon según el escenario
            escalar(contenedorB, escenario.escalaPokemon);
            escalar(contenedorA, escenario.escalaPokemon);

            // Los pies del Pokémon quedan un poco abajo del centro de su plataforma
            colocar(contenedorB, bx, by + 6 * dp);
            colocar(contenedorA, ax, ay + 6 * dp);

            // Tarjeta de A: si A está hasta abajo, va a media altura (bajo B);
            // si A está más arriba, va abajo a la derecha para no tapar a B
            FrameLayout.LayoutParams lpHud = (FrameLayout.LayoutParams) hudA.getLayoutParams();
            lpHud.topMargin = escenario.aY > 0.8f
                    ? Math.round(alto * 0.53f)
                    : Math.round(alto - hudA.getHeight() - 16 * dp);
            hudA.setLayoutParams(lpHud);
            hudA.setVisibility(View.VISIBLE);

            // Entrada: cada Pokémon llega desde su lado
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

    private void comenzarBatalla() {
        if (batallaTerminada || isFinishing() || isDestroyed()) return;

        ocultarBanner();

        turnoDe = motor.quienEmpieza(pokemonA, pokemonB);
        boolean porVelocidad = pokemonA.velocidad != pokemonB.velocidad;

        agregarBitacora("¡" + pokemonA.nombre + " vs " + pokemonB.nombre + "!\nEmpieza "
                + turnoDe.nombre + (porVelocidad
                ? " por tener mayor Speed (" + turnoDe.velocidad + ")."
                : " por sorteo (misma Speed)."));

        handler.postDelayed(this::siguienteTurno, 700);
    }

    private void siguienteTurno() {
        if (batallaTerminada || isFinishing() || isDestroyed()) return;

        BattlePokemon atacante = turnoDe;
        BattlePokemon defensor = atacante == pokemonA ? pokemonB : pokemonA;

        BattleTurn turno = motor.atacar(atacante, defensor);
        tvTurno.setText("Turno " + turno.numero);

        boolean ataqueDeA = atacante == pokemonA;
        animarAtaque(ataqueDeA ? contenedorA : contenedorB, ataqueDeA ? 40f : -40f);
        animarGolpe(ataqueDeA ? contenedorB : contenedorA, turno);

        if (ataqueDeA) actualizarBarraHp(barraHpB, tvHpB, pokemonB, true);
        else actualizarBarraHp(barraHpA, tvHpA, pokemonA, true);

        agregarBitacora(turno.descripcion());

        if (turno.derrotado) {
            terminarBatalla(atacante, defensor);
            return;
        }

        turnoDe = defensor;
        handler.postDelayed(this::siguienteTurno, PAUSA_TURNO);
    }

    private void terminarBatalla(BattlePokemon ganador, BattlePokemon perdedor) {
        batallaTerminada = true;

        int totalTurnos = motor.getTurnos().size();
        agregarBitacora("🏆 ¡" + ganador.nombre + " gana la batalla en " + totalTurnos + " turnos!");

        // El perdedor cae y se desvanece
        View vistaPerdedor = perdedor == pokemonA ? contenedorA : contenedorB;
        vistaPerdedor.animate().translationY(80f).alpha(0f).setDuration(700).setStartDelay(300).start();

        tvTurno.setText("Fin · " + totalTurnos + " turnos");
        mostrarBanner("🏆 ¡" + ganador.nombre + " gana!");

        guardarEnHistorial(ganador);

        Intent resultado = new Intent()
                .putExtra(RESULTADO_GANO_A, ganador == pokemonA)
                .putExtra(RESULTADO_HP_GANADOR, ganador.getHpActual())
                .putExtra(RESULTADO_TURNOS, totalTurnos);
        setResult(RESULT_OK, resultado);

        handler.postDelayed(() -> {
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }, PAUSA_REGRESO);
    }

    private void guardarEnHistorial(BattlePokemon ganador) {
        List<String> movimientos = new ArrayList<>();
        for (BattleTurn t : motor.getTurnos()) movimientos.add(t.descripcion());

        // Se usa el contexto de la aplicación: la arena puede cerrarse antes de que responda
        Context app = getApplicationContext();

        HistorialManager.guardarBatalla(
                HistorialManager.MODO_EMULATOR,
                Arrays.asList(pokemonA.nombre, pokemonB.nombre),
                Arrays.asList(pokemonA.id, pokemonB.id),
                ganador.nombre,
                movimientos,
                guardado -> {
                    if (!Boolean.TRUE.equals(guardado)) {
                        Toast.makeText(app, "No se pudo guardar la batalla en el Historial",
                                Toast.LENGTH_LONG).show();
                    }
                });
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
