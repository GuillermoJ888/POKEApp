package com.example.pokeapp.data;

/** Un Pokémon favorito del entrenador (se guarda en Firestore). */
public class Favorito {

    private final int id;
    private final String nombre;

    public Favorito(int id, String nombre) {
        this.id = id;
        this.nombre = nombre;
    }

    public int getId() { return id; }

    public String getNombre() { return nombre; }

    public String getSpriteUrl() {
        return "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/" + id + ".png";
    }
}
