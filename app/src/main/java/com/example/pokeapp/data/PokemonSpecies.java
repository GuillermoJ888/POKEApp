package com.example.pokeapp.data;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class PokemonSpecies {

    @SerializedName("flavor_text_entries")
    private List<FlavorTextEntry> flavorTextEntries;

    @SerializedName("evolution_chain")
    private NamedApiResource evolutionChain;

    /** Id de la cadena evolutiva (0 si no hay). */
    public int getEvolutionChainId() {
        return evolutionChain == null ? 0 : evolutionChain.getId();
    }

    public List<FlavorTextEntry> getFlavorTextEntries() {
        return flavorTextEntries;
    }

    /** Busca la primera descripción en español; si no hay, usa inglés. */
    public String obtenerDescripcion() {

        if (flavorTextEntries == null) return "";

        for (FlavorTextEntry entry : flavorTextEntries) {
            if (entry.getLanguage() != null
                    && "es".equals(entry.getLanguage().getName())) {
                return limpiar(entry.getFlavorText());
            }
        }

        for (FlavorTextEntry entry : flavorTextEntries) {
            if (entry.getLanguage() != null
                    && "en".equals(entry.getLanguage().getName())) {
                return limpiar(entry.getFlavorText());
            }
        }

        return "";
    }

    private String limpiar(String texto) {
        if (texto == null) return "";
        return texto.replace("\n", " ").replace("\f", " ").trim();
    }
}
