package com.example.pokeapp.data;

public class NamedApiResource {

    private String name;
    private String url;

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    /** Saca el número final de la URL (".../pokemon-species/4/" -> 4). */
    public int getId() {
        if (url == null) return 0;

        String[] partes = url.split("/");
        try {
            return Integer.parseInt(partes[partes.length - 1]);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
