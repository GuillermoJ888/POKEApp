package com.example.pokeapp;

import android.app.Application;

import com.example.pokeapp.data.Preferencias;

/** Arranque de la app: aplica el tema (claro/oscuro) guardado antes de abrir cualquier pantalla. */
public class PokeApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Preferencias.aplicarTema(this);
    }
}
