package com.example.pokeapp;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.pokeapp.data.Preferencias;
import com.google.firebase.auth.FirebaseAuth;

public class SplashActivity extends AppCompatActivity {

    private static final long DURACION_SPLASH_MS = 1800;
    private static final long DURACION_MAXIMA_VIDEO_MS = 12000;

    /** true = reproducir res/raw/splash_video.mp4 al abrir la app; false = solo la imagen con la barra. */
    private static final boolean USAR_VIDEO = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean yaSalio;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        animarLogo();

        if (!reproducirVideo()) {
            handler.postDelayed(this::irAPantallaCorrespondiente, DURACION_SPLASH_MS);
        }
    }

    /**
     * Si existe el video res/raw/splash_video (mp4), se reproduce a pantalla completa y al terminar
     * se entra a la app. Si no existe o falla, queda la imagen con la barra de carga de siempre.
     */
    private boolean reproducirVideo() {
        if (!USAR_VIDEO) return false;
        int video = getResources().getIdentifier("splash_video", "raw", getPackageName());
        if (video == 0) return false;

        VideoView videoSplash = findViewById(R.id.videoSplash);
        videoSplash.setVisibility(View.VISIBLE);
        videoSplash.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/" + video));
        videoSplash.setOnPreparedListener(reproductor -> {
            if (!Preferencias.sonido(this)) reproductor.setVolume(0f, 0f);
            findViewById(R.id.cargaSplash).setVisibility(View.GONE);
        });
        videoSplash.setOnCompletionListener(reproductor -> irAPantallaCorrespondiente());
        videoSplash.setOnErrorListener((reproductor, que, extra) -> {
            irAPantallaCorrespondiente();
            return true;
        });
        videoSplash.start();
        // Por si el video se atora: nunca se queda más de este tiempo en la pantalla de carga
        handler.postDelayed(this::irAPantallaCorrespondiente, DURACION_MAXIMA_VIDEO_MS);
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }

    private void animarLogo() {
        ImageView imgLogo = findViewById(R.id.imgLogo);

        imgLogo.setScaleX(1.1f);
        imgLogo.setScaleY(1.1f);
        imgLogo.setAlpha(0f);

        imgLogo.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(900)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
    }

    private void irAPantallaCorrespondiente() {
        if (yaSalio || isFinishing()) return;
        yaSalio = true;
        handler.removeCallbacksAndMessages(null);

        boolean haySesionActiva =
                FirebaseAuth.getInstance().getCurrentUser() != null;

        Intent intent;

        if (haySesionActiva) {
            intent = new Intent(this, MenuActivity.class);
        } else {
            intent = new Intent(this, LoginActivity.class);
        }

        startActivity(intent);
        finish();
    }
}
