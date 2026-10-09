package com.example.pokeapp;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatButton;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Botón con tema de Pokémon: el cuerpo del Pokémon cubre todo el botón
 * (color, mejillas, panza, manchas…) y sus orejas, cola, alas o bulbo
 * sobresalen por fuera del borde. Todo se dibuja con Canvas, así que se
 * adapta a cualquier tamaño de botón.
 *
 * Uso en XML:  <com.example.pokeapp.PokeBoton app:tema="pikachu" … />
 * Sigue siendo un Button, así que el código que lo usa no cambia.
 */
public class PokeBoton extends AppCompatButton {

    /** Colores de cada tema: cuerpo, contorno, texto y si el texto lleva sombra. */
    private enum Tema {
        PIKACHU(0xFFFFD43B, 0xFF8B5A00, 0xFF3A2600, false),
        CHARMANDER(0xFFF7853E, 0xFFA0441B, 0xFFFFFFFF, true),
        SQUIRTLE(0xFF7EC8E3, 0xFF2B6F8C, 0xFF0D3B66, false),
        BULBASAUR(0xFF6CC4A1, 0xFF2E7D6B, 0xFFFFFFFF, true),
        DITTO(0xFFC7A2DE, 0xFF7B4FA0, 0xFF3E1F57, false),
        EEVEE(0xFFB07A47, 0xFF5A3A22, 0xFFFFFFFF, true),
        CHARIZARD(0xFFF08030, 0xFF8C3A12, 0xFFFFFFFF, true),
        JIGGLYPUFF(0xFFF8BBD0, 0xFFC2185B, 0xFF880E4F, false),
        PSYDUCK(0xFFF6E27F, 0xFFB08A1E, 0xFF5C4500, false),
        SNORLAX(0xFF2F6E7E, 0xFF1B4550, 0xFF1F3B44, false),
        GENGAR(0xFF6B4A9E, 0xFF3B2560, 0xFFFFFFFF, true);

        final int cuerpo, contorno, texto;
        final boolean sombraTexto;

        Tema(int cuerpo, int contorno, int texto, boolean sombraTexto) {
            this.cuerpo = cuerpo;
            this.contorno = contorno;
            this.texto = texto;
            this.sombraTexto = sombraTexto;
        }
    }

    private Tema tema = Tema.PIKACHU;
    private final float dp;
    private final Paint relleno = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trazo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    public PokeBoton(Context context) {
        this(context, null);
    }

    public PokeBoton(Context context, AttributeSet attrs) {
        this(context, attrs, androidx.appcompat.R.attr.buttonStyle);
    }

