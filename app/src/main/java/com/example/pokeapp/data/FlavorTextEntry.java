package com.example.pokeapp.data;

import com.google.gson.annotations.SerializedName;

public class FlavorTextEntry {

    @SerializedName("flavor_text")
    private String flavorText;

    private NamedApiResource language;

    public String getFlavorText() { return flavorText; }
    public NamedApiResource getLanguage() { return language; }
}
