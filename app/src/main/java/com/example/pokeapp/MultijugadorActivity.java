package com.example.pokeapp;

import android.content.Intent;
import android.content.pm.PackageManager;
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
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.example.pokeapp.battle.BattlePokemon;
import com.example.pokeapp.data.CargadorPokemon;
import com.example.pokeapp.data.HistorialManager;
import com.example.pokeapp.data.Pokemon;
import com.example.pokeapp.data.TypeColors;
import com.example.pokeapp.data.UsuarioManager;
import com.example.pokeapp.red.ConexionBatalla;
import com.example.pokeapp.red.Protocolo;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Batalla entre dispositivos: dos celulares cercanos, cada quien con su equipo de 3.
 *
 * Esta pantalla es la "sala": se arma el equipo, uno crea la sala y el otro la busca
 * (ConexionBatalla), y al conectarse cada celular manda su equipo. Cuando ya se mandó
 * el propio y llegó el del rival, se abre la arena. Al terminar la conexión sigue
 * abierta para la revancha: empieza cuando los dos vuelven a mandar su equipo.
 */
public class MultijugadorActivity extends AppCompatActivity {

    private static final int TAM_EQUIPO = VersusActivity.TAM_EQUIPO;
    private static final int MAX_EQUIPO_RIVAL = 6;

    private enum Estado { INICIO, ANUNCIANDO, BUSCANDO, CONECTADO }

    private final Pokemon[] equipo = new Pokemon[TAM_EQUIPO];
    private ConexionBatalla red;
    private Estado estado = Estado.INICIO;
    private String miNombre;

    /** Salas encontradas al buscar: id → nombre de quien la creó. */
    private final Map<String, String> salas = new LinkedHashMap<>();

    // Intercambio de equipos (se reinicia después de cada batalla)
    private boolean equipoEnviado;
    private List<Integer> idsRival;
    private String nombreRival;
    private boolean cargandoRival;
    private List<Pokemon> equipoRival = new ArrayList<>();
    private boolean huboBatalla;

    private LinearLayout slots, listaSalas;
    private TextView tvTituloEquipo, tvEstado, btnCancelar;
    private View btnCrear, btnBuscar, btnRevancha;
    private AlertDialog dialogoConexion;
    private Runnable trasPermisos;

