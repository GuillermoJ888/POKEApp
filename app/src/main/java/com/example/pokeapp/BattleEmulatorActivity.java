package com.example.pokeapp;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.pokeapp.battle.BattlePokemon;
import com.example.pokeapp.data.ApiClient;
import com.example.pokeapp.data.HistorialManager;
import com.example.pokeapp.data.ListaPokemon;
import com.example.pokeapp.data.Pokemon;
import com.example.pokeapp.data.PokemonMini;
import com.example.pokeapp.data.PokemonStat;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Battle Emulator: el usuario busca y acepta el Pokémon A y el Pokémon B.
 * "Iniciar batalla" abre la arena a pantalla completa (BattleArenaActivity);
 * al volver se muestra el ganador en una ventana emergente.
 */
public class BattleEmulatorActivity extends AppCompatActivity {

    private static final int MAX_SUGERENCIAS = 6;
    private static final int ID_MAXIMO_ALEATORIO = 1025;
    private static final Random random = new Random();

    /** Se muestran mientras el buscador está vacío, para que no se vea en blanco. */
    private static final List<PokemonMini> POPULARES = Arrays.asList(
            new PokemonMini(25, "Pikachu"),
            new PokemonMini(6, "Charizard"),
            new PokemonMini(150, "Mewtwo"),
            new PokemonMini(448, "Lucario"),
            new PokemonMini(94, "Gengar"),
            new PokemonMini(9, "Blastoise"),
            new PokemonMini(3, "Venusaur"),
            new PokemonMini(149, "Dragonite"),
            new PokemonMini(143, "Snorlax"),
            new PokemonMini(133, "Eevee"),
            new PokemonMini(248, "Tyranitar"),
            new PokemonMini(445, "Garchomp"));

    private ScrollView scrollRaiz;
    private View cardElegidoA, cardElegidoB, tvVsListos, btnIniciarBatalla;
    private View seccionBuscador, seccionPopulares, cardPrevia;
    private ImageView imgElegidoA, imgElegidoB, imgPrevia;
    private TextView tvNombreElegidoA, tvTiposElegidoA, tvNombreElegidoB, tvTiposElegidoB;
    private TextView tvPaso, tvMensajeSeleccion, tvNombrePrevia, tvTiposPrevia, tvStatsPrevia;
    private EditText etBuscar;
    private RecyclerView recyclerSugerencias, recyclerPopulares;
    private SugerenciaAdapter sugerenciaAdapter;
    private ProgressBar progressSeleccion;
    private Button btnAceptar;

    private Pokemon previa, elegidoA, elegidoB;
    private boolean ignorarCambioTexto = false;
    private int tokenCarga = 0;          // evita que una respuesta vieja pise a una nueva

    /** Abre la arena y recibe quién ganó. */
    private final ActivityResultLauncher<Intent> lanzadorArena = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            resultado -> {
                Intent datos = resultado.getData();
                if (resultado.getResultCode() != RESULT_OK || datos == null) return;

                mostrarGanador(
                        datos.getBooleanExtra(BattleArenaActivity.RESULTADO_GANO_A, true),
                        datos.getIntExtra(BattleArenaActivity.RESULTADO_HP_GANADOR, 0),
                        datos.getIntExtra(BattleArenaActivity.RESULTADO_TURNOS, 0));
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_battle_emulator);

        enlazarVistas();
        configurarBuscador();
        configurarPopulares();
        ListaPokemon.cargar();
        actualizarVista();

        findViewById(R.id.btnRegresar).setOnClickListener(v -> finish());
        findViewById(R.id.btnHistorial).setOnClickListener(v -> startActivity(
                HistorialActivity.crearIntent(this, HistorialManager.MODO_EMULATOR)));
        findViewById(R.id.btnBuscar).setOnClickListener(v -> buscar());
        findViewById(R.id.btnSorprendeme).setOnClickListener(v -> elegirAleatorio());
        findViewById(R.id.btnCambiarA).setOnClickListener(v -> {
            elegidoA = null;
            limpiarBusqueda();
            actualizarVista();
        });
        findViewById(R.id.btnCambiarB).setOnClickListener(v -> {
            elegidoB = null;
            limpiarBusqueda();
            actualizarVista();
        });
        btnIniciarBatalla.setOnClickListener(v -> iniciarBatalla());
        btnAceptar.setOnClickListener(v -> aceptar());

