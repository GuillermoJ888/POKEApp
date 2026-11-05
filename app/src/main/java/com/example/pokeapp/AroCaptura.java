package com.example.pokeapp;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;

/**
 * Aro de captura del Safari, como en Pokémon GO: un círculo blanco fijo y dentro otro de color
 * (según lo difícil que es el Pokémon) que se encoge una y otra vez.
 */
public class AroCaptura extends View {

    private static final float FRACCION_MINIMA = 0.25f;

    private final Paint fijo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint aro = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ValueAnimator animacion;
    private float fraccion = 1f;

    public AroCaptura(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float dp = getResources().getDisplayMetrics().density;
        fijo.setStyle(Paint.Style.STROKE);
        fijo.setStrokeWidth(2f * dp);
        fijo.setColor(0xCCFFFFFF);
        aro.setStyle(Paint.Style.STROKE);
        aro.setStrokeWidth(5f * dp);
    }

    /** Empieza a encogerse desde el tamaño completo, en el color indicado. */
    public void iniciar(int color) {
        detener();
        aro.setColor(color);
        animacion = ValueAnimator.ofFloat(1f, FRACCION_MINIMA);
        animacion.setDuration(1500);
        animacion.setRepeatCount(ValueAnimator.INFINITE);
        animacion.setInterpolator(new LinearInterpolator());
        animacion.addUpdateListener(a -> {
            fraccion = (float) a.getAnimatedValue();
            invalidate();
        });
        animacion.start();
        setVisibility(VISIBLE);
    }

    public void detener() {
        if (animacion != null) {
            animacion.cancel();
            animacion = null;
        }
        setVisibility(INVISIBLE);
    }

    /** Tamaño actual del aro: 1 = completo, 0.25 = lo más chico. */
    public float fraccion() {
        return fraccion;
    }

    /** Radio actual del aro de color, en píxeles. */
    public float radio() {
        return radioMaximo() * fraccion;
    }

    private float radioMaximo() {
        return Math.min(getWidth(), getHeight()) / 2f - fijo.getStrokeWidth() - aro.getStrokeWidth() / 2f;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animacion != null) animacion.cancel();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        canvas.drawCircle(cx, cy, radioMaximo(), fijo);
        canvas.drawCircle(cx, cy, radio(), aro);
    }
}
