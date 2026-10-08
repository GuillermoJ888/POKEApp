package com.example.pokeapp.data;

import com.google.gson.annotations.SerializedName;

public class Sprites {

    @SerializedName("front_default")
    private String frontDefault;

    @SerializedName("front_shiny")
    private String frontShiny;

    private Other other;

    public String getFrontDefault() {
        return frontDefault;
    }

    public String getFrontShiny() {
        return frontShiny;
    }

    /** Arte oficial (grande) y, si no existe, el sprite pequeño. */
    public String getImagenNormal() {
        if (other != null && other.officialArtwork != null
                && other.officialArtwork.frontDefault != null) {
            return other.officialArtwork.frontDefault;
        }
        return frontDefault;
    }

    /** Versión shiny; si no hay, regresa la normal. */
    public String getImagenShiny() {
        if (other != null && other.officialArtwork != null
                && other.officialArtwork.frontShiny != null) {
            return other.officialArtwork.frontShiny;
        }
        return frontShiny != null ? frontShiny : getImagenNormal();
    }

    static class Other {
        @SerializedName("official-artwork")
        Artwork officialArtwork;
    }

    static class Artwork {
        @SerializedName("front_default")
        String frontDefault;

        @SerializedName("front_shiny")
        String frontShiny;
    }
}