        // Cerrar la vista previa y volver a los populares (útil si el Pokémon no se puede aceptar)
        findViewById(R.id.btnCerrarPrevia).setOnClickListener(v -> {
            limpiarBusqueda();
            scrollRaiz.smoothScrollTo(0, 0);
        });
    }

    private void enlazarVistas() {
        scrollRaiz = findViewById(R.id.scrollRaiz);

        cardElegidoA = findViewById(R.id.cardElegidoA);
        imgElegidoA = findViewById(R.id.imgElegidoA);
        tvNombreElegidoA = findViewById(R.id.tvNombreElegidoA);
        tvTiposElegidoA = findViewById(R.id.tvTiposElegidoA);
        cardElegidoB = findViewById(R.id.cardElegidoB);
        imgElegidoB = findViewById(R.id.imgElegidoB);
        tvNombreElegidoB = findViewById(R.id.tvNombreElegidoB);
        tvTiposElegidoB = findViewById(R.id.tvTiposElegidoB);
        tvVsListos = findViewById(R.id.tvVsListos);
        btnIniciarBatalla = findViewById(R.id.btnIniciarBatalla);

        seccionBuscador = findViewById(R.id.seccionBuscador);
        tvPaso = findViewById(R.id.tvPaso);
        etBuscar = findViewById(R.id.etBuscar);
        recyclerSugerencias = findViewById(R.id.recyclerSugerencias);
        seccionPopulares = findViewById(R.id.seccionPopulares);
        recyclerPopulares = findViewById(R.id.recyclerPopulares);
        progressSeleccion = findViewById(R.id.progressSeleccion);
        tvMensajeSeleccion = findViewById(R.id.tvMensajeSeleccion);
        cardPrevia = findViewById(R.id.cardPrevia);
        imgPrevia = findViewById(R.id.imgPrevia);
        tvNombrePrevia = findViewById(R.id.tvNombrePrevia);
        tvTiposPrevia = findViewById(R.id.tvTiposPrevia);
        tvStatsPrevia = findViewById(R.id.tvStatsPrevia);
        btnAceptar = findViewById(R.id.btnAceptar);
    }

    // ------------------------------------------------------------------
    // Estado de la pantalla: A, B y el buscador
    // ------------------------------------------------------------------

    /** El buscador llena primero A y luego B. */
    private boolean eligiendoA() {
        return elegidoA == null;
    }

    private void actualizarVista() {
        boolean listos = elegidoA != null && elegidoB != null;

        mostrarTarjeta(elegidoA, cardElegidoA, imgElegidoA, tvNombreElegidoA, tvTiposElegidoA);
        mostrarTarjeta(elegidoB, cardElegidoB, imgElegidoB, tvNombreElegidoB, tvTiposElegidoB);

        tvVsListos.setVisibility(listos ? View.VISIBLE : View.GONE);
        btnIniciarBatalla.setVisibility(listos ? View.VISIBLE : View.GONE);
        seccionBuscador.setVisibility(listos ? View.GONE : View.VISIBLE);

        boolean hayUno = elegidoA != null || elegidoB != null;
        ponerMargenSuperior(tvPaso, hayUno ? 18 : 4);

        tvPaso.setText(eligiendoA()
                ? (elegidoB == null ? "Paso 1 de 2 · Elige al Pokémon A" : "Elige al Pokémon A")
                : "Paso 2 de 2 · Elige al Pokémon B");
        btnAceptar.setText(eligiendoA() ? "Aceptar como Pokémon A" : "Aceptar como Pokémon B");

        actualizarPopulares();
    }

    private void mostrarTarjeta(Pokemon pokemon, View tarjeta, ImageView img, TextView tvNombre, TextView tvTipos) {
        if (pokemon == null) {
            tarjeta.setVisibility(View.GONE);
            return;
        }
        BattlePokemon datos = new BattlePokemon(pokemon);
        Glide.with(this).load(datos.imagen).into(img);
        tvNombre.setText(datos.nombre);
        tvTipos.setText(datos.tiposTexto());
        tarjeta.setVisibility(View.VISIBLE);
    }

    private void ponerMargenSuperior(View vista, int dp) {
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) vista.getLayoutParams();
        lp.topMargin = Math.round(dp * getResources().getDisplayMetrics().density);
        vista.setLayoutParams(lp);
    }

    // ------------------------------------------------------------------
    // Buscador con sugerencias y populares
    // ------------------------------------------------------------------

    private void configurarBuscador() {
        sugerenciaAdapter = new SugerenciaAdapter(pokemon -> {
            ponerTexto(pokemon.getNombre());
            ocultarSugerencias();
            ocultarTeclado();
            cargarPrevia(String.valueOf(pokemon.getId()));
        });

        recyclerSugerencias.setLayoutManager(new LinearLayoutManager(this));
        recyclerSugerencias.setAdapter(sugerenciaAdapter);

        etBuscar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                actualizarPopulares();
                if (ignorarCambioTexto) return;

                List<PokemonMini> coincidencias = ListaPokemon.filtrar(s.toString(), MAX_SUGERENCIAS);

                if (coincidencias.isEmpty()) {
                    ocultarSugerencias();
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
    }

    private void configurarPopulares() {
        PokemonMiniAdapter adapter = new PokemonMiniAdapter(pokemon -> {
            ponerTexto(pokemon.getNombre());
            ocultarSugerencias();
            ocultarTeclado();
            cargarPrevia(String.valueOf(pokemon.getId()));
        }, true);
        adapter.agregar(POPULARES);

        recyclerPopulares.setLayoutManager(new GridLayoutManager(this, 3));
        recyclerPopulares.setAdapter(adapter);
    }

    /** Los populares se ven solo con el buscador vacío y sin un Pokémon cargado o cargándose. */
    private void actualizarPopulares() {
        boolean mostrar = etBuscar.getText().length() == 0
                && cardPrevia.getVisibility() != View.VISIBLE
                && progressSeleccion.getVisibility() != View.VISIBLE;
        seccionPopulares.setVisibility(mostrar ? View.VISIBLE : View.GONE);
    }

    private void buscar() {
        String query = etBuscar.getText().toString().trim();

        if (TextUtils.isEmpty(query)) {
            Toast.makeText(this, "Escribe un nombre o número", Toast.LENGTH_SHORT).show();
            return;
        }

        ocultarSugerencias();
        ocultarTeclado();
        cargarPrevia(ListaPokemon.resolver(query));
    }

    private void elegirAleatorio() {
        ocultarSugerencias();
        ocultarTeclado();
        ponerTexto("");
        cargarPrevia(String.valueOf(1 + random.nextInt(ID_MAXIMO_ALEATORIO)));
    }

    private void cargarPrevia(String query) {
        final int token = ++tokenCarga;

        previa = null;
        cardPrevia.setVisibility(View.GONE);
        tvMensajeSeleccion.setVisibility(View.GONE);
        progressSeleccion.setVisibility(View.VISIBLE);
        actualizarPopulares();

        ApiClient.getService().getPokemon(query.toLowerCase(Locale.ROOT)).enqueue(new Callback<Pokemon>() {
            @Override
            public void onResponse(Call<Pokemon> call, Response<Pokemon> response) {
                if (token != tokenCarga || isFinishing() || isDestroyed()) return;
                progressSeleccion.setVisibility(View.GONE);

                if (!response.isSuccessful() || response.body() == null) {
                    mostrarMensajeSeleccion("No se encontró ningún Pokémon con ese nombre o número");
                    actualizarPopulares();
                    return;
                }

                previa = response.body();
                mostrarPrevia(previa);
            }

            @Override
            public void onFailure(Call<Pokemon> call, Throwable t) {
                if (token != tokenCarga || isFinishing() || isDestroyed()) return;
                progressSeleccion.setVisibility(View.GONE);
                mostrarMensajeSeleccion("Error de conexión. Revisa tu Internet e intenta de nuevo");
                actualizarPopulares();
            }
        });
    }

    private void mostrarPrevia(Pokemon pokemon) {
        BattlePokemon datos = new BattlePokemon(pokemon);

        Glide.with(this).load(datos.imagen).into(imgPrevia);
        tvNombrePrevia.setText(String.format(Locale.ROOT, "#%03d %s", datos.id, datos.nombre));
        tvTiposPrevia.setText(datos.tiposTexto());
        tvStatsPrevia.setText(resumenStats(pokemon));

        // Validación: A y B no pueden ser el mismo Pokémon
        boolean repetido = esRepetido(pokemon);
        btnAceptar.setEnabled(!repetido);
        btnAceptar.setAlpha(repetido ? 0.5f : 1f);
        if (repetido) mostrarMensajeSeleccion(mensajeRepetido());

        cardPrevia.setVisibility(View.VISIBLE);
        actualizarPopulares();
        scrollRaiz.post(() -> scrollRaiz.smoothScrollTo(0, cardPrevia.getBottom()));
    }

    /** Stats reales de la PokéAPI, los mismos que se usan en la batalla. */
    private String resumenStats(Pokemon pokemon) {
        if (pokemon.getStats() == null) return "";

        int hp = 0, atk = 0, def = 0, atkEsp = 0, defEsp = 0, vel = 0;
        for (PokemonStat s : pokemon.getStats()) {
            switch (s.getStat().getName()) {
                case "hp": hp = s.getBaseStat(); break;
                case "attack": atk = s.getBaseStat(); break;
                case "defense": def = s.getBaseStat(); break;
                case "special-attack": atkEsp = s.getBaseStat(); break;
                case "special-defense": defEsp = s.getBaseStat(); break;
                case "speed": vel = s.getBaseStat(); break;
                default: break;
            }
        }

        return "HP " + hp + " · Ataque " + atk + " · Defensa " + def
                + "\nAt. Esp. " + atkEsp + " · Def. Esp. " + defEsp + " · Velocidad " + vel;
    }

    private void aceptar() {
        if (previa == null) return;

        if (esRepetido(previa)) {
            mostrarMensajeSeleccion(mensajeRepetido());
            return;
        }

        if (eligiendoA()) elegidoA = previa;
        else elegidoB = previa;

        limpiarBusqueda();
        actualizarVista();
        scrollRaiz.smoothScrollTo(0, 0);
    }

    /** ¿El Pokémon que se va a aceptar es el mismo que ya está del otro lado? */
    private boolean esRepetido(Pokemon pokemon) {
        Pokemon otro = eligiendoA() ? elegidoB : elegidoA;
        return otro != null && pokemon != null && otro.getId() == pokemon.getId();
    }

    private String mensajeRepetido() {
        return eligiendoA()
                ? "Ese Pokémon ya es el B. Elige uno diferente para A."
                : "Ese Pokémon ya es el A. Elige uno diferente para B.";
    }

    private void limpiarBusqueda() {
        tokenCarga++;   // descarta cualquier búsqueda que siga en camino
        previa = null;
        ponerTexto("");
        ocultarSugerencias();
        cardPrevia.setVisibility(View.GONE);
        tvMensajeSeleccion.setVisibility(View.GONE);
        progressSeleccion.setVisibility(View.GONE);
        actualizarPopulares();
    }

    private void mostrarMensajeSeleccion(String texto) {
        tvMensajeSeleccion.setText(texto);
        tvMensajeSeleccion.setVisibility(View.VISIBLE);
    }

    /** Cambia el texto del buscador sin que se abran las sugerencias. */
    private void ponerTexto(String texto) {
        ignorarCambioTexto = true;
        etBuscar.setText(texto);
        etBuscar.setSelection(etBuscar.getText().length());
        ignorarCambioTexto = false;
    }

    private void ocultarSugerencias() {
        recyclerSugerencias.setVisibility(View.GONE);
    }

    private void ocultarTeclado() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(etBuscar.getWindowToken(), 0);
    }

    // ------------------------------------------------------------------
    // Batalla (pantalla completa) y ventana del ganador
    // ------------------------------------------------------------------

    private void iniciarBatalla() {
        if (elegidoA == null || elegidoB == null) return;
        lanzadorArena.launch(BattleArenaActivity.crearIntent(this, elegidoA, elegidoB));
    }

    private void mostrarGanador(boolean ganoA, int hpRestante, int turnos) {
        if (elegidoA == null || elegidoB == null) return;

        BattlePokemon ganador = new BattlePokemon(ganoA ? elegidoA : elegidoB);
        BattlePokemon perdedor = new BattlePokemon(ganoA ? elegidoB : elegidoA);

        View vista = LayoutInflater.from(this).inflate(R.layout.dialog_ganador, null);
        Glide.with(this).load(ganador.imagen).into((ImageView) vista.findViewById(R.id.imgGanador));
        ((TextView) vista.findViewById(R.id.tvGanador)).setText(ganador.nombre);
        ((TextView) vista.findViewById(R.id.tvContra)).setText("venció a " + perdedor.nombre);
        ((TextView) vista.findViewById(R.id.tvHpGanador)).setText(hpRestante + " / " + ganador.hpMaxima);
        ((TextView) vista.findViewById(R.id.tvTurnosGanador)).setText(String.valueOf(turnos));

        AlertDialog dialogo = new AlertDialog.Builder(this)
                .setView(vista)
                .setCancelable(true)
                .create();

        vista.findViewById(R.id.btnRevancha).setOnClickListener(v -> {
            dialogo.dismiss();
            iniciarBatalla();
        });
        // A y B se quedan elegidos; cada tarjeta tiene "Cambiar" para escoger otro
        vista.findViewById(R.id.btnElegirOtro).setOnClickListener(v -> {
            dialogo.dismiss();
            scrollRaiz.smoothScrollTo(0, 0);
        });
        vista.findViewById(R.id.btnMenu).setOnClickListener(v -> {
            dialogo.dismiss();
            finish();
        });

        dialogo.show();
        if (dialogo.getWindow() != null) {
            dialogo.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // Entrada con un pequeño rebote
        vista.setScaleX(0.8f);
        vista.setScaleY(0.8f);
        vista.setAlpha(0f);
        vista.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(300)
                .setInterpolator(new android.view.animation.OvershootInterpolator()).start();
    }
}
