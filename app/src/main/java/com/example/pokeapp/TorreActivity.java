package com.example.pokeapp;

import com.example.pokeapp.data.Idioma;
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
import com.google.gson.Gson;
import com.example.pokeapp.battle.Escenario;
import com.example.pokeapp.data.CargadorPokemon;
import com.example.pokeapp.data.HistorialManager;
import com.example.pokeapp.data.Pokemon;
import com.example.pokeapp.data.PokemonStat;
import com.example.pokeapp.data.UsuarioManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Torre Pokémon (módulo D del PDF):
 *  1. La app genera 6 Pokémon al azar (mínimo 2 fuertes o pseudolegendarios; los otros 4 con 20 % de ser legendarios) y el usuario elige 3.
 *  2. Seis niveles seguidos contra rivales al azar del sistema, cada uno con su docente CESBA.
 *  3. El nivel 6 es el jefe final con 4 legendarios al azar.
 *  4. Si gana los 6 niveles en la misma campaña obtiene una medalla (la siguiente de las 10).
 *  5. Extra: al vencer al jefe final se desbloquea un jefe secreto con 5 legendarios (no da medalla).
 * El equipo se cura al empezar cada nivel. Las batallas usan la arena compartida:
 * el usuario toca Atacar y el sistema responde solo.
 */
public class TorreActivity extends AppCompatActivity {

    private static final int NIVELES = 6;
    /** Niveles con docente (sin el jefe secreto); lo usa también ¿Quién es ese Pokémon?. */
    static final int NIVELES_NORMALES = NIVELES;
    private static final int SECRETO = NIVELES + 1;   // jefe secreto: solo aparece al vencer al jefe final
    /** Frase con la que se presenta el jefe secreto al empezar su batalla. */
    private static final String FRASE_SECRETO = "Empezaremos la batalla POKÉMON con A de mujer";
    private static final int OPCIONES = 6;
    private static final int OPCIONES_FUERTES = 2;   // mínimo de pseudolegendarios o fuertes entre las 6 opciones
    /** Probabilidad (%) de que cada una de las otras 4 opciones sea legendaria (~59 % de que salga al menos uno). */
    private static final int PROBABILIDAD_LEGENDARIO = 20;
    private static final int TAM_EQUIPO = 3;

    /** Pokémon rivales por nivel: 3 desde el nivel 1 (así lo pidió el profesor); el 6 son 4 legendarios y el secreto, 5. */
    private static final int[] RIVALES_POR_NIVEL = {3, 3, 3, 3, 3, 4, 5};
    /** Escenario de cada nivel (índice en Escenario.TODOS); el jefe final pelea en la montaña nevada. */
    private static final int[] ESCENARIO_POR_NIVEL = {0, 3, 2, 1, 4, 1, 4};

    private static final Random random = new Random();

    private NestedScrollView scroll;
    private View seccionInicio, seccionEleccion, seccionNivel, cabecera, contenido;
    private android.animation.ObjectAnimator pulso;   // latido del piso 1 ("toca para comenzar")
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
    private Pokemon companero;   // compañero de Mi perfil: entra al rescate contra Badi y el jefe secreto
    private boolean secretoDescubierto;   // el piso secreto solo se dibuja si ya se conquistó la Torre alguna vez

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
        cabecera = findViewById(R.id.cabeceraTorre);
        contenido = findViewById(R.id.contenidoTorre);
        findViewById(R.id.btnRegresarTorre).setOnClickListener(v -> finish());
        findViewById(R.id.btnHistorialTorre).setOnClickListener(v ->
                startActivity(HistorialActivity.crearIntent(this, HistorialManager.MODO_TORRE)));
        btnConfirmar.setOnClickListener(v -> confirmarEquipo());
        btnPelear.setOnClickListener(v -> pelear());
        findViewById(R.id.btnAbandonar).setOnClickListener(v ->
                Dialogos.confirmar(this, "🏳️", Idioma.t("¿Abandonar la campaña?", "Give up the campaign?"),
                        Idioma.t("Perderás el avance de esta Torre y tendrás que empezar desde el nivel 1.", "You will lose your progress in this Tower and start again from level 1."),
                        Idioma.t("Abandonar", "Give up"), Idioma.t("Seguir peleando", "Keep fighting"), this::mostrarInicio, null));

