package com.example.pokeapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pokeapp.data.FavoritosManager;
import com.example.pokeapp.data.UsuarioManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.List;

/**
 * Menú Principal: saludo con el nombre del entrenador y los 13 botones
 * (A–M) del proyecto. Los módulos que aún no existen muestran
 * "Próximamente" hasta su fase correspondiente.
 */
public class MenuActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);

        // Sube a Firestore lo que estaba guardado en el celular (solo la 1ª vez)
        FavoritosManager.migrarDesdeSharedPreferences(this);
        UsuarioManager.guardarPerfil(null);

        mostrarSaludo();
        configurarModulos();

        // La pokébola de arriba a la derecha abre el perfil del usuario.
        // Cerrar sesión ahora vive solo ahí, no repetido en este Menú.
        findViewById(R.id.imgAvatar).setOnClickListener(v ->
                startActivity(new Intent(this, PerfilActivity.class)));
    }

    private void mostrarSaludo() {
        TextView tvSaludo = findViewById(R.id.tvSaludo);

        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();

        if (usuario != null && usuario.getDisplayName() != null && !usuario.getDisplayName().isEmpty()) {
            tvSaludo.setText("¡Hola, " + usuario.getDisplayName() + "!");
        } else {
            tvSaludo.setText("¡Hola, Entrenador!");
        }
    }

    private void configurarModulos() {
        List<Modulo> modulos = Modulo.todos();

        RecyclerView recycler = findViewById(R.id.recyclerModulos);

        GridLayoutManager layout = new GridLayoutManager(this, 2);
        // El último botón (M) ocupa las dos columnas para que no quede cojo
        layout.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return position == modulos.size() - 1 && modulos.size() % 2 != 0 ? 2 : 1;
            }
        });

        recycler.setLayoutManager(layout);
        recycler.setAdapter(new ModuloAdapter(modulos, this::abrirModulo));
    }

    private void abrirModulo(Modulo modulo) {
        if (modulo.estaActivo()) {
            startActivity(new Intent(this, modulo.destino));
        } else {
            Toast.makeText(
                    this,
                    modulo.titulo + " llega en una próxima fase del proyecto",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }
}
