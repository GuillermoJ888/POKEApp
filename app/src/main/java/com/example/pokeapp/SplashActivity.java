package com.example.pokeapp;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class SplashActivity extends AppCompatActivity {

    private static final long DURACION_SPLASH_MS = 1800;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        animarLogo();

        new Handler(Looper.getMainLooper()).postDelayed(
                this::irAPantallaCorrespondiente,
                DURACION_SPLASH_MS
        );
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
