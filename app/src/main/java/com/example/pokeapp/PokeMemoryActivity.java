package com.example.pokeapp;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.pokeapp.data.CargadorPokemon;
import com.example.pokeapp.data.PokemonMini;
import com.example.pokeapp.data.UsuarioManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * PokéMemory (módulo H): cartas boca abajo con parejas de Pokémon al azar (imágenes de la PokéAPI).
 * Se voltean de dos en dos; si son iguales se quedan, si no se vuelven a tapar.
 *
 * - Dificultad: Fácil 6 parejas, Normal 8, Difícil 10.
 * - Puntos: 100 por pareja − 10 por cada intento fallado − 1 por segundo (mínimo 10).
 * - La mejor puntuación se guarda en Firestore (usuarios/{uid}.recordMemory).
 */
public class PokeMemoryActivity extends AppCompatActivity {

    private static final String RECORD = "recordMemory";
    private static final int COLUMNAS = 4;
    private static final int[] PAREJAS = {6, 8, 10};
    private static final String[] NIVELES = {"Fácil", "Normal", "Difícil"};
    private static final long PAUSA_FALLO = 800;

    private final Random random = new Random();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final List<Integer> cartas = new ArrayList<>();      // ID del Pokémon de cada carta
    private final List<ImageView> vistas = new ArrayList<>();
    private boolean[] descubierta;
    private int primera = -1;          // carta volteada que espera a su pareja
    private boolean bloqueado;         // mientras se ven dos cartas que no coinciden
    private int nivel = 1, intentos, encontradas, segundos, record;
    private boolean jugando, relojCorriendo;

