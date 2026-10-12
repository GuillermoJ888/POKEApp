package com.example.pokeapp;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

/**
 * Pokéball dibujada con Canvas, parecida a la original de cada tipo (Safari):
 * POKE = roja; SUPER = azul con dos franjas rojas; ULTRA = negra con dos franjas amarillas.
 */
public class BolaDrawable extends Drawable {

    public static final int POKE = 0, SUPER = 1, ULTRA = 2;

    private static final int NEGRO = 0xFF1F1F1F, BLANCO = 0xFFF7F7F7;
    private static final int[] TAPA = {0xFFE3350D, 0xFF2F74D0, 0xFF2B2B2B};
    private static final int[] FRANJA = {0, 0xFFE3350D, 0xFFF5C518};

    private final int tipo;
    private final Paint pintura = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path circulo = new Path();

    public BolaDrawable(int tipo) {
        this.tipo = Math.max(POKE, Math.min(ULTRA, tipo));
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        float lado = Math.min(getBounds().width(), getBounds().height());
        float cx = getBounds().exactCenterX(), cy = getBounds().exactCenterY();
        float r = lado / 2f - lado * 0.04f;
        float borde = lado * 0.07f;

        // Todo lo de adentro se recorta al círculo de la bola
        circulo.reset();
        circulo.addCircle(cx, cy, r, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(circulo);

        pintura.setStyle(Paint.Style.FILL);
        pintura.setColor(BLANCO);
        canvas.drawRect(cx - r, cy, cx + r, cy + r, pintura);          // mitad de abajo
        pintura.setColor(TAPA[tipo]);
        canvas.drawRect(cx - r, cy - r, cx + r, cy, pintura);          // tapa

        if (tipo != POKE) {
            // Dos franjas inclinadas hacia afuera en la tapa
            pintura.setColor(FRANJA[tipo]);
            float ancho = r * (tipo == SUPER ? 0.42f : 0.30f);
            for (int lado2 = -1; lado2 <= 1; lado2 += 2) {
                canvas.save();
                canvas.rotate(lado2 * 28f, cx + lado2 * r * 0.55f, cy);
                canvas.drawRect(new RectF(cx + lado2 * r * 0.55f - ancho / 2f, cy - r * 1.3f,
                        cx + lado2 * r * 0.55f + ancho / 2f, cy), pintura);
                canvas.restore();
            }
        }

        pintura.setColor(NEGRO);
        canvas.drawRect(cx - r, cy - borde / 2f, cx + r, cy + borde / 2f, pintura);   // banda central
        canvas.restore();

        // Contorno y botón del centro
        pintura.setStyle(Paint.Style.STROKE);
        pintura.setStrokeWidth(borde);
        canvas.drawCircle(cx, cy, r, pintura);
        pintura.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, r * 0.34f, pintura);
        pintura.setColor(BLANCO);
        canvas.drawCircle(cx, cy, r * 0.20f, pintura);
    }

    @Override
    public void setAlpha(int alpha) {
        pintura.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter filtro) {
        pintura.setColorFilter(filtro);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
