package com.example.pokeapp.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.widget.ImageView;

import com.example.pokeapp.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Avatar del entrenador. Se guarda SOLO en el celular (SharedPreferences y, si el
 * usuario sube una foto, un archivo interno de la app), no en Firebase, y separado
 * por cuenta: si dos usuarios entran en el mismo celular, cada uno conserva el suyo.
 */
public class AvatarManager {

    public static class Avatar {
        public final String clave;
        public final String nombre;
        public final int dibujo;      // 0 cuando es la foto propia (está en un archivo)

        Avatar(String clave, String nombre, int dibujo) {
            this.clave = clave;
            this.nombre = nombre;
            this.dibujo = dibujo;
        }

        public boolean esFotoPropia() {
            return CLAVE_FOTO.equals(clave);
        }
    }

    /** Hasta cuántas imágenes avatar_1 … avatar_N se buscan en res/drawable(-nodpi). */
    private static final int MAX_IMAGENES = 30;
    private static final String CLAVE_FOTO = "foto";
    /** Lado, en pixeles, al que se reduce la foto propia. */
    private static final int LADO_FOTO = 512;

    private static final Avatar CLASICO = new Avatar("clasico", "Clásico", R.drawable.ic_avatar_entrenador);
    private static final Avatar FOTO = new Avatar(CLAVE_FOTO, "Mi foto", 0);

    /** Modelos dibujados en la app; solo se muestran si todavía no hay imágenes propias. */
    private static final List<Avatar> MODELOS = Arrays.asList(
            new Avatar("chico1", "Chico 1", R.drawable.ic_avatar_chico1),
            new Avatar("chico2", "Chico 2", R.drawable.ic_avatar_chico2),
            new Avatar("chica1", "Chica 1", R.drawable.ic_avatar_chica1),
            new Avatar("chica2", "Chica 2", R.drawable.ic_avatar_chica2));

    /**
     * Avatares disponibles: la foto propia (si ya subió una), el Clásico y las imágenes
     * avatar_1, avatar_2… que existan en res/drawable-nodpi.
     */
    public static List<Avatar> todos(Context context) {
        List<Avatar> lista = new ArrayList<>();
        if (archivoFoto(context).exists()) lista.add(FOTO);
        lista.add(CLASICO);

        int antes = lista.size();
        for (int n = 1; n <= MAX_IMAGENES; n++) {
            int dibujo = context.getResources().getIdentifier("avatar_" + n, "drawable", context.getPackageName());
            if (dibujo != 0) lista.add(new Avatar("avatar_" + n, "Avatar " + n, dibujo));
        }

        if (lista.size() == antes) lista.addAll(MODELOS);
        return Collections.unmodifiableList(lista);
    }

    private static final String PREFS = "poke_app_avatar";

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String uid() {
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        return usuario != null ? usuario.getUid() : "invitado";
    }

    /** Clave por cuenta: "avatar_<uid>" (o "avatar_invitado" sin sesión). */
    private static String claveUsuario() {
        return "avatar_" + uid();
    }

    /** Archivo interno de la app con la foto que subió este usuario. */
    private static File archivoFoto(Context context) {
        return new File(context.getApplicationContext().getFilesDir(), "foto_perfil_" + uid() + ".jpg");
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

    /** Pone en la imagen el avatar elegido por el usuario (foto propia o dibujo). */
    public static void mostrar(Context context, ImageView imagen) {
        pintar(context, imagen, obtener(context));
    }

    /** Pone en la imagen un avatar cualquiera de la lista. */
    public static void pintar(Context context, ImageView imagen, Avatar avatar) {
        if (avatar.esFotoPropia()) {
            Bitmap foto = BitmapFactory.decodeFile(archivoFoto(context).getPath());
            if (foto != null) {
                imagen.setImageBitmap(foto);
                return;
            }
            imagen.setImageResource(CLASICO.dibujo);
        } else {
            imagen.setImageResource(avatar.dibujo);
        }
    }

    /**
     * Guarda la foto elegida de la galería como avatar: la endereza, la recorta en
     * cuadrado al centro, la reduce a 512 px y la deja en un archivo interno de la app.
     * Devuelve true si se pudo.
     */
    public static boolean guardarFoto(Context context, Uri origen) {
        try {
            Bitmap original;
            try (InputStream entrada = context.getContentResolver().openInputStream(origen)) {
                // Se lee reducida para no llenar la memoria con fotos enormes
                BitmapFactory.Options medidas = new BitmapFactory.Options();
                medidas.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(entrada, null, medidas);
                int reduccion = 1;
                while (Math.min(medidas.outWidth, medidas.outHeight) / (reduccion * 2) >= LADO_FOTO) reduccion *= 2;

                try (InputStream otra = context.getContentResolver().openInputStream(origen)) {
                    BitmapFactory.Options opciones = new BitmapFactory.Options();
                    opciones.inSampleSize = reduccion;
                    original = BitmapFactory.decodeStream(otra, null, opciones);
                }
            }
            if (original == null) return false;

            // Las fotos de cámara guardan su giro aparte (EXIF): se endereza
            int grados = 0;
            try (InputStream exifEntrada = context.getContentResolver().openInputStream(origen)) {
                if (exifEntrada != null) {
                    int orientacion = new ExifInterface(exifEntrada)
                            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                    if (orientacion == ExifInterface.ORIENTATION_ROTATE_90) grados = 90;
                    else if (orientacion == ExifInterface.ORIENTATION_ROTATE_180) grados = 180;
                    else if (orientacion == ExifInterface.ORIENTATION_ROTATE_270) grados = 270;
                }
            } catch (Exception sinExif) {
                // sin datos de giro: se deja como viene
            }

            // Recorte cuadrado al centro + giro + reducción
            int lado = Math.min(original.getWidth(), original.getHeight());
            int x = (original.getWidth() - lado) / 2, y = (original.getHeight() - lado) / 2;
            Matrix matriz = new Matrix();
            float escala = LADO_FOTO / (float) lado;
            matriz.postScale(escala, escala);
            matriz.postRotate(grados);
            Bitmap cuadrada = Bitmap.createBitmap(original, x, y, lado, lado, matriz, true);

            try (FileOutputStream salida = new FileOutputStream(archivoFoto(context))) {
                cuadrada.compress(Bitmap.CompressFormat.JPEG, 90, salida);
            }
            guardar(context, FOTO);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
