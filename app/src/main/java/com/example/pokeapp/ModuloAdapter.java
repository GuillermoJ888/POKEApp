package com.example.pokeapp;

import android.content.Context;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * Pinta los botones del menú en una cuadrícula (RecyclerView).
 * Cada botón lleva el diseño a toda la tarjeta: imagen mod_<clave> si existe,
 * o el degradado del módulo con su icono grande como ilustración.
 */
public class ModuloAdapter extends RecyclerView.Adapter<ModuloAdapter.ModuloViewHolder> {

    public interface OnModuloClick {
        void alTocar(Modulo modulo);
    }

    private static final float RADIO_DP = 20f;

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
        float densidad = ctx.getResources().getDisplayMetrics().density;

        h.tvTitulo.setText(modulo.titulo);
        h.tvDescripcion.setText(modulo.descripcion);
        h.tvProximamente.setVisibility(activo ? View.GONE : View.VISIBLE);

        // Fondo a toda la tarjeta: degradado del módulo con esquinas redondeadas
        GradientDrawable fondo = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{modulo.colorInicio, modulo.colorFin});
        fondo.setCornerRadius(RADIO_DP * densidad);
        h.tarjeta.setBackground(fondo);
        h.tarjeta.setClipToOutline(true);   // la imagen y la sombra respetan las esquinas
        h.tarjeta.setElevation(activo ? 6 * densidad : 0f);

        // 1) Imagen del usuario a todo el botón: res/drawable/mod_<clave>.png (o mod_a ... mod_m)
        int imagenUsuario = buscarImagen(ctx, "mod_" + modulo.clave);
        if (imagenUsuario == 0 && !modulo.letraAntigua.isEmpty()) {
            imagenUsuario = buscarImagen(ctx, "mod_" + modulo.letraAntigua);
        }

        if (imagenUsuario != 0) {
            h.imgFondo.setImageResource(imagenUsuario);
            sinSuavizadoSiEsPixelArt(h.imgFondo);
            h.imgFondo.setVisibility(View.VISIBLE);
            h.imgMarcaAgua.setVisibility(View.GONE);
            h.imgIcono.setVisibility(View.GONE);
            h.tvEmoji.setVisibility(View.GONE);
        } else {
            // 2) Sin imagen: ilustración grande con el dibujo propio o el emoji
            h.imgFondo.setVisibility(View.GONE);
            h.imgMarcaAgua.setVisibility(View.VISIBLE);

            if (modulo.iconoRes != 0) {
                h.imgIcono.setImageResource(modulo.iconoRes);
                h.imgIcono.setVisibility(View.VISIBLE);
                h.tvEmoji.setVisibility(View.GONE);
            } else {
                h.tvEmoji.setText(modulo.emoji);
                h.tvEmoji.setVisibility(View.VISIBLE);
                h.imgIcono.setVisibility(View.GONE);
            }
        }

        // "Próximamente": el botón completo se ve en tonos grises
        if (activo) {
            h.tarjeta.setLayerType(View.LAYER_TYPE_NONE, null);
            h.tarjeta.setAlpha(1f);
        } else {
            ColorMatrix gris = new ColorMatrix();
            gris.setSaturation(0.15f);
            Paint pintura = new Paint();
            pintura.setColorFilter(new ColorMatrixColorFilter(gris));
            h.tarjeta.setLayerType(View.LAYER_TYPE_HARDWARE, pintura);
            h.tarjeta.setAlpha(0.85f);
        }

        h.itemView.setOnClickListener(v -> listener.alTocar(modulo));
    }

    /** Pixel art (imágenes chicas): sin suavizado para que no se vea borroso. */
    private void sinSuavizadoSiEsPixelArt(ImageView img) {
        Drawable dibujo = img.getDrawable();
        if (dibujo instanceof BitmapDrawable
                && ((BitmapDrawable) dibujo).getBitmap().getWidth() <= 256) {
            dibujo.mutate();
            ((BitmapDrawable) dibujo).setFilterBitmap(false);
        }
    }

    private int buscarImagen(Context ctx, String nombre) {
        return ctx.getResources().getIdentifier(nombre, "drawable", ctx.getPackageName());
    }

    @Override
    public int getItemCount() {
        return modulos.size();
    }

    static class ModuloViewHolder extends RecyclerView.ViewHolder {
        final View tarjeta;
        final ImageView imgFondo, imgMarcaAgua, imgIcono;
        final TextView tvEmoji, tvTitulo, tvDescripcion, tvProximamente;

        ModuloViewHolder(@NonNull View itemView) {
            super(itemView);
            tarjeta = itemView.findViewById(R.id.tarjetaModulo);
            imgFondo = itemView.findViewById(R.id.imgFondoModulo);
            imgMarcaAgua = itemView.findViewById(R.id.imgMarcaAgua);
            imgIcono = itemView.findViewById(R.id.imgModulo);
            tvEmoji = itemView.findViewById(R.id.tvEmojiModulo);
            tvTitulo = itemView.findViewById(R.id.tvTituloModulo);
            tvDescripcion = itemView.findViewById(R.id.tvDescripcionModulo);
            tvProximamente = itemView.findViewById(R.id.tvProximamente);
        }
    }
}
