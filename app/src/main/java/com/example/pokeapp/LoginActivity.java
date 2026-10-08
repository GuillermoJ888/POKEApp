package com.example.pokeapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;

import com.example.pokeapp.data.UsuarioManager;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;

public class LoginActivity extends AppCompatActivity {

    private static final String PREFS = "poke_app_prefs";
    private static final String KEY_CORREO_RECORDADO = "correo_recordado";

    private EditText etCorreo, etContrasena;
    private CheckBox cbRecordarme;
    private Button btnIniciarSesion, btnGoogle;
    private TextView tvOlvideContrasena, tvCrearCuenta;
    private ProgressBar progressLogin;
    private ImageButton btnMostrarContrasena;

    private boolean contrasenaVisible = false;

    private FirebaseAuth auth;
    private SharedPreferences prefs;
    private CredentialManager credentialManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        auth = FirebaseAuth.getInstance();
        credentialManager = CredentialManager.create(this);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        enlazarVistas();
        precargarCorreoRecordado();
        configurarListeners();
    }

    private void enlazarVistas() {
        etCorreo = findViewById(R.id.etCorreo);
        etContrasena = findViewById(R.id.etContrasena);
        cbRecordarme = findViewById(R.id.cbRecordarme);
        btnIniciarSesion = findViewById(R.id.btnIniciarSesion);
        btnGoogle = findViewById(R.id.btnGoogle);
        tvOlvideContrasena = findViewById(R.id.tvOlvideContrasena);
        tvCrearCuenta = findViewById(R.id.tvCrearCuenta);
        progressLogin = findViewById(R.id.progressLogin);
        btnMostrarContrasena = findViewById(R.id.btnMostrarContrasena);
    }

    private void precargarCorreoRecordado() {
        String correoGuardado = prefs.getString(KEY_CORREO_RECORDADO, null);

        if (correoGuardado != null) {
            etCorreo.setText(correoGuardado);
            cbRecordarme.setChecked(true);
        }
    }

    private void configurarListeners() {

        btnMostrarContrasena.setOnClickListener(v -> alternarVisibilidadContrasena());

        btnIniciarSesion.setOnClickListener(v -> intentarIniciarSesion());

        tvOlvideContrasena.setOnClickListener(v -> mostrarDialogoRecuperarContrasena());

        tvCrearCuenta.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class))
        );

        btnGoogle.setOnClickListener(v -> iniciarConGoogle());
    }

    private void alternarVisibilidadContrasena() {
        contrasenaVisible = !contrasenaVisible;

        int seleccion = etContrasena.getSelectionEnd();

        etContrasena.setInputType(
                contrasenaVisible
                        ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                        : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
        );

        etContrasena.setSelection(seleccion);
    }

    private void intentarIniciarSesion() {

        String correo = etCorreo.getText().toString().trim();
        String contrasena = etContrasena.getText().toString().trim();

        if (TextUtils.isEmpty(correo) || !Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            etCorreo.setError("Correo inválido");
            return;
        }

        if (TextUtils.isEmpty(contrasena) || contrasena.length() < 6) {
            etContrasena.setError("Mínimo 6 caracteres");
            return;
        }

        mostrarCargando(true);

        auth.signInWithEmailAndPassword(correo, contrasena)
                .addOnCompleteListener(this, task -> {

                    mostrarCargando(false);

                    if (task.isSuccessful()) {

                        guardarPreferenciaRecordarme(correo);

                        startActivity(new Intent(this, MenuActivity.class));
                        finish();

                    } else {
                        Toast.makeText(
                                this,
                                "No se pudo iniciar sesión: correo o contraseña incorrectos",
                                Toast.LENGTH_SHORT
                        ).show();
                        if (task.getException() != null) {
                            android.util.Log.e("LoginActivity", "Login falló", task.getException());
                        }
                    }
                });
    }

    private void guardarPreferenciaRecordarme(String correo) {
        SharedPreferences.Editor editor = prefs.edit();

        if (cbRecordarme.isChecked()) {
            editor.putString(KEY_CORREO_RECORDADO, correo);
        } else {
            editor.remove(KEY_CORREO_RECORDADO);
        }

        editor.apply();
    }

    private void mostrarDialogoRecuperarContrasena() {

        EditText input = new EditText(this);
        input.setHint("Tu correo electrónico");
        input.setText(etCorreo.getText().toString());

        new AlertDialog.Builder(this)
                .setTitle("Recuperar contraseña")
                .setMessage("Te enviaremos un enlace para restablecer tu contraseña.")
                .setView(input)
                .setPositiveButton("Enviar", (dialog, which) -> {

                    String correo = input.getText().toString().trim();

                    if (TextUtils.isEmpty(correo) || !Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
                        Toast.makeText(this, "Escribe un correo válido", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    auth.sendPasswordResetEmail(correo)
                            .addOnCompleteListener(task -> Toast.makeText(
                                    this,
                                    task.isSuccessful()
                                            ? "Revisa tu correo para restablecer la contraseña"
                                            : "No se pudo enviar el correo",
                                    Toast.LENGTH_SHORT
                            ).show());
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ------------------------------------------------------------------
    // Google Sign-In (Credential Manager + Firebase Auth)
    // ------------------------------------------------------------------

    private void iniciarConGoogle() {

        // default_web_client_id lo genera google-services.json cuando
        // el proveedor Google está habilitado en Firebase.
        int resId = getResources().getIdentifier(
                "default_web_client_id", "string", getPackageName());

        if (resId == 0) {
            Toast.makeText(
                    this,
                    "Falta habilitar Google en Firebase y volver a descargar google-services.json",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        GetGoogleIdOption opcionGoogle = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(getString(resId))
                .setAutoSelectEnabled(false)
                .build();

        GetCredentialRequest solicitud = new GetCredentialRequest.Builder()
                .addCredentialOption(opcionGoogle)
                .build();

        mostrarCargando(true);

        credentialManager.getCredentialAsync(
                this,
                solicitud,
                new CancellationSignal(),
                ContextCompat.getMainExecutor(this),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse resultado) {
                        manejarCredencialGoogle(resultado.getCredential());
                    }

                    @Override
                    public void onError(GetCredentialException e) {
                        mostrarCargando(false);

                        if (!(e instanceof GetCredentialCancellationException)) {
                            Toast.makeText(
                                    LoginActivity.this,
                                    "No se pudo abrir Google: " + e.getMessage(),
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    }
                }
        );
    }

    private void manejarCredencialGoogle(Credential credencial) {

        if (credencial instanceof CustomCredential
                && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credencial.getType())) {

            try {
                GoogleIdTokenCredential googleCredencial =
                        GoogleIdTokenCredential.createFrom(((CustomCredential) credencial).getData());

                AuthCredential firebaseCredencial =
                        GoogleAuthProvider.getCredential(googleCredencial.getIdToken(), null);

                auth.signInWithCredential(firebaseCredencial)
                        .addOnCompleteListener(this, task -> {

                            mostrarCargando(false);

                            if (task.isSuccessful()) {
                                UsuarioManager.guardarPerfil(googleCredencial.getDisplayName());

                                startActivity(new Intent(this, MenuActivity.class));
                                finish();
                            } else {
                                String detalle = task.getException() != null
                                        ? task.getException().getMessage() : "Error desconocido";
                                Toast.makeText(this, "Firebase rechazó el acceso: " + detalle,
                                        Toast.LENGTH_LONG).show();
                            }
                        });

            } catch (Exception e) {
                mostrarCargando(false);
                Toast.makeText(this, "Respuesta de Google inválida", Toast.LENGTH_SHORT).show();
            }

        } else {
            mostrarCargando(false);
            Toast.makeText(this, "Tipo de credencial no soportado", Toast.LENGTH_SHORT).show();
        }
    }

    private void mostrarCargando(boolean mostrar) {
        progressLogin.setVisibility(mostrar ? android.view.View.VISIBLE : android.view.View.GONE);
        btnIniciarSesion.setEnabled(!mostrar);
        btnGoogle.setEnabled(!mostrar);
    }
}
