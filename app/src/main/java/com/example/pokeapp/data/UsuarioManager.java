package com.example.pokeapp.data;

import androidx.annotation.NonNull;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Perfil y progreso del entrenador en Firestore:
 *
 *   usuarios/{uid}  ->  { nombre, correo, medallas, ultimoAcceso }
 */
public class UsuarioManager {

    private static DocumentReference documento() {
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null) return null;

        return FirebaseFirestore.getInstance()
                .collection("usuarios")
                .document(usuario.getUid());
    }

    /** Crea o actualiza el perfil sin borrar el progreso existente (merge). */
    public static void guardarPerfil(String nombre) {
        DocumentReference doc = documento();
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (doc == null || usuario == null) return;

        Map<String, Object> datos = new HashMap<>();
        if (nombre != null && !nombre.isEmpty()) datos.put("nombre", nombre);
        if (usuario.getEmail() != null) datos.put("correo", usuario.getEmail());
        datos.put("ultimoAcceso", FieldValue.serverTimestamp());

        doc.set(datos, SetOptions.merge());
    }

    /** Progreso del entrenador guardado en usuarios/{uid}. */
    public static class Estadisticas {
        public final int medallas, victorias, derrotas;

        public Estadisticas(int medallas, int victorias, int derrotas) {
            this.medallas = medallas;
            this.victorias = victorias;
            this.derrotas = derrotas;
        }

        /** Puntos de experiencia: 1 por victoria y 5 por medalla. */
        public int puntos() { return victorias + 5 * medallas; }

        /** Cada 5 puntos sube un nivel. */
        public int nivel() { return 1 + puntos() / 5; }

        /** Avance (0–100) hacia el siguiente nivel. */
        public int progresoNivel() { return (puntos() % 5) * 20; }
    }

    public static void obtenerEstadisticas(@NonNull FavoritosManager.Resultado<Estadisticas> callback) {
        DocumentReference doc = documento();
        if (doc == null) {
            callback.alTerminar(new Estadisticas(0, 0, 0));
            return;
        }

        doc.get().addOnSuccessListener(snapshot -> callback.alTerminar(new Estadisticas(
                valor(snapshot.getLong("medallas")),
                valor(snapshot.getLong("victorias")),
                valor(snapshot.getLong("derrotas"))
        ))).addOnFailureListener(e -> callback.alTerminar(new Estadisticas(0, 0, 0)));
    }

    private static int valor(Long numero) {
        return numero == null ? 0 : numero.intValue();
    }

    /** Suma 1 a victorias o a derrotas, sin pisar el resto del progreso. */
    public static void registrarResultadoBatalla(boolean gano) {
        DocumentReference doc = documento();
        if (doc == null) return;

        Map<String, Object> datos = new HashMap<>();
        datos.put(gano ? "victorias" : "derrotas", FieldValue.increment(1));

        doc.set(datos, SetOptions.merge());
    }

    public static void obtenerMedallas(@NonNull FavoritosManager.Resultado<Integer> callback) {
        DocumentReference doc = documento();
        if (doc == null) {
            callback.alTerminar(0);
            return;
        }

        doc.get().addOnSuccessListener(snapshot -> {
            Long medallas = snapshot.getLong("medallas");
            callback.alTerminar(medallas == null ? 0 : medallas.intValue());
        }).addOnFailureListener(e -> callback.alTerminar(0));
    }

    public interface MedallasListas {
        /** total = medallas obtenidas; fechas = número de cada medalla que tiene (1–10) → cuándo la ganó. */
        void alTerminar(int total, Map<Integer, Date> fechas);
    }

    /** Para el módulo Medallas: cuántas tiene y la fecha de cada una (si falla: 0 y vacío). */
    @SuppressWarnings("unchecked")
    public static void obtenerMedallasConFecha(@NonNull MedallasListas callback) {
        DocumentReference doc = documento();
        if (doc == null) {
            callback.alTerminar(0, new HashMap<>());
            return;
        }

        doc.get().addOnSuccessListener(snapshot -> {
            Map<Integer, Date> fechas = new HashMap<>();
            Object lista = snapshot.get("medallasObtenidas");
            if (lista instanceof List) {
                for (Object o : (List<Object>) lista) {
                    if (!(o instanceof Map)) continue;
                    Object numero = ((Map<String, Object>) o).get("numero");
                    Object fecha = ((Map<String, Object>) o).get("fecha");
                    if (numero instanceof Number && fecha instanceof Timestamp) {
                        fechas.put(((Number) numero).intValue(), ((Timestamp) fecha).toDate());
                    }
                }
            }
            callback.alTerminar(valor(snapshot.getLong("medallas")), fechas);
        }).addOnFailureListener(e -> callback.alTerminar(0, new HashMap<>()));
    }

    /** Mejor puntuación de un minijuego (campo de usuarios/{uid}, por ejemplo "recordTipos"); 0 si no hay. */
    public static void obtenerRecord(String campo, @NonNull FavoritosManager.Resultado<Integer> callback) {
        DocumentReference doc = documento();
        if (doc == null) {
            callback.alTerminar(0);
            return;
        }
        doc.get().addOnSuccessListener(snapshot -> callback.alTerminar(valor(snapshot.getLong(campo))))
                .addOnFailureListener(e -> callback.alTerminar(0));
    }

    public static void guardarRecord(String campo, int puntos) {
        DocumentReference doc = documento();
        if (doc == null) return;

        Map<String, Object> datos = new HashMap<>();
        datos.put(campo, puntos);
        doc.set(datos, SetOptions.merge());
    }

    /** Safari: agrega el Pokémon a la colección del usuario (no se repite si ya lo tenía). */
    public static void registrarCaptura(int id, String nombre) {
        DocumentReference doc = documento();
        if (doc == null) return;

        Map<String, Object> pokemon = new HashMap<>();
        pokemon.put("id", id);
        pokemon.put("nombre", nombre);

        Map<String, Object> datos = new HashMap<>();
        datos.put("capturados", FieldValue.arrayUnion(pokemon));
        doc.set(datos, SetOptions.merge());
    }

    /** Colección del Safari, en el orden en que se capturaron (vacía si no hay o si falla). */
    @SuppressWarnings("unchecked")
    public static void obtenerCapturados(@NonNull FavoritosManager.Resultado<List<PokemonMini>> callback) {
        DocumentReference doc = documento();
        if (doc == null) {
            callback.alTerminar(new ArrayList<>());
            return;
        }

        doc.get().addOnSuccessListener(snapshot -> {
            List<PokemonMini> lista = new ArrayList<>();
            Object guardados = snapshot.get("capturados");
            if (guardados instanceof List) {
                for (Object o : (List<Object>) guardados) {
                    if (!(o instanceof Map)) continue;
                    Object id = ((Map<String, Object>) o).get("id");
                    Object nombre = ((Map<String, Object>) o).get("nombre");
                    if (id instanceof Number) lista.add(new PokemonMini(((Number) id).intValue(), String.valueOf(nombre)));
                }
            }
            callback.alTerminar(lista);
        }).addOnFailureListener(e -> callback.alTerminar(new ArrayList<>()));
    }

    // Campos de usuarios/{uid} donde cada minijuego guarda su mejor marca
    public static final String RECORD_QUIEN = "recordQuien";
    public static final String RACHA_QUIEN = "rachaQuien";
    public static final String RECORD_TIPOS = "recordTipos";
    public static final String RECORD_MEMORY = "recordMemory";

    /** Récords de los minijuegos y tamaño de la colección del Safari, para Mi perfil. */
    public static class ResumenJuegos {
        public final int recordQuien, rachaQuien, recordTipos, recordMemory, capturados;

        ResumenJuegos(int recordQuien, int rachaQuien, int recordTipos, int recordMemory, int capturados) {
            this.recordQuien = recordQuien;
            this.rachaQuien = rachaQuien;
            this.recordTipos = recordTipos;
            this.recordMemory = recordMemory;
            this.capturados = capturados;
        }
    }

    /** Lee todo en una sola consulta (si falla o no hay sesión, todo en 0). */
    public static void obtenerResumenJuegos(@NonNull FavoritosManager.Resultado<ResumenJuegos> callback) {
        DocumentReference doc = documento();
        if (doc == null) {
            callback.alTerminar(new ResumenJuegos(0, 0, 0, 0, 0));
            return;
        }

        doc.get().addOnSuccessListener(snapshot -> {
            Object capturados = snapshot.get("capturados");
            callback.alTerminar(new ResumenJuegos(
                    valor(snapshot.getLong(RECORD_QUIEN)),
                    valor(snapshot.getLong(RACHA_QUIEN)),
                    valor(snapshot.getLong(RECORD_TIPOS)),
                    valor(snapshot.getLong(RECORD_MEMORY)),
                    capturados instanceof List ? ((List<?>) capturados).size() : 0));
        }).addOnFailureListener(e -> callback.alTerminar(new ResumenJuegos(0, 0, 0, 0, 0)));
    }

    /**
     * Configuración → Reiniciar progreso: deja en cero victorias, derrotas y medallas,
     * borra los récords de minijuegos, la colección del Safari y el historial de batallas.
     * NO borra la cuenta ni los favoritos. Devuelve true si se pudo.
     */
    public static void reiniciarProgreso(@NonNull FavoritosManager.Resultado<Boolean> callback) {
        DocumentReference doc = documento();
        if (doc == null) {
            callback.alTerminar(false);
            return;
        }

        Map<String, Object> datos = new HashMap<>();
        datos.put("victorias", 0);
        datos.put("derrotas", 0);
        datos.put("medallas", 0);
        datos.put("medallasObtenidas", FieldValue.delete());
        datos.put("torresCompletadas", FieldValue.delete());
        datos.put("capturados", FieldValue.delete());
        datos.put(RECORD_QUIEN, FieldValue.delete());
        datos.put(RACHA_QUIEN, FieldValue.delete());
        datos.put(RECORD_TIPOS, FieldValue.delete());
        datos.put(RECORD_MEMORY, FieldValue.delete());

        doc.set(datos, SetOptions.merge()).addOnSuccessListener(v ->
                // El historial es una subcolección: se borra documento por documento, en un lote
                doc.collection("historial").get().addOnSuccessListener(batallas -> {
                    WriteBatch lote = FirebaseFirestore.getInstance().batch();
                    int enLote = 0;
                    for (DocumentSnapshot batalla : batallas.getDocuments()) {
                        if (enLote == 450) break;   // un lote admite hasta 500 operaciones
                        lote.delete(batalla.getReference());
                        enLote++;
                    }
                    lote.commit()
                            .addOnSuccessListener(x -> callback.alTerminar(true))
                            .addOnFailureListener(e -> callback.alTerminar(false));
                }).addOnFailureListener(e -> callback.alTerminar(false))
        ).addOnFailureListener(e -> callback.alTerminar(false));
    }

    public static final int MAX_MEDALLAS = 10;

    // Números de medalla (1–10). Las 7 primeras son una por nivel de la Torre.
    public static final int MEDALLA_JEFE_FINAL = 6;
    public static final int MEDALLA_SECRETA = 7;
    public static final int MEDALLA_CAMPEON = 8;    // conquistar la Torre 3 veces
    public static final int MEDALLA_LEYENDA = 9;    // conquistar la Torre 5 veces
    public static final int MEDALLA_MAESTRO = 10;   // tener las otras nueve
    public static final int TORRES_CAMPEON = 3, TORRES_LEYENDA = 5;

    /**
     * Se llama al GANAR un nivel de la Torre (1–6, o 7 = jefe secreto): da la medalla de ese nivel
     * si aún no la tenía y, al vencer al jefe final, cuenta una Torre conquistada (a las 3 y a las 5
     * hay medalla extra; con las nueve se gana la décima). Cada medalla guarda su número y fecha
     * en "medallasObtenidas" para el módulo Medallas.
     * Devuelve los números de las medallas recién ganadas (lista vacía si ninguna) o null si falló.
     */
    @SuppressWarnings("unchecked")
    public static void otorgarMedallasTorre(int nivel, @NonNull FavoritosManager.Resultado<List<Integer>> callback) {
        DocumentReference doc = documento();
        if (doc == null) {
            callback.alTerminar(null);
            return;
        }

        doc.get().addOnSuccessListener(snapshot -> {
            Set<Integer> tiene = new HashSet<>();
            Object lista = snapshot.get("medallasObtenidas");
            if (lista instanceof List) {
                for (Object o : (List<Object>) lista) {
                    Object numero = o instanceof Map ? ((Map<String, Object>) o).get("numero") : null;
                    if (numero instanceof Number) tiene.add(((Number) numero).intValue());
                }
            }

            int torres = valor(snapshot.getLong("torresCompletadas"));
            if (nivel == MEDALLA_JEFE_FINAL) torres++;

            List<Integer> nuevas = new ArrayList<>();
            if (nivel >= 1 && nivel <= MEDALLA_SECRETA && tiene.add(nivel)) nuevas.add(nivel);
            if (torres >= TORRES_CAMPEON && tiene.add(MEDALLA_CAMPEON)) nuevas.add(MEDALLA_CAMPEON);
            if (torres >= TORRES_LEYENDA && tiene.add(MEDALLA_LEYENDA)) nuevas.add(MEDALLA_LEYENDA);
            if (tiene.size() >= MAX_MEDALLAS - 1 && tiene.add(MEDALLA_MAESTRO)) nuevas.add(MEDALLA_MAESTRO);

            Map<String, Object> datos = new HashMap<>();
            datos.put("torresCompletadas", torres);
            datos.put("medallas", tiene.size());
            if (!nuevas.isEmpty()) {
                Object[] registros = new Object[nuevas.size()];
                for (int i = 0; i < registros.length; i++) {
                    Map<String, Object> medalla = new HashMap<>();
                    medalla.put("numero", nuevas.get(i));
                    medalla.put("fecha", Timestamp.now());
                    registros[i] = medalla;
                }
                datos.put("medallasObtenidas", FieldValue.arrayUnion(registros));
            }

            doc.set(datos, SetOptions.merge())
                    .addOnSuccessListener(v -> callback.alTerminar(nuevas))
                    .addOnFailureListener(e -> callback.alTerminar(null));
        }).addOnFailureListener(e -> callback.alTerminar(null));
    }
}
