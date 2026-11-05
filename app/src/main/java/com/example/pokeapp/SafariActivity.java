package com.example.pokeapp;

import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.bumptech.glide.Glide;
import com.example.pokeapp.battle.BattlePokemon;
import com.example.pokeapp.data.CargadorPokemon;
import com.example.pokeapp.data.Idioma;
import com.example.pokeapp.data.PokemonMini;
import com.example.pokeapp.data.Preferencias;
import com.example.pokeapp.data.UsuarioManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Safari Pokémon (módulo F): aparecen Pokémon al azar y se intenta capturarlos.
 *
 * - Tres Pokéball con distinta probabilidad: Poké Ball 35 %, Super Ball 55 %, Ultra Ball 80 %.
 *   A los Pokémon fuertes (suma de stats alta) se les resta hasta 25 puntos.
 * - Cada safari empieza con 8 Poké Ball, 4 Super Ball y 2 Ultra Ball; termina al acabarse.
 * - Lanzamiento estilo Pokémon GO: se elige la Pokéball con su botón y se desliza con el dedo hacia
 *   el Pokémon. Si va floja o desviada, se pierde.
 * - Aro de captura: se encoge sin parar y su color indica la dificultad (verde, amarillo, naranja, rojo).
 *   Si la bola cae dentro del aro da un bono según lo chico que esté: Bien +5, Genial +12, Excelente +20.
 * - Rango de entrenador según los Pokémon distintos de la colección (Novato a Leyenda).
 * - Bola curva (+10 %): antes de lanzarla se gira en círculos con el dedo hasta que empieza a brillar.
 *   Tocar otra vez el botón de la bola elegida la lanza recta, sin bonos.
 * - Si no queda atrapado, el Pokémon puede escapar (25 %). También se puede huir del encuentro.
 * - Los capturados se guardan en la colección del usuario (Firestore: usuarios/{uid}.capturados).
 */
public class SafariActivity extends AppCompatActivity {

    private static final String[] BOLAS = {"Poké Ball", "Super Ball", "Ultra Ball"};
    private static final int[] PROBABILIDAD = {35, 55, 80};
    private static final int[] INICIALES = {8, 4, 2};
    private static final int[] COLOR = {0xFFE3350D, 0xFF1D4ED8, 0xFFB8860B};
    private static final int PROBABILIDAD_ESCAPE = 25;
    private static final int BONO_CURVA = 10, BONO_BIEN = 5, BONO_GENIAL = 12, BONO_EXCELENTE = 20;
    private static final float ARO_EXCELENTE = 0.45f, ARO_GENIAL = 0.72f;   // tamaño del aro (1 = completo)
    private static final int[] RANGO_MINIMO = {0, 5, 15, 30, 60, 100};      // Pokémon distintos capturados

    // Lanzamiento con el dedo (medidas en dp)
    private static final float ARRASTRE_MINIMO = 40f;      // menos que esto no cuenta como lanzamiento
    private static final float VELOCIDAD_MINIMA = 250f;    // dp/s hacia arriba para soltar la bola
    private static final float VELOCIDAD_ALCANCE = 500f;   // dp/s para que llegue hasta el Pokémon
    private static final float PASO_GIRO = 8f;             // cada cuántos dp se mide hacia dónde va el dedo
    private static final double GIRO_CURVA = Math.PI * 1.6;   // casi una vuelta completa = bola curva
    private static final float AYUDA_PUNTERIA = 0.6f;      // 1 = sin ayuda; menos = se acerca más al Pokémon

    private final Random random = new Random();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final int[] bolas = new int[BOLAS.length];
    private BattlePokemon salvaje;
    private int dificultad;            // puntos que se restan a la probabilidad (0–25)
    private boolean ocupado = true;    // cargando o a media animación
    private int seleccionada;          // Pokéball lista para lanzar
    private float dp;

    // Recorrido del dedo mientras arrastra la bola (para sacar velocidad y curva)
    private final List<float[]> trazo = new ArrayList<>();   // {x, y, milisegundos}

