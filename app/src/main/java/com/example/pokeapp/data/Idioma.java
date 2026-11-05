package com.example.pokeapp.data;

/**
 * Textos que se arman en el código (con nombres, puntos, etc.): se escriben en los dos idiomas
 * juntos y se usa el que tenga la app. Los textos fijos de las pantallas siguen en res/values.
 */
public final class Idioma {

    private Idioma() { }

    public static String t(String espanol, String ingles) {
        return Preferencias.esIngles() ? ingles : espanol;
    }
}
