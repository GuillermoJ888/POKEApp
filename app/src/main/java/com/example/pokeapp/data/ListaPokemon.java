package com.example.pokeapp.data;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Lista de todos los nombres de Pokémon, descargada una sola vez y guardada en
 * memoria, para sugerir mientras se escribe en cualquier buscador de la app.
 */
public class ListaPokemon {

    private static final int ID_MAXIMO_BASE = 10000;   // arriba de esto son formas alternas (mega, etc.)

    private static final List<PokemonMini> todos = new ArrayList<>();
    private static final List<String> normalizados = new ArrayList<>();
    private static boolean cargando = false;

    /** Pide la lista a la PokéAPI si todavía no se tiene. */
    public static void cargar() {
        if (!todos.isEmpty() || cargando) return;
        cargando = true;

        ApiClient.getService().listarPokemon(2000, 0).enqueue(new Callback<PokemonListResponse>() {
            @Override
            public void onResponse(Call<PokemonListResponse> call, Response<PokemonListResponse> response) {
                cargando = false;

                if (!response.isSuccessful() || response.body() == null
                        || response.body().getResults() == null) return;

                for (NamedApiResource r : response.body().getResults()) {
                    int id = r.getId();
                    if (id <= 0 || id >= ID_MAXIMO_BASE) continue;

                    todos.add(new PokemonMini(id, nombreBonito(r.getName())));
                    normalizados.add(normalizar(r.getName()));
                }
            }

            @Override
            public void onFailure(Call<PokemonListResponse> call, Throwable t) {
                // Sin lista no hay sugerencias, pero se puede buscar por nombre exacto o número
                cargando = false;
            }
        });
    }

    /** Pokémon cuyo nombre empieza con (o contiene) lo escrito; o cuyo número empieza igual. */
    public static List<PokemonMini> filtrar(String escrito, int maximo) {
        List<PokemonMini> resultado = new ArrayList<>();
        String q = normalizar(escrito);

        if (q.isEmpty() || todos.isEmpty()) return resultado;

        if (q.matches("\\d+")) {
            for (PokemonMini p : todos) {
                if (String.valueOf(p.getId()).startsWith(q)) {
                    resultado.add(p);
                    if (resultado.size() == maximo) break;
                }
            }
            return resultado;
        }

        // 1) los que empiezan igual
        for (int i = 0; i < normalizados.size() && resultado.size() < maximo; i++) {
            if (normalizados.get(i).startsWith(q)) resultado.add(todos.get(i));
        }

        // 2) los que lo contienen en otra parte del nombre
        for (int i = 0; i < normalizados.size() && resultado.size() < maximo; i++) {
            PokemonMini p = todos.get(i);
            if (!resultado.contains(p) && normalizados.get(i).contains(q)) resultado.add(p);
        }

        return resultado;
    }

    /**
     * Convierte lo escrito en algo que la PokéAPI entienda: el número tal cual,
     * el id del nombre exacto o, si está incompleto ("eev"), el de la primera coincidencia.
     */
    public static String resolver(String escrito) {
        String limpio = escrito.trim().toLowerCase(Locale.ROOT);
        if (limpio.matches("\\d+")) return limpio;

        String q = normalizar(limpio);
        for (int i = 0; i < normalizados.size(); i++) {
            if (normalizados.get(i).equals(q)) return String.valueOf(todos.get(i).getId());
        }

        List<PokemonMini> coincidencias = filtrar(limpio, 1);
        return coincidencias.isEmpty() ? limpio : String.valueOf(coincidencias.get(0).getId());
    }

    /** Minúsculas, sin acentos ni guiones/espacios: "Mr. Mime" -> "mrmime". */
    public static String normalizar(String texto) {
        String sinAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinAcentos.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** "mr-mime" -> "Mr Mime", "pikachu" -> "Pikachu". */
    public static String nombreBonito(String nombreApi) {
        if (nombreApi == null || nombreApi.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (String parte : nombreApi.split("-")) {
            if (parte.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(parte.substring(0, 1).toUpperCase(Locale.ROOT)).append(parte.substring(1));
        }
        return sb.toString();
    }
}
