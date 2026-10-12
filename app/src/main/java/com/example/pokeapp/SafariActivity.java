package com.example.pokeapp;

import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
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
 * - Si el lanzamiento falla, el Pokémon puede escapar (25 %). También se puede huir del encuentro.
 * - Los capturados se guardan en la colección del usuario (Firestore: usuarios/{uid}.capturados).
 */
public class SafariActivity extends AppCompatActivity {

    private static final String[] BOLAS = {"Poké Ball", "Super Ball", "Ultra Ball"};
    private static final int[] PROBABILIDAD = {35, 55, 80};
    private static final int[] INICIALES = {8, 4, 2};
    private static final int[] COLOR = {0xFFE3350D, 0xFF1D4ED8, 0xFFB8860B};
    private static final int PROBABILIDAD_ESCAPE = 25;

    private final Random random = new Random();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final int[] bolas = new int[BOLAS.length];
    private BattlePokemon salvaje;
    private int dificultad;            // puntos que se restan a la probabilidad (0–25)
    private boolean ocupado = true;    // cargando o a media animación
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
            botones[i].setOnClickListener(v -> lanzar(tipo));

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
            botones[i].setAlpha(bolas[i] > 0 && hayPokemon ? 1f : 0.4f);
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
        tvMensaje.setText("¡Un " + p.nombre + " salvaje apareció! Elige una Pokéball.");
        ocupado = false;
        pintar();
    }

    private boolean yaCapturado(int id) {
        for (PokemonMini m : coleccion) if (m.getId() == id) return true;
        return false;
    }

    // ------------------------------------------------------------------
    // Lanzar la Pokéball
    // ------------------------------------------------------------------

    private void lanzar(int tipo) {
        if (ocupado || salvaje == null || bolas[tipo] <= 0) return;
        ocupado = true;
        bolas[tipo]--;
        boolean capturado = random.nextInt(100) < probabilidad(tipo);
        tvMensaje.setText("¡Lanzaste una " + BOLAS[tipo] + "!");
        pintar();

        // 1) La bola sube girando desde abajo hasta el Pokémon
        float dp = getResources().getDisplayMetrics().density;
        imgBola.setImageDrawable(new BolaDrawable(tipo));
        imgBola.setVisibility(View.VISIBLE);
        imgBola.setTranslationY(220 * dp);
        imgBola.setRotation(0f);
        imgBola.setScaleX(1f);
        imgBola.setScaleY(1f);
        imgBola.animate().translationY(0f).rotation(720f).setDuration(450).withEndAction(() -> {
            // 2) El Pokémon entra en la bola
            imgPokemon.animate().scaleX(0f).scaleY(0f).alpha(0f).setDuration(250)
                    .withEndAction(() -> sacudir(3, capturado)).start();
        }).start();
    }

    /** 3) La bola se sacude; al terminar se sabe si quedó capturado. */
    private void sacudir(int veces, boolean capturado) {
        if (isFinishing() || isDestroyed()) return;
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
