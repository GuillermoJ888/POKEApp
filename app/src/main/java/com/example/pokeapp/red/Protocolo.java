package com.example.pokeapp.red;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * Mensajes de la batalla entre dispositivos (versión 1).
 *
 * ARCHIVO COMPARTIDO: se copia tal cual junto con ConexionBatalla.java.
 *
 * Idea: el ANFITRIÓN (quien creó la sala) calcula toda la batalla con sus reglas;
 * el INVITADO solo manda lo que elige y dibuja lo que el anfitrión le dice. Así no
 * importa que las dos apps tengan fórmulas de daño distintas.
 *
 * Los lados siempre se nombran "anfitrion" / "invitado" (nunca "yo" / "rival").
 *
 *   equipo  (ambos, al conectar)    {"t":"equipo","nombre":"Javi","pokemon":[25,6,9]}
 *   inicio  (anfitrión → invitado)  {"t":"inicio","escenario":2,"anfitrion":[P,P,P],"invitado":[P,P,P]}
 *              P = {"id":25,"hp":35,"ataques":[{"nombre":"Rayo","tipo":"electric","potencia":90,"precision":100}]}
 *   listo   (invitado → anfitrión)  {"t":"listo"}
 *   turno   (anfitrión → invitado)  {"t":"turno","lado":"invitado","pide":"accion"}      o "reemplazo"
 *   accion  (invitado → anfitrión)  {"t":"accion","hace":"atacar","indice":2}            o "cambiar"
 *   ataque  (anfitrión → invitado)  {"t":"ataque","lado":"anfitrion","numero":3,"nombre":"Rayo","tipo":"electric",
 *                                    "potencia":90,"precision":100,"danio":18,"efectividad":2.0,
 *                                    "critico":false,"fallo":false,"hp":17}
 *   entra   (anfitrión → invitado)  {"t":"entra","lado":"invitado","indice":1,"porDecision":true}
 *   fin     (anfitrión → invitado)  {"t":"fin","gana":"anfitrion","turnos":12}
 *
 * Los ID de Pokémon y los tipos ("electric", "fire"…) son los de la PokéAPI.
 * Los índices empiezan en 0. Explicación completa en PROTOCOLO_BATALLA.md.
 */
public final class Protocolo {

    private Protocolo() {}

    public static final String TIPO = "t";

    public static final String EQUIPO = "equipo";
    public static final String INICIO = "inicio";
    public static final String LISTO = "listo";
    public static final String TURNO = "turno";
    public static final String ACCION = "accion";
    public static final String ATAQUE = "ataque";
    public static final String ENTRA = "entra";
    public static final String FIN = "fin";

    public static final String ANFITRION = "anfitrion";
    public static final String INVITADO = "invitado";

    public static final String PIDE_ACCION = "accion";
    public static final String PIDE_REEMPLAZO = "reemplazo";

    public static final String HACE_ATACAR = "atacar";
    public static final String HACE_CAMBIAR = "cambiar";

    /** "anfitrion" o "invitado". */
    public static String lado(boolean anfitrion) {
        return anfitrion ? ANFITRION : INVITADO;
    }

    public static String tipo(JSONObject mensaje) {
        return mensaje.optString(TIPO, "");
    }

    // ------------------------------------------------------------------
    // Ambos
    // ------------------------------------------------------------------

    /** Mi nombre y los ID (PokéAPI) de mi equipo, en orden. */
    public static JSONObject equipo(String nombre, List<Integer> ids) {
        return nuevo(EQUIPO, "nombre", nombre, "pokemon", new JSONArray(ids));
    }

    // ------------------------------------------------------------------
    // Invitado → anfitrión
    // ------------------------------------------------------------------

    /** Ya dibujé la batalla: puedes empezar a mandar turnos. */
    public static JSONObject listo() {
        return nuevo(LISTO);
    }

    /** Uso mi ataque número "indice" (0–3). */
    public static JSONObject atacar(int indice) {
        return nuevo(ACCION, "hace", HACE_ATACAR, "indice", indice);
    }

    /** Mando al Pokémon número "indice" de mi equipo (por decisión o porque cayó el anterior). */
    public static JSONObject cambiar(int indice) {
        return nuevo(ACCION, "hace", HACE_CAMBIAR, "indice", indice);
    }

    // ------------------------------------------------------------------
    // Anfitrión → invitado
    // ------------------------------------------------------------------

    /** Un Pokémon dentro de "inicio": su ID, su HP y sus ataques (ver ataqueDe). */
    public static JSONObject pokemon(int id, int hp, JSONArray ataques) {
        return nuevo(null, "id", id, "hp", hp, "ataques", ataques);
    }

    /** Un ataque dentro de "inicio". tipo null = sin tipo; precision 0 = nunca falla. */
    public static JSONObject ataqueDe(String nombre, String tipo, int potencia, int precision) {
        return nuevo(null, "nombre", nombre, "tipo", tipo, "potencia", potencia, "precision", precision);
    }

    /** Estado inicial de los dos equipos, calculado por el anfitrión. */
    public static JSONObject inicio(int escenario, JSONArray equipoAnfitrion, JSONArray equipoInvitado) {
        return nuevo(INICIO, "escenario", escenario, ANFITRION, equipoAnfitrion, INVITADO, equipoInvitado);
    }

    /** Le toca a ese lado: elegir ataque/cambio (PIDE_ACCION) o mandar otro Pokémon (PIDE_REEMPLAZO). */
    public static JSONObject turno(String lado, String pide) {
        return nuevo(TURNO, "lado", lado, "pide", pide);
    }

    /** Resultado de un ataque del Pokémon activo de "lado"; hp = HP con que queda el defensor. */
    public static JSONObject ataque(String lado, int numero, String nombre, String tipo, int potencia,
                                    int precision, int danio, float efectividad, boolean critico,
                                    boolean fallo, int hp) {
        return nuevo(ATAQUE, "lado", lado, "numero", numero, "nombre", nombre, "tipo", tipo,
                "potencia", potencia, "precision", precision, "danio", danio,
                "efectividad", (double) efectividad, "critico", critico, "fallo", fallo, "hp", hp);
    }

    /** Ese lado manda al Pokémon "indice"; porDecision = usó Cambiar (false = cayó el anterior). */
    public static JSONObject entra(String lado, int indice, boolean porDecision) {
        return nuevo(ENTRA, "lado", lado, "indice", indice, "porDecision", porDecision);
    }

    public static JSONObject fin(String gana, int turnos) {
        return nuevo(FIN, "gana", gana, "turnos", turnos);
    }

    /** Arma {"t": tipo, clave: valor, ...}; los valores null no se escriben. */
    private static JSONObject nuevo(String tipo, Object... clavesYValores) {
        JSONObject o = new JSONObject();
        try {
            if (tipo != null) o.put(TIPO, tipo);
            for (int i = 0; i + 1 < clavesYValores.length; i += 2) {
                if (clavesYValores[i + 1] != null) o.put((String) clavesYValores[i], clavesYValores[i + 1]);
            }
        } catch (JSONException ignorado) {
            // Solo ocurre con números no válidos (NaN), que aquí no se usan
        }
        return o;
    }
}
