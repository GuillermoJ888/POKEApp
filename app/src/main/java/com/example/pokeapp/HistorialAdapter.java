package com.example.pokeapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.pokeapp.data.HistorialManager.Batalla;
import com.example.pokeapp.data.PokemonMini;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Historial agrupado por día: un encabezado ("HOY", "AYER", "LUNES 5 DE OCTUBRE")
 * seguido de las tarjetas de las batallas de ese día.
 */
public class HistorialAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnBatallaClick {
        void alTocar(Batalla batalla);
    }

    private static final int TIPO_SECCION = 0;
    private static final int TIPO_BATALLA = 1;

    private static final Locale ES = Locale.forLanguageTag("es-MX");
    private static final SimpleDateFormat FORMATO_HORA = new SimpleDateFormat("h:mm a", ES);
    private static final SimpleDateFormat FORMATO_DIA = new SimpleDateFormat("EEEE d 'de' MMMM", ES);

    /** String = encabezado de día; Batalla = tarjeta. */
    private final List<Object> filas = new ArrayList<>();
    private final OnBatallaClick listener;

    public HistorialAdapter(List<Batalla> batallas, OnBatallaClick listener) {
        this.listener = listener;

        String diaAnterior = null;
        for (Batalla b : batallas) {
            String dia = nombreDelDia(b.fecha);
            if (!dia.equals(diaAnterior)) {
                filas.add(dia);
                diaAnterior = dia;
            }
            filas.add(b);
        }
    }

    /** "HOY", "AYER" o "LUNES 5 DE OCTUBRE". Sin fecha (aún sincronizando) cuenta como hoy. */
    static String nombreDelDia(Date fecha) {
        if (fecha == null) return "HOY";

        Calendar dia = Calendar.getInstance();
        dia.setTime(fecha);
        Calendar hoy = Calendar.getInstance();

        if (mismoDia(dia, hoy)) return "HOY";

        hoy.add(Calendar.DAY_OF_YEAR, -1);
        if (mismoDia(dia, hoy)) return "AYER";

        return FORMATO_DIA.format(fecha).toUpperCase(ES);
    }

    private static boolean mismoDia(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    static String hora(Date fecha) {
        return fecha != null ? FORMATO_HORA.format(fecha) : "Guardando...";
    }

    static void cargarSprite(ImageView img, List<Integer> ids, int posicion) {
        int id = ids.size() > posicion ? ids.get(posicion) : -1;
        if (id > 0) {
            Glide.with(img).load(new PokemonMini(id, "").getSpriteUrl()).into(img);
        } else {
            img.setImageResource(R.drawable.ic_pokeball_logo);   // batallas guardadas sin id
        }
    }

    static String nombre(Batalla b, int posicion) {
        return b.pokemon.size() > posicion ? b.pokemon.get(posicion) : "?";
    }

    @Override
    public int getItemViewType(int position) {
        return filas.get(position) instanceof String ? TIPO_SECCION : TIPO_BATALLA;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TIPO_SECCION) {
            return new SeccionViewHolder(inflater.inflate(R.layout.item_historial_seccion, parent, false));
        }
        return new BatallaViewHolder(inflater.inflate(R.layout.item_historial_batalla, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object fila = filas.get(position);

        if (holder instanceof SeccionViewHolder) {
            ((SeccionViewHolder) holder).tvSeccion.setText((String) fila);
            return;
        }

        BatallaViewHolder h = (BatallaViewHolder) holder;
        Batalla b = (Batalla) fila;

        String nombreA = nombre(b, 0);
        String nombreB = nombre(b, 1);
        boolean ganoA = nombreA.equals(b.ganador);
        boolean ganoB = nombreB.equals(b.ganador);

        h.tvHora.setText(hora(b.fecha));
        h.tvTurnos.setText(b.turnos + (b.turnos == 1 ? " turno" : " turnos"));
        h.tvNombreA.setText(nombreA);
        h.tvNombreB.setText(nombreB);
        h.tvGanador.setText("🏆 Ganó " + b.ganador);

        cargarSprite(h.imgA, b.pokemonIds, 0);
        cargarSprite(h.imgB, b.pokemonIds, 1);

        // Ganador con corona; perdedor apagado
        h.coronaA.setVisibility(ganoA ? View.VISIBLE : View.INVISIBLE);
        h.coronaB.setVisibility(ganoB ? View.VISIBLE : View.INVISIBLE);
        h.ladoA.setAlpha(ganoB ? 0.4f : 1f);
        h.ladoB.setAlpha(ganoA ? 0.4f : 1f);

        h.itemView.setOnClickListener(v -> listener.alTocar(b));
    }

    @Override
    public int getItemCount() {
        return filas.size();
    }

    static class SeccionViewHolder extends RecyclerView.ViewHolder {
        final TextView tvSeccion;

        SeccionViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSeccion = itemView.findViewById(R.id.tvSeccion);
        }
    }

    static class BatallaViewHolder extends RecyclerView.ViewHolder {
        final View ladoA, ladoB, coronaA, coronaB;
        final ImageView imgA, imgB;
        final TextView tvHora, tvTurnos, tvNombreA, tvNombreB, tvGanador;

        BatallaViewHolder(@NonNull View itemView) {
            super(itemView);
            ladoA = itemView.findViewById(R.id.ladoA);
            ladoB = itemView.findViewById(R.id.ladoB);
            coronaA = itemView.findViewById(R.id.coronaA);
            coronaB = itemView.findViewById(R.id.coronaB);
            imgA = itemView.findViewById(R.id.imgHistorialA);
            imgB = itemView.findViewById(R.id.imgHistorialB);
            tvHora = itemView.findViewById(R.id.tvHistorialHora);
            tvTurnos = itemView.findViewById(R.id.tvHistorialTurnos);
            tvNombreA = itemView.findViewById(R.id.tvHistorialNombreA);
            tvNombreB = itemView.findViewById(R.id.tvHistorialNombreB);
            tvGanador = itemView.findViewById(R.id.tvHistorialGanador);
        }
    }
}
