package com.example.pokeapp.battle;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;

public class TypeChartTest {

    private static final float MARGEN = 0.0001f;

    @Test
    public void superEfectivo() {
        assertEquals(2f, TypeChart.efectividad("fire", "grass"), MARGEN);
        assertEquals(2f, TypeChart.efectividad("water", "fire"), MARGEN);
    }

    @Test
    public void pocoEfectivo() {
        assertEquals(0.5f, TypeChart.efectividad("fire", "water"), MARGEN);
    }

    @Test
    public void sinEfecto() {
        assertEquals(0f, TypeChart.efectividad("normal", "ghost"), MARGEN);
        assertEquals(0f, TypeChart.efectividad("electric", "ground"), MARGEN);
    }

    @Test
    public void neutroYTipoDesconocido() {
        assertEquals(1f, TypeChart.efectividad("normal", "water"), MARGEN);
        assertEquals(1f, TypeChart.efectividad("inventado", "water"), MARGEN);
        assertEquals(1f, TypeChart.efectividad("fire", "inventado"), MARGEN);
    }

    @Test
    public void dosTiposSeMultiplican() {
        assertEquals(4f, TypeChart.efectividad("fire", Arrays.asList("grass", "ice")), MARGEN);
        assertEquals(0.25f, TypeChart.efectividad("fire", Arrays.asList("water", "rock")), MARGEN);
        assertEquals(0f, TypeChart.efectividad("ground", Arrays.asList("fire", "flying")), MARGEN);
    }
}
