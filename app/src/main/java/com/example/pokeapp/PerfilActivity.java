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
        imgAvatar.setImageResource(AvatarManager.obtener(this).dibujo);
        findViewById(R.id.contenedorAvatar).setOnClickListener(v -> elegirAvatar());

        findViewById(R.id.btnRegresar).setOnClickListener(v -> finish());

        findViewById(R.id.btnCerrarSesionPerfil).setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
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
            img.setImageResource(avatar.dibujo);
            img.setClipToOutline(true);
            ((TextView) opcion.findViewById(R.id.tvOpcionNombre)).setText(avatar.nombre);
            opcion.findViewById(R.id.tvOpcionElegida)
                    .setVisibility(avatar.clave.equals(actual) ? View.VISIBLE : View.GONE);

            opcion.setOnClickListener(v -> {
                AvatarManager.guardar(this, avatar);
                imgAvatar.setImageResource(avatar.dibujo);
                dialogo.dismiss();
            });
            grid.addView(opcion);
        }

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
    }
}
