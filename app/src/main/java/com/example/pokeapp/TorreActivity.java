package com.example.pokeapp;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.GridLayout;
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
import com.example.pokeapp.battle.Escenario;
import com.example.pokeapp.data.CargadorPokemon;
import com.example.pokeapp.data.HistorialManager;
import com.example.pokeapp.data.Pokemon;
import com.example.pokeapp.data.PokemonStat;
import com.example.pokeapp.data.UsuarioManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Torre Pokémon (módulo D del PDF):
 *  1. La app genera 6 Pokémon al azar y el usuario elige 3.
 *  2. Seis niveles seguidos contra rivales al azar del sistema, cada uno con su docente CESBA.
 *  3. El nivel 6 es el jefe final con 4 legendarios al azar.
 *  4. Si gana los 6 niveles en la misma campaña obtiene una medalla (hasta 10).
 * El equipo se cura al empezar cada nivel. Las batallas usan la arena compartida:
 * el usuario toca Atacar y el sistema responde solo.
 */
public class TorreActivity extends AppCompatActivity {

    private static final int NIVELES = 6;
    private static final int OPCIONES = 6;
    private static final int TAM_EQUIPO = 3;

    /** Pokémon rivales por nivel (el 6 son legendarios). */
    private static final int[] RIVALES_POR_NIVEL = {1, 2, 2, 3, 3, 4};
    /** Escenario de cada nivel (índice en Escenario.TODOS); el jefe pelea en el gimnasio. */
    private static final int[] ESCENARIO_POR_NIVEL = {0, 3, 2, 1, 5, 4};

    private static final Random random = new Random();

    private NestedScrollView scroll;
    private View seccionInicio, seccionEleccion, seccionNivel;
    private LinearLayout listaNiveles, filaRivales, filaTuEquipo;
    private GridLayout gridOpciones;
    private TextView tvMedallas, tvElegidos, tvNivelActual, tvDocente, tvEscenarioNivel, tvMensajeNivel;
    private ImageView imgDocente;
    private View progressEleccion, progressRivales, btnConfirmar, btnPelear;

    private final List<Pokemon> opciones = new ArrayList<>();
    private final List<Pokemon> elegidos = new ArrayList<>();
    private final List<Pokemon> equipo = new ArrayList<>();
    private final List<Pokemon> rivales = new ArrayList<>();
    private int nivel = 0;   // 0 = sin campaña

    private final ActivityResultLauncher<Intent> lanzadorArena = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            resultado -> {
                Intent datos = resultado.getData();
                if (resultado.getResultCode() != RESULT_OK || datos == null) return;   // salió con ✕
                alTerminarNivel(datos.getBooleanExtra(BattleArenaActivity.RESULTADO_GANO_A, false));
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_torre);

        scroll = findViewById(R.id.scrollTorre);
        seccionInicio = findViewById(R.id.seccionInicio);
        seccionEleccion = findViewById(R.id.seccionEleccion);
        seccionNivel = findViewById(R.id.seccionNivel);
        listaNiveles = findViewById(R.id.listaNiveles);
        filaRivales = findViewById(R.id.filaRivales);
        filaTuEquipo = findViewById(R.id.filaTuEquipo);
        gridOpciones = findViewById(R.id.gridOpciones);
        tvMedallas = findViewById(R.id.tvMedallasTorre);
        tvElegidos = findViewById(R.id.tvElegidos);
        tvNivelActual = findViewById(R.id.tvNivelActual);
        tvDocente = findViewById(R.id.tvDocente);
        tvEscenarioNivel = findViewById(R.id.tvEscenarioNivel);
        tvMensajeNivel = findViewById(R.id.tvMensajeNivel);
        imgDocente = findViewById(R.id.imgDocente);
        progressEleccion = findViewById(R.id.progressEleccion);
        progressRivales = findViewById(R.id.progressRivales);
        btnConfirmar = findViewById(R.id.btnConfirmarEquipo);
        btnPelear = findViewById(R.id.btnPelearNivel);