    private final ActivityResultLauncher<Intent> lanzadorElegir = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            resultado -> {
                Pokemon elegido = ElegirPokemonActivity.leerResultado(resultado.getData());
                if (resultado.getResultCode() != RESULT_OK || elegido == null) return;
                equipo[resultado.getData().getIntExtra(ElegirPokemonActivity.EXTRA_POSICION, 0)] = elegido;
                actualizarVista();
            });

    private final ActivityResultLauncher<String[]> lanzadorPermisos = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            concedidos -> {
                Runnable accion = trasPermisos;
                trasPermisos = null;
                if (!concedidos.containsValue(false)) {
                    if (accion != null) accion.run();
                } else {
                    Toast.makeText(this, "Sin esos permisos no se puede encontrar al otro celular",
                            Toast.LENGTH_LONG).show();
                }
            });

    private final ActivityResultLauncher<Intent> lanzadorArena = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            resultado -> {
                Intent datos = resultado.getData();
                if (resultado.getResultCode() != RESULT_OK || datos == null) return;   // batalla cancelada

                huboBatalla = true;
                boolean gane = datos.getBooleanExtra(BattleArenaActivity.RESULTADO_GANO_A, true);
                UsuarioManager.registrarResultadoBatalla(gane);
                mostrarGanador(gane,
                        datos.getIntExtra(BattleArenaActivity.RESULTADO_INDICE_GANADOR, 0),
                        datos.getIntExtra(BattleArenaActivity.RESULTADO_HP_GANADOR, 0),
                        datos.getIntExtra(BattleArenaActivity.RESULTADO_TURNOS, 0));
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multijugador);

        red = ConexionBatalla.obtener(this);
        red.desconectar();   // por si quedó algo abierto de una visita anterior

        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        miNombre = usuario != null && usuario.getDisplayName() != null && !usuario.getDisplayName().isEmpty()
                ? usuario.getDisplayName() : "Entrenador";

        slots = findViewById(R.id.slotsMiEquipo);
        listaSalas = findViewById(R.id.listaSalas);
        tvTituloEquipo = findViewById(R.id.tvTituloMiEquipo);
        tvEstado = findViewById(R.id.tvEstadoRed);
        btnCrear = findViewById(R.id.btnCrearSala);
        btnBuscar = findViewById(R.id.btnBuscarSala);
        btnRevancha = findViewById(R.id.btnRevanchaRed);
        btnCancelar = findViewById(R.id.btnCancelarRed);

        findViewById(R.id.btnRegresar).setOnClickListener(v -> finish());
        findViewById(R.id.btnHistorial).setOnClickListener(v ->
                startActivity(HistorialActivity.crearIntent(this, HistorialManager.MODO_DISPOSITIVOS)));
        btnCrear.setOnClickListener(v -> conPermisos(this::crearSala));
        btnBuscar.setOnClickListener(v -> conPermisos(this::buscarSalas));
        btnRevancha.setOnClickListener(v -> enviarEquipo());
        btnCancelar.setOnClickListener(v -> desconectar());

        actualizarVista();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Mientras se cargan los Pokémon del rival nadie escucha: lo que llegue es para la arena
        if (!cargandoRival) red.setEscucha(escucha);

        // La conexión pudo cortarse mientras se veía la arena
        if (estado == Estado.CONECTADO && !cargandoRival && !red.estaConectado()) {
            Toast.makeText(this, "Se terminó la conexión con el otro celular", Toast.LENGTH_SHORT).show();
            reiniciar();
        }
        actualizarVista();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        red.quitarEscucha(escucha);
        red.desconectar();
    }

    // ------------------------------------------------------------------
    // Conexión
    // ------------------------------------------------------------------

    /** Pide (si faltan) los permisos de Bluetooth/WiFi cercano y luego hace la acción. */
    private void conPermisos(Runnable accion) {
        if (contar() < TAM_EQUIPO) {
            Toast.makeText(this, "Primero completa tu equipo de " + TAM_EQUIPO, Toast.LENGTH_SHORT).show();
            return;
        }
        List<String> faltan = new ArrayList<>();
        for (String permiso : ConexionBatalla.permisos()) {
            if (ContextCompat.checkSelfPermission(this, permiso) != PackageManager.PERMISSION_GRANTED) {
                faltan.add(permiso);
            }
        }
        if (faltan.isEmpty()) {
            accion.run();
        } else {
            trasPermisos = accion;
            lanzadorPermisos.launch(faltan.toArray(new String[0]));
        }
    }

    private void crearSala() {
        estado = Estado.ANUNCIANDO;
        red.crearSala(miNombre);
        actualizarVista();
    }

    private void buscarSalas() {
        estado = Estado.BUSCANDO;
        salas.clear();
        red.buscarSalas(miNombre);
        actualizarVista();
    }

    private void desconectar() {
        red.desconectar();
        reiniciar();
        actualizarVista();
    }

    private void reiniciar() {
        estado = Estado.INICIO;
        salas.clear();
        equipoEnviado = false;
        idsRival = null;
        cargandoRival = false;
        huboBatalla = false;
        if (dialogoConexion != null) dialogoConexion.dismiss();
    }

    private final ConexionBatalla.Escucha escucha = new ConexionBatalla.Escucha() {
        @Override
        public void alEncontrarSala(String idSala, String nombre) {
            salas.put(idSala, nombre);
            actualizarVista();
        }

        @Override
        public void alPerderSala(String idSala) {
            salas.remove(idSala);
            actualizarVista();
        }

        @Override
        public void alPedirConexion(String rival, String codigo) {
            if (isFinishing() || isDestroyed()) return;
            View vista = LayoutInflater.from(MultijugadorActivity.this).inflate(R.layout.dialog_conexion, null);
            ((TextView) vista.findViewById(R.id.tvRivalConexion)).setText("¿Pelear contra " + rival + "?");
            ((TextView) vista.findViewById(R.id.tvCodigoConexion)).setText(codigo);

            AlertDialog dialogo = new AlertDialog.Builder(MultijugadorActivity.this)
                    .setView(vista).setCancelable(false).create();
            vista.findViewById(R.id.btnAceptarConexion).setOnClickListener(v -> {
                dialogo.dismiss();
                tvEstado.setText("Conectando con " + rival + "…");
                red.aceptar();
            });
            vista.findViewById(R.id.btnRechazarConexion).setOnClickListener(v -> {
                dialogo.dismiss();
                red.rechazar();
            });

            dialogoConexion = dialogo;
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

        @Override
        public void alConectar(String rival) {
            estado = Estado.CONECTADO;
            nombreRival = rival;
            enviarEquipo();
        }

        @Override
        public void alRechazar() {
            if (dialogoConexion != null) dialogoConexion.dismiss();
            Toast.makeText(MultijugadorActivity.this, "No se aceptó la conexión", Toast.LENGTH_SHORT).show();
            actualizarVista();   // la sala (o la búsqueda) sigue abierta
        }

        @Override
        public void alFallar(String motivo) {
            if (estado == Estado.CONECTADO) return;
            Toast.makeText(MultijugadorActivity.this,
                    motivo + ". Revisa que Bluetooth, WiFi y ubicación estén encendidos.",
                    Toast.LENGTH_LONG).show();
            // Si falló al unirse a una sala se sigue buscando; si falló lo demás, se regresa al inicio
            if (estado != Estado.BUSCANDO || salas.isEmpty()) desconectar();
        }

        @Override
        public void alRecibir(JSONObject mensaje) {
            if (!Protocolo.EQUIPO.equals(Protocolo.tipo(mensaje))) return;

            JSONArray ids = mensaje.optJSONArray("pokemon");
            List<Integer> lista = new ArrayList<>();
            for (int i = 0; ids != null && i < ids.length() && i < MAX_EQUIPO_RIVAL; i++) {
                int id = ids.optInt(i, 0);
                if (id > 0) lista.add(id);
            }
            if (lista.isEmpty()) return;

            idsRival = lista;
            String nombre = mensaje.optString("nombre", "");
            if (!nombre.isEmpty()) nombreRival = nombre;
            actualizarVista();
            intentarIniciar();
        }

        @Override
        public void alDesconectar() {
            Toast.makeText(MultijugadorActivity.this, "El otro celular se desconectó", Toast.LENGTH_SHORT).show();
            reiniciar();
            actualizarVista();
        }
    };

    // ------------------------------------------------------------------
    // Intercambio de equipos y batalla
    // ------------------------------------------------------------------

    /** Manda mi equipo (al conectar, o al tocar Revancha). */
    private void enviarEquipo() {
        if (estado != Estado.CONECTADO || equipoEnviado || contar() < TAM_EQUIPO) return;
        List<Integer> ids = new ArrayList<>();
        for (Pokemon p : equipo) ids.add(p.getId());
        red.enviar(Protocolo.equipo(miNombre, ids));
        equipoEnviado = true;
        actualizarVista();
        intentarIniciar();
    }

    /** La batalla empieza cuando ya mandé mi equipo y ya llegó el del rival. */
    private void intentarIniciar() {
        if (!equipoEnviado || idsRival == null || cargandoRival) return;
        cargandoRival = true;
        red.setEscucha(null);   // desde aquí los mensajes se guardan para la arena
        actualizarVista();

        CargadorPokemon.cargar(idsRival, rival -> {
            if (isFinishing() || isDestroyed() || !cargandoRival) return;
            cargandoRival = false;
            equipoEnviado = false;
            idsRival = null;

            if (rival == null || !red.estaConectado()) {
                Toast.makeText(this, rival == null
                        ? "No se pudieron cargar los Pokémon del rival (revisa tu internet)"
                        : "Se terminó la conexión con el otro celular", Toast.LENGTH_LONG).show();
                red.setEscucha(escucha);
                desconectar();
                return;
            }

            equipoRival = rival;
            List<Pokemon> mio = new ArrayList<>();
            for (Pokemon p : equipo) mio.add(p);
            lanzadorArena.launch(BattleArenaActivity.crearIntentRed(this, mio, rival,
                    miNombre, nombreRival != null ? nombreRival : "Rival", red.esAnfitrion()));
        });
    }

    private void mostrarGanador(boolean gane, int indice, int hpRestante, int turnos) {
        List<Pokemon> ganadores = new ArrayList<>();
        if (gane) for (Pokemon p : equipo) ganadores.add(p);
        else ganadores.addAll(equipoRival);
        if (ganadores.isEmpty() || ganadores.get(0) == null) return;
        BattlePokemon pokemon = new BattlePokemon(ganadores.get(Math.min(indice, ganadores.size() - 1)));
        String rival = nombreRival != null ? nombreRival : "Rival";

        View vista = LayoutInflater.from(this).inflate(R.layout.dialog_ganador, null);
        Glide.with(this).load(pokemon.imagen).into((ImageView) vista.findViewById(R.id.imgGanador));
        ((TextView) vista.findViewById(R.id.tvGanador)).setText(gane ? "¡Ganaste!" : rival);
        ((TextView) vista.findViewById(R.id.tvContra)).setText(gane
                ? "venciste a " + rival + " con " + pokemon.nombre
                : "te venció con " + pokemon.nombre);
        // El HP máximo lo dicta el anfitrión, así que aquí solo se muestra el HP con que quedó
        ((TextView) vista.findViewById(R.id.tvHpGanador)).setText(String.valueOf(hpRestante));
        ((TextView) vista.findViewById(R.id.tvTurnosGanador)).setText(String.valueOf(turnos));
        ((TextView) vista.findViewById(R.id.btnElegirOtro)).setText("Cambiar equipo");

        AlertDialog dialogo = new AlertDialog.Builder(this).setView(vista).create();
        vista.findViewById(R.id.btnRevancha).setOnClickListener(v -> {
            dialogo.dismiss();
            enviarEquipo();
        });
        vista.findViewById(R.id.btnElegirOtro).setOnClickListener(v -> dialogo.dismiss());
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

    // ------------------------------------------------------------------
    // Vista
    // ------------------------------------------------------------------

    private int contar() {
        int n = 0;
        for (Pokemon p : equipo) if (p != null) n++;
        return n;
    }

    /** El equipo solo se puede cambiar antes de conectarse o entre batallas (antes de mandarlo). */
    private boolean puedeEditarEquipo() {
        return estado == Estado.INICIO || (estado == Estado.CONECTADO && !equipoEnviado);
    }

    private void actualizarVista() {
        pintarSlots();
        tvTituloEquipo.setText("🔴 Tu equipo · " + contar() + "/" + TAM_EQUIPO);
        pintarSalas();

        String rival = nombreRival != null ? nombreRival : "tu rival";
        boolean revancha = estado == Estado.CONECTADO && !equipoEnviado && !cargandoRival;
        switch (estado) {
            case ANUNCIANDO:
                tvEstado.setText("📡 Sala de " + miNombre + " creada. Esperando a que tu rival la busque…");
                break;
            case BUSCANDO:
                tvEstado.setText(salas.isEmpty() ? "🔍 Buscando salas cercanas…" : "🔍 Toca una sala para unirte:");
                break;
            case CONECTADO:
                if (cargandoRival) {
                    tvEstado.setText("✅ Conectado con " + rival + ". Preparando la batalla…");
                } else if (equipoEnviado) {
                    tvEstado.setText("✅ Conectado con " + rival + ". Esperando su equipo…");
                } else if (idsRival != null) {
                    tvEstado.setText("✅ " + rival + " quiere la revancha. Toca Revancha para aceptar.");
                } else {
                    tvEstado.setText("✅ Conectado con " + rival + (huboBatalla
                            ? ". Puedes cambiar tu equipo y tocar Revancha."
                            : ". Completa tu equipo para empezar."));
                }
                break;
            default:
                tvEstado.setText("Arma tu equipo. Luego uno crea la sala y el otro la busca. "
                        + "Los dos necesitan Bluetooth, WiFi y ubicación encendidos.");
                break;
        }

        btnCrear.setVisibility(estado == Estado.INICIO ? View.VISIBLE : View.GONE);
        btnBuscar.setVisibility(estado == Estado.INICIO ? View.VISIBLE : View.GONE);
        btnRevancha.setVisibility(revancha ? View.VISIBLE : View.GONE);
        btnCancelar.setVisibility(estado == Estado.INICIO ? View.GONE : View.VISIBLE);
        btnCancelar.setText(estado == Estado.CONECTADO ? "Desconectar" : "Cancelar");
    }

    private void pintarSalas() {
        listaSalas.removeAllViews();
        if (estado != Estado.BUSCANDO) return;

        float dp = getResources().getDisplayMetrics().density;
        for (Map.Entry<String, String> sala : salas.entrySet()) {
            TextView fila = new TextView(this);
            fila.setText("⚔️ Sala de " + sala.getValue());
            fila.setTextColor(ContextCompat.getColor(this, R.color.menu_text));
            fila.setTextSize(15);
            fila.setTypeface(null, android.graphics.Typeface.BOLD);
            fila.setBackgroundResource(R.drawable.bg_mini_normal);
            fila.setPadding(Math.round(14 * dp), Math.round(12 * dp), Math.round(14 * dp), Math.round(12 * dp));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Math.round(8 * dp);
            fila.setOnClickListener(v -> {
                tvEstado.setText("Conectando con " + sala.getValue() + "…");
                red.unirse(sala.getKey());
            });
            listaSalas.addView(fila, lp);
        }
    }

    /** Tres lugares: "+" abre el buscador; un Pokémon se reemplaza al tocarlo o se quita con ✕. */
    private void pintarSlots() {
        slots.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        float dp = getResources().getDisplayMetrics().density;
        boolean editable = puedeEditarEquipo();

        for (int i = 0; i < TAM_EQUIPO; i++) {
            final int posicion = i;
            View slot = inflater.inflate(R.layout.item_slot_equipo, slots, false);
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
                // Ya conectado no se puede dejar un lugar vacío: solo reemplazar
                quitar.setVisibility(estado == Estado.INICIO ? View.VISIBLE : View.GONE);

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

            slot.setAlpha(editable ? 1f : 0.6f);
            if (editable) slot.setOnClickListener(v -> abrirElegir(posicion));
            slots.addView(slot);
        }
    }

    private void abrirElegir(int posicion) {
        // No se puede repetir dentro del equipo (sí reemplazar el del mismo lugar)
        ArrayList<Integer> excluir = new ArrayList<>();
        for (int i = 0; i < equipo.length; i++) {
            if (equipo[i] != null && i != posicion) excluir.add(equipo[i].getId());
        }
        lanzadorElegir.launch(ElegirPokemonActivity.crearIntent(this, null,
                "Tu equipo · lugar " + (posicion + 1) + " de " + TAM_EQUIPO, posicion, excluir));
    }
}
