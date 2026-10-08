package com.example.pokeapp;

import java.util.ArrayList;
import java.util.List;

/**
 * Un botón del Menú Principal.
 *
 * La imagen del botón se busca primero como "mod_<clave>" en res/drawable
 * (ej. mod_pokedex.png). Si no existe, usa el dibujo propio (iconoRes)
 * y, si tampoco hay, el emoji.
 */
public class Modulo {

    public final String clave;          // nombre del archivo de imagen: mod_<clave>
    public final String letraAntigua;   // compatibilidad con mod_a ... mod_m (puede ir vacía)
    public final String titulo;
    public final String descripcion;
    public final String emoji;
    public final int iconoRes;          // 0 = sin dibujo propio
    public final Class<?> destino;      // null = aún no implementado

    public Modulo(String clave, String letraAntigua, String titulo, String descripcion,
                  String emoji, int iconoRes, Class<?> destino) {
        this.clave = clave;
        this.letraAntigua = letraAntigua;
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.emoji = emoji;
        this.iconoRes = iconoRes;
        this.destino = destino;
    }

    public boolean estaActivo() {
        return destino != null;
    }

    /** Todos los módulos del menú, en orden. */
    public static List<Modulo> todos() {
        List<Modulo> m = new ArrayList<>();
        m.add(new Modulo("pokedex", "a", "Pokédex", "Busca Pokémon", "📱", R.drawable.ic_pokedex, PokedexActivity.class));
        m.add(new Modulo("emulator", "b", "Battle Emulator", "Duelo simulado", "⚔️", 0, BattleEmulatorActivity.class));
        m.add(new Modulo("versus", "c", "Battle Versus", "2 jugadores", "🆚", 0, null));
        m.add(new Modulo("torre_batalla", "", "Torre de Batalla", "Un jefe por piso", "🏰", 0, null));
        m.add(new Modulo("torre", "d", "Torre Pokémon", "6 niveles", "🗼", 0, null));
        m.add(new Modulo("quien", "e", "¿Quién es ese Pokémon?", "Adivina la silueta", "❓", 0, null));
        m.add(new Modulo("safari", "f", "Safari Pokémon", "Captura Pokémon", "🌿", 0, null));
        m.add(new Modulo("tipos", "g", "Maestro de Tipos", "Ventajas de tipo", "🔥", 0, null));
        m.add(new Modulo("memory", "h", "PokéMemory", "Encuentra parejas", "🃏", 0, null));
        m.add(new Modulo("favoritos", "i", "Favoritos", "En la nube ☁️", "⭐", 0, FavoritosActivity.class));
        m.add(new Modulo("historial", "j", "Historial", "Tus batallas", "📜", 0, null));
        m.add(new Modulo("medallas", "k", "Medallas", "10 logros", "🏅", 0, null));
        m.add(new Modulo("perfil", "l", "Mi perfil", "Tu progreso", "🧢", R.drawable.ic_gorra, PerfilActivity.class));
        m.add(new Modulo("config", "m", "Configuración", "Tema y sonido", "⚙️", 0, null));
        return m;
    }
}
