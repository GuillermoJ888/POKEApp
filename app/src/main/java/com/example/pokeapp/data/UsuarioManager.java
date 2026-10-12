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
        /** total = medallas obtenidas; fechas = número de medalla (1–10) → cuándo se ganó. */
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

    /**
     * Se llama al completar la Torre Pokémon: desbloquea la siguiente medalla
     * (máximo 10) y guarda su número y fecha en "medallasObtenidas" para el
     * módulo Medallas. Devuelve el nuevo total, o -1 si no se pudo guardar.
     */
    public static void otorgarMedalla(@NonNull FavoritosManager.Resultado<Integer> callback) {
        DocumentReference doc = documento();
        if (doc == null) {
            callback.alTerminar(-1);
            return;
        }

        doc.get().addOnSuccessListener(snapshot -> {
            int actuales = valor(snapshot.getLong("medallas"));
            if (actuales >= MAX_MEDALLAS) {
                callback.alTerminar(actuales);   // ya tiene las 10
                return;
            }

            int nueva = actuales + 1;
            Map<String, Object> medalla = new HashMap<>();
            medalla.put("numero", nueva);
            medalla.put("fecha", Timestamp.now());

            Map<String, Object> datos = new HashMap<>();
            datos.put("medallas", nueva);
            datos.put("medallasObtenidas", FieldValue.arrayUnion(medalla));

            doc.set(datos, SetOptions.merge())
                    .addOnSuccessListener(v -> callback.alTerminar(nueva))
                    .addOnFailureListener(e -> callback.alTerminar(-1));
        }).addOnFailureListener(e -> callback.alTerminar(-1));
    }
}