    // Giro de la bola: se suman las vueltas que da el dedo; al pasar GIRO_CURVA queda "cargada"
    private float giroX, giroY;        // último punto donde se midió el rumbo
    private double rumbo, giro;        // rumbo anterior y vueltas acumuladas (radianes; + = sentido del reloj)
    private boolean hayRumbo;
    private int curva;                 // 0 = recta; +1 / −1 = bola curva hacia la derecha / izquierda
    private int capturadosHoy;
    private List<PokemonMini> coleccion = new ArrayList<>();

    private ImageView imgPokemon, imgBola;
    private AroCaptura aro;
    private TextView tvNombre, tvDatos, tvMensaje, tvColeccion;
    private final TextView[] botones = new TextView[BOLAS.length];
    private View btnHuir, btnNuevo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_safari);
        pantallaCompleta();

        imgPokemon = findViewById(R.id.imgSafari);
        imgBola = findViewById(R.id.imgBolaSafari);
        aro = findViewById(R.id.aroSafari);
        tvNombre = findViewById(R.id.tvNombreSafari);
        tvDatos = findViewById(R.id.tvDatosSafari);
        tvMensaje = findViewById(R.id.tvMensajeSafari);
        tvColeccion = findViewById(R.id.tvColeccionSafari);
        botones[0] = findViewById(R.id.btnBola0);
        botones[1] = findViewById(R.id.btnBola1);
        botones[2] = findViewById(R.id.btnBola2);
        btnHuir = findViewById(R.id.btnHuirSafari);
        btnNuevo = findViewById(R.id.btnNuevoSafari);

        findViewById(R.id.btnRegresar).setOnClickListener(v -> finish());
        findViewById(R.id.btnVerColeccion).setOnClickListener(v -> mostrarColeccion());
        for (int i = 0; i < BOLAS.length; i++) {
            final int tipo = i;
            botones[i].setOnClickListener(v -> elegirBola(tipo));

            // Cada botón lleva dibujada su Pokéball
            BolaDrawable icono = new BolaDrawable(i);
            int lado = Math.round(30 * getResources().getDisplayMetrics().density);
            icono.setBounds(0, 0, lado, lado);
            botones[i].setCompoundDrawablesRelative(icono, null, null, null);
            botones[i].setPadding(lado / 3, 0, lado / 4, 0);
        }
        btnHuir.setOnClickListener(v -> {
            if (ocupado || salvaje == null) return;
            tvMensaje.setText(Idioma.t("Huiste de " + salvaje.nombre + ".", "You ran away from " + salvaje.nombre + "."));
            siguienteEncuentro(700);
        });
        btnNuevo.setOnClickListener(v -> nuevoSafari());
        dp = getResources().getDisplayMetrics().density;
        imgBola.setOnTouchListener(this::alTocarBola);

        UsuarioManager.obtenerCapturados(lista -> {
            coleccion = lista;
            pintar();
        });
        nuevoSafari();
    }

    /** Oculta barra de estado y de navegación (se ven deslizando desde el borde), como en la arena. */
    private void pantallaCompleta() {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }

    private void nuevoSafari() {
        System.arraycopy(INICIALES, 0, bolas, 0, BOLAS.length);
        capturadosHoy = 0;
        btnNuevo.setVisibility(View.GONE);
        siguienteEncuentro(0);
    }

    private int probabilidad(int tipo) {
        return Math.max(5, PROBABILIDAD[tipo] - dificultad);
    }

    private int nivelRango() {
        int nivel = 0;
        for (int i = 0; i < RANGO_MINIMO.length; i++) if (coleccion.size() >= RANGO_MINIMO[i]) nivel = i;
        return nivel;
    }

    private String rango() {
        String[] nombres = Preferencias.esIngles()
                ? new String[]{"Rookie", "Explorer", "Expert", "Veteran", "Master", "Legend"}
                : new String[]{"Novato", "Explorador", "Experto", "Veterano", "Maestro", "Leyenda"};
        return nombres[nivelRango()];
    }

    /** Color del aro según lo difícil que es el Pokémon, como en Pokémon GO. */
    private int colorAro() {
        if (dificultad <= 6) return 0xFF4ADE80;
        if (dificultad <= 13) return 0xFFFACC15;
        if (dificultad <= 19) return 0xFFFB923C;
        return 0xFFEF4444;
    }

    private boolean quedanBolas() {
        for (int n : bolas) if (n > 0) return true;
        return false;
    }

    private void pintar() {
        tvColeccion.setText(Idioma.t("🏅 Rango " + rango() + " · 🎒 " + coleccion.size() + " · Hoy: " + capturadosHoy,
                "🏅 " + rango() + " rank · 🎒 " + coleccion.size() + " · Today: " + capturadosHoy));
        boolean hayPokemon = salvaje != null;
        for (int i = 0; i < BOLAS.length; i++) {
            botones[i].setText(BOLAS[i] + "\n×" + bolas[i] + (hayPokemon ? " · " + probabilidad(i) + "%" : ""));
            botones[i].setAlpha(bolas[i] <= 0 || !hayPokemon ? 0.35f : i == seleccionada ? 1f : 0.65f);
            botones[i].getBackground().mutate().setTint(COLOR[i]);
        }
        btnHuir.setVisibility(hayPokemon ? View.VISIBLE : View.INVISIBLE);
    }

    // ------------------------------------------------------------------
    // Encuentros
    // ------------------------------------------------------------------

    private void siguienteEncuentro(long espera) {
        ocupado = true;
        salvaje = null;
        aro.detener();
        handler.postDelayed(() -> {
            if (isFinishing() || isDestroyed()) return;
            if (!quedanBolas()) {
                terminarSafari();
                return;
            }
            imgPokemon.animate().cancel();
            imgPokemon.setVisibility(View.INVISIBLE);
            imgBola.setVisibility(View.INVISIBLE);
            tvNombre.setText("…");
            tvDatos.setText("");
            tvMensaje.setText(getString(R.string.t_algo_se_mueve_entre_la_hierba));
            pintar();

            int id = CargadorPokemon.idsAleatorios(1, false, random).get(0);
            CargadorPokemon.cargar(Collections.singletonList(id), lista -> {
                if (isFinishing() || isDestroyed()) return;
                if (lista == null) {
                    tvMensaje.setText(Idioma.t("No se pudo cargar el Pokémon. Revisa tu internet.",
                            "Could not load the Pokémon. Check your internet."));
                    btnNuevo.setVisibility(View.VISIBLE);
                    return;
                }
                aparecer(new BattlePokemon(lista.get(0)));
            });
        }, espera);
    }

    private void aparecer(BattlePokemon p) {
        salvaje = p;
        int total = p.hpMaxima + p.ataque + p.defensa + p.ataqueEspecial + p.defensaEspecial + p.velocidad;
        // De 300 (débil) a 600 (muy fuerte) en stats: de 0 a 25 puntos más difícil
        dificultad = Math.max(0, Math.min(25, (total - 300) / 12));

        Glide.with(this).load(p.imagen).into(imgPokemon);
        imgPokemon.setVisibility(View.VISIBLE);
        imgPokemon.setScaleX(0f);
        imgPokemon.setScaleY(0f);
        imgPokemon.setAlpha(1f);
        imgPokemon.animate().scaleX(1f).scaleY(1f).setDuration(350).start();

        // PC estilo Pokémon GO: según sus stats y su nivel al azar (solo informativo)
        int pc = total * (80 + random.nextInt(121)) / 100;
        tvNombre.setText(Idioma.t("PC ", "CP ") + pc + " · " + p.nombre + (yaCapturado(p.id) ? " ✔" : ""));
        tvDatos.setText(String.format(Locale.ROOT, Idioma.t(
                "N.º %d · %s\nHP %d · Ataque %d · Defensa %d · Velocidad %d",
                "No. %d · %s\nHP %d · Attack %d · Defense %d · Speed %d"),
                p.id, p.tiposTexto(), p.hpMaxima, p.ataque, p.defensa, p.velocidad));
        tvMensaje.setText(Idioma.t(
                "¡Un " + p.nombre + " salvaje apareció!\nLánzala cuando el aro esté chico; si antes la giras sale curva (+" + BONO_CURVA + " %).",
                "A wild " + p.nombre + " appeared!\nThrow when the ring is small; spin it first for a curveball (+" + BONO_CURVA + "%)."));
        ocupado = false;
        prepararBola();
        pintar();
    }

    private boolean yaCapturado(int id) {
        for (PokemonMini m : coleccion) if (m.getId() == id) return true;
        return false;
    }

    // ------------------------------------------------------------------
    // Lanzar la Pokéball
    // ------------------------------------------------------------------

    /** Botón de una Pokéball: la deja lista; si ya era la elegida, la lanza recta (sin bonos). */
    private void elegirBola(int tipo) {
        if (ocupado || salvaje == null || bolas[tipo] <= 0) return;
        if (tipo == seleccionada) {
            lanzar(0f, 1f, 0, false);
            return;
        }
        seleccionada = tipo;
        prepararBola();
        pintar();
    }

    /** Coloca abajo la Pokéball elegida (o la primera que quede), lista para deslizarla. */
    private void prepararBola() {
        if (bolas[seleccionada] <= 0) {
            for (int i = 0; i < BOLAS.length; i++) {
                if (bolas[i] > 0) {
                    seleccionada = i;
                    break;
                }
            }
        }
        imgBola.animate().cancel();
        imgBola.setImageDrawable(new BolaDrawable(seleccionada));
        imgBola.setTranslationX(0f);
        imgBola.setTranslationY(0f);
        imgBola.setRotation(0f);
        imgBola.setScaleX(1f);
        imgBola.setScaleY(1f);
        imgBola.setAlpha(1f);
        imgBola.clearColorFilter();
        curva = 0;
        imgBola.setVisibility(bolas[seleccionada] > 0 ? View.VISIBLE : View.INVISIBLE);
        if (salvaje != null && bolas[seleccionada] > 0) aro.iniciar(colorAro());
    }

    /** El dedo arrastra la bola (y puede girarla); al soltarla hacia arriba con impulso, sale lanzada. */
    private boolean alTocarBola(View bola, MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (ocupado || salvaje == null || bolas[seleccionada] <= 0) return false;
                bola.getParent().requestDisallowInterceptTouchEvent(true);   // que la pantalla no se desplace
                trazo.clear();
                trazo.add(new float[]{e.getRawX(), e.getRawY(), e.getEventTime()});
                giroX = e.getRawX();
                giroY = e.getRawY();
                giro = 0;
                hayRumbo = false;
                return true;

            case MotionEvent.ACTION_MOVE:
                if (trazo.isEmpty()) return false;
                trazo.add(new float[]{e.getRawX(), e.getRawY(), e.getEventTime()});
                bola.setTranslationX(e.getRawX() - trazo.get(0)[0]);
                bola.setTranslationY(e.getRawY() - trazo.get(0)[1]);
                medirGiro(e.getRawX(), e.getRawY());
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (trazo.isEmpty()) return false;
                trazo.add(new float[]{e.getRawX(), e.getRawY(), e.getEventTime()});
                soltarBola(e.getActionMasked() == MotionEvent.ACTION_CANCEL);
                trazo.clear();
                return true;
        }
        return false;
    }

    /**
     * Suma cuánto va dando vuelta el dedo. Un trazo recto casi no suma; los círculos suman una vuelta
     * cada uno. Al juntar casi una vuelta la bola queda cargada como bola curva (brilla y vibra).
     */
    private void medirGiro(float x, float y) {
        float dx = x - giroX, dy = y - giroY;
        if (Math.hypot(dx, dy) < PASO_GIRO * dp) return;
        double nuevo = Math.atan2(dy, dx);
        if (hayRumbo) {
            double cambio = nuevo - rumbo;
            if (cambio > Math.PI) cambio -= 2 * Math.PI;
            if (cambio < -Math.PI) cambio += 2 * Math.PI;
            giro += cambio;
            imgBola.setRotation((float) Math.toDegrees(giro));   // la bola gira junto con el dedo
        }
        rumbo = nuevo;
        hayRumbo = true;
        giroX = x;
        giroY = y;

        if (curva == 0 && Math.abs(giro) >= GIRO_CURVA) {
            curva = giro > 0 ? 1 : -1;
            imgBola.setColorFilter(0x66FFE066);   // brillo dorado: ya es bola curva
            Efectos.vibrar(this, 40);
            tvMensaje.setText(Idioma.t("🌀 ¡Bola curva lista! Ahora lánzala hacia " + salvaje.nombre + ".",
                    "🌀 Curveball ready! Now throw it at " + salvaje.nombre + "."));
        }
    }

    private void soltarBola(boolean cancelado) {
        float[] inicio = trazo.get(0), fin = trazo.get(trazo.size() - 1);

        // Velocidad del dedo en los últimos 120 ms (dp por segundo)
        int i = trazo.size() - 1;
        while (i > 0 && fin[2] - trazo.get(i - 1)[2] <= 120) i--;
        float segundos = Math.max(1f, fin[2] - trazo.get(i)[2]) / 1000f;
        float vx = (fin[0] - trazo.get(i)[0]) / segundos / dp;
        float vy = (fin[1] - trazo.get(i)[1]) / segundos / dp;

        // Cuenta como lanzamiento solo si el último impulso va claramente hacia arriba
        // (soltarla a media vuelta, yendo de lado o hacia abajo, no la lanza)
        boolean lanzada = !cancelado && fin[1] - inicio[1] < -ARRASTRE_MINIMO * dp
                && vy < -VELOCIDAD_MINIMA && Math.abs(vx) < -vy;
        if (!lanzada) {
            // Solo la movió: regresa a su lugar, no se gasta y conserva el giro si ya estaba cargada
            imgBola.animate().translationX(0f).translationY(0f).setDuration(180).start();
            return;
        }

        float inclinacion = vx / -vy;   // hacia dónde apuntaba el dedo al soltar (entre −1 y 1)
        float fuerza = Math.min(1f, -vy / VELOCIDAD_ALCANCE);
        lanzar(inclinacion, fuerza, curva, true);
    }

    /**
     * 1) La bola vuela desde donde está hasta la altura del Pokémon.
     * inclinacion = cuánto se va de lado por cada tramo que sube; fuerza 0–1 (menos de 1 se queda corta);
     * curva = 0 recta, +1 / −1 bola curva (solo cambia el vuelo y el bono, no a dónde cae);
     * conBonos = false en el lanzamiento con botón.
     */
    private void lanzar(float inclinacion, float fuerza, int curva, boolean conBonos) {
        ocupado = true;
        int tipo = seleccionada;
        bolas[tipo]--;
        pintar();

        // Posiciones como desplazamiento de la bola respecto a su lugar de reposo
        float x0 = imgBola.getTranslationX(), y0 = imgBola.getTranslationY();
        float centroPokemonX = imgPokemon.getLeft() + imgPokemon.getWidth() / 2f
                - (imgBola.getLeft() + imgBola.getWidth() / 2f);
        float centroPokemonY = imgPokemon.getTop() + imgPokemon.getHeight() / 2f
                - (imgBola.getTop() + imgBola.getHeight() / 2f);
        float subida = Math.min(0f, centroPokemonY - y0);            // negativo: hacia arriba
        // Cae hacia donde apuntó el dedo, con un poco de ayuda para acercarla al Pokémon
        float apuntado = x0 + inclinacion * -subida * fuerza;
        float x1 = conBonos ? centroPokemonX + (apuntado - centroPokemonX) * AYUDA_PUNTERIA : centroPokemonX;
        float y1 = y0 + subida * fuerza;

        float distancia = Math.abs(x1 - centroPokemonX);
        boolean llega = fuerza >= 1f;
        boolean acierta = llega && distancia <= imgPokemon.getWidth() * 0.5f;
        boolean esCurva = conBonos && curva != 0;
        float arco = curva * 85f * dp;

        tvMensaje.setText(esCurva
                ? Idioma.t("¡Bola curva con " + BOLAS[tipo] + "!", "Curveball with " + BOLAS[tipo] + "!")
                : Idioma.t("¡Lanzaste una " + BOLAS[tipo] + "!", "You threw a " + BOLAS[tipo] + "!"));

        ValueAnimator vuelo = ValueAnimator.ofFloat(0f, 1f);
        vuelo.setDuration(esCurva ? 620 : 460);
        vuelo.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            imgBola.setTranslationX(x0 + (x1 - x0) * t + arco * (float) Math.sin(Math.PI * t));
            imgBola.setTranslationY(y0 + (y1 - y0) * t);
            imgBola.setRotation((esCurva ? 1440f : 720f) * t);
            imgBola.setScaleX(1f - 0.25f * t);   // se ve más chica al alejarse
            imgBola.setScaleY(1f - 0.25f * t);
        });
        vuelo.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animacion) {
                if (isFinishing() || isDestroyed() || salvaje == null) return;
                imgBola.setRotation(720f);
                // Se mide el aro justo al llegar: cuanto más chico, mejor tiro
                boolean dentroDelAro = conBonos && acierta && distancia <= aro.radio();
                float tamanoAro = aro.fraccion();
                aro.detener();
                if (!acierta) {
                    fallar(llega ? Idioma.t("¡Se fue de lado!", "It went wide!")
                            : Idioma.t("¡Se quedó corta!", "It fell short!"));
                    return;
                }
                int bonoAro = 0;
                String tiro = "";
                if (dentroDelAro) {
                    if (tamanoAro <= ARO_EXCELENTE) {
                        bonoAro = BONO_EXCELENTE;
                        tiro = Idioma.t("✨ ¡Excelente! ", "✨ Excellent! ");
                        Efectos.vibrar(SafariActivity.this, 60);
                    } else if (tamanoAro <= ARO_GENIAL) {
                        bonoAro = BONO_GENIAL;
                        tiro = Idioma.t("🎯 ¡Genial! ", "🎯 Great! ");
                    } else {
                        bonoAro = BONO_BIEN;
                        tiro = Idioma.t("👍 ¡Bien! ", "👍 Nice! ");
                    }
                }
                int probabilidad = Math.min(95, probabilidad(tipo) + (esCurva ? BONO_CURVA : 0) + bonoAro);
                boolean capturado = random.nextInt(100) < probabilidad;
                if (esCurva || dentroDelAro) {
                    tvMensaje.setText(tiro + (esCurva ? Idioma.t("🌀 ¡Bola curva! ", "🌀 Curveball! ") : "")
                            + Idioma.t("\nProbabilidad: " + probabilidad + " %", "\nChance: " + probabilidad + "%"));
                }
                // 2) El Pokémon entra en la bola
                imgPokemon.animate().scaleX(0f).scaleY(0f).alpha(0f).setDuration(250)
                        .withEndAction(() -> sacudir(3, capturado)).start();
            }
        });
        vuelo.start();
    }

    /** La bola no le dio al Pokémon: se pierde y, si quedan, se prepara otra. */
    private void fallar(String motivo) {
        Efectos.fallo(this);
        imgBola.animate().alpha(0f).setDuration(220).withEndAction(() -> {
            if (isFinishing() || isDestroyed() || salvaje == null) return;
            if (!quedanBolas()) {
                tvMensaje.setText(motivo + Idioma.t(" Perdiste la Pokéball.", " You lost the Poké Ball."));
                siguienteEncuentro(1200);   // sin bolas: ahí se termina el safari
                return;
            }
            tvMensaje.setText(motivo + Idioma.t(" Perdiste la Pokéball.\nDesliza otra hacia " + salvaje.nombre + ".",
                    " You lost the Poké Ball.\nSwipe another at " + salvaje.nombre + "."));
            ocupado = false;
            prepararBola();
            pintar();
        }).start();
    }

    /** 3) La bola se sacude; al terminar se sabe si quedó capturado. */
    private void sacudir(int veces, boolean capturado) {
        if (isFinishing() || isDestroyed() || salvaje == null) return;
        if (veces == 0) {
            resolver(capturado);
            return;
        }
        imgBola.animate().rotation(700f).setDuration(140).withEndAction(() ->
                imgBola.animate().rotation(740f).setDuration(140).withEndAction(() ->
                        imgBola.animate().rotation(720f).setDuration(120).setStartDelay(0)
                                .withEndAction(() -> handler.postDelayed(
                                        () -> sacudir(veces - 1, capturado), 220)).start()
                ).start()
        ).start();
    }

    private void resolver(boolean capturado) {
        if (capturado) {
            capturadosHoy++;
            boolean nuevo = !yaCapturado(salvaje.id);
            int rangoAntes = nivelRango();
            if (nuevo) coleccion.add(new PokemonMini(salvaje.id, salvaje.nombre));
            String subioRango = nivelRango() > rangoAntes
                    ? Idioma.t("\n🏅 ¡Subiste a rango " + rango() + "!", "\n🏅 You reached " + rango() + " rank!") : "";
            UsuarioManager.registrarCaptura(salvaje.id, salvaje.nombre);
            Efectos.victoria(this);   // sonido y vibración (según Configuración)
            tvMensaje.setText(Idioma.t(
                    "⭐ ¡" + salvaje.nombre + " fue capturado!"
                            + (nuevo ? "\nSe agregó a tu colección." : "\nYa lo tenías en tu colección."),
                    "⭐ " + salvaje.nombre + " was caught!"
                            + (nuevo ? "\nAdded to your collection." : "\nAlready in your collection.")) + subioRango);
            imgBola.animate().scaleX(1.25f).scaleY(1.25f).setDuration(200).withEndAction(() ->
                    imgBola.animate().scaleX(1f).scaleY(1f).setDuration(200).start()).start();
            siguienteEncuentro(1700);
            return;
        }

        // Se salió de la bola
        imgBola.setVisibility(View.INVISIBLE);
        imgPokemon.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(250).start();

        if (random.nextInt(100) < PROBABILIDAD_ESCAPE) {
            tvMensaje.setText(Idioma.t("¡" + salvaje.nombre + " se liberó y escapó!", salvaje.nombre + " broke free and fled!"));
            imgPokemon.animate().translationX(imgPokemon.getWidth() * 2f).setStartDelay(500).setDuration(350)
                    .withEndAction(() -> imgPokemon.setTranslationX(0f)).start();
            siguienteEncuentro(1500);
        } else if (!quedanBolas()) {
            tvMensaje.setText(Idioma.t("¡" + salvaje.nombre + " se liberó!", salvaje.nombre + " broke free!"));
            siguienteEncuentro(1200);   // sin bolas: ahí se termina el safari
        } else {
            tvMensaje.setText(Idioma.t("¡" + salvaje.nombre + " se liberó! Intenta otra vez.", salvaje.nombre + " broke free! Try again."));
            ocupado = false;
            prepararBola();
            pintar();
        }
    }

    private void terminarSafari() {
        salvaje = null;
        imgPokemon.setVisibility(View.INVISIBLE);
        imgBola.setVisibility(View.INVISIBLE);
        tvNombre.setText(Idioma.t("Safari terminado", "Safari over"));
        tvDatos.setText("");
        tvMensaje.setText(Idioma.t(
                "Te quedaste sin Pokéball.\nCapturaste " + capturadosHoy + " Pokémon en este safari.",
                "You ran out of Poké Balls.\nYou caught " + capturadosHoy + " Pokémon in this safari."));
        btnNuevo.setVisibility(View.VISIBLE);
        pintar();
    }

    // ------------------------------------------------------------------
    // Colección
    // ------------------------------------------------------------------

    private void mostrarColeccion() {
        if (coleccion.isEmpty()) {
            Toast.makeText(this, Idioma.t("Aún no has capturado ningún Pokémon", "You haven't caught any Pokémon yet"),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        float dp = getResources().getDisplayMetrics().density;
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        grid.setPadding(Math.round(12 * dp), Math.round(12 * dp), Math.round(12 * dp), Math.round(12 * dp));

        for (PokemonMini m : coleccion) {
            LinearLayout celda = new LinearLayout(this);
            celda.setOrientation(LinearLayout.VERTICAL);
            celda.setGravity(Gravity.CENTER_HORIZONTAL);

            ImageView sprite = new ImageView(this);
            Glide.with(this).load(m.getSpriteUrl()).into(sprite);
            celda.addView(sprite, new LinearLayout.LayoutParams(Math.round(72 * dp), Math.round(72 * dp)));

            TextView nombre = new TextView(this);
            nombre.setText(m.getNombre());
            nombre.setTextSize(12);
            nombre.setTypeface(null, Typeface.BOLD);
            nombre.setGravity(Gravity.CENTER);
            nombre.setMaxLines(1);
            celda.addView(nombre);

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
            lp.width = 0;
            lp.bottomMargin = Math.round(8 * dp);
            grid.addView(celda, lp);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(grid);
        new AlertDialog.Builder(this)
                .setTitle(Idioma.t("🎒 Mi colección (", "🎒 My collection (") + coleccion.size() + ")")
                .setView(scroll)
                .setPositiveButton(Idioma.t("Cerrar", "Close"), null)
                .show();
    }
}
