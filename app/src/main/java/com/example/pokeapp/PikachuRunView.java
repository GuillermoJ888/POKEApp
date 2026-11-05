package com.example.pokeapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.example.pokeapp.data.Idioma;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * El juego de Pikachu Run: corre solo y cada vez más rápido.
 * Lado derecho de la pantalla = saltar (dejar presionado salta más alto);
 * lado izquierdo = agacharse. Todas las medidas de juego van en dp y se convierten al dibujar.
 */
public class PikachuRunView extends View {

    public interface AlTerminar {
        void partidaTerminada(int puntos);
    }

    // IDs de la PokéAPI de los obstáculos
    static final int[] DE_SUELO = {50, 100, 74, 27};      // Diglett, Voltorb, Geodude, Sandshrew
    static final int[] VOLADORES = {41, 16};              // Zubat, Pidgey

    private static final int LISTO = 0, JUGANDO = 1, PAUSA = 2, FIN = 3;

    private static final float VEL_INICIAL = 300f, VEL_MAXIMA = 780f, DP_POR_PUNTO = 30f;
    private static final float GRAVEDAD = 2600f, IMPULSO_SALTO = 790f, CORTE_SALTO = 300f;
    private static final float ALTO_PIKACHU = 46f, ALTO_SUELO = 46f, ALTO_VOLADOR = 44f;
    private static final float VUELO_MEDIO = 25f, VUELO_ALTO = 88f, ALTURA_RAYO = 105f;
    private static final float MARGEN_CHOQUE = 0.18f;     // el choque se mide un poco por dentro del dibujo
    private static final int PUNTOS_RAYO = 25, PUNTOS_VOLADORES = 150, PUNTOS_PAREJAS = 250;
    private static final int PUNTOS_NOCHE = 500;

    private static class Obstaculo {
        int id;
        float x, altura, alto;   // x = borde izquierdo; altura = pies sobre el suelo
        float ancho;
    }

    private static class Rayo {
        float x;
        Rayo(float x) { this.x = x; }
    }

    private final float dp;
    private final Random random = new Random();
    private final Paint pintura = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sprite = new Paint();
    private final RectF destino = new RectF();

    private final Map<Integer, Bitmap> sprites = new HashMap<>();
    // Pikachu en pixel art: galope (estirado, medio, recogido, medio), salto y agachado
    private final Bitmap[] galope = {
            PikachuPixel.bitmap(PikachuPixel.ESTIRADO), PikachuPixel.bitmap(PikachuPixel.MEDIO),
            PikachuPixel.bitmap(PikachuPixel.RECOGIDO), PikachuPixel.bitmap(PikachuPixel.MEDIO)};
    private final Bitmap enElAire = PikachuPixel.bitmap(PikachuPixel.SALTO);
    private final Bitmap pegadoAlSuelo = PikachuPixel.bitmap(PikachuPixel.AGACHADO);
    private final List<Obstaculo> obstaculos = new ArrayList<>();
    private final List<Rayo> rayos = new ArrayList<>();
    private final float[] nubes = {120f, 340f, 560f, 780f};

    private AlTerminar alTerminar;
    private int estado = LISTO;
    private double puntos;
    private int record;
    private float velocidad = VEL_INICIAL;
    private float alturaPikachu, velocidadY;     // dp sobre el suelo; + = hacia arriba
    private boolean agachado, saltoPresionado;
    private float siguienteObstaculo, recorridoSuelo, tiempo, noche;
    private long ultimoCuadro, momentoFin;

