package com.example.pokeapp;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.example.pokeapp.data.ApiClient;
import com.example.pokeapp.data.EvolutionChainResponse;
import com.example.pokeapp.data.EvolutionChainResponse.ChainLink;
import com.example.pokeapp.data.FavoritosManager;
import com.example.pokeapp.data.NamedApiResource;
import com.example.pokeapp.data.Pokemon;
import com.example.pokeapp.data.PokemonListResponse;
import com.example.pokeapp.data.PokemonMini;
import com.example.pokeapp.data.PokemonSpecies;
import com.example.pokeapp.data.PokemonStat;
import com.example.pokeapp.data.TypeColors;
import com.example.pokeapp.data.TypeSlot;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PokedexActivity extends AppCompatActivity {

    public static final String EXTRA_BUSQUEDA_INICIAL = "extra_busqueda_inicial";

    private static final int TAM_PAGINA = 5;
    private static final int MAX_SUGERENCIAS = 6;
    private static final int ID_MAXIMO_BASE = 10000;   // arriba de esto son formas alternas (mega, etc.)
    private static final String URL_ARTE =
            "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/";

    private ScrollView scrollPokedex;
    private EditText etBuscar;
    private TextView tvMensaje, tvNumero, tvNombre, tvDescripcion;
    private TextView tvAltura, tvPeso, tvHabilidades, tvEvolucionVacio;
    private LinearLayout cardResultado, layoutTipos, layoutStats, layoutEvolucion;
    private View scrollEvolucion;
    private ImageButton btnFavorito, btnCry;
    private ProgressBar progressBuscar;
    private ViewPager2 pagerPokemon;
    private RecyclerView recyclerLista, recyclerSugerencias;

    private ImagenPokemonAdapter imagenAdapter;
    private PokemonMiniAdapter miniAdapter;
    private SugerenciaAdapter sugerenciaAdapter;

    // Todos los nombres (para sugerir mientras se escribe) y su versión normalizada
    private final List<PokemonMini> todosLosPokemon = new ArrayList<>();
    private final List<String> todosNormalizados = new ArrayList<>();
    private boolean ignorarCambioTexto = false;

    private MediaPlayer mediaPlayer;
    private Pokemon pokemonActual;

    private int offsetLista = 0;
    private int tokenCarga = 0;          // evita que una respuesta vieja pise a una nueva
    private boolean errorFavoritoMostrado = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pokedex);

        enlazarVistas();
        configurarImagenes();
        configurarLista();
        configurarSugerencias();
        cargarTodosLosNombres();

        findViewById(R.id.btnRegresar).setOnClickListener(v -> finish());
        findViewById(R.id.btnBuscar).setOnClickListener(v -> buscar());
        findViewById(R.id.btnVerMas).setOnClickListener(v -> cargarSiguientesPokemon(true));
        btnFavorito.setOnClickListener(v -> alternarFavorito());
        btnCry.setOnClickListener(v -> reproducirCry());

        etBuscar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                buscar();
                return true;
            }
            return false;
        });

        cargarSiguientesPokemon(false);

        // Si venimos desde Favoritos abre ese Pokémon; si no, muestra el #1
        String busquedaInicial = getIntent().getStringExtra(EXTRA_BUSQUEDA_INICIAL);
        if (!TextUtils.isEmpty(busquedaInicial)) {
            ponerTexto(busquedaInicial);
            cargarPokemon(busquedaInicial);
        } else {
            cargarPokemon("1");
        }
    }

    private void enlazarVistas() {
        scrollPokedex = findViewById(R.id.scrollPokedex);
        etBuscar = findViewById(R.id.etBuscar);
        tvMensaje = findViewById(R.id.tvMensaje);
        progressBuscar = findViewById(R.id.progressBuscar);
        cardResultado = findViewById(R.id.cardResultado);
        tvNumero = findViewById(R.id.tvNumero);
        tvNombre = findViewById(R.id.tvNombre);
        tvDescripcion = findViewById(R.id.tvDescripcion);
        tvAltura = findViewById(R.id.tvAltura);
        tvPeso = findViewById(R.id.tvPeso);
        tvHabilidades = findViewById(R.id.tvHabilidades);
        layoutTipos = findViewById(R.id.layoutTipos);
        layoutStats = findViewById(R.id.layoutStats);
        layoutEvolucion = findViewById(R.id.layoutEvolucion);
        scrollEvolucion = findViewById(R.id.scrollEvolucion);
        tvEvolucionVacio = findViewById(R.id.tvEvolucionVacio);
        btnFavorito = findViewById(R.id.btnFavorito);
        btnCry = findViewById(R.id.btnCry);
        pagerPokemon = findViewById(R.id.pagerPokemon);
        recyclerLista = findViewById(R.id.recyclerLista);
        recyclerSugerencias = findViewById(R.id.recyclerSugerencias);
    }

    // ------------------------------------------------------------------
    // Imagen grande con deslizamiento: Normal <-> Shiny
    // ------------------------------------------------------------------

    private void configurarImagenes() {
        imagenAdapter = new ImagenPokemonAdapter();
        pagerPokemon.setAdapter(imagenAdapter);

        TabLayout tabs = findViewById(R.id.tabsPokemon);
        new TabLayoutMediator(tabs, pagerPokemon, (tab, posicion) ->
                tab.setText(posicion == 0 ? "Normal" : "✨ Shiny")
        ).attach();
    }

    // ------------------------------------------------------------------
    // Lista de Pokémon (empieza con los primeros 5)
    // ------------------------------------------------------------------

    private void configurarLista() {
        miniAdapter = new PokemonMiniAdapter(mini -> {
            ponerTexto(mini.getNombre());
            cargarPokemon(String.valueOf(mini.getId()));
        });

        recyclerLista.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        recyclerLista.setAdapter(miniAdapter);
    }

    private void cargarSiguientesPokemon(boolean desplazarAlFinal) {
        ApiClient.getService().listarPokemon(TAM_PAGINA, offsetLista)
                .enqueue(new Callback<PokemonListResponse>() {
                    @Override
                    public void onResponse(Call<PokemonListResponse> call, Response<PokemonListResponse> response) {
                        if (isFinishing() || isDestroyed()) return;

                        if (!response.isSuccessful() || response.body() == null
                                || response.body().getResults() == null) {
                            Toast.makeText(PokedexActivity.this,
                                    "No se pudo cargar la lista", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        List<PokemonMini> nuevos = new ArrayList<>();
                        for (NamedApiResource r : response.body().getResults()) {
                            nuevos.add(new PokemonMini(r.getId(), capitalizar(r.getName())));
                        }

                        offsetLista += TAM_PAGINA;
                        miniAdapter.agregar(nuevos);

                        if (pokemonActual != null) miniAdapter.seleccionar(pokemonActual.getId());

                        if (desplazarAlFinal) {
                            recyclerLista.smoothScrollToPosition(miniAdapter.getItemCount() - 1);
                        }
                    }

                    @Override
                    public void onFailure(Call<PokemonListResponse> call, Throwable t) {
                        if (isFinishing() || isDestroyed()) return;
                        Toast.makeText(PokedexActivity.this,
                                "Sin conexión: no se pudo cargar la lista", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ------------------------------------------------------------------
    // Buscador con sugerencias (nombres parecidos)
    // ------------------------------------------------------------------

    private void configurarSugerencias() {
        sugerenciaAdapter = new SugerenciaAdapter(pokemon -> {
            ponerTexto(pokemon.getNombre());
            ocultarSugerencias();
            ocultarTeclado();
            cargarPokemon(String.valueOf(pokemon.getId()));
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
                if (ignorarCambioTexto) return;

                List<PokemonMini> coincidencias = filtrar(s.toString());

                if (coincidencias.isEmpty()) {
                    ocultarSugerencias();
                } else {
                    sugerenciaAdapter.actualizar(coincidencias);
                    recyclerSugerencias.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    /** Pide una sola vez la lista completa de nombres y la guarda en memoria. */
    private void cargarTodosLosNombres() {
        ApiClient.getService().listarPokemon(2000, 0).enqueue(new Callback<PokemonListResponse>() {
            @Override
            public void onResponse(Call<PokemonListResponse> call, Response<PokemonListResponse> response) {
                if (!response.isSuccessful() || response.body() == null
                        || response.body().getResults() == null) return;

                for (NamedApiResource r : response.body().getResults()) {
                    int id = r.getId();

                    // Solo los Pokémon base (sin mega, cosplay, etc.)
                    if (id <= 0 || id >= ID_MAXIMO_BASE) continue;

                    todosLosPokemon.add(new PokemonMini(id, capitalizar(r.getName())));
                    todosNormalizados.add(normalizar(r.getName()));
                }
            }

            @Override
            public void onFailure(Call<PokemonListResponse> call, Throwable t) {
                // Sin lista no hay sugerencias, pero se puede seguir buscando por nombre exacto
            }
        });
    }

    /** Minúsculas, sin acentos ni guiones/espacios: "Mr. Mime" -> "mrmime". */
    private String normalizar(String texto) {
        String sinAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinAcentos.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** Pokémon cuyo nombre empieza con (o contiene) lo escrito; o cuyo número empieza igual. */
    private List<PokemonMini> filtrar(String escrito) {
        List<PokemonMini> resultado = new ArrayList<>();
        String q = normalizar(escrito);

        if (q.isEmpty() || todosLosPokemon.isEmpty()) return resultado;

        if (q.matches("\\d+")) {
            for (PokemonMini p : todosLosPokemon) {
                if (String.valueOf(p.getId()).startsWith(q)) {
                    resultado.add(p);
                    if (resultado.size() == MAX_SUGERENCIAS) break;
                }
            }
            return resultado;
        }

        // 1) los que empiezan igual
        for (int i = 0; i < todosNormalizados.size() && resultado.size() < MAX_SUGERENCIAS; i++) {
            if (todosNormalizados.get(i).startsWith(q)) resultado.add(todosLosPokemon.get(i));
        }

        // 2) los que lo contienen en otra parte del nombre
        for (int i = 0; i < todosNormalizados.size() && resultado.size() < MAX_SUGERENCIAS; i++) {
            PokemonMini p = todosLosPokemon.get(i);
            if (!resultado.contains(p) && todosNormalizados.get(i).contains(q)) resultado.add(p);
        }

        return resultado;
    }

    /**
     * Si escribiste un nombre incompleto ("eev") y le das Buscar,
     * abre la primera coincidencia en vez de marcar error.
     */
    private String resolverConsulta(String query) {
        if (query.matches("\\d+")) return query;

        List<PokemonMini> coincidencias = filtrar(query);
        if (coincidencias.isEmpty()) return query;

        String q = normalizar(query);
        for (int i = 0; i < todosNormalizados.size(); i++) {
            if (todosNormalizados.get(i).equals(q)) {
                return String.valueOf(todosLosPokemon.get(i).getId());
            }
        }

        return String.valueOf(coincidencias.get(0).getId());
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
    // Buscar / cargar un Pokémon
    // ------------------------------------------------------------------

    private void buscar() {
        String query = etBuscar.getText().toString().trim().toLowerCase(Locale.ROOT);

        if (TextUtils.isEmpty(query)) {
            Toast.makeText(this, "Escribe un nombre o número", Toast.LENGTH_SHORT).show();
            return;
        }

        ocultarSugerencias();
        ocultarTeclado();

        cargarPokemon(resolverConsulta(query));
    }

    private void cargarPokemon(String query) {
        final int token = ++tokenCarga;

        mostrarCargando(true);
        cardResultado.setVisibility(View.GONE);
        tvMensaje.setVisibility(View.GONE);

        ApiClient.getService().getPokemon(query).enqueue(new Callback<Pokemon>() {
            @Override
            public void onResponse(Call<Pokemon> call, Response<Pokemon> response) {
                if (token != tokenCarga || isFinishing() || isDestroyed()) return;

                if (!response.isSuccessful() || response.body() == null) {
                    mostrarCargando(false);
                    mostrarMensaje("No se encontró ningún Pokémon con ese nombre o número");
                    return;
                }

                pokemonActual = response.body();
                mostrarPokemon(pokemonActual);
                cargarEspecie(pokemonActual.getId(), token);
            }

            @Override
            public void onFailure(Call<Pokemon> call, Throwable t) {
                if (token != tokenCarga || isFinishing() || isDestroyed()) return;
                mostrarCargando(false);
                mostrarMensaje("Error de conexión. Revisa tu Internet e intenta de nuevo");
            }
        });
    }

    private void cargarEspecie(int id, int token) {
        ApiClient.getService().getPokemonSpecies(String.valueOf(id)).enqueue(new Callback<PokemonSpecies>() {
            @Override
            public void onResponse(Call<PokemonSpecies> call, Response<PokemonSpecies> response) {
                if (token != tokenCarga || isFinishing() || isDestroyed()) return;

                mostrarCargando(false);

                if (response.isSuccessful() && response.body() != null) {
                    tvDescripcion.setText(response.body().obtenerDescripcion());

                    int idCadena = response.body().getEvolutionChainId();
                    if (idCadena > 0) {
                        cargarEvolucion(idCadena, id, token);
                        return;
                    }
                } else {
                    tvDescripcion.setText("");
                }

                mostrarSinEvolucion("No hay datos de evolución");
            }

            @Override
            public void onFailure(Call<PokemonSpecies> call, Throwable t) {
                if (token != tokenCarga || isFinishing() || isDestroyed()) return;
                mostrarCargando(false);
                tvDescripcion.setText("");
                mostrarSinEvolucion("No se pudo cargar la evolución");
            }
        });
    }

    // ------------------------------------------------------------------
    // Línea evolutiva
    // ------------------------------------------------------------------

    private void cargarEvolucion(int idCadena, int idActual, int token) {
        ApiClient.getService().getEvolutionChain(String.valueOf(idCadena))
                .enqueue(new Callback<EvolutionChainResponse>() {
                    @Override
                    public void onResponse(Call<EvolutionChainResponse> call, Response<EvolutionChainResponse> response) {
                        if (token != tokenCarga || isFinishing() || isDestroyed()) return;

                        if (response.isSuccessful() && response.body() != null
                                && response.body().getChain() != null) {
                            pintarEvolucion(response.body().getChain(), idActual);
                        } else {
                            mostrarSinEvolucion("No se pudo cargar la evolución");
                        }
                    }

                    @Override
                    public void onFailure(Call<EvolutionChainResponse> call, Throwable t) {
                        if (token != tokenCarga || isFinishing() || isDestroyed()) return;
                        mostrarSinEvolucion("No se pudo cargar la evolución");
                    }
                });
    }

    private void mostrarSinEvolucion(String texto) {
        scrollEvolucion.setVisibility(View.GONE);
        tvEvolucionVacio.setText(texto);
        tvEvolucionVacio.setVisibility(View.VISIBLE);
    }

    /**
     * Ordena la cadena por etapas (columnas): etapa 1 -> etapa 2 -> etapa 3.
     * Si una etapa tiene varias evoluciones (ej. Eevee) se apilan en la misma columna.
     */
    private void pintarEvolucion(ChainLink raiz, int idActual) {
        List<List<ChainLink>> etapas = new ArrayList<>();
        List<ChainLink> actual = new ArrayList<>();
        actual.add(raiz);

        while (!actual.isEmpty()) {
            etapas.add(actual);

            List<ChainLink> siguiente = new ArrayList<>();
            for (ChainLink eslabon : actual) {
                if (eslabon.getEvolvesTo() != null) siguiente.addAll(eslabon.getEvolvesTo());
            }
            actual = siguiente;
        }

        if (etapas.size() == 1) {
            mostrarSinEvolucion("Este Pokémon no evoluciona");
            return;
        }

        layoutEvolucion.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (int i = 0; i < etapas.size(); i++) {

            if (i > 0) {
                TextView flecha = new TextView(this);
                flecha.setText("➜");
                flecha.setTextColor(0xFFFACC15);
                flecha.setTextSize(22);
                flecha.setPadding(8, 0, 8, 0);
                layoutEvolucion.addView(flecha);
            }

            LinearLayout columna = new LinearLayout(this);
            columna.setOrientation(LinearLayout.VERTICAL);

            for (ChainLink eslabon : etapas.get(i)) {
                columna.addView(crearNodoEvolucion(inflater, columna, eslabon, idActual));
            }

            layoutEvolucion.addView(columna);
        }

        tvEvolucionVacio.setVisibility(View.GONE);
        scrollEvolucion.setVisibility(View.VISIBLE);
    }

    private View crearNodoEvolucion(LayoutInflater inflater, LinearLayout padre, ChainLink eslabon, int idActual) {
        View nodo = inflater.inflate(R.layout.item_evolucion, padre, false);

        int id = eslabon.getSpecies().getId();

        ImageView img = nodo.findViewById(R.id.imgEvolucion);
        TextView tvNum = nodo.findViewById(R.id.tvNumeroEvolucion);
        TextView tvNom = nodo.findViewById(R.id.tvNombreEvolucion);

        Glide.with(this).load(URL_ARTE + id + ".png").into(img);
        tvNum.setText(String.format(Locale.ROOT, "#%03d", id));
        tvNom.setText(capitalizar(eslabon.getSpecies().getName()));

        nodo.setBackgroundResource(id == idActual ? R.drawable.bg_mini_selected : R.drawable.bg_mini_normal);

        nodo.setOnClickListener(v -> {
            ponerTexto(capitalizar(eslabon.getSpecies().getName()));
            cargarPokemon(String.valueOf(id));
            scrollPokedex.smoothScrollTo(0, 0);
        });

        return nodo;
    }

    // ------------------------------------------------------------------
    // Pintar datos
    // ------------------------------------------------------------------

    private void mostrarPokemon(Pokemon pokemon) {

        tvMensaje.setVisibility(View.GONE);
        cardResultado.setVisibility(View.VISIBLE);

        tvNumero.setText(String.format(Locale.ROOT, "#%03d", pokemon.getId()));
        tvNombre.setText(capitalizar(pokemon.getName()));
        tvDescripcion.setText("");

        tvAltura.setText(String.format(Locale.ROOT, "%.1f m", pokemon.getHeight() / 10f));
        tvPeso.setText(String.format(Locale.ROOT, "%.1f kg", pokemon.getWeight() / 10f));

        if (pokemon.getAbilities() != null && !pokemon.getAbilities().isEmpty()) {
            tvHabilidades.setText(capitalizar(
                    pokemon.getAbilities().get(0).getAbility().getName().replace("-", " ")
            ));
        } else {
            tvHabilidades.setText("--");
        }

        if (pokemon.getSprites() != null) {
            imagenAdapter.actualizar(
                    pokemon.getSprites().getImagenNormal(),
                    pokemon.getSprites().getImagenShiny());
            pagerPokemon.setCurrentItem(0, false);
        }

        tvEvolucionVacio.setText("Cargando...");
        tvEvolucionVacio.setVisibility(View.VISIBLE);
        scrollEvolucion.setVisibility(View.GONE);

        miniAdapter.seleccionar(pokemon.getId());
        actualizarBotonFavorito(pokemon.getId());
        pintarTipos(pokemon.getTypes());
        pintarStats(pokemon.getStats());
    }

    private void pintarTipos(List<TypeSlot> tipos) {
        layoutTipos.removeAllViews();

        if (tipos == null) return;

        for (TypeSlot slot : tipos) {
            String nombreTipo = slot.getType().getName();

            TextView chip = new TextView(this);
            chip.setText(TypeColors.traducir(nombreTipo));
            chip.setTextColor(0xFFFFFFFF);
            chip.setTextSize(12);
            chip.setPadding(32, 12, 32, 12);

            android.graphics.drawable.GradientDrawable fondo =
                    new android.graphics.drawable.GradientDrawable();
            fondo.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            fondo.setCornerRadius(40f);
            fondo.setColor(TypeColors.obtenerColor(nombreTipo));
            fondo.setStroke(3, 0xFFFFFFFF);
            chip.setBackground(fondo);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMarginEnd(10);
            chip.setLayoutParams(params);

            layoutTipos.addView(chip);
        }
    }

    private void pintarStats(List<PokemonStat> stats) {
        layoutStats.removeAllViews();

        if (stats == null) return;

        LayoutInflater inflater = LayoutInflater.from(this);

        for (PokemonStat stat : stats) {
            View fila = inflater.inflate(R.layout.item_stat_bar, layoutStats, false);

            TextView etiqueta = fila.findViewById(R.id.tvEtiquetaStat);
            ProgressBar barra = fila.findViewById(R.id.barraStat);
            TextView valor = fila.findViewById(R.id.tvValorStat);

            etiqueta.setText(etiquetaStat(stat.getStat().getName()));
            barra.setProgress(stat.getBaseStat());
            valor.setText(String.valueOf(stat.getBaseStat()));

            layoutStats.addView(fila);
        }
    }

    private String etiquetaStat(String nombre) {
        switch (nombre) {
            case "hp": return "HP";
            case "attack": return "Ataque";
            case "defense": return "Defensa";
            case "special-attack": return "At. Esp.";
            case "special-defense": return "Def. Esp.";
            case "speed": return "Velocidad";
            default: return nombre;
        }
    }

    // ------------------------------------------------------------------
    // Favoritos (Firestore)
    // ------------------------------------------------------------------

    private void actualizarBotonFavorito(int id) {
        FavoritosManager.esFavorito(id, esFavorito -> {
            if (isFinishing() || isDestroyed()) return;

            if (esFavorito == null) {
                pintarEstrella(false);
                avisarErrorFavoritos();
                return;
            }

            pintarEstrella(esFavorito);
        });
    }

    private void pintarEstrella(boolean esFavorito) {
        btnFavorito.setImageResource(
                esFavorito
                        ? android.R.drawable.btn_star_big_on
                        : android.R.drawable.btn_star_big_off
        );
    }

    private void avisarErrorFavoritos() {
        // Solo una vez por pantalla, para no llenar de avisos
        if (errorFavoritoMostrado) return;
        errorFavoritoMostrado = true;

        Toast.makeText(this, "Favoritos: " + FavoritosManager.ultimoError(), Toast.LENGTH_LONG).show();
    }

    private void alternarFavorito() {
        if (pokemonActual == null) return;

        btnFavorito.setEnabled(false);

        FavoritosManager.alternarFavorito(
                pokemonActual.getId(),
                capitalizar(pokemonActual.getName()),
                esFavorito -> {
                    btnFavorito.setEnabled(true);

                    if (esFavorito == null) {
                        Toast.makeText(this,
                                "No se pudo guardar el favorito: " + FavoritosManager.ultimoError(),
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    pintarEstrella(esFavorito);
                }
        );
    }

    // ------------------------------------------------------------------
    // Sonido, mensajes y utilidades
    // ------------------------------------------------------------------

    private void reproducirCry() {
        if (pokemonActual == null) return;

        String url = "https://raw.githubusercontent.com/PokeAPI/cries/main/cries/pokemon/latest/"
                + pokemonActual.getId() + ".ogg";

        liberarSonido();

        mediaPlayer = new MediaPlayer();

        try {
            mediaPlayer.setDataSource(url);
            mediaPlayer.setOnPreparedListener(MediaPlayer::start);
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Toast.makeText(this, "No se pudo reproducir el sonido", Toast.LENGTH_SHORT).show();
                return true;
            });
            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo reproducir el sonido", Toast.LENGTH_SHORT).show();
        }
    }

    private void liberarSonido() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    private void mostrarMensaje(String texto) {
        tvMensaje.setText(texto);
        tvMensaje.setVisibility(View.VISIBLE);
    }

    private void mostrarCargando(boolean mostrar) {
        progressBuscar.setVisibility(mostrar ? View.VISIBLE : View.GONE);
    }

    private String capitalizar(String texto) {
        if (texto == null || texto.isEmpty()) return "";
        return texto.substring(0, 1).toUpperCase(Locale.ROOT) + texto.substring(1);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        liberarSonido();
    }
}
