package com.example.pokeapp.red;

import android.Manifest;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Conexión entre dos celulares cercanos con Nearby Connections (usa Bluetooth y WiFi
 * por debajo, sin internet, sin emparejar y sin escribir IPs).
 *
 * ARCHIVO COMPARTIDO: se copia tal cual (junto con Protocolo.java) en las apps que
 * quieran pelear entre sí; solo se cambia la línea "package". No depende de ninguna
 * otra clase del proyecto. Requiere en build.gradle:
 *     implementation("com.google.android.gms:play-services-nearby:19.3.0")
 * y los permisos del AndroidManifest (ver PROTOCOLO_BATALLA.md).
 *
 * Uso:
 *   ConexionBatalla red = ConexionBatalla.obtener(context);
 *   red.setEscucha(escucha);
 *   red.crearSala("Javi");            // anfitrión: se anuncia y espera
 *   red.buscarSalas("Ana");           // invitado: alEncontrarSala(...) → red.unirse(id)
 *   → alPedirConexion(...) en ambos → red.aceptar()  → alConectar(...)
 *   red.enviar(Protocolo.equipo(...));   → alRecibir(mensaje) en el otro celular
 *
 * Es una sola para toda la app (la conexión sigue viva al pasar de la sala a la arena).
 * Los mensajes que llegan cuando ninguna pantalla escucha se guardan y se entregan
 * a la siguiente que llame a setEscucha.
 */
public class ConexionBatalla {

    /** Las dos apps deben usar EXACTAMENTE este texto y esta estrategia para encontrarse. */
    public static final String SERVICIO = "cesba.pokebatalla.v1";
    private static final Strategy ESTRATEGIA = Strategy.P2P_POINT_TO_POINT;

    public interface Escucha {
        /** (Invitado) apareció una sala cerca. */
        default void alEncontrarSala(String idSala, String nombre) {}

        /** (Invitado) esa sala ya no está. */
        default void alPerderSala(String idSala) {}

        /** Alguien quiere conectarse: mostrar el código y llamar a aceptar() o rechazar(). */
        default void alPedirConexion(String nombreRival, String codigo) {}

        default void alConectar(String nombreRival) {}

        /** Alguno de los dos rechazó la conexión (la sala o la búsqueda siguen abiertas). */
        default void alRechazar() {}

        /** No se pudo crear la sala, buscar o pedir la conexión. */
        default void alFallar(String motivo) {}

        default void alRecibir(JSONObject mensaje) {}

        default void alDesconectar() {}
    }

    private static ConexionBatalla instancia;

    public static synchronized ConexionBatalla obtener(Context context) {
        if (instancia == null) instancia = new ConexionBatalla(context.getApplicationContext());
        return instancia;
    }

