package com.example.pokeapp;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;

import com.bumptech.glide.Glide;
import com.example.pokeapp.battle.BattlePokemon;
import com.example.pokeapp.data.HistorialManager;
import com.example.pokeapp.data.Pokemon;
import com.example.pokeapp.data.UsuarioManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Battle Versus: dos jugadores en el mismo celular. El Jugador 1 arma su equipo de 3,
 * luego el Jugador 2, y pelean por turnos en la arena (cada uno toca Atacar y elige
 * quién entra cuando cae uno). El resultado cuenta como victoria/derrota del Jugador 1
 * (el usuario con sesión) y se guarda en el Historial.
 */
public class VersusActivity extends AppCompatActivity {

    public static final int TAM_EQUIPO = 3;
    private static final String JUGADOR_1 = "Jugador 1";
    private static final String JUGADOR_2 = "Jugador 2";

    private final List<Pokemon> equipo1 = new ArrayList<>();
    private final List<Pokemon> equipo2 = new ArrayList<>();

    private NestedScrollView scroll;
    private LinearLayout slots1, slots2;
    private TextView tvTitulo1, tvTitulo2, tvPaso;
    private View btnIniciar;
    private SelectorPokemon selector;

    private final ActivityResultLauncher<Intent> lanzadorArena = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            resultado -> {
                Intent datos = resultado.getData();
                if (resultado.getResultCode() != RESULT_OK || datos == null) return;

                boolean ganoJugador1 = datos.getBooleanExtra(BattleArenaActivity.RESULTADO_GANO_A, true);
                UsuarioManager.registrarResultadoBatalla(ganoJugador1);

                mostrarGanador(ganoJugador1,
                        datos.getIntExtra(BattleArenaActivity.RESULTADO_INDICE_GANADOR, 0),
                        datos.getIntExtra(BattleArenaActivity.RESULTADO_HP_GANADOR, 0),
                        datos.getIntExtra(BattleArenaActivity.RESULTADO_TURNOS, 0));
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_versus);

        scroll = findViewById(R.id.scrollVersus);
        slots1 = findViewById(R.id.slotsJugador1);
        slots2 = findViewById(R.id.slotsJugador2);
        tvTitulo1 = findViewById(R.id.tvTituloJugador1);
        tvTitulo2 = findViewById(R.id.tvTituloJugador2);
        tvPaso = findViewById(R.id.tvPasoVersus);
        btnIniciar = findViewById(R.id.btnIniciarVersus);

        selector = new SelectorPokemon(this, findViewById(R.id.selectorVersus), scroll, this::agregarAlEquipo);
        selector.setValidador(p -> {
            List<Pokemon> equipo = equipoQueElige();
            if (equipo == null) return "Los dos equipos ya están completos";
            for (Pokemon q : equipo) {
                if (q.getId() == p.getId()) return "Ese Pokémon ya está en el equipo del " + jugadorQueElige();
            }
            return null;
        });

        findViewById(R.id.btnRegresar).setOnClickListener(v -> finish());
        findViewById(R.id.btnHistorial).setOnClickListener(v ->
                startActivity(HistorialActivity.crearIntent(this, HistorialManager.MODO_VERSUS)));
        btnIniciar.setOnClickListener(v -> iniciarBatalla());

