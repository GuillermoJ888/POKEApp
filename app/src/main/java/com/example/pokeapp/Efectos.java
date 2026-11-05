package com.example.pokeapp;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

import com.example.pokeapp.data.Preferencias;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Sonido y vibración de la app. Todo pasa por aquí para respetar lo que el
 * usuario eligió en Configuración (Preferencias.sonido / Preferencias.vibracion).
 */
public final class Efectos {

    private static final String URL_CRY =
            "https://raw.githubusercontent.com/PokeAPI/cries/main/cries/pokemon/latest/";

    private static MediaPlayer cry;
    private static ToneGenerator tonos;

    // Efectos 8 bits de los minijuegos (res/raw/snd_*.wav)
    private static SoundPool efectos;
    private static final Map<Integer, Integer> idPorRecurso = new HashMap<>();
    private static final Set<Integer> cargados = new HashSet<>();
    private static int pendiente = -1;   // si se pidió antes de terminar de cargar, suena al cargar

    /** Todos los sonidos de los minijuegos, para cargarlos al abrir uno. */
    public static final int[] SONIDOS_JUEGOS = {
            R.raw.snd_salto, R.raw.snd_moneda, R.raw.snd_choque, R.raw.snd_acierto, R.raw.snd_error,
            R.raw.snd_carta, R.raw.snd_pareja, R.raw.snd_lanzar, R.raw.snd_sacudida, R.raw.snd_captura,
            R.raw.snd_escape, R.raw.snd_record};

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

    /** Carga los efectos en memoria (se llama al abrir un minijuego para que suenen sin retraso). */
    public static void precargar(Context context) {
        for (int recurso : SONIDOS_JUEGOS) idDe(context, recurso);
    }

    /** Reproduce un efecto de res/raw si el sonido está activado en Configuración. */
    public static void sonar(Context context, int recurso) {
        if (!Preferencias.sonido(context)) return;
        try {
            int id = idDe(context, recurso);
            if (cargados.contains(id)) efectos.play(id, 1f, 1f, 1, 0, 1f);
            else pendiente = id;
        } catch (Exception ignorada) {
            // si el celular no deja reproducir, el juego sigue sin sonido
        }
    }

    private static int idDe(Context context, int recurso) {
        if (efectos == null) {
            efectos = new SoundPool.Builder()
                    .setMaxStreams(4)
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                    .build();
            efectos.setOnLoadCompleteListener((pool, id, estado) -> {
                if (estado != 0) return;
                cargados.add(id);
                if (id == pendiente) {
                    pendiente = -1;
                    pool.play(id, 1f, 1f, 1, 0, 1f);
                }
            });
        }
        Integer id = idPorRecurso.get(recurso);
        if (id == null) {
            id = efectos.load(context.getApplicationContext(), recurso, 1);
            idPorRecurso.put(recurso, id);
        }
        return id;
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
