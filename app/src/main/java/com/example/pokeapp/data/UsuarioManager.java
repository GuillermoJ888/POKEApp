package com.example.pokeapp.data;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
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
}
