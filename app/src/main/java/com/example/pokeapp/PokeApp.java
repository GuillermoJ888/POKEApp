package com.example.pokeapp;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;

import androidx.annotation.NonNull;

import com.example.pokeapp.data.ApiClient;
import com.example.pokeapp.data.Preferencias;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Arranque de la app: aplica el tema guardado antes de abrir cualquier pantalla.
 *  - Claro / oscuro: lo resuelve Android (values y values-night).
 *  - Pokédex: a cada pantalla se le pone el estilo Theme.POKEApp.Pokedex antes de dibujarse.
 */
public class PokeApp extends Application implements Application.ActivityLifecycleCallbacks {

    /** Tema con el que se dibujó cada pantalla, para redibujarla si el usuario lo cambia. */
    private final Map<Activity, String> temaDibujado = new WeakHashMap<>();

    @Override
    public void onCreate() {
        super.onCreate();
        Preferencias.aplicarTema(this);
        ApiClient.activarCache(getCacheDir());
        registerActivityLifecycleCallbacks(this);
    }

    /** Color del tema actual: PokeApp.color(this, R.attr.pkTexto). */
    public static int color(Context context, int atributo) {
        TypedValue valor = new TypedValue();
        context.getTheme().resolveAttribute(atributo, valor, true);
        return valor.data;
    }

    private void vestir(Activity activity) {
        String tema = Preferencias.tema(activity);
        temaDibujado.put(activity, tema);
        if (Preferencias.TEMA_POKEDEX.equals(tema)) activity.setTheme(R.style.Theme_POKEApp_Pokedex);
    }

    @Override
    public void onActivityPreCreated(@NonNull Activity activity, Bundle estado) {
        vestir(activity);   // Android 10 o superior
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, Bundle estado) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) vestir(activity);
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        // Al volver a una pantalla dibujada con otro tema (ej. el Menú tras Configuración)
        if (!Preferencias.tema(activity).equals(temaDibujado.get(activity))) activity.recreate();
    }

    @Override public void onActivityStarted(@NonNull Activity activity) { }
    @Override public void onActivityPaused(@NonNull Activity activity) { }
    @Override public void onActivityStopped(@NonNull Activity activity) { }
    @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle estado) { }
    @Override public void onActivityDestroyed(@NonNull Activity activity) { }
}
