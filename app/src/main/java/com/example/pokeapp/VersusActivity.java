package com.example.pokeapp;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
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
import com.example.pokeapp.data.TypeColors;
import com.example.pokeapp.data.UsuarioManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Battle Versus: dos jugadores en el mismo celular, 3 Pokémon cada uno.
 * Cada "+" abre ElegirPokemonActivity y al aceptar regresa con ese lugar lleno.
 * Pelean por turnos en la arena (cada uno toca Atacar y elige quién entra cuando
 * cae uno). El resultado cuenta como victoria/derrota del Jugador 1 (el usuario
 * con sesión) y se guarda en el Historial.
 */
public class VersusActivity extends AppCompatActivity {

    public static final int TAM_EQUIPO = 3;
    private static final String JUGADOR_1 = "Jugador 1";
    private static final String JUGADOR_2 = "Jugador 2";

    /** Lugares de cada equipo (null = vacío); se pueden llenar en cualquier orden. */
    private final Pokemon[] equipo1 = new Pokemon[TAM_EQUIPO];
    private final Pokemon[] equipo2 = new Pokemon[TAM_EQUIPO];
    private Pokemon[] equipoEligiendo;   // a qué equipo va el Pokémon que regresa

    private NestedScrollView scroll;
    private LinearLayout slots1, slots2;
    private TextView tvTitulo1, tvTitulo2, tvPaso;
    private View btnIniciar;

    /** Regresa de "Elegir Pokémon" con el Pokémon y el lugar que se llenó. */
    private final ActivityResultLauncher<Intent> lanzadorElegir = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            resultado -> {
                Pokemon elegido = ElegirPokemonActivity.leerResultado(resultado.getData());
                if (resultado.getResultCode() != RESULT_OK || elegido == null || equipoEligiendo == null) return;
                int posicion = resultado.getData().getIntExtra(ElegirPokemonActivity.EXTRA_POSICION, 0);
                equipoEligiendo[posicion] = elegido;
                actualizarVista();
            });

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

        findViewById(R.id.btnRegresar).setOnClickListener(v -> finish());
        findViewById(R.id.btnHistorial).setOnClickListener(v ->
                startActivity(HistorialActivity.crearIntent(this, HistorialManager.MODO_VERSUS)));
        btnIniciar.setOnClickListener(v -> iniciarBatalla());

        actualizarVista();
    }

    // ------------------------------------------------------------------
    // Armar los equipos
    // ------------------------------------------------------------------

    private void actualizarVista() {
        pintarSlots(slots1, equipo1, JUGADOR_1);
        pintarSlots(slots2, equipo2, JUGADOR_2);
        tvTitulo1.setText("🔴 " + JUGADOR_1 + " · " + contar(equipo1) + "/" + TAM_EQUIPO);
        tvTitulo2.setText("🔵 " + JUGADOR_2 + " · " + contar(equipo2) + "/" + TAM_EQUIPO);

        boolean listos = contar(equipo1) == TAM_EQUIPO && contar(equipo2) == TAM_EQUIPO;
        btnIniciar.setEnabled(listos);
        btnIniciar.setAlpha(listos ? 1f : 0.5f);
        tvPaso.setText(listos
                ? "¡Equipos listos! Toca un Pokémon para cambiarlo o su ✕ para quitarlo."
                : "Toca un + para agregar un Pokémon (faltan "
                        + (2 * TAM_EQUIPO - contar(equipo1) - contar(equipo2)) + ")");
    }

    private int contar(Pokemon[] equipo) {
        int n = 0;
        for (Pokemon p : equipo) if (p != null) n++;
        return n;
    }

    /** Tres lugares por equipo: "+" abre el buscador; un Pokémon se reemplaza al tocarlo o se quita con ✕. */
    private void pintarSlots(LinearLayout contenedor, Pokemon[] equipo, String jugador) {
        contenedor.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        float dp = getResources().getDisplayMetrics().density;

        for (int i = 0; i < TAM_EQUIPO; i++) {
            final int posicion = i;
            View slot = inflater.inflate(R.layout.item_slot_equipo, contenedor, false);
            ImageView img = slot.findViewById(R.id.imgSlot);
            TextView vacio = slot.findViewById(R.id.tvVacioSlot);
            TextView nombre = slot.findViewById(R.id.tvNombreSlot);
            View quitar = slot.findViewById(R.id.tvQuitarSlot);
            Pokemon p = equipo[i];

            if (p != null) {
                BattlePokemon datos = new BattlePokemon(p);
                Glide.with(this).load(datos.imagen).into(img);
                nombre.setText(datos.nombre);
                vacio.setVisibility(View.GONE);
                quitar.setVisibility(View.VISIBLE);

                // Fondo con el color de su tipo principal
                int color = TypeColors.obtenerColor(datos.tipos.get(0));
                GradientDrawable fondo = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                        new int[]{color, 0xFFFFFFFF});
                fondo.setCornerRadius(16 * dp);
                fondo.setStroke(Math.round(2 * dp), color);
                slot.findViewById(R.id.fondoSlot).setBackground(fondo);

                quitar.setOnClickListener(v -> {
                    equipo[posicion] = null;
                    actualizarVista();
                });
            } else {
                img.setImageDrawable(null);
                nombre.setText("Agregar");
                vacio.setVisibility(View.VISIBLE);
                quitar.setVisibility(View.GONE);
            }

            slot.setOnClickListener(v -> abrirElegir(equipo, jugador, posicion));
            contenedor.addView(slot);
        }
    }

    private void abrirElegir(Pokemon[] equipo, String jugador, int posicion) {
        // No se puede repetir dentro del mismo equipo (sí se puede reemplazar el del mismo lugar)
        ArrayList<Integer> excluir = new ArrayList<>();
        for (int i = 0; i < equipo.length; i++) {
            if (equipo[i] != null && i != posicion) excluir.add(equipo[i].getId());
        }

        equipoEligiendo = equipo;
        lanzadorElegir.launch(ElegirPokemonActivity.crearIntent(this, jugador,
                jugador + " · lugar " + (posicion + 1) + " de " + TAM_EQUIPO, posicion, excluir));
    }

    // ------------------------------------------------------------------
    // Batalla y resultado
    // ------------------------------------------------------------------

    private List<Pokemon> lista(Pokemon[] equipo) {
        List<Pokemon> l = new ArrayList<>();
        for (Pokemon p : equipo) if (p != null) l.add(p);
        return l;
    }

    private void iniciarBatalla() {
        if (contar(equipo1) < TAM_EQUIPO || contar(equipo2) < TAM_EQUIPO) return;
        lanzadorArena.launch(BattleArenaActivity.crearIntentEquipos(this,
                lista(equipo1), lista(equipo2), JUGADOR_1, JUGADOR_2, true, true,
                HistorialManager.MODO_VERSUS, -1));
    }

    private void mostrarGanador(boolean ganoJugador1, int indice, int hpRestante, int turnos) {
        List<Pokemon> equipoGanador = lista(ganoJugador1 ? equipo1 : equipo2);
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
