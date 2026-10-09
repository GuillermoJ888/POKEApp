package com.example.pokeapp.data;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/** Respuesta de la PokéAPI en /move/{nombre}: tipo, potencia, precisión y nombre en español. */
public class MoveDetalle {

    private String name;
    private Integer power;      // null en ataques de estado (no hacen daño)
    private Integer accuracy;   // null = nunca falla
    private NamedApiResource type;

    @SerializedName("damage_class")
    private NamedApiResource damageClass;   // physical, special o status

    private List<Nombre> names;

    public String getName() { return name; }
    public Integer getPower() { return power; }
    public Integer getAccuracy() { return accuracy; }
    public String getTipo() { return type != null ? type.getName() : "normal"; }

    /** true si hace daño (los de estado como "Gruñido" no sirven en esta batalla). */
    public boolean haceDanio() {
        return power != null && power > 0
                && (damageClass == null || !"status".equals(damageClass.getName()));
    }

    /** "Rayo" (nombre en español) o, si no lo trae, "Thunderbolt". */
    public String getNombreEspanol() {
        if (names != null) {
            for (Nombre n : names) {
                if (n.language != null && "es".equals(n.language.getName()) && n.name != null) return n.name;
            }
        }
        return ListaPokemon.nombreBonito(name);
    }

    static class Nombre {
        String name;
        NamedApiResource language;
    }
}
