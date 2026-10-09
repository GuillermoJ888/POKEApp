package com.example.pokeapp;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.pokeapp.data.HistorialManager;
import com.example.pokeapp.data.HistorialManager.Batalla;
import com.example.pokeapp.data.PokemonMini;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Historial de batallas de un modo de juego (por ahora Battle Emulator):
 * resumen arriba y batallas agrupadas por día. Al tocar una se ven sus turnos.
 */
public class HistorialActivity extends AppCompatActivity {

    private static final String EXTRA_MODO = "extra_modo";

    private String modo;
    private View seccionResumen, seccionVacia, progress;
    private TextView tvVacio;
    private RecyclerView recycler;

    public static Intent crearIntent(Context context, String modo) {
        return new Intent(context, HistorialActivity.class).putExtra(EXTRA_MODO, modo);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_historial);

        modo = getIntent().getStringExtra(EXTRA_MODO);
        if (modo == null) modo = HistorialManager.MODO_EMULATOR;

        ((TextView) findViewById(R.id.tvModo)).setText(modo);
        seccionResumen = findViewById(R.id.seccionResumen);
        seccionVacia = findViewById(R.id.seccionVacia);
        progress = findViewById(R.id.progressHistorial);
        tvVacio = findViewById(R.id.tvVacio);
        recycler = findViewById(R.id.recyclerHistorial);
        recycler.setLayoutManager(new LinearLayoutManager(this));

        findViewById(R.id.btnRegresar).setOnClickListener(v -> finish());

