package com.example.pokeapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.pokeapp.data.Favorito;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Adaptador del RecyclerView de favoritos: recicla las filas en vez de crearlas todas. */
public class FavoritosAdapter extends RecyclerView.Adapter<FavoritosAdapter.FavoritoViewHolder> {

    public interface OnFavoritoClick {
        void alTocar(Favorito favorito);
    }

    private final List<Favorito> items = new ArrayList<>();
    private final OnFavoritoClick listener;

    public FavoritosAdapter(OnFavoritoClick listener) {
        this.listener = listener;
    }

    public void actualizar(List<Favorito> nuevos) {
        items.clear();
        items.addAll(nuevos);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public FavoritoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View vista = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_favorito, parent, false);
        return new FavoritoViewHolder(vista);
    }

    @Override
    public void onBindViewHolder(@NonNull FavoritoViewHolder holder, int position) {
        Favorito favorito = items.get(position);

        holder.tvNombre.setText(favorito.getNombre());
        holder.tvNumero.setText(String.format(Locale.ROOT, "#%03d", favorito.getId()));

        Glide.with(holder.imgSprite)
                .load(favorito.getSpriteUrl())
                .into(holder.imgSprite);

        holder.itemView.setOnClickListener(v -> listener.alTocar(favorito));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class FavoritoViewHolder extends RecyclerView.ViewHolder {
        final ImageView imgSprite;
        final TextView tvNombre, tvNumero;

        FavoritoViewHolder(@NonNull View itemView) {
            super(itemView);
            imgSprite = itemView.findViewById(R.id.imgSpriteFavorito);
            tvNombre = itemView.findViewById(R.id.tvNombreFavorito);
            tvNumero = itemView.findViewById(R.id.tvNumeroFavorito);
        }
    }
}
