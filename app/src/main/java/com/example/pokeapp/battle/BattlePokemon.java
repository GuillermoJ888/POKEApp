package com.example.pokeapp.battle;

import com.example.pokeapp.data.ListaPokemon;
import com.example.pokeapp.data.Pokemon;
import com.example.pokeapp.data.PokemonStat;
import com.example.pokeapp.data.TypeColors;
import com.example.pokeapp.data.TypeSlot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Un Pokémon dentro de una batalla: sus stats de combate y su HP actual.
 *
 * Los stats son los reales de la PokéAPI (base_stat), sin conversiones:
 * Pikachu pelea con HP 35, Ataque 55, Defensa 40 y Velocidad 90.
 */
public class BattlePokemon {

    public final int id;
    public final String nombre;
    public final String imagen;
    public final List<String> tipos;

    public final int hpMaxima, ataque, defensa, ataqueEspecial, defensaEspecial, velocidad;

    private int hpActual;

    public BattlePokemon(Pokemon pokemon) {
        this.id = pokemon.getId();
        this.nombre = ListaPokemon.nombreBonito(pokemon.getName());
        this.imagen = pokemon.getSprites() != null ? pokemon.getSprites().getImagenNormal() : null;

        List<String> listaTipos = new ArrayList<>();
        if (pokemon.getTypes() != null) {
            for (TypeSlot slot : pokemon.getTypes()) {
                if (slot.getType() != null) listaTipos.add(slot.getType().getName());
            }
        }
        if (listaTipos.isEmpty()) listaTipos.add("normal");
        this.tipos = Collections.unmodifiableList(listaTipos);

        int hp = 50, atk = 50, def = 50, atkEsp = 50, defEsp = 50, vel = 50;

        if (pokemon.getStats() != null) {
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
        }

        this.hpMaxima = hp;
        this.ataque = atk;
        this.defensa = def;
        this.ataqueEspecial = atkEsp;
        this.defensaEspecial = defEsp;
        this.velocidad = vel;
        this.hpActual = hpMaxima;
    }

    public int getHpActual() {
        return hpActual;
    }

    public boolean estaDerrotado() {
        return hpActual <= 0;
    }

    /** Lo usa el motor al aplicar un golpe; el HP nunca baja de 0. */
    void recibirDanio(int danio) {
        hpActual = Math.max(0, hpActual - danio);
    }

    /** Regresa al HP máximo (por ejemplo, entre niveles de la Torre). */
    public void curar() {
        hpActual = hpMaxima;
    }

    /** "Fuego · Volador" */
    public String tiposTexto() {
        StringBuilder sb = new StringBuilder();
        for (String tipo : tipos) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(TypeColors.traducir(tipo));
        }
        return sb.toString();
    }
}
