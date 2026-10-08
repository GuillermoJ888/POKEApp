package com.example.pokeapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pokeapp.data.FavoritosManager;

public class FavoritosActivity extends AppCompatActivity {

    private RecyclerView recyclerFavoritos;
    private TextView tvVacio;
    private ProgressBar progressFavoritos;
    private FavoritosAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favoritos);

        recyclerFavoritos = findViewById(R.id.recyclerFavoritos);
        tvVacio = findViewById(R.id.tvVacio);
        progressFavoritos = findViewById(R.id.progressFavoritos);

        adapter = new FavoritosAdapter(favorito -> {
            Intent intent = new Intent(this, PokedexActivity.class);
            intent.putExtra(PokedexActivity.EXTRA_BUSQUEDA_INICIAL, String.valueOf(favorito.getId()));
            startActivity(intent);
        });

        recyclerFavoritos.setLayoutManager(new LinearLayoutManager(this));
        recyclerFavoritos.setAdapter(adapter);

        findViewById(R.id.btnRegresar).setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        cargarFavoritos();
    }

    private void cargarFavoritos() {
        progressFavoritos.setVisibility(View.VISIBLE);

        FavoritosManager.obtenerTodos(lista -> {
            progressFavoritos.setVisibility(View.GONE);

            if (lista == null) {
                Toast.makeText(this, "No se pudieron cargar tus favoritos: " + FavoritosManager.ultimoError(),
                        Toast.LENGTH_LONG).show();
                tvVacio.setText("⚠️ " + FavoritosManager.ultimoError());
                tvVacio.setVisibility(View.VISIBLE);
                return;
            }

            adapter.actualizar(lista);
            tvVacio.setVisibility(lista.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }
}
