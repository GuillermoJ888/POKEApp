package com.example.pokeapp.battle;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tabla de efectividad de tipos: tipo del ataque contra tipo del defensor.
 * Solo se guardan los casos especiales (×2, ×0.5 y ×0); todo lo demás vale ×1.
 */
public final class TypeChart {

    private static final Map<String, Map<String, Float>> TABLA = new HashMap<>();

    static {
        //   atacante     súper eficaz (×2)                          poco eficaz (×0.5)                                          no afecta (×0)
        fila("normal",   "",                                        "rock,steel",                                               "ghost");
        fila("fire",     "grass,ice,bug,steel",                     "fire,water,rock,dragon",                                   "");
        fila("water",    "fire,ground,rock",                        "water,grass,dragon",                                       "");
        fila("electric", "water,flying",                            "electric,grass,dragon",                                    "ground");
        fila("grass",    "water,ground,rock",                       "fire,grass,poison,flying,bug,dragon,steel",                "");
        fila("ice",      "grass,ground,flying,dragon",              "fire,water,ice,steel",                                     "");
        fila("fighting", "normal,ice,rock,dark,steel",              "poison,flying,psychic,bug,fairy",                          "ghost");
        fila("poison",   "grass,fairy",                             "poison,ground,rock,ghost",                                 "steel");
        fila("ground",   "fire,electric,poison,rock,steel",         "grass,bug",                                                "flying");
        fila("flying",   "grass,fighting,bug",                      "electric,rock,steel",                                      "");
        fila("psychic",  "fighting,poison",                         "psychic,steel",                                            "dark");
        fila("bug",      "grass,psychic,dark",                      "fire,fighting,poison,flying,ghost,steel,fairy",            "");
        fila("rock",     "fire,ice,flying,bug",                     "fighting,ground,steel",                                    "");
        fila("ghost",    "psychic,ghost",                           "dark",                                                     "normal");
        fila("dragon",   "dragon",                                  "steel",                                                    "fairy");
        fila("dark",     "psychic,ghost",                           "fighting,dark,fairy",                                      "");
        fila("steel",    "ice,rock,fairy",                          "fire,water,electric,steel",                                "");
        fila("fairy",    "fighting,dragon,dark",                    "fire,poison,steel",                                        "");
    }

    private TypeChart() { }

    private static void fila(String atacante, String dobles, String mitades, String inmunes) {
        Map<String, Float> multiplicadores = new HashMap<>();
        poner(multiplicadores, dobles, 2f);
        poner(multiplicadores, mitades, 0.5f);
        poner(multiplicadores, inmunes, 0f);
        TABLA.put(atacante, multiplicadores);
    }

    private static void poner(Map<String, Float> multiplicadores, String tipos, float valor) {
        if (tipos.isEmpty()) return;
        for (String tipo : tipos.split(",")) multiplicadores.put(tipo, valor);
    }

    /** Multiplicador de un tipo de ataque contra un solo tipo. */
    public static float efectividad(String tipoAtaque, String tipoDefensor) {
        Map<String, Float> multiplicadores = TABLA.get(tipoAtaque);
        if (multiplicadores == null) return 1f;

        Float valor = multiplicadores.get(tipoDefensor);
        return valor == null ? 1f : valor;
    }

    /** Multiplicador contra un Pokémon de uno o dos tipos (se multiplican: ×2 · ×2 = ×4). */
    public static float efectividad(String tipoAtaque, List<String> tiposDefensor) {
        float total = 1f;
        for (String tipo : tiposDefensor) total *= efectividad(tipoAtaque, tipo);
        return total;
    }
}
