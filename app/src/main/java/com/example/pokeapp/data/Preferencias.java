package com.example.pokeapp.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import java.util.Locale;

/**
 * Ajustes del módulo Configuración. Se guardan SOLO en el celular (SharedPreferences),
 * porque son preferencias del dispositivo y no hace falta subirlas a Firebase.
 */
public class Preferencias {

    private static final String PREFS = "poke_app_configuracion";
    private static final String SONIDO = "sonido";
    private static final String VIBRACION = "vibracion";
    private static final String TEMA = "tema";

    public static final String TEMA_CLARO = "claro";
    public static final String TEMA_OSCURO = "oscuro";
    public static final String TEMA_POKEDEX = "pokedex";
    public static final String TEMA_SISTEMA = "sistema";

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Efectos de batalla y cry de los Pokémon (activado por defecto). */
    public static boolean sonido(Context context) {
        return prefs(context).getBoolean(SONIDO, true);
    }

    public static void setSonido(Context context, boolean activo) {
        prefs(context).edit().putBoolean(SONIDO, activo).apply();
    }

    /** Vibrar en golpes y capturas (activado por defecto). */
    public static boolean vibracion(Context context) {
        return prefs(context).getBoolean(VIBRACION, true);
    }

    public static void setVibracion(Context context, boolean activa) {
        prefs(context).edit().putBoolean(VIBRACION, activa).apply();
    }

    /** Tema de la app: claro (por defecto), oscuro, Pokédex o el que tenga el celular. */
    public static String tema(Context context) {
        return prefs(context).getString(TEMA, TEMA_CLARO);
    }

    public static void setTema(Context context, String tema) {
        prefs(context).edit().putString(TEMA, tema).apply();
        aplicarTema(context);
    }

    /** Aplica el tema guardado (se llama al abrir la app y al cambiarlo). */
    public static void aplicarTema(Context context) {
        String tema = tema(context);
        int modo = TEMA_OSCURO.equals(tema) ? AppCompatDelegate.MODE_NIGHT_YES
                : TEMA_SISTEMA.equals(tema) ? AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                : AppCompatDelegate.MODE_NIGHT_NO;
        AppCompatDelegate.setDefaultNightMode(modo);
    }

    /** true si la app está en inglés (elegido en Configuración o, si no se ha elegido, el del celular). */
    public static boolean esIngles() {
        LocaleListCompat elegido = AppCompatDelegate.getApplicationLocales();
        Locale locale = !elegido.isEmpty() && elegido.get(0) != null ? elegido.get(0) : Locale.getDefault();
        return "en".equals(locale.getLanguage());
    }

    /** Cambia el idioma de toda la app ("es" o "en"); Android lo recuerda. */
    public static void setIdioma(String codigo) {
        MovimientoManager.olvidarNombres();
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(codigo));
    }
}