    private TextView tvIntentos, tvParejas, tvTiempo, tvRecord, tvMensaje, btnNivel;
    private GridLayout grid;
    private View btnJugar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_poke_memory);

        tvIntentos = findViewById(R.id.tvIntentosMemory);
        tvParejas = findViewById(R.id.tvParejasMemory);
        tvTiempo = findViewById(R.id.tvTiempoMemory);
        tvRecord = findViewById(R.id.tvRecordMemory);
        tvMensaje = findViewById(R.id.tvMensajeMemory);
        btnNivel = findViewById(R.id.btnNivelMemory);
        grid = findViewById(R.id.gridMemory);
        btnJugar = findViewById(R.id.btnJugarMemory);

        findViewById(R.id.btnRegresar).setOnClickListener(v -> finish());
        btnJugar.setOnClickListener(v -> empezar());
        btnNivel.setOnClickListener(v -> {
            nivel = (nivel + 1) % NIVELES.length;
            pintarMarcador();
        });

        UsuarioManager.obtenerRecord(RECORD, valor -> {
            record = Math.max(record, valor);
            pintarMarcador();
        });
        pintarMarcador();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }

    private void pintarMarcador() {
        tvIntentos.setText("🔁 " + intentos);
        tvParejas.setText("🃏 " + encontradas + "/" + (jugando ? cartas.size() / 2 : PAREJAS[nivel]));
        tvTiempo.setText(String.format(Locale.ROOT, "⏱ %d:%02d", segundos / 60, segundos % 60));
        tvRecord.setText("🏆 Récord: " + record);
        btnNivel.setText("Dificultad: " + NIVELES[nivel] + " (" + PAREJAS[nivel] + " parejas) · tocar para cambiar");
    }

    private void empezar() {
        handler.removeCallbacksAndMessages(null);
        intentos = 0;
        encontradas = 0;
        segundos = 0;
        primera = -1;
        bloqueado = false;
        relojCorriendo = false;
        jugando = true;

        // Parejas de Pokémon al azar, revueltas
        cartas.clear();
        for (int id : CargadorPokemon.idsAleatorios(PAREJAS[nivel], false, random)) {
            cartas.add(id);
            cartas.add(id);
        }
        Collections.shuffle(cartas, random);
        descubierta = new boolean[cartas.size()];

        float dp = getResources().getDisplayMetrics().density;
        grid.removeAllViews();
        vistas.clear();
        for (int i = 0; i < cartas.size(); i++) {
            final int posicion = i;
            ImageView carta = new ImageView(this);
            carta.setScaleType(ImageView.ScaleType.FIT_CENTER);
            carta.setContentDescription("Carta " + (i + 1));
            carta.setOnClickListener(v -> voltear(posicion));
            tapar(carta);

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
            lp.width = 0;
            lp.height = Math.round(88 * dp);
            lp.setMargins(Math.round(4 * dp), Math.round(4 * dp), Math.round(4 * dp), Math.round(4 * dp));
            grid.addView(carta, lp);
            vistas.add(carta);

            // Se descargan desde ya para que al voltear aparezcan al instante
            Glide.with(this).load(new PokemonMini(cartas.get(i), "").getSpriteUrl()).preload();
        }

        btnJugar.setVisibility(View.GONE);
        btnNivel.setVisibility(View.GONE);
        tvMensaje.setText("Encuentra las " + PAREJAS[nivel] + " parejas");
        pintarMarcador();
    }

    /** Carta boca abajo: la pokébola. */
    private void tapar(ImageView carta) {
        float dp = getResources().getDisplayMetrics().density;
        Glide.with(this).clear(carta);
        carta.setBackgroundResource(R.drawable.bg_mini_selected);
        carta.setPadding(Math.round(18 * dp), Math.round(18 * dp), Math.round(18 * dp), Math.round(18 * dp));
        carta.setImageResource(R.drawable.ic_pokeball_logo);
    }

    private void destapar(int posicion) {
        ImageView carta = vistas.get(posicion);
        float dp = getResources().getDisplayMetrics().density;
        carta.setBackgroundResource(R.drawable.bg_mini_normal);
        carta.setPadding(Math.round(4 * dp), Math.round(4 * dp), Math.round(4 * dp), Math.round(4 * dp));
        Glide.with(this).load(new PokemonMini(cartas.get(posicion), "").getSpriteUrl()).into(carta);
    }

    /** Giro corto: la carta se cierra, cambia de cara y se abre. */
    private void girar(ImageView carta, Runnable cambio) {
        carta.animate().scaleX(0f).setDuration(110).withEndAction(() -> {
            cambio.run();
            carta.animate().scaleX(1f).setDuration(110).start();
        }).start();
    }

    private void voltear(int posicion) {
        if (!jugando || bloqueado || descubierta[posicion] || posicion == primera) return;
        if (!relojCorriendo) {
            relojCorriendo = true;
            handler.postDelayed(this::tic, 1000);
        }
        girar(vistas.get(posicion), () -> destapar(posicion));

        if (primera < 0) {
            primera = posicion;
            return;
        }

        intentos++;
        int otra = primera;
        primera = -1;

        if (cartas.get(otra).equals(cartas.get(posicion))) {
            descubierta[otra] = true;
            descubierta[posicion] = true;
            encontradas++;
            vistas.get(otra).setAlpha(0.75f);
            vistas.get(posicion).setAlpha(0.75f);
            if (encontradas == cartas.size() / 2) terminar();
        } else {
            bloqueado = true;
            handler.postDelayed(() -> {
                girar(vistas.get(otra), () -> tapar(vistas.get(otra)));
                girar(vistas.get(posicion), () -> tapar(vistas.get(posicion)));
                bloqueado = false;
            }, PAUSA_FALLO);
        }
        pintarMarcador();
    }

    private void tic() {
        if (!jugando) return;
        segundos++;
        pintarMarcador();
        handler.postDelayed(this::tic, 1000);
    }

    private void terminar() {
        int parejas = cartas.size() / 2;
        int puntos = Math.max(10, parejas * 100 - (intentos - parejas) * 10 - segundos);
        boolean nuevoRecord = puntos > record;
        if (nuevoRecord) {
            record = puntos;
            UsuarioManager.guardarRecord(RECORD, puntos);
        }

        jugando = false;
        relojCorriendo = false;
        tvMensaje.setText("🎉 ¡Completado! " + puntos + " puntos\n" + intentos + " intentos en "
                + segundos + " s" + (nuevoRecord ? "\n🏆 ¡Nuevo récord!" : ""));
        ((TextView) btnJugar).setText("🔁 Jugar otra vez");
        btnJugar.setVisibility(View.VISIBLE);
        btnNivel.setVisibility(View.VISIBLE);
        pintarMarcador();
    }
}