    public PikachuRunView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        dp = getResources().getDisplayMetrics().density;
        sprite.setFilterBitmap(false);   // que los sprites se vean pixelados y nítidos
        pintura.setTypeface(Typeface.DEFAULT_BOLD);
    }

    void setAlTerminar(AlTerminar escucha) {
        alTerminar = escucha;
    }

    void setRecord(int valor) {
        record = valor;
        invalidate();
    }

    void agregarSprite(int id, Bitmap imagen) {
        sprites.put(id, imagen);
        invalidate();
    }

    /** La app pasó a segundo plano: el juego espera a que se toque otra vez. */
    void pausar() {
        if (estado == JUGANDO) {
            estado = PAUSA;
            agachado = false;
            saltoPresionado = false;
            invalidate();
        }
    }

    private void empezar() {
        obstaculos.clear();
        rayos.clear();
        puntos = 0;
        velocidad = VEL_INICIAL;
        alturaPikachu = 0;
        velocidadY = 0;
        agachado = false;
        saltoPresionado = false;
        siguienteObstaculo = 200f;
        estado = JUGANDO;
        ultimoCuadro = 0;
        invalidate();
    }

    // ------------------------------------------------------------------
    // Controles
    // ------------------------------------------------------------------

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int accion = e.getActionMasked();
        boolean ladoDerecho = e.getX(e.getActionIndex()) >= getWidth() / 2f;

        if (accion == MotionEvent.ACTION_DOWN || accion == MotionEvent.ACTION_POINTER_DOWN) {
            if (estado == LISTO) {
                empezar();
            } else if (estado == PAUSA) {
                estado = JUGANDO;
                ultimoCuadro = 0;
                invalidate();
            } else if (estado == FIN) {
                if (System.currentTimeMillis() - momentoFin > 500) empezar();
            } else if (ladoDerecho) {
                saltoPresionado = true;
                if (alturaPikachu <= 0f) velocidadY = IMPULSO_SALTO;
            } else {
                agachado = true;
            }
        } else if (accion == MotionEvent.ACTION_UP || accion == MotionEvent.ACTION_POINTER_UP
                || accion == MotionEvent.ACTION_CANCEL) {
            if (ladoDerecho || accion != MotionEvent.ACTION_POINTER_UP) {
                saltoPresionado = false;
                if (velocidadY > CORTE_SALTO) velocidadY = CORTE_SALTO;   // soltar pronto = salto corto
            }
            if (!ladoDerecho || accion != MotionEvent.ACTION_POINTER_UP) agachado = false;
            performClick();
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    // ------------------------------------------------------------------
    // Reglas
    // ------------------------------------------------------------------

    private void avanzar(float dt) {
        tiempo += dt;
        velocidad = Math.min(VEL_MAXIMA, VEL_INICIAL + (float) puntos * 0.45f);
        float paso = velocidad * dt;
        puntos += paso / DP_POR_PUNTO;
        recorridoSuelo += paso;

        // Salto; agacharse en el aire hace caer más rápido
        if (alturaPikachu > 0f || velocidadY > 0f) {
            velocidadY -= GRAVEDAD * (agachado ? 2.5f : 1f) * dt;
            alturaPikachu += velocidadY * dt;
            if (alturaPikachu <= 0f) {
                alturaPikachu = 0f;
                velocidadY = 0f;
            }
        }

        float anchoDp = getWidth() / dp;
        for (int i = 0; i < nubes.length; i++) {
            nubes[i] -= paso * 0.15f;
            if (nubes[i] < -80f) nubes[i] = anchoDp + random.nextInt(120);
        }

        // Cada 500 puntos cambia entre día y noche, poco a poco
        float metaNoche = ((int) puntos / PUNTOS_NOCHE) % 2;
        noche += (metaNoche - noche) * Math.min(1f, dt * 1.2f);

        siguienteObstaculo -= paso;
        if (siguienteObstaculo <= 0f) crearObstaculo(anchoDp);

        RectF cuerpo = cajaPikachu();
        for (Iterator<Obstaculo> it = obstaculos.iterator(); it.hasNext(); ) {
            Obstaculo o = it.next();
            o.x -= paso;
            if (o.x + o.ancho < 0f) {
                it.remove();
            } else if (RectF.intersects(cuerpo, cajaDe(o))) {
                terminar();
                return;
            }
        }
        for (Iterator<Rayo> it = rayos.iterator(); it.hasNext(); ) {
            Rayo r = it.next();
            r.x -= paso;
            if (r.x < -30f) {
                it.remove();
            } else if (RectF.intersects(cuerpo, new RectF(r.x, ALTURA_RAYO, r.x + 26f, ALTURA_RAYO + 26f))) {
                it.remove();
                puntos += PUNTOS_RAYO;
                Efectos.vibrar(getContext(), 20);
            }
        }
    }

    private void crearObstaculo(float anchoDp) {
        boolean volador = puntos >= PUNTOS_VOLADORES && random.nextInt(100) < 30;
        if (volador) {
            agregar(VOLADORES[random.nextInt(VOLADORES.length)], anchoDp + 10f,
                    random.nextBoolean() ? VUELO_MEDIO : VUELO_ALTO, ALTO_VOLADOR);
        } else {
            agregar(DE_SUELO[random.nextInt(DE_SUELO.length)], anchoDp + 10f, 0f, ALTO_SUELO);
            if (puntos >= PUNTOS_PAREJAS && random.nextInt(100) < 30) {
                agregar(DE_SUELO[random.nextInt(DE_SUELO.length)], anchoDp + 10f + ALTO_SUELO, 0f, ALTO_SUELO);
            }
        }
        if (random.nextInt(100) < 20) rayos.add(new Rayo(anchoDp + 10f + velocidad * 0.42f));

        // Espacio suficiente para caer de un salto y reaccionar al siguiente
        siguienteObstaculo = velocidad * (0.85f + random.nextFloat() * 0.9f);
    }

    private void agregar(int id, float x, float altura, float alto) {
        Obstaculo o = new Obstaculo();
        o.id = id;
        o.x = x;
        o.altura = altura;
        o.alto = alto;
        o.ancho = alto * proporcion(sprites.get(id));
        obstaculos.add(o);
    }

    private static float proporcion(@Nullable Bitmap b) {
        return b == null ? 1f : (float) b.getWidth() / b.getHeight();
    }

    private void terminar() {
        estado = FIN;
        momentoFin = System.currentTimeMillis();
        agachado = false;
        int total = (int) puntos;
        if (alTerminar != null) alTerminar.partidaTerminada(total);
        if (total > record) record = total;
    }

    // Las cajas de choque van en dp: x hacia la derecha, y = altura sobre el suelo (top > bottom numérico no aplica)
    private float xPikachu() {
        return getWidth() / dp * 0.12f;
    }

    private boolean estaAgachado() {
        return agachado && alturaPikachu <= 0f;
    }

    /** Cuadro del Pikachu que toca: en el aire, agachado o uno de los 4 pasos del galope (más rápido = más pasos). */
    private Bitmap cuadroPikachu() {
        if (alturaPikachu > 0f) return enElAire;
        if (estaAgachado()) return pegadoAlSuelo;
        if (estado != JUGANDO) return galope[1];
        return galope[(int) (tiempo * 12f * velocidad / VEL_INICIAL) % galope.length];
    }

    private float altoPikachuActual() {
        return estaAgachado()
                ? ALTO_PIKACHU * pegadoAlSuelo.getHeight() / galope[0].getHeight() : ALTO_PIKACHU;
    }

    private float anchoPikachuActual() {
        return altoPikachuActual() * proporcion(cuadroPikachu());
    }

    private RectF cajaPikachu() {
        return caja(xPikachu(), alturaPikachu, anchoPikachuActual(), altoPikachuActual());
    }

    private RectF cajaDe(Obstaculo o) {
        return caja(o.x, o.altura, o.ancho, o.alto);
    }

    /** RectF con top = altura menor y bottom = altura mayor (así funciona intersects). */
    private static RectF caja(float x, float altura, float ancho, float alto) {
        float mx = ancho * MARGEN_CHOQUE, my = alto * MARGEN_CHOQUE;
        return new RectF(x + mx, altura + my, x + ancho - mx, altura + alto - my);
    }

    // ------------------------------------------------------------------
    // Dibujo
    // ------------------------------------------------------------------

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long ahora = System.nanoTime();
        if (estado == JUGANDO) {
            float dt = ultimoCuadro == 0 ? 0f : Math.min(1f / 30f, (ahora - ultimoCuadro) / 1e9f);
            ultimoCuadro = ahora;
            avanzar(dt);
        }

        int ancho = getWidth(), alto = getHeight();
        float suelo = alto * 0.78f;

        canvas.drawColor(mezclar(0xFF87CEEB, 0xFF14213D, noche));

        pintura.setColor(noche > 0.5f ? 0x33FFFFFF : 0xCCFFFFFF);
        for (int i = 0; i < nubes.length; i++) {
            float x = nubes[i] * dp, y = alto * (0.14f + 0.07f * (i % 3));
            canvas.drawOval(x, y, x + 74f * dp, y + 22f * dp, pintura);
            canvas.drawOval(x + 18f * dp, y - 11f * dp, x + 56f * dp, y + 14f * dp, pintura);
        }

        pintura.setColor(mezclar(0xFF7CB342, 0xFF2E4A2E, noche));
        canvas.drawRect(0, suelo, ancho, alto, pintura);
        pintura.setColor(mezclar(0xFF558B2F, 0xFF1E3320, noche));
        canvas.drawRect(0, suelo, ancho, suelo + 4f * dp, pintura);
        float separacion = 46f * dp;
        float inicio = -(recorridoSuelo * dp % separacion);
        for (float x = inicio; x < ancho; x += separacion) {
            canvas.drawRect(x, suelo + 16f * dp, x + 18f * dp, suelo + 20f * dp, pintura);
        }

        pintura.setTextSize(22f * dp);
        pintura.setTextAlign(Paint.Align.LEFT);
        pintura.setColor(Color.WHITE);
        for (Rayo r : rayos) {
            canvas.drawText("⚡", r.x * dp, suelo - ALTURA_RAYO * dp, pintura);
        }

        for (Obstaculo o : obstaculos) {
            float baile = o.altura > 0f ? (float) Math.sin(tiempo * 12f) * 4f : 0f;
            dibujar(canvas, sprites.get(o.id), o.x, o.altura + baile, o.ancho, o.alto, suelo, false,
                    o.altura > 0f ? 0xFF7E57C2 : 0xFF8D6E63);
        }

        // Pikachu en cuatro patas (pixel art propio)
        dibujar(canvas, cuadroPikachu(), xPikachu(), alturaPikachu,
                anchoPikachuActual(), altoPikachuActual(), suelo, false, 0xFFFACC15);

        dibujarTextos(canvas, ancho, alto);

        if (estado == JUGANDO) postInvalidateOnAnimation();
    }

    private void dibujar(Canvas canvas, @Nullable Bitmap imagen, float x, float altura, float anchoDp, float altoDp,
                         float suelo, boolean espejo, int colorSinImagen) {
        float izquierda = x * dp, abajo = suelo - altura * dp;
        destino.set(izquierda, abajo - altoDp * dp, izquierda + anchoDp * dp, abajo);
        if (imagen == null) {
            // Sin internet: un círculo de color en lugar del sprite
            pintura.setColor(colorSinImagen);
            canvas.drawOval(destino, pintura);
            return;
        }
        if (espejo) {
            canvas.save();
            canvas.scale(-1f, 1f, destino.centerX(), destino.centerY());
            canvas.drawBitmap(imagen, null, destino, sprite);
            canvas.restore();
        } else {
            canvas.drawBitmap(imagen, null, destino, sprite);
        }
    }

    private void dibujarTextos(Canvas canvas, int ancho, int alto) {
        pintura.setColor(noche > 0.5f ? Color.WHITE : 0xFF142036);
        pintura.setTextAlign(Paint.Align.RIGHT);
        pintura.setTextSize(20f * dp);
        canvas.drawText(String.format(Locale.ROOT, "🏆 %05d   %05d", record, (int) puntos),
                ancho - 18f * dp, 34f * dp, pintura);

        if (estado == JUGANDO) return;
        String titulo, detalle;
        if (estado == LISTO) {
            titulo = Idioma.t("Toca para empezar", "Tap to start");
            detalle = Idioma.t("Derecha: saltar · Izquierda: agacharse", "Right: jump · Left: duck");
        } else if (estado == PAUSA) {
            titulo = Idioma.t("Pausa", "Paused");
            detalle = Idioma.t("Toca para seguir", "Tap to continue");
        } else {
            titulo = Idioma.t("¡Pikachu chocó!", "Pikachu crashed!");
            detalle = Idioma.t("Toca para intentar otra vez", "Tap to try again");
        }
        pintura.setTextAlign(Paint.Align.CENTER);
        pintura.setTextSize(28f * dp);
        canvas.drawText(titulo, ancho / 2f, alto * 0.36f, pintura);
        pintura.setTextSize(15f * dp);
        canvas.drawText(detalle, ancho / 2f, alto * 0.36f + 28f * dp, pintura);
    }

    private static int mezclar(int a, int b, float t) {
        return Color.rgb(
                Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }
}
