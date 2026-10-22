package com.example.pokeapp;

import android.content.Intent;
import android.graphics.Color;
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
                    Toast.makeText(this, "No se pudo usar esa imagen", Toast.LENGTH_SHORT).show();
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
            tvNombre.setText(nombre != null && !nombre.isEmpty() ? nombre : "Entrenador");
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
                Dialogos.confirmar(this, "🚪", "¿Cerrar sesión?",
                        "Tendrás que volver a entrar con tu correo o con Google.",
                        "Cerrar sesión", "Cancelar",
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
                        Toast.makeText(this, nombre + " es tu compañero", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "No se pudo guardar. Revisa tu conexión.", Toast.LENGTH_LONG).show();
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
                .setTitle("Elige tu compañero")
                .setView(scroll)
                .setNegativeButton("Cerrar", null)
                .setNeutralButton("Sin compañero", (d, w) -> UsuarioManager.guardarCompanero(0, ok -> {
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
        ((TextView) subir.findViewById(R.id.tvOpcionNombre)).setText("📷 Subir foto");
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
            tvMedallas.setText(est.medallas + "/10");
            tvVictorias.setText(String.valueOf(est.victorias));
            tvDerrotas.setText(String.valueOf(est.derrotas));
            tvNivel.setText("Nivel " + est.nivel());
            progressNivel.setProgress(est.progresoNivel());
        });

        // Récords de minijuegos y colección del Safari
        UsuarioManager.obtenerResumenJuegos(r -> {
            if (isFinishing() || isDestroyed()) return;
            ((TextView) findViewById(R.id.tvRecordQuienPerfil)).setText(r.recordQuien > 0
                    ? r.recordQuien + " pts · racha " + r.rachaQuien : "Sin jugar");
            ((TextView) findViewById(R.id.tvRecordTiposPerfil)).setText(r.recordTipos > 0
                    ? r.recordTipos + " pts" : "Sin jugar");
            ((TextView) findViewById(R.id.tvRecordMemoryPerfil)).setText(r.recordMemory > 0
                    ? r.recordMemory + " pts" : "Sin jugar");
            ((TextView) findViewById(R.id.tvCapturadosPerfil)).setText(String.valueOf(r.capturados));
        });
    }
}