    public PokeBoton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        dp = getResources().getDisplayMetrics().density;

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.PokeBoton);
            tema = Tema.values()[a.getInt(R.styleable.PokeBoton_tema, 0)];
            a.recycle();
        }

        trazo.setStyle(Paint.Style.STROKE);
        trazo.setStrokeJoin(Paint.Join.ROUND);
        trazo.setStrokeCap(Paint.Cap.ROUND);

        setBackground(new Cuerpo());
        setStateListAnimator(null);   // sin la sombra/elevación de Material
        setAllCaps(false);
        setTypeface(getTypeface(), Typeface.BOLD);
        setGravity(Gravity.CENTER);
        setTextColor(tema.texto);
        if (tema.sombraTexto) setShadowLayer(3 * dp, 0, 1.5f * dp, 0x99000000);
        int horizontal = Math.round(26 * dp);
        setPadding(horizontal, getPaddingTop(), horizontal, getPaddingBottom());
    }

    // ------------------------------------------------------------------
    // Que lo que sobresale no se recorte
    // ------------------------------------------------------------------

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        // Los contenedores cercanos dejan dibujar fuera de su borde
        // (sin pasar de una lista o scroll, para no salirse de la pantalla)
        ViewParent padre = getParent();
        for (int i = 0; i < 4 && padre instanceof ViewGroup; i++) {
            if (padre instanceof ScrollView || padre instanceof HorizontalScrollView
                    || padre instanceof RecyclerView) break;
            ((ViewGroup) padre).setClipChildren(false);
            ((ViewGroup) padre).setClipToPadding(false);
            padre = padre.getParent();
        }
    }

    // Al presionar se "aplasta" un poquito
    @Override
    public void setPressed(boolean presionado) {
        super.setPressed(presionado);
        float escala = presionado ? 0.95f : 1f;
        animate().scaleX(escala).scaleY(escala).setDuration(90).start();
    }

    // ------------------------------------------------------------------
    // Partes que sobresalen (detrás del cuerpo)
    // ------------------------------------------------------------------

    @Override
    public void draw(@NonNull Canvas canvas) {
        float w = getWidth(), h = getHeight();
        trazo.setColor(tema.contorno);
        trazo.setStrokeWidth(2.5f * dp);

        switch (tema) {
            case PIKACHU:
                // Orejas con la punta redondeada (no tan puntiagudas)
                oreja(canvas, h * 0.55f, h * 0.38f, h * 0.65f, -h * 0.20f, tema.cuerpo, 0xFF222222, 0.42f, 0.32f);
                oreja(canvas, h * 1.08f, h * 0.38f, h * 0.65f, h * 0.12f, tema.cuerpo, 0xFF222222, 0.42f, 0.32f);
                colaRayo(canvas, w, h);
                break;
            case CHARMANDER:
                colaConFlama(canvas, w, h, tema.cuerpo);
                break;
            case SQUIRTLE:
                colaEnroscada(canvas, h);
                break;
            case BULBASAUR:
                oreja(canvas, h * 0.50f, h * 0.30f, h * 0.30f, -h * 0.10f, tema.cuerpo, 0, 0);
                oreja(canvas, w - h * 0.50f, h * 0.30f, h * 0.30f, h * 0.10f, tema.cuerpo, 0, 0);
                bulbo(canvas, w, h);
                break;
            case DITTO:
                bultosDitto(canvas, w, h);
                break;
            case EEVEE:
                oreja(canvas, h * 0.60f, h * 0.42f, h * 0.62f, -h * 0.28f, tema.cuerpo, 0xFF5A3A22, 0.35f);
                oreja(canvas, h * 1.30f, h * 0.42f, h * 0.62f, h * 0.18f, tema.cuerpo, 0xFF5A3A22, 0.35f);
                colaEsponjada(canvas, w, h);
                break;
            case CHARIZARD:
                ala(canvas, w, h, false);
                ala(canvas, w, h, true);
                break;
            case JIGGLYPUFF:
                oreja(canvas, h * 0.50f, h * 0.36f, h * 0.45f, -h * 0.18f, tema.cuerpo, 0xFF4A2B3A, 0.45f);
                oreja(canvas, w - h * 0.50f, h * 0.36f, h * 0.45f, h * 0.18f, tema.cuerpo, 0xFF4A2B3A, 0.45f);
                rizo(canvas, w, h);
                break;
            case PSYDUCK:
                pelitos(canvas, w, h);
                break;
            case SNORLAX:
                oreja(canvas, h * 0.55f, h * 0.36f, h * 0.30f, -h * 0.06f, tema.cuerpo, 0, 0);
                oreja(canvas, w - h * 0.55f, h * 0.36f, h * 0.30f, h * 0.06f, tema.cuerpo, 0, 0);
                break;
            case GENGAR:
                oreja(canvas, h * 0.50f, h * 0.40f, h * 0.50f, -h * 0.25f, tema.cuerpo, 0, 0);
                oreja(canvas, w - h * 0.50f, h * 0.40f, h * 0.50f, h * 0.25f, tema.cuerpo, 0, 0);
                for (float x : new float[]{0.40f, 0.50f, 0.60f}) {
                    oreja(canvas, w * x, h * 0.24f, h * 0.26f, 0, tema.cuerpo, 0, 0);
                }
                break;
        }

        super.draw(canvas);   // cuerpo (fondo) + texto encima
    }

    /** Oreja triangular que nace del borde de arriba; puede llevar punta de otro color. */
    private void oreja(Canvas c, float cx, float base, float alto, float inclinacion,
                       int color, int colorPunta, float fraccionPunta) {
        oreja(c, cx, base, alto, inclinacion, color, colorPunta, fraccionPunta, 0f);
    }

    /**
     * Igual, pero con la punta redondeada: redondez es el radio de la punta
     * como fracción de la base (0 = puntiaguda).
     */
    private void oreja(Canvas c, float cx, float base, float alto, float inclinacion,
                       int color, int colorPunta, float fraccionPunta, float redondez) {
        float metida = getHeight() * 0.18f;   // parte que queda escondida bajo el cuerpo
        float px = cx + inclinacion, py = -alto, r = base * redondez;

        path.reset();
        path.moveTo(cx - base / 2f, metida);
        if (r > 0) {
            path.quadTo(cx - base * 0.35f + inclinacion * 0.5f, -alto * 0.45f, px - r, py + r);
            path.quadTo(px, py - r * 0.6f, px + r, py + r);   // punta redonda
        } else {
            path.quadTo(cx - base * 0.35f + inclinacion * 0.5f, -alto * 0.45f, px, py);
        }
        path.quadTo(cx + base * 0.35f + inclinacion * 0.5f, -alto * 0.45f, cx + base / 2f, metida);
        path.close();

        relleno.setColor(color);
        c.drawPath(path, relleno);

        if (colorPunta != 0) {
            c.save();
            c.clipRect(-10000f, -10000f, 10000f, -alto * (1f - fraccionPunta));
            relleno.setColor(colorPunta);
            c.drawPath(path, relleno);
            c.restore();
        }
        c.drawPath(path, trazo);
    }

    /** Cola de rayo de Pikachu, a la derecha. */
    private void colaRayo(Canvas c, float w, float h) {
        path.reset();
        path.moveTo(w - h * 0.25f, h * 0.70f);
        path.lineTo(w + h * 0.12f, h * 0.42f);
        path.lineTo(w + h * 0.02f, h * 0.34f);
        path.lineTo(w + h * 0.30f, -h * 0.02f);
        path.lineTo(w + h * 0.36f, h * 0.08f);
        path.lineTo(w + h * 0.20f, h * 0.36f);
        path.lineTo(w + h * 0.30f, h * 0.43f);
        path.lineTo(w - h * 0.05f, h * 0.85f);
        path.close();
        relleno.setColor(tema.cuerpo);
        c.drawPath(path, relleno);

        // La base de la cola (la parte que sale del cuerpo) es café, como la de Pikachu
        c.save();
        c.clipRect(w - h, h * 0.40f, w + h, h * 2f);
        relleno.setColor(0xFF8B5A00);
        c.drawPath(path, relleno);
        c.restore();

        c.drawPath(path, trazo);
    }

    /** Cola de Charmander con su flama en la punta. */
    private void colaConFlama(Canvas c, float w, float h, int colorCola) {
        Paint cola = new Paint(Paint.ANTI_ALIAS_FLAG);
        cola.setStyle(Paint.Style.STROKE);
        cola.setStrokeCap(Paint.Cap.ROUND);

        path.reset();
        path.moveTo(w - h * 0.30f, h * 0.70f);
        path.quadTo(w + h * 0.22f, h * 0.70f, w + h * 0.20f, h * 0.30f);

        cola.setStrokeWidth(h * 0.20f + 5 * dp);
        cola.setColor(tema.contorno);
        c.drawPath(path, cola);
        cola.setStrokeWidth(h * 0.20f);
        cola.setColor(colorCola);
        c.drawPath(path, cola);

        flama(c, w + h * 0.20f, h * 0.08f, h * 0.24f);
    }

    /** Flama en forma de gota: roja-naranja por fuera, amarilla por dentro. */
    private void flama(Canvas c, float cx, float cy, float s) {
        relleno.setColor(0xFFFF5722);
        c.drawPath(gota(cx, cy, s), relleno);
        relleno.setColor(0xFFFFC107);
        c.drawPath(gota(cx, cy + s * 0.30f, s * 0.55f), relleno);
    }

    private Path gota(float cx, float cy, float s) {
        Path g = new Path();
        g.moveTo(cx, cy - 1.4f * s);
        g.cubicTo(cx + 0.4f * s, cy - 0.8f * s, cx + s, cy - 0.2f * s, cx + 0.7f * s, cy + 0.5f * s);
        g.cubicTo(cx + 0.4f * s, cy + 1.0f * s, cx - 0.4f * s, cy + 1.0f * s, cx - 0.7f * s, cy + 0.5f * s);
        g.cubicTo(cx - s, cy - 0.2f * s, cx - 0.3f * s, cy - 0.6f * s, cx, cy - 1.4f * s);
        g.close();
        return g;
    }

    /** Cola enroscada de Squirtle, a la izquierda. */
    private void colaEnroscada(Canvas c, float h) {
        Paint cola = new Paint(Paint.ANTI_ALIAS_FLAG);
        cola.setStyle(Paint.Style.STROKE);
        cola.setStrokeCap(Paint.Cap.ROUND);

        path.reset();
        path.moveTo(h * 0.35f, h * 0.72f);
        path.quadTo(-h * 0.05f, h * 0.95f, -h * 0.22f, h * 0.55f);
        path.addArc(new RectF(-h * 0.36f, h * 0.10f, -h * 0.02f, h * 0.48f), 180, 270);

        cola.setStrokeWidth(h * 0.15f + 5 * dp);
        cola.setColor(tema.contorno);
        c.drawPath(path, cola);
        cola.setStrokeWidth(h * 0.15f);
        cola.setColor(tema.cuerpo);
        c.drawPath(path, cola);
    }

    /** Bulbo de Bulbasaur arriba al centro. */
    private void bulbo(Canvas c, float w, float h) {
        RectF ovalo = new RectF(w / 2f - h * 0.55f, -h * 0.48f, w / 2f + h * 0.55f, h * 0.35f);
        relleno.setColor(0xFF4CAF50);
        c.drawOval(ovalo, relleno);

        Paint hojas = new Paint(Paint.ANTI_ALIAS_FLAG);
        hojas.setStyle(Paint.Style.STROKE);
        hojas.setStrokeWidth(2 * dp);
        hojas.setStrokeCap(Paint.Cap.ROUND);
        hojas.setColor(0xFF2E7D32);
        for (float dx : new float[]{-0.28f, 0f, 0.28f}) {
            path.reset();
            path.moveTo(w / 2f + h * dx * 0.4f, h * 0.10f);
            path.quadTo(w / 2f + h * dx, -h * 0.15f, w / 2f + h * dx * 0.7f, -h * 0.40f);
            c.drawPath(path, hojas);
        }
        trazo.setColor(0xFF2E7D32);
        c.drawOval(ovalo, trazo);
        trazo.setColor(tema.contorno);
    }

    /** Bultos "derretidos" de Ditto arriba y gotas abajo. */
    private void bultosDitto(Canvas c, float w, float h) {
        relleno.setColor(tema.cuerpo);
        for (float x : new float[]{0.22f, 0.48f, 0.76f}) {
            float r = h * (x == 0.48f ? 0.24f : 0.18f);
            c.drawCircle(w * x, h * 0.10f, r, trazo);
            c.drawCircle(w * x, h * 0.10f, r, relleno);
        }
        for (float x : new float[]{0.32f, 0.66f}) {
            RectF gota = new RectF(w * x - h * 0.10f, h * 0.70f, w * x + h * 0.10f, h * 1.22f);
            c.drawRoundRect(gota, h * 0.10f, h * 0.10f, trazo);
            c.drawRoundRect(gota, h * 0.10f, h * 0.10f, relleno);
        }
    }

    /** Cola esponjada de Eevee, a la derecha, con punta crema. */
    private void colaEsponjada(Canvas c, float w, float h) {
        float cx = w + h * 0.02f, cy = h * 0.22f, r = h * 0.34f;
        c.drawCircle(cx, cy, r, trazo);
        relleno.setColor(tema.cuerpo);
        c.drawCircle(cx, cy, r, relleno);
        relleno.setColor(0xFFF3E1B5);
        c.drawCircle(cx + r * 0.25f, cy - r * 0.35f, r * 0.55f, relleno);
    }

    /** Ala de Charizard (membrana azul verdosa, borde naranja), a un lado. */
    private void ala(Canvas c, float w, float h, boolean izquierda) {
        c.save();
        if (izquierda) c.scale(-1f, 1f, w / 2f, 0);   // espejo para el lado izquierdo

        path.reset();
        path.moveTo(w - h * 0.50f, h * 0.40f);
        path.lineTo(w + h * 0.05f, -h * 0.58f);
        path.lineTo(w + h * 0.34f, -h * 0.22f);
        path.lineTo(w + h * 0.16f, -h * 0.06f);
        path.lineTo(w + h * 0.30f, h * 0.20f);
        path.lineTo(w - h * 0.20f, h * 0.50f);
        path.close();

        relleno.setColor(0xFF2E7D8C);
        c.drawPath(path, relleno);
        Paint borde = new Paint(trazo);
        borde.setColor(tema.cuerpo);
        borde.setStrokeWidth(3.5f * dp);
        c.drawPath(path, borde);
        c.drawPath(path, trazo);
        c.restore();

        if (!izquierda) flama(c, w - h * 0.12f, h * 0.92f, h * 0.20f);
    }

    /** Rizo de Jigglypuff arriba al centro. */
    private void rizo(Canvas c, float w, float h) {
        Paint rizo = new Paint(Paint.ANTI_ALIAS_FLAG);
        rizo.setStyle(Paint.Style.STROKE);
        rizo.setStrokeCap(Paint.Cap.ROUND);

        path.reset();
        path.moveTo(w / 2f + h * 0.05f, h * 0.15f);
        path.addArc(new RectF(w / 2f - h * 0.20f, -h * 0.38f, w / 2f + h * 0.12f, -h * 0.06f), 90, 300);

        rizo.setStrokeWidth(h * 0.12f + 5 * dp);
        rizo.setColor(tema.contorno);
        c.drawPath(path, rizo);
        rizo.setStrokeWidth(h * 0.12f);
        rizo.setColor(tema.cuerpo);
        c.drawPath(path, rizo);
    }

    /** Los tres pelitos de Psyduck. */
    private void pelitos(Canvas c, float w, float h) {
        Paint pelo = new Paint(Paint.ANTI_ALIAS_FLAG);
        pelo.setStyle(Paint.Style.STROKE);
        pelo.setStrokeCap(Paint.Cap.ROUND);
        pelo.setStrokeWidth(3 * dp);
        pelo.setColor(0xFF222222);
        for (float dx : new float[]{-0.16f, 0f, 0.16f}) {
            path.reset();
            path.moveTo(w / 2f + h * dx, h * 0.08f);
            path.quadTo(w / 2f + h * dx * 0.4f, -h * 0.18f, w / 2f + h * dx * 1.7f, -h * 0.36f);
            c.drawPath(path, pelo);
        }
    }

    // ------------------------------------------------------------------
    // Cuerpo del Pokémon a todo el botón (fondo)
    // ------------------------------------------------------------------

    private class Cuerpo extends Drawable {

        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borde = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path forma = new Path();

        Cuerpo() {
            borde.setStyle(Paint.Style.STROKE);
            borde.setStrokeWidth(3 * dp);
        }

        @Override
        public void draw(@NonNull Canvas c) {
            float w = getBounds().width(), h = getBounds().height();
            float g = 1.5f * dp;   // medio grosor del borde
            RectF r = new RectF(g, g, w - g, h - g);
            float radio = Math.min(h / 2f, 22 * dp);

            forma.reset();
            forma.addRoundRect(r, radio, radio, Path.Direction.CW);

            p.setColor(tema.cuerpo);
            c.drawPath(forma, p);

            c.save();
            c.clipPath(forma);
            detalles(c, w, h);
            c.restore();

            borde.setColor(tema.contorno);
            c.drawPath(forma, borde);
        }

        /** Mejillas, panzas, manchas y caras de cada Pokémon (dentro del botón). */
        private void detalles(Canvas c, float w, float h) {
            switch (tema) {
                case PIKACHU:
                    p.setColor(0xFFE53935);   // mejillas
                    c.drawCircle(h * 0.52f, h * 0.60f, h * 0.16f, p);
                    c.drawCircle(w - h * 0.52f, h * 0.60f, h * 0.16f, p);
                    p.setColor(0xFF8B5A00);   // franjas de la espalda
                    c.drawRoundRect(new RectF(w * 0.62f, -h * 0.1f, w * 0.62f + h * 0.12f, h * 0.22f), h * 0.06f, h * 0.06f, p);
                    c.drawRoundRect(new RectF(w * 0.62f + h * 0.22f, -h * 0.1f, w * 0.62f + h * 0.34f, h * 0.18f), h * 0.06f, h * 0.06f, p);
                    break;
                case CHARMANDER:
                    p.setColor(0xFFFFB067);   // panza
                    c.drawOval(new RectF(w * 0.22f, h * 0.50f, w * 0.78f, h * 1.45f), p);
                    break;
                case SQUIRTLE:
                    p.setColor(0xFFC08A4B);   // caparazón
                    c.drawOval(new RectF(w - h * 0.95f, h * 0.05f, w + h * 0.35f, h * 1.10f), p);
                    p.setColor(0xFFE8C07D);
                    c.drawOval(new RectF(w - h * 0.75f, h * 0.22f, w + h * 0.20f, h * 0.92f), p);
                    p.setColor(0xFFF5E3A0);   // panza
                    c.drawRect(0, h * 0.82f, w, h, p);
                    break;
                case BULBASAUR:
                    p.setColor(0xFF3E8E7E);   // manchas
                    c.drawCircle(w * 0.12f, h * 0.32f, h * 0.12f, p);
                    c.drawCircle(w * 0.22f, h * 0.78f, h * 0.08f, p);
                    c.drawCircle(w * 0.86f, h * 0.30f, h * 0.10f, p);
                    c.drawCircle(w * 0.92f, h * 0.74f, h * 0.08f, p);
                    break;
                case DITTO:
                    cara(c, h * 0.62f, h, 0xFF222222);
                    break;
                case EEVEE:
                    p.setColor(0xFFF3E1B5);   // collar esponjado
                    for (float y : new float[]{0.15f, 0.50f, 0.85f}) {
                        c.drawCircle(h * 0.20f, h * y, h * 0.26f, p);
                    }
                    break;
                case CHARIZARD:
                    p.setColor(0xFFFFC98B);   // panza
                    c.drawOval(new RectF(w * 0.25f, h * 0.55f, w * 0.75f, h * 1.45f), p);
                    break;
                case JIGGLYPUFF:
                    p.setColor(0xFFF48FB1);   // chapitas
                    c.drawOval(new RectF(h * 0.25f, h * 0.55f, h * 0.70f, h * 0.78f), p);
                    c.drawOval(new RectF(w - h * 0.70f, h * 0.55f, w - h * 0.25f, h * 0.78f), p);
                    break;
                case PSYDUCK:
                    p.setColor(0xFFF5D9A8);   // pico
                    c.drawOval(new RectF(-h * 0.20f, h * 0.30f, h * 0.55f, h * 0.80f), p);
                    break;
                case SNORLAX:
                    p.setColor(0xFFF2E3C6);   // panza
                    c.drawOval(new RectF(w * 0.10f, h * 0.15f, w * 0.90f, h * 1.35f), p);
                    break;
                case GENGAR:
                    p.setColor(0xFFE53935);   // ojos rasgados
                    path.reset();
                    path.moveTo(h * 0.35f, h * 0.38f);
                    path.lineTo(h * 0.75f, h * 0.48f);
                    path.lineTo(h * 0.40f, h * 0.58f);
                    path.close();
                    c.drawPath(path, p);
                    path.reset();
                    path.moveTo(w - h * 0.35f, h * 0.38f);
                    path.lineTo(w - h * 0.75f, h * 0.48f);
                    path.lineTo(w - h * 0.40f, h * 0.58f);
                    path.close();
                    c.drawPath(path, p);
                    break;
            }
        }

        /** Ojitos de punto y sonrisa (Ditto). */
        private void cara(Canvas c, float cx, float h, int color) {
            p.setColor(color);
            c.drawCircle(cx - h * 0.14f, h * 0.42f, h * 0.05f, p);
            c.drawCircle(cx + h * 0.14f, h * 0.42f, h * 0.05f, p);
            Paint boca = new Paint(Paint.ANTI_ALIAS_FLAG);
            boca.setStyle(Paint.Style.STROKE);
            boca.setStrokeWidth(2 * dp);
            boca.setStrokeCap(Paint.Cap.ROUND);
            boca.setColor(color);
            c.drawArc(new RectF(cx - h * 0.18f, h * 0.42f, cx + h * 0.18f, h * 0.68f), 20, 140, false, boca);
        }

        @Override public void setAlpha(int alpha) { p.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter cf) { p.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
