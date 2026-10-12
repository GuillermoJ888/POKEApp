package com.example.pokeapp;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

import com.example.pokeapp.data.Preferencias;

/**
 * Sonido y vibración de la app. Todo pasa por aquí para respetar lo que el
 * usuario eligió en Configuración (Preferencias.sonido / Preferencias.vibracion).
 */
public final class Efectos {

    private static final String URL_CRY =
            "https://raw.githubusercontent.com/PokeAPI/cries/main/cries/pokemon/latest/";

    private static MediaPlayer cry;
    private static ToneGenerator tonos;

    private Efectos() { }

    /**
     * Reproduce el cry de un Pokémon (de la PokéAPI). Devuelve false si el sonido
     * está desactivado en Configuración, para que la pantalla pueda avisarlo.
     */
    public static boolean cry(Context context, int idPokemon) {
        if (!Preferencias.sonido(context)) return false;

        soltarCry();
        cry = new MediaPlayer();
        try {
            cry.setDataSource(URL_CRY + idPokemon + ".ogg");
            cry.setOnPreparedListener(MediaPlayer::start);
            cry.setOnCompletionListener(mp -> soltarCry());
            cry.setOnErrorListener((mp, what, extra) -> {
                soltarCry();
                return true;
            });
            cry.prepareAsync();
        } catch (Exception e) {
            soltarCry();
        }
        return true;
    }

    public static void soltarCry() {
        if (cry != null) {
            try {
                cry.release();
            } catch (Exception ignorada) {
                // ya estaba liberado
            }
            cry = null;
        }
    }

    /** Golpe de batalla: tono corto y vibración (más fuertes si es crítico). */
    public static void golpe(Context context, boolean critico) {
        tono(context, critico ? ToneGenerator.TONE_CDMA_HIGH_L : ToneGenerator.TONE_PROP_BEEP, critico ? 180 : 90);
        vibrar(context, critico ? 120 : 45);
    }

    /** El ataque falló o no le afecta: tono grave, sin vibración. */
    public static void fallo(Context context) {
        tono(context, ToneGenerator.TONE_PROP_NACK, 120);
    }

    /** Fin de la batalla o captura lograda. */
    public static void victoria(Context context) {
        tono(context, ToneGenerator.TONE_PROP_ACK, 250);
        vibrar(context, 200);
    }

    private static void tono(Context context, int tipo, int duracionMs) {
        if (!Preferencias.sonido(context)) return;
        try {
            if (tonos == null) tonos = new ToneGenerator(AudioManager.STREAM_MUSIC, 55);
            tonos.startTone(tipo, duracionMs);
        } catch (Exception ignorada) {
            // algunos dispositivos no permiten crear el generador de tonos
            tonos = null;
        }
    }

    public static void vibrar(Context context, int duracionMs) {
        if (!Preferencias.vibracion(context)) return;
        try {
            Vibrator vibrador = (Vibrator) context.getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrador == null || !vibrador.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrador.vibrate(VibrationEffect.createOneShot(duracionMs, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrador.vibrate(duracionMs);
            }
        } catch (Exception ignorada) {
            // sin permiso o sin motor de vibración: no pasa nada
        }
    }
}
