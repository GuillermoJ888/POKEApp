package com.example.pokeapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.pokeapp.data.PokemonMini;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Sugerencias que aparecen bajo el buscador mientras escribes. */
public class SugerenciaAdapter extends RecyclerView.Adapter<SugerenciaAdapter.SugerenciaViewHolder> {

    public interface OnSugerenciaClick {
        void alTocar(PokemonMini pokemon);
    }

    private final List<PokemonMini> items = new ArrayList<>();
    private final OnSugerenciaClick listener;

    public SugerenciaAdapter(OnSugerenciaClick listener) {
        this.listener = listener;
    }

    public void actualizar(List<PokemonMini> nuevos) {
        items.clear();
        items.addAll(nuevos);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SugerenciaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View vista = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_sugerencia, parent, false);
        return new SugerenciaViewHolder(vista);
    }

    @Override
    public void onBindViewHolder(@NonNull SugerenciaViewHolder h, int position) {
        PokemonMini p = items.get(position);

        h.tvNombre.setText(p.getNombre());
        h.tvNumero.setText(String.format(Locale.ROOT, "#%03d", p.getId()));
        Glide.with(h.img).load(p.getSpriteUrl()).into(h.img);

        h.itemView.setOnClickListener(v -> listener.alTocar(p));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class SugerenciaViewHolder extends RecyclerView.ViewHolder {
        final ImageView img;
        final TextView tvNombre, tvNumero;

        SugerenciaViewHolder(@NonNull View itemView) {
            super(itemView);
            img = itemView.findViewById(R.id.imgSugerencia);
            tvNombre = itemView.findViewById(R.id.tvNombreSugerencia);
            tvNumero = itemView.findViewById(R.id.tvNumeroSugerencia);
        }
    }
}
