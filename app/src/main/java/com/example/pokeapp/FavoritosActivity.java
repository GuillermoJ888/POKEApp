package com.example.pokeapp;

import com.example.pokeapp.data.Idioma;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pokeapp.data.Favorito;
import com.example.pokeapp.data.FavoritosManager;

public class FavoritosActivity extends AppCompatActivity {

    private RecyclerView recyclerFavoritos;
    private TextView tvVacio;
    private View seccionVacia;
    private ProgressBar progressFavoritos;
    private FavoritosAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favoritos);

        recyclerFavoritos = findViewById(R.id.recyclerFavoritos);
        tvVacio = findViewById(R.id.tvVacio);
        seccionVacia = findViewById(R.id.seccionVacia);
        Animaciones.flotar(findViewById(R.id.imgVacio));
        findViewById(R.id.btnIrPokedex).setOnClickListener(v ->
                startActivity(new Intent(this, PokedexActivity.class)));
        progressFavoritos = findViewById(R.id.progressFavoritos);

        adapter = new FavoritosAdapter(new FavoritosAdapter.Acciones() {
            @Override
            public void alTocar(Favorito favorito) {   // abre su detalle en la Pokédex
                Intent intent = new Intent(FavoritosActivity.this, PokedexActivity.class);
                intent.putExtra(PokedexActivity.EXTRA_BUSQUEDA_INICIAL, String.valueOf(favorito.getId()));
                startActivity(intent);
            }

            @Override
            public void alEscuchar(Favorito favorito) {
                if (!Efectos.cry(FavoritosActivity.this, favorito.getId())) {
                    Toast.makeText(FavoritosActivity.this,
                            Idioma.t("El sonido está desactivado en Configuración", "Sound is turned off in Settings"), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void alEliminar(Favorito favorito) {
                confirmarEliminar(favorito);
            }
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

    @Override
    protected void onPause() {
        super.onPause();
        Efectos.soltarCry();
    }

    private void confirmarEliminar(Favorito favorito) {
        Dialogos.confirmar(this, "🗑️", Idioma.t("¿Quitar a ", "Remove ") + favorito.getNombre() + "?",
                Idioma.t("Dejará de aparecer en tus favoritos. Puedes volver a marcarlo con la estrella en la Pokédex.", "It will no longer appear in your favorites. You can mark it again with the star in the Pokédex."),
                Idioma.t("Quitar de favoritos", "Remove from favorites"), Idioma.t("Cancelar", "Cancel"),
                () -> FavoritosManager.quitar(favorito.getId(), ok -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (Boolean.TRUE.equals(ok)) {
                        cargarFavoritos();
                    } else {
                        Toast.makeText(this, Idioma.t("No se pudo quitar: ", "Could not remove: ") + FavoritosManager.ultimoError(),
                                Toast.LENGTH_LONG).show();
                    }
                }), null);
    }

    private void cargarFavoritos() {
        progressFavoritos.setVisibility(View.VISIBLE);

        FavoritosManager.obtenerTodos(lista -> {
            progressFavoritos.setVisibility(View.GONE);

            if (lista == null) {
                Toast.makeText(this, Idioma.t("No se pudieron cargar tus favoritos: ", "Could not load your favorites: ") + FavoritosManager.ultimoError(),
                        Toast.LENGTH_LONG).show();
                tvVacio.setText("⚠️ " + FavoritosManager.ultimoError());
                seccionVacia.setVisibility(View.VISIBLE);
                return;
            }

            adapter.actualizar(lista);
            seccionVacia.setVisibility(lista.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }
}
