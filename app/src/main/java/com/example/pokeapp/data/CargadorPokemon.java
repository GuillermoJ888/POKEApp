package com.example.pokeapp.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Carga varios Pokémon de la PokéAPI a la vez y genera IDs al azar (normales o legendarios). */
public class CargadorPokemon {

    public static final int ID_MAXIMO = 1025;

    /** Legendarios y singulares usados por el jefe final de la Torre. */
    public static final List<Integer> LEGENDARIOS = Arrays.asList(
            144, 145, 146, 150, 151, 243, 244, 245, 249, 250, 251,
            377, 378, 379, 380, 381, 382, 383, 384, 385, 386,
            480, 481, 482, 483, 484, 485, 486, 487, 488, 491, 492, 493,
            638, 639, 640, 641, 642, 643, 644, 645, 646,
            716, 717, 718, 785, 786, 787, 788, 789, 790, 791, 792, 800,
            888, 889, 890, 891, 892, 894, 895, 896, 897, 898,
            1001, 1002, 1003, 1004, 1007, 1008, 1014, 1015, 1016, 1017, 1024);

    public interface Listo {
        /** Lista en el mismo orden que los IDs, o null si alguno falló. */
        void alTerminar(List<Pokemon> pokemon);
    }

    /** n IDs distintos al azar: solo legendarios, o solo Pokémon que NO son legendarios. */
    public static List<Integer> idsAleatorios(int n, boolean legendarios, Random random) {
        Set<Integer> elegidos = new HashSet<>();
        List<Integer> resultado = new ArrayList<>();
        Set<Integer> legendariosSet = new HashSet<>(LEGENDARIOS);

        while (resultado.size() < n) {
            int id = legendarios
                    ? LEGENDARIOS.get(random.nextInt(LEGENDARIOS.size()))
                    : 1 + random.nextInt(ID_MAXIMO);
            if (!legendarios && legendariosSet.contains(id)) continue;
            if (elegidos.add(id)) resultado.add(id);
        }
        return resultado;
    }

    /** Pide todos los Pokémon en paralelo y avisa una sola vez cuando terminan. */
    public static void cargar(List<Integer> ids, Listo listo) {
        Pokemon[] resultado = new Pokemon[ids.size()];
        int[] pendientes = {ids.size()};
        boolean[] fallo = {false};

        if (ids.isEmpty()) {
            listo.alTerminar(new ArrayList<>());
            return;
        }

        for (int i = 0; i < ids.size(); i++) {
            final int posicion = i;
            ApiClient.getService().getPokemon(String.valueOf(ids.get(i))).enqueue(new Callback<Pokemon>() {
                @Override
                public void onResponse(Call<Pokemon> call, Response<Pokemon> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        resultado[posicion] = response.body();
                    } else {
                        fallo[0] = true;
                    }
                    terminarUno();
                }

                @Override
                public void onFailure(Call<Pokemon> call, Throwable t) {
                    fallo[0] = true;
                    terminarUno();
                }

                private void terminarUno() {
                    pendientes[0]--;
                    if (pendientes[0] == 0) {
                        listo.alTerminar(fallo[0] ? null : new ArrayList<>(Arrays.asList(resultado)));
                    }
                }
            });
        }
    }
}
