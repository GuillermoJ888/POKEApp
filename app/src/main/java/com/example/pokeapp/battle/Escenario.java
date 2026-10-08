package com.example.pokeapp.battle;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;

import com.example.pokeapp.R;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Fondo de batalla: el dibujo del escenario, dónde se paran los Pokémon y el color
 * de las plataformas. Para agregar uno nuevo basta con sumar una línea a TODOS
 * (el fondo puede ser un vector XML o una imagen en res/drawable-nodpi).
 *
 * Las posiciones son el centro de cada plataforma como fracción de la IMAGEN
 * (0 = izquierda/arriba, 1 = derecha/abajo); la arena las ajusta al recorte de pantalla.
 */
public class Escenario {

    // Posiciones de los fondos vectoriales (plataformas que pone la app)
    private static final float B_X = 0.70f, B_Y = 0.45f, A_X = 0.32f, A_Y = 0.92f;

    public final String nombre;
    public final int fondo;
    public final float bX, bY, aX, aY;
    /** true = el fondo ya trae las plataformas dibujadas; la app no pone las suyas. */
    public final boolean plataformasDibujadas;
    /** Tamaño de los Pokémon (1 = normal); menor si las plataformas del dibujo están juntas. */
    public final float escalaPokemon;
    private final int centroPlataforma, bordePlataforma, contornoPlataforma;

    public static final List<Escenario> TODOS = Collections.unmodifiableList(Arrays.asList(
            new Escenario("Bosque", R.drawable.bg_escenario_bosque, 0xFFC8E89A, 0xFF7DBF55, 0xFF4E8A32),
            new Escenario("Montaña nevada", R.drawable.bg_escenario_nieve, 0xFFFFFFFF, 0xFFCFE0F0, 0xFF9DB8D6),
            new Escenario("Desierto", R.drawable.bg_escenario_desierto, 0xFFF6DCA0, 0xFFD9AE63, 0xFFB5853F),
            new Escenario("Playa", R.drawable.bg_escenario_playa, 0xFFFFF4D6, 0xFFEBD39A, 0xFFC9A866),
            new Escenario("Gimnasio", R.drawable.bg_escenario_gimnasio, 0xFFF2F2F2, 0xFFC9C9C9, 0xFFE3350D),
            // Imagen de 800 × 1328 con las plataformas ya dibujadas (juntas: Pokémon al 75 %)
            new Escenario("Bosque encantado", R.drawable.bg_escenario_bosque_encantado,
                    0.696f, 0.527f, 0.338f, 0.655f, 0.75f)
    ));

    /** Fondo sin plataformas: la app dibuja óvalos con estos colores en las posiciones de siempre. */
    public Escenario(String nombre, int fondo, int centroPlataforma, int bordePlataforma, int contornoPlataforma) {
        this.nombre = nombre;
        this.fondo = fondo;
        this.bX = B_X;
        this.bY = B_Y;
        this.aX = A_X;
        this.aY = A_Y;
        this.plataformasDibujadas = false;
        this.escalaPokemon = 1f;
        this.centroPlataforma = centroPlataforma;
        this.bordePlataforma = bordePlataforma;
        this.contornoPlataforma = contornoPlataforma;
    }

    /** Fondo que ya trae sus plataformas: se indica dónde está el centro de cada una. */
    public Escenario(String nombre, int fondo, float bX, float bY, float aX, float aY, float escalaPokemon) {
        this.nombre = nombre;
        this.fondo = fondo;
        this.bX = bX;
        this.bY = bY;
        this.aX = aX;
        this.aY = aY;
        this.plataformasDibujadas = true;
        this.escalaPokemon = escalaPokemon;
        this.centroPlataforma = 0;
        this.bordePlataforma = 0;
        this.contornoPlataforma = 0;
    }

    public static Escenario aleatorio(Random random) {
        return TODOS.get(random.nextInt(TODOS.size()));
    }

    /** Plataforma ovalada con los colores del escenario (una nueva por cada vista). */
    public GradientDrawable crearPlataforma(Context context) {
        float densidad = context.getResources().getDisplayMetrics().density;

        GradientDrawable plataforma = new GradientDrawable();
        plataforma.setShape(GradientDrawable.OVAL);
        plataforma.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        plataforma.setGradientRadius(100 * densidad);
        plataforma.setColors(new int[]{centroPlataforma, bordePlataforma});
        plataforma.setStroke(Math.round(2 * densidad), contornoPlataforma);
        return plataforma;
    }
}