        actualizarVista();
    }

    // ------------------------------------------------------------------
    // Armar los equipos
    // ------------------------------------------------------------------

    /** El Jugador 1 elige primero; cuando completa sus 3, elige el Jugador 2. */
    private List<Pokemon> equipoQueElige() {
        if (equipo1.size() < TAM_EQUIPO) return equipo1;
        if (equipo2.size() < TAM_EQUIPO) return equipo2;
        return null;
    }

    private String jugadorQueElige() {
        return equipoQueElige() == equipo1 ? JUGADOR_1 : JUGADOR_2;
    }

    private void agregarAlEquipo(Pokemon pokemon) {
        List<Pokemon> equipo = equipoQueElige();
        if (equipo == null) return;
        equipo.add(pokemon);
        actualizarVista();
    }

    private void actualizarVista() {
        pintarSlots(slots1, equipo1);
        pintarSlots(slots2, equipo2);
        tvTitulo1.setText("🔴 " + JUGADOR_1 + " · " + equipo1.size() + "/" + TAM_EQUIPO);
        tvTitulo2.setText("🔵 " + JUGADOR_2 + " · " + equipo2.size() + "/" + TAM_EQUIPO);

        List<Pokemon> eligiendo = equipoQueElige();
        boolean listos = eligiendo == null;

        btnIniciar.setVisibility(listos ? View.VISIBLE : View.GONE);
        selector.setVisible(!listos);

        if (listos) {
            tvPaso.setText("¡Equipos listos! Toca un Pokémon con ✕ para cambiarlo.");
        } else {
            String jugador = jugadorQueElige();
            tvPaso.setText(jugador + " · elige tu Pokémon " + (eligiendo.size() + 1) + " de " + TAM_EQUIPO);
            selector.setTextoAceptar("Agregar al equipo del " + jugador);
        }
    }

    /** Tres lugares por equipo; tocar uno lleno lo quita del equipo. */
    private void pintarSlots(LinearLayout contenedor, List<Pokemon> equipo) {
        contenedor.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (int i = 0; i < TAM_EQUIPO; i++) {
            View slot = inflater.inflate(R.layout.item_slot_equipo, contenedor, false);
            ImageView img = slot.findViewById(R.id.imgSlot);
            TextView vacio = slot.findViewById(R.id.tvVacioSlot);
            TextView nombre = slot.findViewById(R.id.tvNombreSlot);
            View quitar = slot.findViewById(R.id.tvQuitarSlot);

            if (i < equipo.size()) {
                Pokemon p = equipo.get(i);
                BattlePokemon datos = new BattlePokemon(p);
                Glide.with(this).load(datos.imagen).into(img);
                nombre.setText(datos.nombre);
                vacio.setVisibility(View.GONE);
                quitar.setVisibility(View.VISIBLE);
                slot.setOnClickListener(v -> {
                    equipo.remove(p);
                    selector.limpiar();
                    actualizarVista();
                });
            } else {
                img.setImageDrawable(null);
                nombre.setText("");
                vacio.setVisibility(View.VISIBLE);
                quitar.setVisibility(View.GONE);
            }
            contenedor.addView(slot);
        }
    }

    // ------------------------------------------------------------------
    // Batalla y resultado
    // ------------------------------------------------------------------

    private void iniciarBatalla() {
        if (equipoQueElige() != null) return;
        lanzadorArena.launch(BattleArenaActivity.crearIntentEquipos(this,
                equipo1, equipo2, JUGADOR_1, JUGADOR_2, true, true,
                HistorialManager.MODO_VERSUS, -1));
    }

    private void mostrarGanador(boolean ganoJugador1, int indice, int hpRestante, int turnos) {
        List<Pokemon> equipoGanador = ganoJugador1 ? equipo1 : equipo2;
        if (equipoGanador.isEmpty()) return;
        BattlePokemon pokemon = new BattlePokemon(equipoGanador.get(Math.min(indice, equipoGanador.size() - 1)));

        View vista = LayoutInflater.from(this).inflate(R.layout.dialog_ganador, null);
        Glide.with(this).load(pokemon.imagen).into((ImageView) vista.findViewById(R.id.imgGanador));
        ((TextView) vista.findViewById(R.id.tvGanador)).setText(ganoJugador1 ? JUGADOR_1 : JUGADOR_2);
        ((TextView) vista.findViewById(R.id.tvContra)).setText(
                "venció a " + (ganoJugador1 ? JUGADOR_2 : JUGADOR_1) + " con " + pokemon.nombre);
        ((TextView) vista.findViewById(R.id.tvHpGanador)).setText(hpRestante + " / " + pokemon.hpMaxima);
        ((TextView) vista.findViewById(R.id.tvTurnosGanador)).setText(String.valueOf(turnos));
        ((TextView) vista.findViewById(R.id.btnElegirOtro)).setText("Cambiar equipos");

        AlertDialog dialogo = new AlertDialog.Builder(this).setView(vista).create();
        vista.findViewById(R.id.btnRevancha).setOnClickListener(v -> {
            dialogo.dismiss();
            iniciarBatalla();
        });
        vista.findViewById(R.id.btnElegirOtro).setOnClickListener(v -> {
            dialogo.dismiss();
            scroll.smoothScrollTo(0, 0);
        });
        vista.findViewById(R.id.btnMenu).setOnClickListener(v -> {
            dialogo.dismiss();
            finish();
        });

        dialogo.show();
        if (dialogo.getWindow() != null) {
            dialogo.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        vista.setScaleX(0.8f);
        vista.setScaleY(0.8f);
        vista.setAlpha(0f);
        vista.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(300)
                .setInterpolator(new OvershootInterpolator()).start();
    }
}
