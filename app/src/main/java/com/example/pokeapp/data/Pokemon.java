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

    public int getId() { return id; }
    public String getName() { return name; }
    public int getHeight() { return height; }
    public int getWeight() { return weight; }
    public Sprites getSprites() { return sprites; }
    public List<PokemonStat> getStats() { return stats; }
    public List<TypeSlot> getTypes() { return types; }
    public List<AbilitySlot> getAbilities() { return abilities; }
}
