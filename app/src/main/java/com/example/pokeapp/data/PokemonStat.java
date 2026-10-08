package com.example.pokeapp.data;

import com.google.gson.annotations.SerializedName;

public class PokemonStat {

    @SerializedName("base_stat")
    private int baseStat;

    private Stat stat;

    public int getBaseStat() { return baseStat; }
    public Stat getStat() { return stat; }
}
