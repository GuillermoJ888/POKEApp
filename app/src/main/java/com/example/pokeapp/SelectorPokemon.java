package com.example.pokeapp;

import android.app.Activity;
import android.content.Context;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.pokeapp.battle.BattlePokemon;
import com.example.pokeapp.data.ApiClient;
import com.example.pokeapp.data.ListaPokemon;
import com.example.pokeapp.data.Pokemon;
import com.example.pokeapp.data.PokemonMini;
import com.example.pokeapp.data.PokemonStat;

import java.util.List;
import java.util.Locale;
import java.util.Random;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Controla el layout reutilizable layout_selector_pokemon.xml: buscar por nombre o número
 * con sugerencias, elegir de los populares o al azar, ver la vista previa y aceptar.
 * Lo usa Battle Versus (y lo puede usar cualquier pantalla que deba elegir Pokémon).
 */
public class SelectorPokemon {

    public interface AlAceptar {
        void alAceptar(Pokemon pokemon);
    }

    /** Devuelve un mensaje de error si el Pokémon no se puede aceptar, o null si está bien. */
    public interface Validador {
        String revisar(Pokemon pokemon);
    }

    private static final int MAX_SUGERENCIAS = 6;
    private static final Random random = new Random();

    private final Activity activity;
    private final NestedScrollView scroll;
    private final AlAceptar alAceptar;
    private Validador validador = p -> null;

    private final View raiz, seccionPopulares, cardPrevia;
    private final EditText etBuscar;
    private final RecyclerView recyclerSugerencias;
    private final SugerenciaAdapter sugerenciaAdapter;
    private final ProgressBar progress;
    private final TextView tvMensaje, tvNombre, tvTipos, tvStats;
    private final ImageView imgPrevia;
    private final Button btnAceptar;

    private Pokemon previa;
    private boolean ignorarCambioTexto = false;
    private int tokenCarga = 0;   // evita que una respuesta vieja pise a una nueva

