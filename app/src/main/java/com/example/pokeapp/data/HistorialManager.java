package com.example.pokeapp.data;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Historial de batallas en Firebase Firestore:
 *
 *   usuarios/{uid}/historial/{auto}
 *       ->  { modo, fecha, pokemon, pokemonIds, ganador, turnos, movimientos }
 *
 * Es independiente de las victorias/derrotas del perfil: cada modo de juego
 * guarda aquí su resumen y se puede consultar por modo.
 */
public class HistorialManager {

    public static final String MODO_EMULATOR = "Battle Emulator";

    private static final int MAX_LEIDAS = 50;

    /** Una batalla guardada. */
    public static class Batalla {
        public final Date fecha;               // puede ser null si aún no sincroniza
        public final List<String> pokemon;
        public final List<Integer> pokemonIds;
        public final String ganador;
        public final int turnos;
        public final List<String> movimientos;

        Batalla(Date fecha, List<String> pokemon, List<Integer> pokemonIds, String ganador,
                int turnos, List<String> movimientos) {
            this.fecha = fecha;
            this.pokemon = pokemon;
            this.pokemonIds = pokemonIds;
            this.ganador = ganador;
            this.turnos = turnos;
            this.movimientos = movimientos;
        }
    }

    private static CollectionReference coleccion() {
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null) return null;

        return FirebaseFirestore.getInstance()
                .collection("usuarios")
                .document(usuario.getUid())
                .collection("historial");
    }

    public static void guardarBatalla(String modo, List<String> pokemon, List<Integer> pokemonIds,
                                      String ganador, List<String> movimientos,
                                      @NonNull FavoritosManager.Resultado<Boolean> callback) {

        CollectionReference col = coleccion();
        if (col == null) {
            callback.alTerminar(false);
            return;
        }

        Map<String, Object> datos = new HashMap<>();
        datos.put("modo", modo);
        datos.put("fecha", FieldValue.serverTimestamp());
        datos.put("pokemon", pokemon);
        datos.put("pokemonIds", pokemonIds);
        datos.put("ganador", ganador);
        datos.put("turnos", movimientos.size());
        datos.put("movimientos", movimientos);

        col.add(datos)
                .addOnSuccessListener(doc -> callback.alTerminar(true))
                .addOnFailureListener(e -> {
                    Log.e("HistorialManager", "No se pudo guardar la batalla", e);
                    callback.alTerminar(false);
                });
    }

    /**
     * Batallas de un modo, de la más reciente a la más antigua; null si falló.
     * Se filtra el modo aquí (y no en la consulta) para no necesitar un índice compuesto.
     */
    public static void obtenerBatallas(String modo, @NonNull FavoritosManager.Resultado<List<Batalla>> callback) {
        CollectionReference col = coleccion();
        if (col == null) {
            callback.alTerminar(new ArrayList<>());
            return;
        }

        col.orderBy("fecha", Query.Direction.DESCENDING).limit(MAX_LEIDAS).get()
                .addOnSuccessListener(snapshot -> {
                    List<Batalla> lista = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        if (!modo.equals(doc.getString("modo"))) continue;
                        lista.add(convertir(doc));
                    }
                    callback.alTerminar(lista);
                })
                .addOnFailureListener(e -> {
                    Log.e("HistorialManager", "No se pudo leer el historial", e);
                    callback.alTerminar(null);
                });
    }

    @SuppressWarnings("unchecked")
    private static Batalla convertir(DocumentSnapshot doc) {
        Timestamp ts = doc.getTimestamp("fecha");

        List<String> pokemon = new ArrayList<>();
        Object nombres = doc.get("pokemon");
        if (nombres instanceof List) {
            for (Object o : (List<Object>) nombres) pokemon.add(String.valueOf(o));
        }

        // Batallas viejas no tienen pokemonIds: la lista queda vacía
        List<Integer> ids = new ArrayList<>();
        Object listaIds = doc.get("pokemonIds");
        if (listaIds instanceof List) {
            for (Object o : (List<Object>) listaIds) {
                if (o instanceof Number) ids.add(((Number) o).intValue());
            }
        }

        List<String> movimientos = new ArrayList<>();
        Object movs = doc.get("movimientos");
        if (movs instanceof List) {
            for (Object o : (List<Object>) movs) movimientos.add(String.valueOf(o));
        }

        Long turnos = doc.getLong("turnos");
        String ganador = doc.getString("ganador");

        return new Batalla(
                ts != null ? ts.toDate() : null,
                pokemon,
                ids,
                ganador != null ? ganador : "?",
                turnos != null ? turnos.intValue() : movimientos.size(),
                movimientos);
    }
}
