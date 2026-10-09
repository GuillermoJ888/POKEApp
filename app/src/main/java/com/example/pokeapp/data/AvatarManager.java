package com.example.pokeapp.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.pokeapp.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

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

    public static final List<Avatar> TODOS = Collections.unmodifiableList(Arrays.asList(
            new Avatar("clasico", "Clásico", R.drawable.ic_avatar_entrenador),
            new Avatar("chico1", "Chico 1", R.drawable.ic_avatar_chico1),
            new Avatar("chico2", "Chico 2", R.drawable.ic_avatar_chico2),
            new Avatar("chica1", "Chica 1", R.drawable.ic_avatar_chica1),
            new Avatar("chica2", "Chica 2", R.drawable.ic_avatar_chica2)
    ));

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
        String clave = prefs(context).getString(claveUsuario(), TODOS.get(0).clave);
        for (Avatar a : TODOS) {
            if (a.clave.equals(clave)) return a;
        }
        return TODOS.get(0);
    }

    public static void guardar(Context context, Avatar avatar) {
        prefs(context).edit().putString(claveUsuario(), avatar.clave).apply();
    }
}
