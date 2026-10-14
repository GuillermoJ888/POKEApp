package com.example.pokeapp.data;

import com.example.pokeapp.battle.BattlePokemon;
import com.example.pokeapp.battle.Movimiento;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Asigna a cada Pokémon sus 4 MEJORES ataques REALES de la PokéAPI (/move/{nombre}).
 *
 * Se revisan TODOS los ataques que la PokéAPI dice que ese Pokémon puede aprender y se eligen
 * los que más daño hacen (potencia × precisión): los 2 más fuertes de su mismo tipo y los
 * 2 más fuertes de otros tipos (distintos entre sí, para tener con qué pegarle a más rivales).
 * No hay azar: el mismo Pokémon recibe siempre los mismos 4 ataques, así que en la Torre
 * los conserva de un nivel a otro. Los detalles se guardan en memoria (y en el celular)
 * para no pedirlos dos veces. Si algo falla, se queda con ataques genéricos de su tipo.
 */
public class MovimientoManager {

    private static final int ATAQUES = 4;

    /**
     * Ataques que no se eligen aunque tengan mucha potencia, porque en los juegos tienen una condición
     * que esta batalla no simula: debilitan a quien los usa, solo sirven con el rival dormido,
     * solo en el primer turno, etc.
     */
    private static final Set<String> NO_VALEN = new HashSet<>(Arrays.asList(
            "explosion", "self-destruct", "misty-explosion", "mind-blown", "steel-beam", "chloroblast",
            "final-gambit", "memento", "dream-eater", "snore", "sleep-talk", "focus-punch", "last-resort",
            "belch", "fake-out", "first-impression", "synchronoise", "burn-up", "double-shock",
            "future-sight", "doom-desire", "spit-up", "natural-gift", "fling", "hold-back", "false-swipe"));

    /** Ataques ya consultados (null = es de estado o no existe). */
    private static final Map<String, Movimiento> CACHE = new HashMap<>();
    private static final Set<String> SIN_DANIO = new HashSet<>();

    /** Al cambiar el idioma se olvidan los ataques guardados, para pedirlos con su nuevo nombre. */
    public static void olvidarNombres() {
        CACHE.clear();
    }

    /** Asigna los ataques y avisa una sola vez cuando terminó (aunque haya fallas). */
    public static void asignar(List<BattlePokemon> pokemon, Runnable listo) {
        // 1) Candidatos de cada Pokémon
        Map<BattlePokemon, List<String>> candidatos = new HashMap<>();
        Set<String> porPedir = new LinkedHashSet<>();

        for (BattlePokemon p : pokemon) {
            List<String> elegidos = new ArrayList<>();
            for (String nombre : p.nombresAtaques) {
                if (!NO_VALEN.contains(nombre)) elegidos.add(nombre);
            }
            candidatos.put(p, elegidos);
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

    /** Daño esperado de un ataque: su potencia por la probabilidad de acertar. */
    private static double fuerza(Movimiento m) {
        return m.potencia * (m.precision > 0 ? m.precision / 100.0 : 1.0);
    }

    /** 3) A cada Pokémon: los 2 más fuertes de su tipo + los 2 más fuertes de otros tipos, hasta 4. */
    private static void repartir(Map<BattlePokemon, List<String>> candidatos) {
        // Del más fuerte al más débil; si empatan, por nombre (para que siempre salga lo mismo)
        Comparator<Movimiento> masFuerte = Comparator.comparingDouble(MovimientoManager::fuerza).reversed()
                .thenComparing(m -> m.nombre);

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
            mismoTipo.sort(masFuerte);
            otros.sort(masFuerte);

            // De los otros tipos, solo el más fuerte de cada tipo (no dos ataques casi iguales)
            List<Movimiento> otrosVariados = new ArrayList<>();
            for (Movimiento m : otros) {
                if (!tieneTipo(otrosVariados, m.tipo)) otrosVariados.add(m);
            }

            List<Movimiento> elegidos = new ArrayList<>();
            agregarHasta(elegidos, mismoTipo, 2);
            agregarHasta(elegidos, otrosVariados, ATAQUES);
            agregarHasta(elegidos, mismoTipo, ATAQUES);
            agregarHasta(elegidos, otros, ATAQUES);

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