    public SelectorPokemon(Activity activity, View raiz, NestedScrollView scroll, AlAceptar alAceptar) {
        this.activity = activity;
        this.raiz = raiz;
        this.scroll = scroll;
        this.alAceptar = alAceptar;

        etBuscar = raiz.findViewById(R.id.etBuscarSel);
        recyclerSugerencias = raiz.findViewById(R.id.recyclerSugerenciasSel);
        seccionPopulares = raiz.findViewById(R.id.seccionPopularesSel);
        progress = raiz.findViewById(R.id.progressSel);
        tvMensaje = raiz.findViewById(R.id.tvMensajeSel);
        cardPrevia = raiz.findViewById(R.id.cardPreviaSel);
        imgPrevia = raiz.findViewById(R.id.imgPreviaSel);
        tvNombre = raiz.findViewById(R.id.tvNombrePreviaSel);
        tvTipos = raiz.findViewById(R.id.tvTiposPreviaSel);
        tvStats = raiz.findViewById(R.id.tvStatsPreviaSel);
        btnAceptar = raiz.findViewById(R.id.btnAceptarSel);

        ListaPokemon.cargar();

        // Sugerencias mientras se escribe
        sugerenciaAdapter = new SugerenciaAdapter(p -> elegirDeLista(p));
        recyclerSugerencias.setLayoutManager(new LinearLayoutManager(activity));
        recyclerSugerencias.setAdapter(sugerenciaAdapter);

        etBuscar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                actualizarPopulares();
                if (ignorarCambioTexto) return;
                List<PokemonMini> coincidencias = ListaPokemon.filtrar(s.toString(), MAX_SUGERENCIAS);
                if (coincidencias.isEmpty()) {
                    recyclerSugerencias.setVisibility(View.GONE);
                } else {
                    sugerenciaAdapter.actualizar(coincidencias);
                    recyclerSugerencias.setVisibility(View.VISIBLE);
                }
            }
        });
        etBuscar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                buscar();
                return true;
            }
            return false;
        });

        // Populares
        PokemonMiniAdapter populares = new PokemonMiniAdapter(this::elegirDeLista, true);
        populares.agregar(ListaPokemon.POPULARES);
        RecyclerView recyclerPopulares = raiz.findViewById(R.id.recyclerPopularesSel);
        recyclerPopulares.setLayoutManager(new GridLayoutManager(activity, 3));
        recyclerPopulares.setAdapter(populares);

        raiz.findViewById(R.id.btnBuscarSel).setOnClickListener(v -> buscar());
        raiz.findViewById(R.id.btnSorprendemeSel).setOnClickListener(v -> {
            ocultarTeclado();
            ponerTexto("");
            cargar(String.valueOf(1 + random.nextInt(1025)));
        });
        btnAceptar.setOnClickListener(v -> aceptar());
    }

    public void setValidador(Validador validador) {
        this.validador = validador;
    }

    public void setTextoAceptar(String texto) {
        btnAceptar.setText(texto);
    }

    public void setVisible(boolean visible) {
        raiz.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /** Deja el selector como nuevo (sin texto ni vista previa). */
    public void limpiar() {
        tokenCarga++;
        previa = null;
        ponerTexto("");
        recyclerSugerencias.setVisibility(View.GONE);
        cardPrevia.setVisibility(View.GONE);
        tvMensaje.setVisibility(View.GONE);
        progress.setVisibility(View.GONE);
        actualizarPopulares();
    }

    // ------------------------------------------------------------------

    private void elegirDeLista(PokemonMini pokemon) {
        ponerTexto(pokemon.getNombre());
        recyclerSugerencias.setVisibility(View.GONE);
        ocultarTeclado();
        cargar(String.valueOf(pokemon.getId()));
    }

    private void buscar() {
        String query = etBuscar.getText().toString().trim();
        if (TextUtils.isEmpty(query)) {
            Toast.makeText(activity, "Escribe un nombre o número", Toast.LENGTH_SHORT).show();
            return;
        }
        recyclerSugerencias.setVisibility(View.GONE);
        ocultarTeclado();
        cargar(ListaPokemon.resolver(query));
    }

    private void cargar(String query) {
        final int token = ++tokenCarga;
        previa = null;
        cardPrevia.setVisibility(View.GONE);
        tvMensaje.setVisibility(View.GONE);
        progress.setVisibility(View.VISIBLE);
        actualizarPopulares();

        ApiClient.getService().getPokemon(query.toLowerCase(Locale.ROOT)).enqueue(new Callback<Pokemon>() {
            @Override
            public void onResponse(Call<Pokemon> call, Response<Pokemon> response) {
                if (token != tokenCarga || activity.isFinishing() || activity.isDestroyed()) return;
                progress.setVisibility(View.GONE);
                if (!response.isSuccessful() || response.body() == null) {
                    mostrarMensaje("No se encontró ningún Pokémon con ese nombre o número");
                    actualizarPopulares();
                    return;
                }
                previa = response.body();
                mostrarPrevia(previa);
            }

            @Override
            public void onFailure(Call<Pokemon> call, Throwable t) {
                if (token != tokenCarga || activity.isFinishing() || activity.isDestroyed()) return;
                progress.setVisibility(View.GONE);
                mostrarMensaje("Error de conexión. Revisa tu Internet e intenta de nuevo");
                actualizarPopulares();
            }
        });
    }

    private void mostrarPrevia(Pokemon pokemon) {
        BattlePokemon datos = new BattlePokemon(pokemon);
        Glide.with(activity).load(datos.imagen).into(imgPrevia);
        tvNombre.setText(String.format(Locale.ROOT, "#%03d %s", datos.id, datos.nombre));
        tvTipos.setText(datos.tiposTexto());
        tvStats.setText(resumenStats(pokemon));

        String error = validador.revisar(pokemon);
        btnAceptar.setEnabled(error == null);
        btnAceptar.setAlpha(error == null ? 1f : 0.5f);
        if (error != null) mostrarMensaje(error);

        cardPrevia.setVisibility(View.VISIBLE);
        actualizarPopulares();
        if (scroll != null) scroll.post(() -> scroll.smoothScrollTo(0, (int) (raiz.getTop() + cardPrevia.getBottom())));
    }

    private void aceptar() {
        if (previa == null) return;
        String error = validador.revisar(previa);
        if (error != null) {
            mostrarMensaje(error);
            return;
        }
        Pokemon elegido = previa;
        limpiar();
        alAceptar.alAceptar(elegido);
    }

    /** Stats reales de la PokéAPI, los mismos que se usan en la batalla. */
    private String resumenStats(Pokemon pokemon) {
        if (pokemon.getStats() == null) return "";
        int hp = 0, atk = 0, def = 0, vel = 0;
        for (PokemonStat s : pokemon.getStats()) {
            switch (s.getStat().getName()) {
                case "hp": hp = s.getBaseStat(); break;
                case "attack": atk = s.getBaseStat(); break;
                case "defense": def = s.getBaseStat(); break;
                case "speed": vel = s.getBaseStat(); break;
                default: break;
            }
        }
        return "HP " + hp + " · Ataque " + atk + " · Defensa " + def + " · Velocidad " + vel;
    }

    private void actualizarPopulares() {
        boolean mostrar = etBuscar.getText().length() == 0
                && cardPrevia.getVisibility() != View.VISIBLE
                && progress.getVisibility() != View.VISIBLE;
        seccionPopulares.setVisibility(mostrar ? View.VISIBLE : View.GONE);
    }

    private void mostrarMensaje(String texto) {
        tvMensaje.setText(texto);
        tvMensaje.setVisibility(View.VISIBLE);
    }

    private void ponerTexto(String texto) {
        ignorarCambioTexto = true;
        etBuscar.setText(texto);
        etBuscar.setSelection(etBuscar.getText().length());
        ignorarCambioTexto = false;
    }

    private void ocultarTeclado() {
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(etBuscar.getWindowToken(), 0);
    }
}
