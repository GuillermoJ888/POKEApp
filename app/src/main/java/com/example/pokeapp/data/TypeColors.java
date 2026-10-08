package com.example.pokeapp.data;

import android.graphics.Color;

import java.util.HashMap;
import java.util.Map;

public class TypeColors {

    private static final Map<String, String> COLORES = new HashMap<>();

    static {
        COLORES.put("normal", "#A8A878");
        COLORES.put("fire", "#F08030");
        COLORES.put("water", "#6890F0");
        COLORES.put("electric", "#F8D030");
        COLORES.put("grass", "#78C850");
        COLORES.put("ice", "#98D8D8");
        COLORES.put("fighting", "#C03028");
        COLORES.put("poison", "#A040A0");
        COLORES.put("ground", "#E0C068");
        COLORES.put("flying", "#A890F0");
        COLORES.put("psychic", "#F85888");
        COLORES.put("bug", "#A8B820");
        COLORES.put("rock", "#B8A038");
        COLORES.put("ghost", "#705898");
        COLORES.put("dragon", "#7038F8");
        COLORES.put("dark", "#705848");
        COLORES.put("steel", "#B8B8D0");
        COLORES.put("fairy", "#EE99AC");
    }

    public static int obtenerColor(String tipo) {
        String hex = COLORES.get(tipo);
        return Color.parseColor(hex != null ? hex : "#777777");
    }

    public static String traducir(String tipo) {
        switch (tipo) {
            case "normal": return "Normal";
            case "fire": return "Fuego";
            case "water": return "Agua";
            case "electric": return "Eléctrico";
            case "grass": return "Planta";
            case "ice": return "Hielo";
            case "fighting": return "Lucha";
            case "poison": return "Veneno";
            case "ground": return "Tierra";
            case "flying": return "Volador";
            case "psychic": return "Psíquico";
            case "bug": return "Bicho";
            case "rock": return "Roca";
            case "ghost": return "Fantasma";
            case "dragon": return "Dragón";
            case "dark": return "Siniestro";
            case "steel": return "Acero";
            case "fairy": return "Hada";
            default: return tipo;
        }
    }
}
