package com.example.pokeapp.data;

/** Pokémon resumido para la lista de la Pokédex. */
public class PokemonMini {

    private final int id;
    private final String nombre;

    public PokemonMini(int id, String nombre) {
        this.id = id;
        this.nombre = nombre;
    }

    public int getId() { return id; }
    public String getNombre() { return nombre; }

    public String getSpriteUrl() {
        return "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/" + id + ".png";
    }
}
