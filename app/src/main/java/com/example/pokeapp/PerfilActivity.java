package com.example.pokeapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.pokeapp.data.FavoritosManager;
import com.example.pokeapp.data.UsuarioManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class PerfilActivity extends AppCompatActivity {

    private TextView tvFavoritos, tvMedallas, tvVictorias, tvDerrotas, tvNivel;
    private ProgressBar progressNivel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_perfil);

        TextView tvNombre = findViewById(R.id.tvNombrePerfil);
        TextView tvCorreo = findViewById(R.id.tvCorreoPerfil);
        tvFavoritos = findViewById(R.id.tvContadorFavoritos);
        tvMedallas = findViewById(R.id.tvContadorMedallas);
        tvVictorias = findViewById(R.id.tvContadorVictorias);
        tvDerrotas = findViewById(R.id.tvContadorDerrotas);
        tvNivel = findViewById(R.id.tvNivel);
        progressNivel = findViewById(R.id.progressNivel);

        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();

        if (usuario != null) {
            String nombre = usuario.getDisplayName();
            tvNombre.setText(nombre != null && !nombre.isEmpty() ? nombre : "Entrenador");
            tvCorreo.setText(usuario.getEmail());
        }

        findViewById(R.id.btnRegresar).setOnClickListener(v -> finish());

        findViewById(R.id.btnCerrarSesionPerfil).setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        FavoritosManager.contar(total -> tvFavoritos.setText(String.valueOf(total)));

        UsuarioManager.obtenerEstadisticas(est -> {
            tvMedallas.setText(est.medallas + "/10");
            tvVictorias.setText(String.valueOf(est.victorias));
            tvDerrotas.setText(String.valueOf(est.derrotas));
            tvNivel.setText("Nivel " + est.nivel());
            progressNivel.setProgress(est.progresoNivel());
        });
    }
}
