package com.example.pokeapp.data;

import java.util.List;

public class Pokemon {

    private int id;
    private String name;
    private int height;
    private int weight;
    private Sprites sprites;
    private List<PokemonStat> stats;
    private List<TypeSlot> types;
    private List<AbilitySlot> abilities;
    private List<MoveSlot> moves;

    public int getId() { return id; }
    public String getName() { return name; }
    public int getHeight() { return height; }
    public int getWeight() { return weight; }
    public Sprites getSprites() { return sprites; }
    public List<PokemonStat> getStats() { return stats; }
    public List<TypeSlot> getTypes() { return types; }
    public List<AbilitySlot> getAbilities() { return abilities; }
    public List<MoveSlot> getMoves() { return moves; }

    /**
     * Un ataque que el Pokémon puede aprender. Solo se guarda su nombre/URL
     * (los detalles de cada versión del juego se ignoran para que pese poco).
     */
    public static class MoveSlot {
        private NamedApiResource move;

        public NamedApiResource getMove() { return move; }
    }
}
