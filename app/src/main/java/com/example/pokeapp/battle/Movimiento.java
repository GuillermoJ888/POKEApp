package com.example.pokeapp.battle;

import com.example.pokeapp.data.TypeColors;

/** Un ataque de batalla: nombre, tipo, potencia y precisión. */
public class Movimiento {

    /** Se usa cuando ningún ataque del Pokémon le afecta al rival (evita batallas eternas). */
    public static final Movimiento FORCEJEO = new Movimiento("Forcejeo", null, 50, 0);

    public final String nombre;
    public final String tipo;        // "electric", "fire"… (null = sin tipo)
    public final int potencia;
    public final int precision;      // 1–100; 0 = nunca falla

    public Movimiento(String nombre, String tipo, int potencia, int precision) {
        this.nombre = nombre;
        this.tipo = tipo;
        this.potencia = potencia;
        this.precision = precision;
    }

    /** Ataque genérico de un tipo, por si no se pudieron cargar los reales de la PokéAPI. */
    public static Movimiento basico(String tipo) {
        return new Movimiento("Ataque " + TypeColors.traducir(tipo), tipo, 60, 100);
    }

    public String tipoTexto() {
        return tipo != null ? TypeColors.traducir(tipo) : "Sin tipo";
    }

    /** "Eléctrico · Pot. 90 · 100%" */
    public String detalle() {
        return tipoTexto() + " · Pot. " + potencia + " · " + (precision > 0 ? precision + "%" : "—");
    }
}
