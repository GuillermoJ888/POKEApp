package com.example.pokeapp;

import com.example.pokeapp.data.Idioma;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.pokeapp.data.AvatarManager;
import com.example.pokeapp.data.CargadorPokemon;
import com.example.pokeapp.data.FavoritosManager;
import com.example.pokeapp.data.PokemonMini;
import com.example.pokeapp.data.UsuarioManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Date;
import java.util.Map;

public class PerfilActivity extends AppCompatActivity {

    private TextView tvFavoritos, tvMedallas, tvVictorias, tvDerrotas, tvNivel;
    private ProgressBar progressNivel;
    private ImageView imgAvatar;
    private int companero;   // ID del compañero elegido (0 = ninguno)

    /** Selector de fotos de Android (no pide permisos): regresa la imagen elegida. */
    private final ActivityResultLauncher<PickVisualMediaRequest> lanzadorFoto = registerForActivityResult(
            new ActivityResultContracts.PickVisualMedia(),
            uri -> {
                if (uri == null) return;   // canceló
                if (AvatarManager.guardarFoto(this, uri)) {
                    AvatarManager.mostrar(this, imgAvatar);
                } else {
                    Toast.makeText(this, Idioma.t("No se pudo usar esa imagen", "Could not use that image"), Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_perfil);

        TextView tvNombre = findViewById(R.id.tvNombrePerfil);
        TextView tvCorreo = findViewById(R.id.tvCorreoPerfil);
        tvFavoritos = findViewById(R.id.tvContadorFavoritos);
        tvMedallas = findViewById(R.id.tvContadorMedallas);
        tvVictorias = findViewById(R.id.tvContadorVictorias);
        tvDerrotas = findViewById(R.id.tvContadorDerrotas);
        tvNivel = findViewById(R.id.tvNivel);
        progressNivel = findViewById(R.id.progressNivel);

        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();

        if (usuario != null) {
            String nombre = usuario.getDisplayName();
            tvNombre.setText(nombre != null && !nombre.isEmpty() ? nombre : Idioma.t("Entrenador", "Trainer"));
            tvCorreo.setText(usuario.getEmail());
        }

        // Avatar elegido (guardado solo en el celular); al tocarlo se puede cambiar
        imgAvatar = findViewById(R.id.imgAvatarPerfil);
        AvatarManager.mostrar(this, imgAvatar);
        findViewById(R.id.contenedorAvatar).setOnClickListener(v -> elegirAvatar());

        // Compañero para la Torre (se guarda en la cuenta)
        findViewById(R.id.tarjetaCompanero).setOnClickListener(v -> elegirCompanero());
        UsuarioManager.obtenerCompanero(this::mostrarCompanero);

        // El contador de favoritos abre la lista de favoritos
        findViewById(R.id.tarjetaFavoritosPerfil).setOnClickListener(v ->
                startActivity(new Intent(this, FavoritosActivity.class)));

        findViewById(R.id.btnRegresar).setOnClickListener(v -> finish());

        findViewById(R.id.btnCerrarSesionPerfil).setOnClickListener(v ->
                Dialogos.confirmar(this, "🚪", Idioma.t("¿Cerrar sesión?", "Sign out?"),
                        Idioma.t("Tendrás que volver a entrar con tu correo o con Google.", "You will have to sign in again with your email or Google."),
                        Idioma.t("Cerrar sesión", "Sign out"), Idioma.t("Cancelar", "Cancel"),
                        () -> {
                            FirebaseAuth.getInstance().signOut();
                            Intent intent = new Intent(this, LoginActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        }, null));
    }

    /** Muestra el compañero elegido en su tarjeta (id 0 = ninguno). */
    private void mostrarCompanero(int id) {
        if (isFinishing() || isDestroyed()) return;
        companero = id;
        ImageView img = findViewById(R.id.imgCompanero);
        TextView nombre = findViewById(R.id.tvCompanero);
        int i = CargadorPokemon.COMPANEROS.indexOf(id);
        if (i < 0) {
            img.setImageResource(R.drawable.ic_pokeball_logo);
            nombre.setText(R.string.t_elige_un_companero);
            return;
        }
        Glide.with(this).load(new PokemonMini(id, CargadorPokemon.NOMBRES_COMPANEROS.get(i)).getSpriteUrl()).into(img);
        nombre.setText(CargadorPokemon.NOMBRES_COMPANEROS.get(i));
    }

    /** Ventana con Pikachu, Raichu y los iniciales con sus evoluciones (una línea por renglón); el elegido lleva ✓. */
    private void elegirCompanero() {
        float dp = getResources().getDisplayMetrics().density;
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        grid.setPadding(Math.round(10 * dp), Math.round(10 * dp), Math.round(10 * dp), Math.round(10 * dp));
        AlertDialog[] dialogo = new AlertDialog[1];

        for (int i = 0; i < CargadorPokemon.COMPANEROS.size(); i++) {
            int id = CargadorPokemon.COMPANEROS.get(i);
            String nombre = CargadorPokemon.NOMBRES_COMPANEROS.get(i);

            LinearLayout celda = new LinearLayout(this);
            celda.setOrientation(LinearLayout.VERTICAL);
            celda.setGravity(Gravity.CENTER_HORIZONTAL);
            celda.setPadding(0, Math.round(4 * dp), 0, Math.round(4 * dp));
            if (id == companero) celda.setBackgroundResource(R.drawable.bg_chip_turnos);

            ImageView sprite = new ImageView(this);
            Glide.with(this).load(new PokemonMini(id, nombre).getSpriteUrl()).into(sprite);
            celda.addView(sprite, new LinearLayout.LayoutParams(Math.round(64 * dp), Math.round(64 * dp)));

            TextView texto = new TextView(this);
            texto.setText(id == companero ? "✓ " + nombre : nombre);
            texto.setTextSize(11);
            texto.setTypeface(null, Typeface.BOLD);
            texto.setGravity(Gravity.CENTER);
            texto.setMaxLines(1);
            celda.addView(texto);

            celda.setOnClickListener(v -> {
                if (dialogo[0] != null) dialogo[0].dismiss();
                UsuarioManager.guardarCompanero(id, ok -> {
                    if (Boolean.TRUE.equals(ok)) {
                        mostrarCompanero(id);
                        Toast.makeText(this, nombre + Idioma.t(" es tu compañero", " is your partner"), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, Idioma.t("No se pudo guardar. Revisa tu conexión.", "Could not save. Check your connection."), Toast.LENGTH_LONG).show();
                    }
                });
            });

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
            lp.width = 0;
            grid.addView(celda, lp);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(grid);
        dialogo[0] = new AlertDialog.Builder(this)
                .setTitle(Idioma.t("Elige tu compañero", "Choose your partner"))
                .setView(scroll)
                .setNegativeButton(Idioma.t("Cerrar", "Close"), null)
                .setNeutralButton(Idioma.t("Sin compañero", "No partner"), (d, w) -> UsuarioManager.guardarCompanero(0, ok -> {
                    if (Boolean.TRUE.equals(ok)) mostrarCompanero(0);
                }))
                .show();
    }

    /** Ventana con todos los avatares; el elegido lleva ✓ y se guarda solo en el celular. */
    private void elegirAvatar() {
        View vista = LayoutInflater.from(this).inflate(R.layout.dialog_avatares, null);
        GridLayout grid = vista.findViewById(R.id.gridAvatares);

        AlertDialog dialogo = new AlertDialog.Builder(this).setView(vista).create();
        String actual = AvatarManager.obtener(this).clave;

        for (AvatarManager.Avatar avatar : AvatarManager.todos(this)) {
            View opcion = LayoutInflater.from(this).inflate(R.layout.item_avatar, grid, false);
            ImageView img = opcion.findViewById(R.id.imgOpcionAvatar);
            AvatarManager.pintar(this, img, avatar);
            img.setClipToOutline(true);
            ((TextView) opcion.findViewById(R.id.tvOpcionNombre)).setText(avatar.nombre);
            opcion.findViewById(R.id.tvOpcionElegida)
                    .setVisibility(avatar.clave.equals(actual) ? View.VISIBLE : View.GONE);

            opcion.setOnClickListener(v -> {
                AvatarManager.guardar(this, avatar);
                AvatarManager.pintar(this, imgAvatar, avatar);
                dialogo.dismiss();
            });
            grid.addView(opcion);
        }

        // Última opción: subir una foto propia de la galería
        View subir = LayoutInflater.from(this).inflate(R.layout.item_avatar, grid, false);
        ImageView imgSubir = subir.findViewById(R.id.imgOpcionAvatar);
        imgSubir.setImageResource(android.R.drawable.ic_menu_camera);
        imgSubir.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        imgSubir.setPadding(40, 40, 40, 40);
        ((TextView) subir.findViewById(R.id.tvOpcionNombre)).setText(Idioma.t("📷 Subir foto", "📷 Upload photo"));
        subir.setOnClickListener(v -> {
            dialogo.dismiss();
            lanzadorFoto.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build());
        });
        grid.addView(subir);

        dialogo.show();
        if (dialogo.getWindow() != null) {
            dialogo.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        FavoritosManager.contar(total -> tvFavoritos.setText(String.valueOf(total)));

        UsuarioManager.obtenerEstadisticas(est -> {
            tvMedallas.setText(est.medallas + "/" + UsuarioManager.MAX_MEDALLAS);
            tvVictorias.setText(String.valueOf(est.victorias));
            tvDerrotas.setText(String.valueOf(est.derrotas));
            tvNivel.setText(getString(R.string.t_nivel_n, est.nivel()));
            progressNivel.setProgress(est.progresoNivel());
        });

        UsuarioManager.obtenerMedallasConFecha((total, fechas) -> {
            if (isFinishing() || isDestroyed()) return;
            pintarMedallas(fechas);
        });

        // Récords de minijuegos y colección del Safari
        UsuarioManager.obtenerResumenJuegos(r -> {
            if (isFinishing() || isDestroyed()) return;
            ((TextView) findViewById(R.id.tvRecordQuienPerfil)).setText(r.recordQuien > 0
                    ? getString(R.string.t_pts_racha, r.recordQuien, r.rachaQuien) : getString(R.string.t_sin_jugar));
            ((TextView) findViewById(R.id.tvRecordTiposPerfil)).setText(r.recordTipos > 0
                    ? getString(R.string.t_n_pts, r.recordTipos) : getString(R.string.t_sin_jugar));
            ((TextView) findViewById(R.id.tvRecordMemoryPerfil)).setText(r.recordMemory > 0
                    ? getString(R.string.t_n_pts, r.recordMemory) : getString(R.string.t_sin_jugar));
            ((TextView) findViewById(R.id.tvRecordPikachuPerfil)).setText(r.recordPikachu > 0
                    ? getString(R.string.t_n_pts, r.recordPikachu) : getString(R.string.t_sin_jugar));
            ((TextView) findViewById(R.id.tvCapturadosPerfil)).setText(String.valueOf(r.capturados));
        });
    }

    /** Las 12 medallas en dos filas: a color las ganadas y en gris las que faltan. */
    private void pintarMedallas(Map<Integer, Date> fechas) {
        GridLayout grid = findViewById(R.id.gridMedallasPerfil);
        grid.removeAllViews();
        grid.setOnClickListener(v -> startActivity(new Intent(this, MedallasActivity.class)));
        float dp = getResources().getDisplayMetrics().density;

        ColorMatrix gris = new ColorMatrix();
        gris.setSaturation(0f);

        for (int numero = 1; numero <= UsuarioManager.MAX_MEDALLAS; numero++) {
            ImageView medalla = new ImageView(this);
            int imagen = getResources().getIdentifier("medalla_" + numero, "drawable", getPackageName());
            if (imagen != 0) medalla.setImageResource(imagen);
            medalla.setScaleType(ImageView.ScaleType.FIT_CENTER);
            medalla.setContentDescription(MedallasActivity.nombre(numero));
            if (!fechas.containsKey(numero)) {
                medalla.setColorFilter(new ColorMatrixColorFilter(gris));
                medalla.setAlpha(0.45f);
            }

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED),
                    GridLayout.spec(GridLayout.UNDEFINED, 1f));
            lp.width = 0;
            lp.height = Math.round(62 * dp);
            lp.setMargins(Math.round(2 * dp), Math.round(4 * dp), Math.round(2 * dp), Math.round(4 * dp));
            grid.addView(medalla, lp);
        }
    }
}
