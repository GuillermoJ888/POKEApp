package com.example.pokeapp;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.example.pokeapp.data.UsuarioManager;

/** Pikachu Run: minijuego extra estilo "dinosaurio de Chrome", con récord en la nube. */
public class PikachuRunActivity extends AppCompatActivity {

    private static final String URL_SPRITE =
            "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/%d.png";

    private PikachuRunView juego;
    private int record;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pikachu_run);

        WindowInsetsControllerCompat barras =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        barras.hide(WindowInsetsCompat.Type.systemBars());
        barras.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        findViewById(R.id.btnRegresar).setOnClickListener(v -> finish());
        juego = findViewById(R.id.juegoPikachu);
        juego.setAlTerminar(this::alTerminar);

        for (int id : PikachuRunView.DE_SUELO) cargarSprite(id);
        for (int id : PikachuRunView.VOLADORES) cargarSprite(id);

        UsuarioManager.obtenerRecord(UsuarioManager.RECORD_PIKACHU, valor -> {
            if (isFinishing() || isDestroyed()) return;
            record = Math.max(record, valor);
            juego.setRecord(record);
        });
    }

    private void cargarSprite(int id) {
        Glide.with(this).asBitmap().load(String.format(java.util.Locale.ROOT, URL_SPRITE, id))
                .disallowHardwareConfig()
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap imagen, @Nullable Transition<? super Bitmap> t) {
                        juego.agregarSprite(id, recortar(imagen));
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable marcador) {
                        // La copia recortada es independiente de Glide
                    }
                });
    }

    /** Quita lo transparente de alrededor para que el tamaño y los choques sean justos. */
    private static Bitmap recortar(Bitmap b) {
        int izq = b.getWidth(), der = -1, arriba = b.getHeight(), abajo = -1;
        for (int y = 0; y < b.getHeight(); y++) {
            for (int x = 0; x < b.getWidth(); x++) {
                if ((b.getPixel(x, y) >>> 24) > 10) {
                    izq = Math.min(izq, x);
                    der = Math.max(der, x);
                    arriba = Math.min(arriba, y);
                    abajo = Math.max(abajo, y);
                }
            }
        }
        if (der < 0) return b.copy(Bitmap.Config.ARGB_8888, false);
        return Bitmap.createBitmap(b, izq, arriba, der - izq + 1, abajo - arriba + 1).copy(Bitmap.Config.ARGB_8888, false);
    }

    private void alTerminar(int puntos) {
        if (puntos >= UsuarioManager.PUNTOS_MEDALLA_PIKACHU) {
            UsuarioManager.otorgarMedallaEspecial(UsuarioManager.MEDALLA_PIKACHU_RUN, nuevas -> {
                if (isFinishing() || isDestroyed() || nuevas == null || nuevas.isEmpty()) return;
                Animaciones.medallaGanada(this, nuevas.get(0), () -> { });
            });
        }
        if (puntos > record) {
            record = puntos;
            UsuarioManager.guardarRecord(UsuarioManager.RECORD_PIKACHU, puntos);
            Efectos.victoria(this);
        } else {
            Efectos.fallo(this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        juego.pausar();
    }
}
