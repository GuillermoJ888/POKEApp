package com.example.pokeapp;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

/** Dos páginas deslizables: imagen normal (0) e imagen shiny (1). */
public class ImagenPokemonAdapter extends RecyclerView.Adapter<ImagenPokemonAdapter.ImagenViewHolder> {

    private String urlNormal;
    private String urlShiny;

    public void actualizar(String urlNormal, String urlShiny) {
        this.urlNormal = urlNormal;
        this.urlShiny = urlShiny;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ImagenViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ImageView img = (ImageView) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_imagen_pokemon, parent, false);
        return new ImagenViewHolder(img);
    }

    @Override
    public void onBindViewHolder(@NonNull ImagenViewHolder holder, int position) {
        Glide.with(holder.imagen)
                .load(position == 0 ? urlNormal : urlShiny)
                .into(holder.imagen);
    }

    @Override
    public int getItemCount() {
        return 2;
    }

    static class ImagenViewHolder extends RecyclerView.ViewHolder {
        final ImageView imagen;

        ImagenViewHolder(@NonNull ImageView itemView) {
            super(itemView);
            imagen = itemView;
        }
    }
}