        cargar();
    }

    private void cargar() {
        HistorialManager.obtenerBatallas(modo, batallas -> {
            if (isFinishing() || isDestroyed()) return;
            progress.setVisibility(View.GONE);

            if (batallas == null) {
                tvVacio.setText("No se pudo cargar el Historial.\nRevisa tu conexión y las reglas de Firestore.");
                seccionVacia.setVisibility(View.VISIBLE);
                return;
            }

            if (batallas.isEmpty()) {
                seccionVacia.setVisibility(View.VISIBLE);
                return;
            }

            mostrarResumen(batallas);
            recycler.setAdapter(new HistorialAdapter(batallas, this::mostrarDetalle));
        });
    }

    /** Total de batallas, turnos promedio y el Pokémon con más victorias. */
    private void mostrarResumen(List<Batalla> batallas) {
        int sumaTurnos = 0;
        Map<String, Integer> victorias = new HashMap<>();
        Map<String, Integer> idPorNombre = new HashMap<>();

        for (Batalla b : batallas) {
            sumaTurnos += b.turnos;

            Integer actuales = victorias.get(b.ganador);
            victorias.put(b.ganador, actuales == null ? 1 : actuales + 1);

            for (int i = 0; i < b.pokemon.size() && i < b.pokemonIds.size(); i++) {
                idPorNombre.put(b.pokemon.get(i), b.pokemonIds.get(i));
            }
        }

        String campeon = null;
        int maxVictorias = 0;
        for (Map.Entry<String, Integer> e : victorias.entrySet()) {
            if (e.getValue() > maxVictorias) {
                campeon = e.getKey();
                maxVictorias = e.getValue();
            }
        }

        ((TextView) findViewById(R.id.tvTotalBatallas)).setText(String.valueOf(batallas.size()));
        ((TextView) findViewById(R.id.tvPromedioTurnos)).setText(
                String.format(Locale.ROOT, "%.1f", sumaTurnos / (float) batallas.size()));
        ((TextView) findViewById(R.id.tvCampeon)).setText(campeon != null ? campeon : "-");
        ((TextView) findViewById(R.id.tvCampeonVictorias)).setText(
                "👑 " + maxVictorias + (maxVictorias == 1 ? " victoria" : " victorias"));

        ImageView imgCampeon = findViewById(R.id.imgCampeon);
        Integer idCampeon = campeon != null ? idPorNombre.get(campeon) : null;
        if (idCampeon != null) {
            Glide.with(this).load(new PokemonMini(idCampeon, "").getSpriteUrl()).into(imgCampeon);
        } else {
            imgCampeon.setImageResource(R.drawable.ic_pokeball_logo);
        }

        seccionResumen.setVisibility(View.VISIBLE);
    }

    /**
     * Lista numerada: un renglón por turno. Cada movimiento guardado viene así:
     *   Turno 1 / Charizard ataca (tipo Fuego) / Causa 148 puntos de daño · ¡Es súper eficaz! /
     *   Pikachu queda con 32 HP · ¡Se debilitó!
     * y se convierte en:
     *   "1. Charizard ataca (tipo Fuego) y causa 148 puntos de daño (¡es súper eficaz!);
     *    Pikachu queda con 32 HP y se debilita."
     */
    private static String narrar(Batalla batalla) {
        StringBuilder lista = new StringBuilder();

        int posicion = 0;
        for (String movimiento : batalla.movimientos) {
            posicion++;
            String[] lineas = movimiento.split("\n");

            // Equipos de cada jugador (Versus / Torre): renglones tal cual, sin número
            if (!lineas[0].startsWith("Turno ")) {
                for (String linea : lineas) agregarRenglon(lista, "👥 " + linea);
                lista.append("\n");
                posicion--;
                continue;
            }

            // Formato viejo (una sola línea): se agrega tal cual con su número
            if (lineas.length < 4) {
                agregarRenglon(lista, posicion + ". " + movimiento.replace("\n", " "));
                continue;
            }

            String numero = lineas[0].substring("Turno ".length()).trim();
            String ataque = lineas[1];
            String estado = lineas[3].replace(" · ¡Se debilitó!", " y se debilita");

            // "Causa 18 puntos…" → "y causa 18 puntos…"; "¡Pero falló!" → ", pero falló"
            String resultado = lineas[2].startsWith("Causa")
                    ? " y " + primeraMinuscula(detallesEntreParentesis(lineas[2]))
                    : ", " + primeraMinuscula(lineas[2].replace("¡", "").replace("!", ""));

            agregarRenglon(lista, numero + ". " + ataque + resultado + "; " + estado);
        }

        lista.append("\n");
        agregarRenglon(lista, "🏆 " + batalla.ganador + " ganó la batalla en "
                + batalla.turnos + (batalla.turnos == 1 ? " turno" : " turnos"));

        return lista.toString();
    }

    /** "Causa 18 puntos de daño · ¡Golpe crítico!" -> "Causa 18 puntos de daño (¡golpe crítico!)" */
    private static String detallesEntreParentesis(String linea) {
        String[] partes = linea.split(" · ");
        if (partes.length == 1) return linea;

        StringBuilder sb = new StringBuilder(partes[0]).append(" (");
        for (int i = 1; i < partes.length; i++) {
            if (i > 1) sb.append(", ");
            sb.append(primeraMinuscula(partes[i].trim()));
        }
        return sb.append(")").toString();
    }

    private static String primeraMinuscula(String texto) {
        if (texto.isEmpty()) return texto;
        int i = texto.startsWith("¡") ? 1 : 0;
        if (texto.length() <= i) return texto;
        return texto.substring(0, i) + texto.substring(i, i + 1).toLowerCase(Locale.ROOT) + texto.substring(i + 1);
    }

    /** Agrega un renglón terminado en punto, separado del anterior por un salto de línea. */
    private static void agregarRenglon(StringBuilder lista, String renglon) {
        String limpio = renglon.trim();
        if (limpio.isEmpty()) return;
        if (lista.length() > 0 && lista.charAt(lista.length() - 1) != '\n') lista.append('\n');
        lista.append(limpio);
        char ultimo = limpio.charAt(limpio.length() - 1);
        if (ultimo != '.' && ultimo != '!' && ultimo != '?') lista.append('.');
    }

    private void mostrarDetalle(Batalla batalla) {
        View vista = LayoutInflater.from(this).inflate(R.layout.dialog_detalle_batalla, null);

        String nombreA = HistorialAdapter.nombre(batalla, 0);
        String nombreB = HistorialAdapter.nombre(batalla, 1);

        HistorialAdapter.cargarSprite(vista.findViewById(R.id.imgDetalleA), batalla.pokemonIds, 0);
        HistorialAdapter.cargarSprite(vista.findViewById(R.id.imgDetalleB), batalla.pokemonIds, 1);
        ((TextView) vista.findViewById(R.id.tvDetalleVs)).setText(nombreA + " vs " + nombreB);
        ((TextView) vista.findViewById(R.id.tvDetalleGanador)).setText(
                "🏆 Ganó " + batalla.ganador + " · " + batalla.turnos + " turnos");
        ((TextView) vista.findViewById(R.id.tvDetalleFecha)).setText(
                HistorialAdapter.nombreDelDia(batalla.fecha).toLowerCase(Locale.ROOT)
                        + " · " + HistorialAdapter.hora(batalla.fecha));

        ((TextView) vista.findViewById(R.id.tvNarracion)).setText(narrar(batalla));

        // Batallas largas: el párrafo se desplaza dentro de un máximo de 380dp
        View scrollNarracion = vista.findViewById(R.id.scrollNarracion);
        int maximo = Math.round(380 * getResources().getDisplayMetrics().density);
        scrollNarracion.post(() -> {
            if (scrollNarracion.getHeight() > maximo) {
                scrollNarracion.getLayoutParams().height = maximo;
                scrollNarracion.requestLayout();
            }
        });

        AlertDialog dialogo = new AlertDialog.Builder(this).setView(vista).create();
        vista.findViewById(R.id.btnCerrarDetalle).setOnClickListener(v -> dialogo.dismiss());
        dialogo.show();
        if (dialogo.getWindow() != null) {
            dialogo.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }
}
