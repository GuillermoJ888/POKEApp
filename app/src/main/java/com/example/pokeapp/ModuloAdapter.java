package com.example.pokeapp;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/** Pinta los botones del menú en una cuadrícula (RecyclerView). */
public class ModuloAdapter extends RecyclerView.Adapter<ModuloAdapter.ModuloViewHolder> {

    public interface OnModuloClick {
        void alTocar(Modulo modulo);
    }

    private final List<Modulo> modulos;
    private final OnModuloClick listener;

    public ModuloAdapter(List<Modulo> modulos, OnModuloClick listener) {
        this.modulos = modulos;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ModuloViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View vista = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_modulo, parent, false);
        return new ModuloViewHolder(vista);
    }

    @Override
    public void onBindViewHolder(@NonNull ModuloViewHolder h, int position) {
        Modulo modulo = modulos.get(position);
        Context ctx = h.itemView.getContext();
        boolean activo = modulo.estaActivo();

        h.tvTitulo.setText(modulo.titulo);
        h.tvDescripcion.setText(activo ? modulo.descripcion : "Próximamente");

        // 1) Tu imagen: res/drawable/mod_<clave>.png (o mod_a ... mod_m si ya la tenías así)
        int resUsuario = buscarImagen(ctx, "mod_" + modulo.clave);
        if (resUsuario == 0 && !modulo.letraAntigua.isEmpty()) {
            resUsuario = buscarImagen(ctx, "mod_" + modulo.letraAntigua);
        }
        // 2) Si no, el dibujo propio del módulo; 3) si no, el emoji
        int resImagen = resUsuario != 0 ? resUsuario : modulo.iconoRes;

        if (resImagen != 0) {
            h.imgIcono.setImageResource(resImagen);

            // Pixel art (imágenes chicas): sin suavizado para que no se vea borroso
            Drawable dibujo = h.imgIcono.getDrawable();
            if (dibujo instanceof BitmapDrawable
                    && ((BitmapDrawable) dibujo).getBitmap().getWidth() <= 256) {
                dibujo.mutate();
                ((BitmapDrawable) dibujo).setFilterBitmap(false);
            }
            h.imgIcono.setVisibility(View.VISIBLE);
            h.tvEmoji.setVisibility(View.GONE);
        } else {
            h.tvEmoji.setText(modulo.emoji);
            h.tvEmoji.setVisibility(View.VISIBLE);
            h.imgIcono.setVisibility(View.GONE);
        }

        // Estilo según esté activo o "Próximamente"
        h.tarjeta.setBackgroundResource(activo ? R.drawable.bg_card_menu : R.drawable.bg_card_menu_soon);
        h.tarjeta.setElevation(activo ? 6f : 0f);
        h.chipIcono.setBackgroundResource(activo ? R.drawable.bg_icon_chip_red : R.drawable.bg_icon_chip_gray);
        h.tvTitulo.setTextColor(ctx.getColor(activo ? R.color.menu_text : R.color.menu_text_soft));
        h.imgIcono.setAlpha(activo ? 1f : 0.55f);
        h.tvEmoji.setAlpha(activo ? 1f : 0.5f);

        h.itemView.setOnClickListener(v -> listener.alTocar(modulo));
    }

    private int buscarImagen(Context ctx, String nombre) {
        return ctx.getResources().getIdentifier(nombre, "drawable", ctx.getPackageName());
    }

    @Override
    public int getItemCount() {
        return modulos.size();
    }

    static class ModuloViewHolder extends RecyclerView.ViewHolder {
        final View tarjeta, chipIcono;
        final ImageView imgIcono;
        final TextView tvEmoji, tvTitulo, tvDescripcion;

        ModuloViewHolder(@NonNull View itemView) {
            super(itemView);
            tarjeta = itemView.findViewById(R.id.tarjetaModulo);
            chipIcono = itemView.findViewById(R.id.chipIcono);
            imgIcono = itemView.findViewById(R.id.imgModulo);
            tvEmoji = itemView.findViewById(R.id.tvEmojiModulo);
            tvTitulo = itemView.findViewById(R.id.tvTituloModulo);
            tvDescripcion = itemView.findViewById(R.id.tvDescripcionModulo);
        }
    }
}
