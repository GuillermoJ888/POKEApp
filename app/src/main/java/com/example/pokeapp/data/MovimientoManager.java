package com.example.pokeapp.data;

import com.example.pokeapp.battle.BattlePokemon;
import com.example.pokeapp.battle.Movimiento;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Asigna a cada Pokémon 4 ataques REALES de la PokéAPI (/move/{nombre}).
 *
 * De todos los ataques que puede aprender se revisan hasta 12 al azar y se quedan
 * los que hacen daño: primero 2 de su mismo tipo (los más fuertes) y luego otros
 * para tener variedad. Los detalles se guardan en memoria para no pedirlos dos veces.
 * Si algo falla, el Pokémon se queda con sus ataques genéricos de su tipo.
 */
public class MovimientoManager {

    private static final int CANDIDATOS = 12;
    private static final int ATAQUES = 4;

    /** Ataques ya consultados (null = es de estado o no existe). */
    private static final Map<String, Movimiento> CACHE = new HashMap<>();
    private static final Set<String> SIN_DANIO = new HashSet<>();
    private static final Random random = new Random();

    /** Asigna los ataques y avisa una sola vez cuando terminó (aunque haya fallas). */
    public static void asignar(List<BattlePokemon> pokemon, Runnable listo) {
        // 1) Candidatos de cada Pokémon
        Map<BattlePokemon, List<String>> candidatos = new HashMap<>();
        Set<String> porPedir = new LinkedHashSet<>();

        for (BattlePokemon p : pokemon) {
            List<String> todos = new ArrayList<>(p.nombresAtaques);
            Collections.shuffle(todos, random);
            List<String> elegidos = todos.subList(0, Math.min(CANDIDATOS, todos.size()));
            candidatos.put(p, new ArrayList<>(elegidos));
            for (String nombre : elegidos) {
                if (!CACHE.containsKey(nombre) && !SIN_DANIO.contains(nombre)) porPedir.add(nombre);
            }
        }

        if (porPedir.isEmpty()) {
            repartir(candidatos);
            listo.run();
            return;
        }

        // 2) Pedir a la PokéAPI los que no están en memoria (en paralelo)
        int[] pendientes = {porPedir.size()};
        for (String nombre : porPedir) {
            ApiClient.getService().getMove(nombre).enqueue(new Callback<MoveDetalle>() {
                @Override
                public void onResponse(Call<MoveDetalle> call, Response<MoveDetalle> response) {
                    MoveDetalle d = response.isSuccessful() ? response.body() : null;
                    if (d != null && d.haceDanio()) {
                        CACHE.put(nombre, new Movimiento(d.getNombreEspanol(), d.getTipo(), d.getPower(),
                                d.getAccuracy() != null ? d.getAccuracy() : 0));
                    } else if (d != null) {
                        SIN_DANIO.add(nombre);
                    }
                    terminarUno();
                }

                @Override
                public void onFailure(Call<MoveDetalle> call, Throwable t) {
                    terminarUno();
                }

                private void terminarUno() {
                    pendientes[0]--;
                    if (pendientes[0] == 0) {
                        repartir(candidatos);
                        listo.run();
                    }
                }
            });
        }
    }

    /** 3) A cada Pokémon: 2 de su tipo (los más fuertes) + otros para variedad, hasta 4. */
    private static void repartir(Map<BattlePokemon, List<String>> candidatos) {
        for (Map.Entry<BattlePokemon, List<String>> e : candidatos.entrySet()) {
            BattlePokemon p = e.getKey();
            List<Movimiento> mismoTipo = new ArrayList<>();
            List<Movimiento> otros = new ArrayList<>();

            for (String nombre : e.getValue()) {
                Movimiento m = CACHE.get(nombre);
                if (m == null) continue;
                if (p.tipos.contains(m.tipo)) mismoTipo.add(m);
                else otros.add(m);
            }
            mismoTipo.sort(Comparator.comparingInt((Movimiento m) -> m.potencia).reversed());

            List<Movimiento> elegidos = new ArrayList<>();
            agregarHasta(elegidos, mismoTipo, 2);
            agregarHasta(elegidos, otros, ATAQUES);
            agregarHasta(elegidos, mismoTipo, ATAQUES);

            // Si no alcanzó (falló internet o casi no tiene ataques de daño): genéricos de su tipo
            for (String tipo : p.tipos) {
                if (elegidos.size() < ATAQUES && !tieneTipo(elegidos, tipo)) elegidos.add(Movimiento.basico(tipo));
            }
            if (elegidos.size() < ATAQUES && !tieneTipo(elegidos, "normal")) {
                elegidos.add(new Movimiento("Placaje", "normal", 40, 100));
            }

            p.setMovimientos(elegidos);
        }
    }

    private static void agregarHasta(List<Movimiento> destino, List<Movimiento> origen, int limite) {
        for (Movimiento m : origen) {
            if (destino.size() >= limite) return;
            boolean repetido = false;
            for (Movimiento d : destino) if (d.nombre.equals(m.nombre)) repetido = true;
            if (!repetido) destino.add(m);
        }
    }

    private static boolean tieneTipo(List<Movimiento> lista, String tipo) {
        for (Movimiento m : lista) if (tipo.equals(m.tipo)) return true;
        return false;
    }
}