    /** Permisos que hay que pedir al usuario antes de crear o buscar una sala (cambian según Android). */
    public static String[] permisos() {
        // La ubicación (las dos) se pide en todas las versiones: Nearby la exige para buscar
        List<String> lista = new ArrayList<>();
        lista.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        lista.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= 31) {
            lista.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            lista.add(Manifest.permission.BLUETOOTH_CONNECT);
            lista.add(Manifest.permission.BLUETOOTH_SCAN);
        }
        if (Build.VERSION.SDK_INT >= 33) lista.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        // Android 17+: sin el permiso de red local Nearby responde "8: INTERNAL_ERROR"
        if (Build.VERSION.SDK_INT >= 37) lista.add("android.permission.ACCESS_LOCAL_NETWORK");
        return lista.toArray(new String[0]);
    }

    private final ConnectionsClient cliente;
    private Escucha escucha;
    private final List<JSONObject> pendientes = new ArrayList<>();

    private String miNombre = "Entrenador";
    private String idRival;            // con quién estoy conectado (null = con nadie)
    private String idPorConfirmar;     // quién espera mi aceptar()/rechazar()
    private String nombreRival;
    private boolean anfitrion;
    private boolean anunciando, buscando;

    private ConexionBatalla(Context context) {
        cliente = Nearby.getConnectionsClient(context);
    }

    /** Cambia la pantalla que escucha (null = ninguna) y le entrega lo que llegó mientras tanto. */
    public void setEscucha(Escucha nueva) {
        escucha = nueva;
        if (nueva == null) return;
        List<JSONObject> copia = new ArrayList<>(pendientes);
        pendientes.clear();
        for (JSONObject mensaje : copia) {
            if (escucha == nueva) nueva.alRecibir(mensaje);
            else pendientes.add(mensaje);   // la pantalla dejó de escuchar a la mitad
        }
    }

    /** Deja de escuchar solo si la pantalla que escucha es esa. */
    public void quitarEscucha(Escucha anterior) {
        if (escucha == anterior) escucha = null;
    }

    public boolean estaConectado() { return idRival != null; }

    /** true si este celular creó la sala (él calcula la batalla). */
    public boolean esAnfitrion() { return anfitrion; }

    public String getNombreRival() { return nombreRival; }

    // ------------------------------------------------------------------
    // Crear / buscar sala
    // ------------------------------------------------------------------

    /** Anfitrión: se anuncia con su nombre hasta que alguien se conecte. */
    public void crearSala(String nombre) {
        desconectar();
        miNombre = nombre;
        anfitrion = true;
        anunciando = true;
        cliente.startAdvertising(nombre, SERVICIO, cicloConexion,
                        new AdvertisingOptions.Builder().setStrategy(ESTRATEGIA).build())
                .addOnFailureListener(e -> {
                    anunciando = false;
                    fallar("No se pudo crear la sala", e);
                });
    }

    /** Invitado: busca salas cercanas (llegan por alEncontrarSala). */
    public void buscarSalas(String nombre) {
        desconectar();
        miNombre = nombre;
        anfitrion = false;
        buscando = true;
        cliente.startDiscovery(SERVICIO, descubrimiento,
                        new DiscoveryOptions.Builder().setStrategy(ESTRATEGIA).build())
                .addOnFailureListener(e -> {
                    buscando = false;
                    fallar("No se pudo buscar salas", e);
                });
    }

    /** Invitado: pide entrar a una sala encontrada. */
    public void unirse(String idSala) {
        cliente.requestConnection(miNombre, idSala, cicloConexion)
                .addOnFailureListener(e -> fallar("No se pudo conectar con la sala", e));
    }

    public void aceptar() {
        if (idPorConfirmar != null) cliente.acceptConnection(idPorConfirmar, alRecibirDatos);
    }

    public void rechazar() {
        if (idPorConfirmar != null) cliente.rejectConnection(idPorConfirmar);
        idPorConfirmar = null;
    }

    /** Deja de anunciarse/buscar y corta la conexión (el otro celular recibe alDesconectar). */
    public void desconectar() {
        // Solo se detiene lo que de verdad está activo: parar y arrancar de inmediato
        // lo que no estaba corriendo puede hacer fallar a Nearby
        if (anunciando) cliente.stopAdvertising();
        if (buscando) cliente.stopDiscovery();
        if (idRival != null || idPorConfirmar != null) cliente.stopAllEndpoints();
        anunciando = false;
        buscando = false;
        idRival = null;
        idPorConfirmar = null;
        nombreRival = null;
        pendientes.clear();
    }

    // ------------------------------------------------------------------
    // Mensajes
    // ------------------------------------------------------------------

    /** Manda un mensaje del Protocolo (texto JSON en UTF-8). */
    public void enviar(JSONObject mensaje) {
        if (idRival == null) return;
        cliente.sendPayload(idRival, Payload.fromBytes(mensaje.toString().getBytes(StandardCharsets.UTF_8)));
    }

    private final PayloadCallback alRecibirDatos = new PayloadCallback() {
        @Override
        public void onPayloadReceived(@NonNull String id, @NonNull Payload payload) {
            if (payload.getType() != Payload.Type.BYTES || payload.asBytes() == null) return;
            try {
                JSONObject mensaje = new JSONObject(new String(payload.asBytes(), StandardCharsets.UTF_8));
                if (escucha != null) escucha.alRecibir(mensaje);
                else pendientes.add(mensaje);
            } catch (Exception ignorado) {
                // No era un mensaje del protocolo: se descarta
            }
        }

        @Override
        public void onPayloadTransferUpdate(@NonNull String id, @NonNull PayloadTransferUpdate avance) {
        }
    };

    // ------------------------------------------------------------------
    // Avisos de Nearby
    // ------------------------------------------------------------------

    private final EndpointDiscoveryCallback descubrimiento = new EndpointDiscoveryCallback() {
        @Override
        public void onEndpointFound(@NonNull String id, @NonNull DiscoveredEndpointInfo info) {
            if (SERVICIO.equals(info.getServiceId()) && escucha != null) {
                escucha.alEncontrarSala(id, info.getEndpointName());
            }
        }

        @Override
        public void onEndpointLost(@NonNull String id) {
            if (escucha != null) escucha.alPerderSala(id);
        }
    };

    private final ConnectionLifecycleCallback cicloConexion = new ConnectionLifecycleCallback() {
        @Override
        public void onConnectionInitiated(@NonNull String id, @NonNull ConnectionInfo info) {
            if (idRival != null) {              // ya hay rival: solo se pelea 1 contra 1
                cliente.rejectConnection(id);
                return;
            }
            idPorConfirmar = id;
            nombreRival = info.getEndpointName();
            if (escucha != null) escucha.alPedirConexion(nombreRival, info.getAuthenticationDigits());
        }

        @Override
        public void onConnectionResult(@NonNull String id, @NonNull ConnectionResolution resultado) {
            idPorConfirmar = null;
            if (resultado.getStatus().isSuccess()) {
                idRival = id;
                if (anunciando) cliente.stopAdvertising();
                if (buscando) cliente.stopDiscovery();
                anunciando = false;
                buscando = false;
                if (escucha != null) escucha.alConectar(nombreRival);
            } else if (escucha != null) {
                escucha.alRechazar();
            }
        }

        @Override
        public void onDisconnected(@NonNull String id) {
            if (!id.equals(idRival)) return;
            idRival = null;
            pendientes.clear();
            if (escucha != null) escucha.alDesconectar();
        }
    };

    private void fallar(String motivo, Exception e) {
        android.util.Log.e("ConexionBatalla", motivo, e);
        if (escucha != null) {
            escucha.alFallar(motivo + (e.getMessage() != null ? " (" + e.getMessage() + ")" : ""));
        }
    }
}
