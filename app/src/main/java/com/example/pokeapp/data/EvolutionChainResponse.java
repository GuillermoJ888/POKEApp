package com.example.pokeapp.data;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class EvolutionChainResponse {

    private ChainLink chain;

    public ChainLink getChain() { return chain; }

    /** Un eslabón de la cadena: una especie y a qué evoluciona. */
    public static class ChainLink {
        private NamedApiResource species;

        @SerializedName("evolves_to")
        private List<ChainLink> evolvesTo;

        public NamedApiResource getSpecies() { return species; }
        public List<ChainLink> getEvolvesTo() { return evolvesTo; }
    }
}
