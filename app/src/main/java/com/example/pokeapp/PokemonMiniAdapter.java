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

/** Lista horizontal de Pokémon en la parte de arriba de la Pokédex. */
public class PokemonMiniAdapter extends RecyclerView.Adapter<PokemonMiniAdapter.MiniViewHolder> {

    public interface OnMiniClick {
        void alTocar(PokemonMini pokemon);
    }

    private final List<PokemonMini> items = new ArrayList<>();
    private final OnMiniClick listener;
    private final boolean enCuadricula;
    private int idSeleccionado = -1;

    public PokemonMiniAdapter(OnMiniClick listener) {
        this(listener, false);
    }

    /** enCuadricula = true: cada tarjeta llena el ancho de su columna (para GridLayoutManager). */
    public PokemonMiniAdapter(OnMiniClick listener, boolean enCuadricula) {
        this.listener = listener;
        this.enCuadricula = enCuadricula;
    }

    public void agregar(List<PokemonMini> nuevos) {
        int inicio = items.size();
        items.addAll(nuevos);
        notifyItemRangeInserted(inicio, nuevos.size());
    }

    public void seleccionar(int id) {
        idSeleccionado = id;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MiniViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View vista = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_pokemon_mini, parent, false);

        if (enCuadricula) {
            int margen = Math.round(4 * parent.getResources().getDisplayMetrics().density);
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) vista.getLayoutParams();
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.setMargins(margen, margen, margen, margen);
            vista.setLayoutParams(lp);
        }

        return new MiniViewHolder(vista);
    }

    @Override
    public void onBindViewHolder(@NonNull MiniViewHolder h, int position) {
        PokemonMini p = items.get(position);

        h.tvNombre.setText(p.getNombre());
        h.tvNumero.setText(String.format(Locale.ROOT, "#%03d", p.getId()));
        Glide.with(h.img).load(p.getSpriteUrl()).into(h.img);

        h.itemView.setBackgroundResource(
                p.getId() == idSeleccionado ? R.drawable.bg_mini_selected : R.drawable.bg_mini_normal);

        h.itemView.setOnClickListener(v -> listener.alTocar(p));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class MiniViewHolder extends RecyclerView.ViewHolder {
        final ImageView img;
        final TextView tvNombre, tvNumero;

        MiniViewHolder(@NonNull View itemView) {
            super(itemView);
            img = itemView.findViewById(R.id.imgMini);
            tvNombre = itemView.findViewById(R.id.tvNombreMini);
            tvNumero = itemView.findViewById(R.id.tvNumeroMini);
        }
    }
}