        findViewById(R.id.btnRegresar).setOnClickListener(v -> finish());
        findViewById(R.id.btnHistorial).setOnClickListener(v ->
                startActivity(HistorialActivity.crearIntent(this, HistorialManager.MODO_TORRE)));
        findViewById(R.id.btnComenzarTorre).setOnClickListener(v -> comenzarCampania());
        btnConfirmar.setOnClickListener(v -> confirmarEquipo());
        btnPelear.setOnClickListener(v -> pelear());
        findViewById(R.id.btnAbandonar).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("¿Abandonar la campaña?")
                .setMessage("Perderás el avance de esta Torre.")
                .setPositiveButton("Abandonar", (d, w) -> mostrarInicio())
                .setNegativeButton("Seguir", null)
                .show());

        mostrarInicio();
    }

    // ------------------------------------------------------------------
    // Inicio: escalera de niveles
    // ------------------------------------------------------------------

    private void mostrarInicio() {
        nivel = 0;
        equipo.clear();
        mostrarSeccion(seccionInicio);
        pintarEscalera();
        UsuarioManager.obtenerMedallas(total -> {
            if (!isFinishing() && !isDestroyed()) {
                tvMedallas.setText("🏅 Medallas: " + total + " / " + UsuarioManager.MAX_MEDALLAS);
            }
        });
    }

    private void pintarEscalera() {
        listaNiveles.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        // De arriba (jefe) hacia abajo (nivel 1), como una torre
        for (int n = NIVELES; n >= 1; n--) {
            View fila = inflater.inflate(R.layout.item_torre_nivel, listaNiveles, false);
            ((ImageView) fila.findViewById(R.id.imgDocenteNivel)).setImageResource(fotoDocente(n));
            fila.findViewById(R.id.imgDocenteNivel).setClipToOutline(true);
            ((TextView) fila.findViewById(R.id.tvTituloNivel)).setText(
                    "Nivel " + n + " · " + nombreDocente(n));
            ((TextView) fila.findViewById(R.id.tvDetalleNivel)).setText(n == NIVELES
                    ? "👑 Jefe final · 4 legendarios"
                    : RIVALES_POR_NIVEL[n - 1] + (RIVALES_POR_NIVEL[n - 1] == 1 ? " Pokémon rival" : " Pokémon rivales")
                    + " · " + Escenario.TODOS.get(ESCENARIO_POR_NIVEL[n - 1]).nombre);
            ((TextView) fila.findViewById(R.id.tvEstadoNivel)).setText(
                    nivel == 0 ? (n == NIVELES ? "👑" : "🔒") : n < nivel ? "✅" : n == nivel ? "⚔️" : "🔒");
            listaNiveles.addView(fila);
        }
    }

    // ------------------------------------------------------------------
    // Elección: 3 de 6 Pokémon al azar
    // ------------------------------------------------------------------

    private void comenzarCampania() {
        opciones.clear();
        elegidos.clear();
        gridOpciones.removeAllViews();
        actualizarElegidos();
        mostrarSeccion(seccionEleccion);
        progressEleccion.setVisibility(View.VISIBLE);

        CargadorPokemon.cargar(CargadorPokemon.idsAleatorios(OPCIONES, false, random), lista -> {
            if (isFinishing() || isDestroyed()) return;
            progressEleccion.setVisibility(View.GONE);
            if (lista == null) {
                avisoSinConexion(this::comenzarCampania);
                return;
            }
            opciones.addAll(lista);
            pintarOpciones();
        });
    }

    private void pintarOpciones() {
        gridOpciones.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (Pokemon p : opciones) {
            View tarjeta = inflater.inflate(R.layout.item_torre_opcion, gridOpciones, false);
            BattlePokemon datos = new BattlePokemon(p);
            Glide.with(this).load(datos.imagen).into((ImageView) tarjeta.findViewById(R.id.imgOpcion));
            ((TextView) tarjeta.findViewById(R.id.tvNombreOpcion)).setText(datos.nombre);
            ((TextView) tarjeta.findViewById(R.id.tvTiposOpcion)).setText(datos.tiposTexto());
            ((TextView) tarjeta.findViewById(R.id.tvStatsOpcion)).setText(statsCortos(p));

            View marca = tarjeta.findViewById(R.id.tvMarcaOpcion);
            tarjeta.setOnClickListener(v -> {
                if (elegidos.contains(p)) {
                    elegidos.remove(p);
                } else if (elegidos.size() < TAM_EQUIPO) {
                    elegidos.add(p);
                }
                boolean elegido = elegidos.contains(p);
                marca.setVisibility(elegido ? View.VISIBLE : View.GONE);
                tarjeta.setBackgroundResource(elegido ? R.drawable.bg_mini_selected : R.drawable.bg_mini_normal);
                actualizarElegidos();
            });
            gridOpciones.addView(tarjeta);
        }
    }

    private void actualizarElegidos() {
        tvElegidos.setText("Elegidos: " + elegidos.size() + " / " + TAM_EQUIPO);
        boolean listo = elegidos.size() == TAM_EQUIPO;
        btnConfirmar.setEnabled(listo);
        btnConfirmar.setAlpha(listo ? 1f : 0.5f);
    }

    private void confirmarEquipo() {
        if (elegidos.size() != TAM_EQUIPO) return;
        equipo.clear();
        equipo.addAll(elegidos);
        irANivel(1);
    }

    // ------------------------------------------------------------------
    // Niveles
    // ------------------------------------------------------------------

    private void irANivel(int n) {
        nivel = n;
        rivales.clear();
        mostrarSeccion(seccionNivel);

        tvNivelActual.setText(n == NIVELES ? "👑 NIVEL 6 · JEFE FINAL" : "NIVEL " + n + " DE " + NIVELES);
        imgDocente.setImageResource(fotoDocente(n));
        tvDocente.setText(nombreDocente(n));
        tvEscenarioNivel.setText("📍 " + Escenario.TODOS.get(ESCENARIO_POR_NIVEL[n - 1]).nombre);
        tvMensajeNivel.setVisibility(View.GONE);
        pintarFila(filaTuEquipo, equipo);
        filaRivales.removeAllViews();

        btnPelear.setEnabled(false);
        btnPelear.setAlpha(0.5f);
        progressRivales.setVisibility(View.VISIBLE);

        boolean jefe = n == NIVELES;
        CargadorPokemon.cargar(CargadorPokemon.idsAleatorios(RIVALES_POR_NIVEL[n - 1], jefe, random), lista -> {
            if (isFinishing() || isDestroyed() || nivel != n) return;
            progressRivales.setVisibility(View.GONE);
            if (lista == null) {
                tvMensajeNivel.setText("No se pudieron cargar los rivales. Revisa tu conexión.");
                tvMensajeNivel.setVisibility(View.VISIBLE);
                avisoSinConexion(() -> irANivel(n));
                return;
            }
            rivales.addAll(lista);
            pintarFila(filaRivales, rivales);
            btnPelear.setEnabled(true);
            btnPelear.setAlpha(1f);
        });
    }

    /** Sprites con nombre en una fila (rivales o tu equipo). */
    private void pintarFila(LinearLayout fila, List<Pokemon> pokemon) {
        fila.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Pokemon p : pokemon) {
            View slot = inflater.inflate(R.layout.item_slot_equipo, fila, false);
            BattlePokemon datos = new BattlePokemon(p);
            Glide.with(this).load(datos.imagen).into((ImageView) slot.findViewById(R.id.imgSlot));
            ((TextView) slot.findViewById(R.id.tvNombreSlot)).setText(datos.nombre);
            slot.findViewById(R.id.tvVacioSlot).setVisibility(View.GONE);
            slot.setClickable(false);
            fila.addView(slot);
        }
    }

    private void pelear() {
        if (rivales.isEmpty() || equipo.isEmpty()) return;
        lanzadorArena.launch(BattleArenaActivity.crearIntentEquipos(this,
                equipo, rivales,
                "Tú", "Nivel " + nivel + " · " + nombreDocente(nivel),
                true, false,
                HistorialManager.MODO_TORRE, ESCENARIO_POR_NIVEL[nivel - 1]));
    }

    private void alTerminarNivel(boolean gano) {
        if (!gano) {
            mostrarResultado("💥", "Caíste en el nivel " + nivel,
                    "La campaña terminó. Para ganar la medalla hay que superar los 6 niveles seguidos.",
                    "Nueva campaña", this::comenzarCampania,
                    "Salir", this::mostrarInicio);
            return;
        }

        if (nivel < NIVELES) {
            int siguiente = nivel + 1;
            mostrarResultado("🏆", "¡Nivel " + nivel + " superado!",
                    "Venciste a " + nombreDocente(nivel) + ". Tu equipo se cura para el siguiente nivel."
                            + (siguiente == NIVELES ? "\n\n👑 Sigue el jefe final con 4 legendarios." : ""),
                    "Ir al nivel " + siguiente, () -> irANivel(siguiente),
                    "Abandonar", this::mostrarInicio);
            return;
        }

        // ¡Torre completada! → medalla
        UsuarioManager.otorgarMedalla(total -> {
            if (isFinishing() || isDestroyed()) return;
            String mensaje = total < 0
                    ? "Conquistaste la Torre, pero no se pudo guardar la medalla. Revisa tu conexión."
                    : "Ganaste la medalla #" + total + " de " + UsuarioManager.MAX_MEDALLAS + ".";
            mostrarResultado("🏅", "¡Conquistaste la Torre!", mensaje,
                    "Nueva campaña", this::comenzarCampania,
                    "Salir", this::mostrarInicio);
        });
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private void mostrarSeccion(View seccion) {
        seccionInicio.setVisibility(seccion == seccionInicio ? View.VISIBLE : View.GONE);
        seccionEleccion.setVisibility(seccion == seccionEleccion ? View.VISIBLE : View.GONE);
        seccionNivel.setVisibility(seccion == seccionNivel ? View.VISIBLE : View.GONE);
        scroll.post(() -> scroll.scrollTo(0, 0));
    }

    private String nombreDocente(int n) {
        return "Docente CESBA " + n;
    }

    /** Foto autorizada res/drawable-nodpi/docente_<n> si existe; si no, la silueta. */
    private int fotoDocente(int n) {
        int foto = getResources().getIdentifier("docente_" + n, "drawable", getPackageName());
        return foto != 0 ? foto : R.drawable.ic_docente_silueta;
    }

    private String statsCortos(Pokemon pokemon) {
        int hp = 0, atk = 0, def = 0, vel = 0;
        if (pokemon.getStats() != null) {
            for (PokemonStat s : pokemon.getStats()) {
                switch (s.getStat().getName()) {
                    case "hp": hp = s.getBaseStat(); break;
                    case "attack": atk = s.getBaseStat(); break;
                    case "defense": def = s.getBaseStat(); break;
                    case "speed": vel = s.getBaseStat(); break;
                    default: break;
                }
            }
        }
        return "HP " + hp + " · ATK " + atk + "\nDEF " + def + " · VEL " + vel;
    }

    private void avisoSinConexion(Runnable reintentar) {
        new AlertDialog.Builder(this)
                .setTitle("Sin conexión")
                .setMessage("No se pudieron cargar los Pokémon de la PokéAPI.")
                .setPositiveButton("Reintentar", (d, w) -> reintentar.run())
                .setNegativeButton("Salir", (d, w) -> mostrarInicio())
                .show();
    }

    private void mostrarResultado(String emoji, String titulo, String mensaje,
                                  String textoPrincipal, Runnable principal,
                                  String textoSecundario, Runnable secundario) {
        View vista = LayoutInflater.from(this).inflate(R.layout.dialog_torre_resultado, null);
        ((TextView) vista.findViewById(R.id.tvEmojiResultado)).setText(emoji);
        ((TextView) vista.findViewById(R.id.tvTituloResultado)).setText(titulo);
        ((TextView) vista.findViewById(R.id.tvMensajeResultado)).setText(mensaje);
        TextView btnPrincipal = vista.findViewById(R.id.btnPrincipalResultado);
        TextView btnSecundario = vista.findViewById(R.id.btnSecundarioResultado);
        btnPrincipal.setText(textoPrincipal);
        btnSecundario.setText(textoSecundario);

        AlertDialog dialogo = new AlertDialog.Builder(this).setView(vista).setCancelable(false).create();
        btnPrincipal.setOnClickListener(v -> {
            dialogo.dismiss();
            principal.run();
        });
        btnSecundario.setOnClickListener(v -> {
            dialogo.dismiss();
            secundario.run();
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
