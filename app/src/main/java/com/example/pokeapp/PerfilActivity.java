package com.example.pokeapp;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.pokeapp.data.AvatarManager;
import com.example.pokeapp.data.FavoritosManager;
import com.example.pokeapp.data.UsuarioManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class PerfilActivity extends AppCompatActivity {

    private TextView tvFavoritos, tvMedallas, tvVictorias, tvDerrotas, tvNivel;
    private ProgressBar progressNivel;
    private ImageView imgAvatar;

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
