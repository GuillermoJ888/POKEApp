package com.example.pokeapp.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.pokeapp.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Avatar del entrenador. Se guarda SOLO en el celular (SharedPreferences),
 * no en Firebase, y separado por cuenta: si dos usuarios entran en el mismo
 * celular, cada uno conserva el suyo.
 */
public class AvatarManager {

    public static class Avatar {
        public final String clave;
        public final String nombre;
        public final int dibujo;

        Avatar(String clave, String nombre, int dibujo) {
            this.clave = clave;
            this.nombre = nombre;
            this.dibujo = dibujo;
        }
    }

    /** Hasta cuántas imágenes avatar_1 … avatar_N se buscan en res/drawable(-nodpi). */
    private static final int MAX_IMAGENES = 30;

    private static final Avatar CLASICO = new Avatar("clasico", "Clásico", R.drawable.ic_avatar_entrenador);

    /** Modelos dibujados en la app; solo se muestran si todavía no hay imágenes propias. */
    private static final List<Avatar> MODELOS = Arrays.asList(
            new Avatar("chico1", "Chico 1", R.drawable.ic_avatar_chico1),
            new Avatar("chico2", "Chico 2", R.drawable.ic_avatar_chico2),
            new Avatar("chica1", "Chica 1", R.drawable.ic_avatar_chica1),
            new Avatar("chica2", "Chica 2", R.drawable.ic_avatar_chica2));

    /**
     * Avatares disponibles: el Clásico + las imágenes avatar_1, avatar_2… que existan.
     * Para agregar uno basta con copiar la imagen como res/drawable-nodpi/avatar_<n>.png.
     */
    public static List<Avatar> todos(Context context) {
        List<Avatar> lista = new ArrayList<>();
        lista.add(CLASICO);

        for (int n = 1; n <= MAX_IMAGENES; n++) {
            int dibujo = context.getResources().getIdentifier("avatar_" + n, "drawable", context.getPackageName());
            if (dibujo != 0) lista.add(new Avatar("avatar_" + n, "Avatar " + n, dibujo));
        }

        if (lista.size() == 1) lista.addAll(MODELOS);
        return Collections.unmodifiableList(lista);
    }

    private static final String PREFS = "poke_app_avatar";

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Clave por cuenta: "avatar_<uid>" (o "avatar_invitado" sin sesión). */
    private static String claveUsuario() {
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        return "avatar_" + (usuario != null ? usuario.getUid() : "invitado");
    }

    /** Avatar elegido por el usuario actual; el Clásico si nunca ha elegido. */
    public static Avatar obtener(Context context) {
        String clave = prefs(context).getString(claveUsuario(), CLASICO.clave);
        for (Avatar a : todos(context)) {
            if (a.clave.equals(clave)) return a;
        }
        return CLASICO;   // si el avatar elegido ya no existe
    }

    public static void guardar(Context context, Avatar avatar) {
        prefs(context).edit().putString(claveUsuario(), avatar.clave).apply();
    }
}
