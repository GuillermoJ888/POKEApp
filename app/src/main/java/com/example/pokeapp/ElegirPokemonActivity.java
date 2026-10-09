package com.example.pokeapp;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.pokeapp.data.Pokemon;
import com.google.gson.Gson;

import java.util.ArrayList;

/**
 * Pantalla para elegir UN Pokémon (buscador, populares, 🎲 y tarjeta decorada).
 * Se abre al tocar un "+" de un equipo y regresa el Pokémon elegido a quien la abrió.
 * Recibe los IDs que ya están en el equipo para no dejar repetirlos.
 */
public class ElegirPokemonActivity extends AppCompatActivity {

    private static final String EXTRA_TITULO = "extra_titulo";
    private static final String EXTRA_JUGADOR = "extra_jugador";
    private static final String EXTRA_EXCLUIR = "extra_excluir";
    public static final String EXTRA_POSICION = "extra_posicion";
    public static final String RESULTADO_POKEMON = "resultado_pokemon";

    private static final Gson gson = new Gson();

    /**
     * @param jugador  "Jugador 1": se usa en el botón "Agregar al equipo del Jugador 1"
     * @param titulo   texto bajo el título, por ejemplo "Jugador 1 · lugar 2 de 3"
     * @param posicion lugar del equipo que se llena (se regresa tal cual)
     * @param excluir  IDs que ya están en el equipo
     */
    public static Intent crearIntent(Context context, String jugador, String titulo,
                                     int posicion, ArrayList<Integer> excluir) {
        return new Intent(context, ElegirPokemonActivity.class)
                .putExtra(EXTRA_JUGADOR, jugador)
                .putExtra(EXTRA_TITULO, titulo)
                .putExtra(EXTRA_POSICION, posicion)
                .putIntegerArrayListExtra(EXTRA_EXCLUIR, excluir);
    }

    /** Lee el Pokémon que regresó esta pantalla (o null). */
    public static Pokemon leerResultado(Intent datos) {
        if (datos == null) return null;
        String json = datos.getStringExtra(RESULTADO_POKEMON);
        return json != null ? gson.fromJson(json, Pokemon.class) : null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_elegir_pokemon);

        String jugador = getIntent().getStringExtra(EXTRA_JUGADOR);
        String titulo = getIntent().getStringExtra(EXTRA_TITULO);
        int posicion = getIntent().getIntExtra(EXTRA_POSICION, 0);
        ArrayList<Integer> excluir = getIntent().getIntegerArrayListExtra(EXTRA_EXCLUIR);

        ((TextView) findViewById(R.id.tvSubtituloElegir)).setText(titulo != null ? titulo : "");
        findViewById(R.id.btnRegresar).setOnClickListener(v -> finish());

        SelectorPokemon selector = new SelectorPokemon(this, findViewById(R.id.selectorElegir),
                findViewById(R.id.scrollElegir), pokemon -> {
                    setResult(RESULT_OK, new Intent()
                            .putExtra(RESULTADO_POKEMON, gson.toJson(pokemon))
                            .putExtra(EXTRA_POSICION, posicion));
                    finish();
                });

        selector.setTextoAceptar(jugador != null ? "Agregar al equipo del " + jugador : "Aceptar");
        selector.setValidador(p -> excluir != null && excluir.contains(p.getId())
                ? "Ese Pokémon ya está en el equipo" + (jugador != null ? " del " + jugador : "")
                : null);
    }
}