        mostrarInicio();
    }

    /** Carga el compañero elegido en Mi perfil (si lo hay) para los niveles de jefe. */
    private void cargarCompanero() {
        UsuarioManager.obtenerCompanero(id -> {
            if (id == null || id <= 0) {
                companero = null;
                return;
            }
            CargadorPokemon.cargar(Collections.singletonList(id), lista -> {
                if (lista != null && !lista.isEmpty()) companero = lista.get(0);
            });
        });
    }

    // ------------------------------------------------------------------
    // Inicio: escalera de niveles
    // ------------------------------------------------------------------

    private void mostrarInicio() {
        cargarCompanero();   // por si lo cambió en Mi perfil
        nivel = 0;
        equipo.clear();
        mostrarSeccion(seccionInicio);
        pintarEscalera();
        UsuarioManager.obtenerMedallasConFecha((total, fechas) -> {
            if (!isFinishing() && !isDestroyed()) {
                tvMedallas.setText(Idioma.t("🏅 Medallas: ", "🏅 Medals: ") + total + " / " + UsuarioManager.MAX_MEDALLAS);
                // Tener la medalla del jefe final = ya lo venció alguna vez: se revela el piso secreto
                boolean descubierto = total > 0;   // ya completó la Torre alguna vez
                if (descubierto != secretoDescubierto) {
                    secretoDescubierto = descubierto;
                    if (nivel == 0) pintarEscalera();
                }
            }
        });
    }

    /**
     * Dibuja la Torre como una pagoda dorada que ocupa toda la pantalla: un piso por nivel, el más alto arriba.
     * La campaña comienza tocando el piso del nivel 1.
     */
    private void pintarEscalera() {
        if (pulso != null) pulso.cancel();
        listaNiveles.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        float dp = getResources().getDisplayMetrics().density;

        // Remate dorado en la punta
        ImageView punta = new ImageView(this);
        punta.setImageResource(R.drawable.ic_torre_punta);
        LinearLayout.LayoutParams lpPunta = new LinearLayout.LayoutParams(Math.round(48 * dp), Math.round(32 * dp));
        lpPunta.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        lpPunta.bottomMargin = Math.round(-4 * dp);
        listaNiveles.addView(punta, lpPunta);

        // El piso del jefe secreto no existe a la vista hasta haber conquistado la Torre alguna vez
        if (secretoDescubierto) {
            agregarPiso(inflater, SECRETO, R.drawable.ic_torre_techo_secreto, fotoDocente(SECRETO),
                    Idioma.t("NIVEL SECRETO", "SECRET LEVEL"), nombreDocente(SECRETO),
                    Idioma.t("5 legendarios · Vence a ", "5 legendaries · Defeat ") + nombreDocente(NIVELES) + Idioma.t(" para retarlo", " to challenge them"), "🌟");
        }

        // De arriba (jefe) hacia abajo (nivel 1)
        for (int n = NIVELES; n >= 1; n--) {
            boolean jefe = n == NIVELES;
            agregarPiso(inflater, n, jefe ? R.drawable.ic_torre_techo_jefe : R.drawable.ic_torre_techo, fotoDocente(n),
                    jefe ? Idioma.t("NIVEL ", "LEVEL ") + n + Idioma.t(" · JEFE FINAL", " · FINAL BOSS") : n == 1 ? Idioma.t("NIVEL 1 · TOCA PARA COMENZAR", "LEVEL 1 · TAP TO START") : Idioma.t("NIVEL ", "LEVEL ") + n,
                    nombreDocente(n),
                    (jefe ? Idioma.t("4 legendarios", "4 legendaries") : RIVALES_POR_NIVEL[n - 1] + Idioma.t(" Pokémon rivales", " rival Pokémon"))
                            + " · " + Escenario.TODOS.get(ESCENARIO_POR_NIVEL[n - 1]).nombre,
                    nivel == 0 ? (jefe ? "👑" : n == 1 ? "▶️" : "🔒") : n < nivel ? "✅" : n == nivel ? "⚔️" : "🔒");
        }

        // El piso 1 es la entrada: late suavemente y al tocarlo se pasa a elegir Pokémon
        View entrada = listaNiveles.getChildAt(listaNiveles.getChildCount() - 1);
        entrada.setOnClickListener(v -> comenzarCampania());
        pulso = android.animation.ObjectAnimator.ofPropertyValuesHolder(entrada,
                android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.03f),
                android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.03f));
        pulso.setDuration(750);
        pulso.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        pulso.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        pulso.start();

        // Base de piedra (tres escalones, cada uno más ancho)
        int[] colores = {0xFF4A4438, 0xFF37322A, 0xFF26221C};
        for (int escalon = 0; escalon < colores.length; escalon++) {
            View base = new View(this);
            base.setBackgroundColor(colores[escalon]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, Math.round(7 * dp));
            int margen = Math.round((colores.length - 1 - escalon) * 7 * dp);
            lp.setMarginStart(margen);
            lp.setMarginEnd(margen);
            listaNiveles.addView(base, lp);
        }
    }

    /** Agrega un piso a la torre: cuanto más alto el nivel, más angosto (la torre se afina hacia arriba). */
    private void agregarPiso(LayoutInflater inflater, int n, int techo, int foto,
                             String numero, String nombre, String detalle, String estado) {
        View piso = inflater.inflate(R.layout.item_torre_nivel, listaNiveles, false);
        ((ImageView) piso.findViewById(R.id.imgTechoNivel)).setImageResource(techo);
        ImageView ventana = piso.findViewById(R.id.imgDocenteNivel);
        ventana.setImageResource(foto);
        ((TextView) piso.findViewById(R.id.tvNumeroNivel)).setText(numero);
        ((TextView) piso.findViewById(R.id.tvTituloNivel)).setText(nombre);
        ((TextView) piso.findViewById(R.id.tvDetalleNivel)).setText(detalle);
        ((TextView) piso.findViewById(R.id.tvEstadoNivel)).setText(estado);

        float dp = getResources().getDisplayMetrics().density;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        int margen = Math.round((n - 1) * 4 * dp);
        lp.setMarginStart(margen);
        lp.setMarginEnd(margen);
        listaNiveles.addView(piso, lp);
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

        CargadorPokemon.cargar(CargadorPokemon.idsConFuertes(OPCIONES, OPCIONES_FUERTES, PROBABILIDAD_LEGENDARIO, random), lista -> {
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
        tvElegidos.setText(Idioma.t("Elegidos: ", "Chosen: ") + elegidos.size() + " / " + TAM_EQUIPO);
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

        tvNivelActual.setText(n == SECRETO ? Idioma.t("❓ NIVEL SECRETO · JEFE SECRETO", "❓ SECRET LEVEL · SECRET BOSS")
                : n == NIVELES ? Idioma.t("👑 NIVEL 6 · JEFE FINAL", "👑 LEVEL 6 · FINAL BOSS") : Idioma.t("NIVEL ", "LEVEL ") + n + Idioma.t(" DE ", " OF ") + NIVELES);
        imgDocente.setImageResource(fotoDocente(n));
        tvDocente.setText(nombreDocente(n));
        tvEscenarioNivel.setText("📍 " + Escenario.TODOS.get(ESCENARIO_POR_NIVEL[n - 1]).nombre);
        tvMensajeNivel.setVisibility(View.GONE);
        if (n >= NIVELES && companero != null) {
            tvMensajeNivel.setText(Idioma.t("🤝 Si caen tus 3 Pokémon, ", "🤝 If your 3 Pokémon faint, ") + new BattlePokemon(companero).nombre + Idioma.t(" saldrá al rescate.", " will come to the rescue."));
            tvMensajeNivel.setVisibility(View.VISIBLE);
        }
        pintarFila(filaTuEquipo, equipo);
        filaRivales.removeAllViews();

        btnPelear.setEnabled(false);
        btnPelear.setAlpha(0.5f);
        progressRivales.setVisibility(View.VISIBLE);

        boolean jefe = n >= NIVELES;   // el jefe final y el secreto usan legendarios
        CargadorPokemon.cargar(CargadorPokemon.idsAleatorios(RIVALES_POR_NIVEL[n - 1], jefe, random), lista -> {
            if (isFinishing() || isDestroyed() || nivel != n) return;
            progressRivales.setVisibility(View.GONE);
            if (lista == null) {
                tvMensajeNivel.setText(Idioma.t("No se pudieron cargar los rivales. Revisa tu conexión.", "Could not load the rivals. Check your connection."));
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
        int personaje = personajeDocente(nivel);
        lanzadorArena.launch(BattleArenaActivity.crearIntentEquipos(this,
                        equipo, rivales,
                        Idioma.t("Tú", "You"), (nivel == SECRETO ? Idioma.t("Jefe secreto", "Secret boss") : Idioma.t("Nivel ", "Level ") + nivel) + " · " + nombreDocente(nivel),
                        true, false,
                        HistorialManager.MODO_TORRE, ESCENARIO_POR_NIVEL[nivel - 1])
                // Junto al Pokémon rival va el personaje del docente (nunca su foto); si aún no hay dibujo, no sale nadie
                .putExtra(BattleArenaActivity.EXTRA_ENTRENADOR_B, personaje)
                .putExtra(BattleArenaActivity.EXTRA_ENTRENADOR_CUERPO, true)
                .putExtra(BattleArenaActivity.EXTRA_FRASE_ENTRENADOR, nivel == SECRETO ? FRASE_SECRETO : null)
                // Contra Badi y el jefe secreto, el compañero de Mi perfil espera de reserva
                .putExtra(BattleArenaActivity.EXTRA_COMPANERO_A,
                        nivel >= NIVELES && companero != null ? new Gson().toJson(companero) : null));
    }

    /** Al volver de la arena. Solo vencer al jefe final (completar la Torre) da medalla. */
    private void alTerminarNivel(boolean gano) {
        if (!gano) {
            if (nivel == SECRETO) {
                mostrarResultado("💥", nombreDocente(SECRETO) + Idioma.t(" te venció", " defeated you"),
                        Idioma.t("Conservas tus medallas. Conquista otra vez la Torre para volver a retar al jefe secreto.", "You keep your medals. Conquer the Tower again to challenge the secret boss once more."),
                        Idioma.t("Nueva campaña", "New campaign"), this::comenzarCampania,
                        Idioma.t("Salir", "Exit"), this::mostrarInicio);
            } else {
                mostrarResultado("💥", Idioma.t("Caíste en el nivel ", "You fell on level ") + nivel,
                        Idioma.t("La campaña terminó. Para ganar una medalla hay que superar los 6 niveles seguidos.", "The campaign is over. To earn a medal you must clear all 6 levels in a row."),
                        Idioma.t("Nueva campaña", "New campaign"), this::comenzarCampania,
                        Idioma.t("Salir", "Exit"), this::mostrarInicio);
            }
            return;
        }

        int ganado = nivel;
        com.example.pokeapp.data.FavoritosManager.Resultado<List<Integer>> alGuardar = nuevas -> {
            if (isFinishing() || isDestroyed() || nivel != ganado) return;
            String premio = textoMedallas(nuevas);

            // Si ganó medalla, primero se ve la medalla girando y luego la ventana del resultado
            Runnable verResultado = () -> {
                if (ganado == SECRETO) {
                    mostrarResultado("🌟", Idioma.t("¡Venciste al jefe secreto!", "You defeated the secret boss!"),
                            Idioma.t("Derrotaste a ", "You defeated ") + nombreDocente(SECRETO) + Idioma.t(" y a sus 5 legendarios. ", " and their 5 legendaries. ")
                                    + Idioma.t("No queda nadie más fuerte en la Torre.", "There is no one stronger left in the Tower.") + premio,
                            Idioma.t("Nueva campaña", "New campaign"), this::comenzarCampania,
                            Idioma.t("Salir", "Exit"), this::mostrarInicio);
                } else if (ganado < NIVELES) {
                    int siguiente = ganado + 1;
                    mostrarResultado("🏆", Idioma.t("¡Nivel ", "Level ") + ganado + Idioma.t(" superado!", " cleared!"),
                            Idioma.t("Venciste a ", "You defeated ") + nombreDocente(ganado) + "." + premio
                                    + Idioma.t("\n\nTu equipo se cura para el siguiente nivel.", "\n\nYour team heals for the next level.")
                                    + (siguiente == NIVELES ? Idioma.t(" 👑 Sigue el jefe final con 4 legendarios.", " 👑 Next is the final boss with 4 legendaries.") : ""),
                            Idioma.t("Ir al nivel ", "Go to level ") + siguiente, () -> irANivel(siguiente),
                            Idioma.t("Abandonar", "Give up"), this::mostrarInicio);
                } else {
                    mostrarResultado("🏅", Idioma.t("¡Conquistaste la Torre!", "You conquered the Tower!"),
                            Idioma.t("Venciste a ", "You defeated ") + nombreDocente(ganado) + Idioma.t(", el jefe final.", ", the final boss.") + premio
                                    + Idioma.t("\n\n❓ Algo se mueve en lo más alto de la Torre… ¡Apareció un jefe secreto!", "\n\n❓ Something stirs at the top of the Tower… A secret boss appeared!"),
                            Idioma.t("Retar al jefe secreto", "Challenge the secret boss"), () -> irANivel(SECRETO),
                            Idioma.t("Salir", "Exit"), this::mostrarInicio);
                }
            };
            if (nuevas != null && !nuevas.isEmpty()) {
                Animaciones.medallaGanada(this, nuevas.get(0), verResultado);
            } else {
                verResultado.run();
            }
        };
        // El jefe secreto da la medalla 12 (una sola vez); el jefe final, la siguiente de la Torre
        if (ganado == SECRETO) {
            UsuarioManager.otorgarMedallaEspecial(UsuarioManager.MEDALLA_JEFE_SECRETO, alGuardar);
        } else {
            UsuarioManager.otorgarMedallasTorre(ganado, alGuardar);
        }
    }

    /** Renglones "🏅 Ganaste la Medalla Gaz" para la ventana de resultado (vacío si no hubo medalla nueva). */
    private String textoMedallas(List<Integer> nuevas) {
        if (nuevas == null) return Idioma.t("\n\n⚠️ No se pudo guardar tu medalla. Revisa tu conexión.", "\n\n⚠️ Could not save your medal. Check your connection.");
        StringBuilder texto = new StringBuilder();
        for (int numero : nuevas) {
            texto.append(Idioma.t("\n\n🏅 ¡Ganaste la ", "\n\n🏅 You earned the ")).append(MedallasActivity.nombre(numero)).append("!");
        }
        return texto.toString();
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private void mostrarSeccion(View seccion) {
        seccionInicio.setVisibility(seccion == seccionInicio ? View.VISIBLE : View.GONE);
        seccionEleccion.setVisibility(seccion == seccionEleccion ? View.VISIBLE : View.GONE);
        seccionNivel.setVisibility(seccion == seccionNivel ? View.VISIBLE : View.GONE);

        // La torre ocupa toda la pantalla (fondo oscuro, sin la cabecera roja); lo demás se ve como el resto de la app
        boolean torre = seccion == seccionInicio;
        cabecera.setVisibility(torre ? View.GONE : View.VISIBLE);
        scroll.setBackgroundResource(torre ? R.drawable.bg_torre_pantalla : R.drawable.bg_menu_screen);
        float dp = getResources().getDisplayMetrics().density;
        int lado = Math.round((torre ? 10 : 20) * dp);
        contenido.setPadding(lado, Math.round((torre ? 40 : 20) * dp), lado, Math.round((torre ? 44 : 20) * dp));
        if (!torre && pulso != null) pulso.cancel();
        scroll.post(() -> scroll.scrollTo(0, 0));
    }

    /**
     * DOCENTES DE LA TORRE (uno por nivel, del 1 al 6, y el jefe secreto).
     *
     * Nombres: cámbialos aquí por el nombre real de cada docente.
     * Fotos:   copia cada foto AUTORIZADA en  app/src/main/res/drawable-nodpi/
     *          con el nombre  foto_prof1.jpg, foto_prof2.jpg … foto_prof7.jpg
     *          (también sirve .png). No hay que cambiar código: si el archivo existe,
     *          la app lo usa; si no, muestra la silueta.
     */
    private static final String[] NOMBRES_DOCENTES = {
            "Gaz",        // nivel 1  → foto_prof1
            "Angélica",   // nivel 2  → foto_prof2
            "Daniel",     // nivel 3  → foto_prof3
            "Campa",      // nivel 4  → foto_prof4
            "Víctor",     // nivel 5  → foto_prof5
            "Badi",       // nivel 6 (jefe final) → foto_prof6
            "Claudia Sheinbaum"   // jefe secreto → foto_prof7
    };

    static String nombreDocente(int n) {
        return NOMBRES_DOCENTES[Math.max(0, Math.min(n - 1, NOMBRES_DOCENTES.length - 1))];
    }

    /** Foto del docente del nivel (res/drawable-nodpi/foto_prof<n>) o la silueta si aún no está. */
    private int fotoDocente(int n) {
        int foto = getResources().getIdentifier("foto_prof" + n, "drawable", getPackageName());
        return foto != 0 ? foto : R.drawable.ic_docente_silueta;
    }

    /**
     * Personaje ("monito") del docente para la batalla: res/drawable-nodpi/entrenador_<n>.png
     * (PNG sin fondo, de cuerpo completo; entrenador_7 = jefe secreto). 0 si aún no existe.
     */
    private int personajeDocente(int n) {
        return getResources().getIdentifier("entrenador_" + n, "drawable", getPackageName());
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
        return "HP " + hp + " · ATK " + atk + "\nDEF " + def + Idioma.t(" · VEL ", " · SPD ") + vel;
    }

    private void avisoSinConexion(Runnable reintentar) {
        Dialogos.confirmar(this, "📡", Idioma.t("Sin conexión", "No connection"),
                Idioma.t("No se pudieron cargar los Pokémon de la PokéAPI. Revisa tu Internet.", "Could not load the Pokémon from the PokéAPI. Check your internet."),
                Idioma.t("Reintentar", "Retry"), Idioma.t("Salir", "Exit"), reintentar, this::mostrarInicio);
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