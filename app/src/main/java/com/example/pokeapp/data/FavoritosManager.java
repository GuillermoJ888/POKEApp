package com.example.pokeapp.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Favoritos del entrenador guardados en Firebase Firestore:
 *
 *   usuarios/{uid}/favoritos/{idPokemon}  ->  { id, nombre, fecha }
 *
 * Como están ligados al usuario (no al celular), se conservan al
 * cambiar de dispositivo. Firestore también guarda caché local,
 * así que funciona sin internet y se sincroniza al volver la conexión.
 */
public class FavoritosManager {

    public interface Resultado<T> {
        void alTerminar(T valor);
    }

    // ---- Preferencias antiguas (solo para migrar) ----
    private static final String PREFS_VIEJAS = "poke_app_favoritos";
    private static final String KEY_VIEJA = "favoritos";

    private static String ultimoError = "";

    /** Explicación (en español) del último fallo con Firestore, para mostrarla al usuario. */
    public static String ultimoError() {
        return ultimoError;
    }

    private static void registrarError(Exception e) {
        Log.e("FavoritosManager", "Error con Firestore", e);

        if (e instanceof FirebaseFirestoreException) {
            switch (((FirebaseFirestoreException) e).getCode()) {
                case PERMISSION_DENIED:
                    ultimoError = "Firestore bloqueó el acceso (PERMISSION_DENIED). "
                            + "Publica las reglas de firestore.rules en Firebase Console → Firestore → Reglas.";
                    return;
                case NOT_FOUND:
                    ultimoError = "La base de datos de Firestore no existe. "
                            + "Créala en Firebase Console → Firestore Database → Crear base de datos.";
                    return;
                case UNAVAILABLE:
                    ultimoError = "Sin conexión con Firestore. Revisa tu Internet.";
                    return;
                case UNAUTHENTICATED:
                    ultimoError = "Sesión no válida. Cierra sesión y vuelve a entrar.";
                    return;
                default:
                    break;
            }
        }

        ultimoError = e.getMessage() != null ? e.getMessage() : "Error desconocido con Firestore";
    }

    private static CollectionReference coleccion() {
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null) return null;

        return FirebaseFirestore.getInstance()
                .collection("usuarios")
                .document(usuario.getUid())
                .collection("favoritos");
    }

    /** Devuelve true/false, o null si no se pudo consultar (ver ultimoError()). */
    public static void esFavorito(int id, @NonNull Resultado<Boolean> callback) {
        CollectionReference col = coleccion();
        if (col == null) {
            callback.alTerminar(false);
            return;
        }

        col.document(String.valueOf(id)).get()
                .addOnSuccessListener(doc -> callback.alTerminar(doc.exists()))
                .addOnFailureListener(e -> {
                    registrarError(e);
                    callback.alTerminar(null);
                });
    }

    /** Agrega o quita el favorito. Devuelve true si quedó como favorito. */
    public static void alternarFavorito(int id, String nombre, @NonNull Resultado<Boolean> callback) {
        CollectionReference col = coleccion();
        if (col == null) {
            callback.alTerminar(false);
            return;
        }

        col.document(String.valueOf(id)).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                col.document(String.valueOf(id)).delete();
                callback.alTerminar(false);
            } else {
                Map<String, Object> datos = new HashMap<>();
                datos.put("id", id);
                datos.put("nombre", nombre);
                datos.put("fecha", FieldValue.serverTimestamp());
                col.document(String.valueOf(id)).set(datos);
                callback.alTerminar(true);
            }
        }).addOnFailureListener(e -> {
            registrarError(e);
            callback.alTerminar(null);
        });
    }

    public static void obtenerTodos(@NonNull Resultado<List<Favorito>> callback) {
        CollectionReference col = coleccion();
        if (col == null) {
            callback.alTerminar(new ArrayList<>());
            return;
        }

        col.orderBy("fecha", Query.Direction.DESCENDING).get()
                .addOnSuccessListener(snapshot -> {
                    List<Favorito> lista = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Long id = doc.getLong("id");
                        String nombre = doc.getString("nombre");
                        if (id != null && nombre != null) {
                            lista.add(new Favorito(id.intValue(), nombre));
                        }
                    }
                    callback.alTerminar(lista);
                })
                .addOnFailureListener(e -> {
                    registrarError(e);
                    callback.alTerminar(null);
                });
    }

    public static void contar(@NonNull Resultado<Integer> callback) {
        obtenerTodos(lista -> callback.alTerminar(lista == null ? 0 : lista.size()));
    }

    /**
     * Sube a Firestore los favoritos que ya tenías guardados en el celular
     * (SharedPreferences) y luego los borra de ahí. Se ejecuta una sola vez.
     */
    public static void migrarDesdeSharedPreferences(Context context) {
        CollectionReference col = coleccion();
        if (col == null) return;

        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_VIEJAS, Context.MODE_PRIVATE);
        Set<String> viejos = prefs.getStringSet(KEY_VIEJA, null);

        if (viejos == null || viejos.isEmpty()) return;

        for (String item : viejos) {
            String[] partes = item.split("\\|", 2);
            if (partes.length < 2) continue;

            try {
                int id = Integer.parseInt(partes[0]);
                Map<String, Object> datos = new HashMap<>();
                datos.put("id", id);
                datos.put("nombre", partes[1]);
                datos.put("fecha", FieldValue.serverTimestamp());
                col.document(String.valueOf(id)).set(datos);
            } catch (NumberFormatException ignorada) {
                // dato corrupto, se omite
            }
        }

        prefs.edit().remove(KEY_VIEJA).apply();
    }
}
