package com.example.pokeapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.pokeapp.data.UsuarioManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;

public class RegisterActivity extends AppCompatActivity {

    private EditText etNombre, etCorreo, etContrasena, etConfirmarContrasena;
    private Button btnCrearCuenta;
    private ProgressBar progressRegistro;
    private TextView btnRegresar;

    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        auth = FirebaseAuth.getInstance();

        etNombre = findViewById(R.id.etNombre);
        etCorreo = findViewById(R.id.etCorreo);
        etContrasena = findViewById(R.id.etContrasena);
        etConfirmarContrasena = findViewById(R.id.etConfirmarContrasena);
        btnCrearCuenta = findViewById(R.id.btnCrearCuenta);
        progressRegistro = findViewById(R.id.progressRegistro);
        btnRegresar = findViewById(R.id.btnRegresar);

        btnRegresar.setOnClickListener(v -> finish());
        btnCrearCuenta.setOnClickListener(v -> intentarCrearCuenta());
    }

    private void intentarCrearCuenta() {

        String nombre = etNombre.getText().toString().trim();
        String correo = etCorreo.getText().toString().trim();
        String contrasena = etContrasena.getText().toString().trim();
        String confirmar = etConfirmarContrasena.getText().toString().trim();

        if (TextUtils.isEmpty(nombre)) {
            etNombre.setError("Escribe un nombre de entrenador");
            return;
        }

        if (TextUtils.isEmpty(correo) || !Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            etCorreo.setError("Correo inválido");
            return;
        }

        if (TextUtils.isEmpty(contrasena) || contrasena.length() < 6) {
            etContrasena.setError("Mínimo 6 caracteres");
            return;
        }

        if (!contrasena.equals(confirmar)) {
            etConfirmarContrasena.setError("Las contraseñas no coinciden");
            return;
        }

        mostrarCargando(true);

        auth.createUserWithEmailAndPassword(correo, contrasena)
                .addOnCompleteListener(this, task -> {

                    if (!task.isSuccessful()) {
                        mostrarCargando(false);
                        String detalle = task.getException() != null
                                ? task.getException().getMessage() : "Error desconocido";
                        Toast.makeText(this, "No se pudo crear la cuenta: " + detalle,
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    actualizarNombrePerfil(nombre);
                });
    }

    private void actualizarNombrePerfil(String nombre) {

        FirebaseUser usuario = auth.getCurrentUser();

        if (usuario == null) {
            mostrarCargando(false);
            return;
        }

        UserProfileChangeRequest cambios = new UserProfileChangeRequest.Builder()
                .setDisplayName(nombre)
                .build();

        usuario.updateProfile(cambios)
                .addOnCompleteListener(task -> {

                    mostrarCargando(false);

                    UsuarioManager.guardarPerfil(nombre);

                    Toast.makeText(
                            this,
                            "¡Cuenta creada! Bienvenido, " + nombre,
                            Toast.LENGTH_SHORT
                    ).show();

                    startActivity(new Intent(this, MenuActivity.class));
                    finish();
                });
    }

    private void mostrarCargando(boolean mostrar) {
        progressRegistro.setVisibility(mostrar ? android.view.View.VISIBLE : android.view.View.GONE);
        btnCrearCuenta.setEnabled(!mostrar);
    }
}
