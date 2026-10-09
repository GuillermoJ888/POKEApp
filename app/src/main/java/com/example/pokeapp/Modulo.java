package com.example.pokeapp;

import java.util.ArrayList;
import java.util.List;

/**
 * Un botón del Menú Principal.
 *
 * El botón se pinta a todo el diseño: si existe la imagen "mod_<clave>" en res/drawable
 * (ej. mod_pokedex.png) cubre la tarjeta completa; si no, se usa un degradado con los
 * colores del módulo y su dibujo propio (iconoRes) o su emoji como ilustración grande.
 */
public class Modulo {

    public final String clave;          // nombre del archivo de imagen: mod_<clave>
    public final String letraAntigua;   // compatibilidad con mod_a ... mod_m (puede ir vacía)
    public final String titulo;
    public final String descripcion;
    public final String emoji;
    public final int iconoRes;          // 0 = sin dibujo propio
    public final int colorInicio;       // degradado del botón (arriba-izquierda)
    public final int colorFin;          // degradado del botón (abajo-derecha)
    public final Class<?> destino;      // null = aún no implementado

    public Modulo(String clave, String letraAntigua, String titulo, String descripcion,
                  String emoji, int iconoRes, int colorInicio, int colorFin, Class<?> destino) {
        this.clave = clave;
        this.letraAntigua = letraAntigua;
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.emoji = emoji;
        this.iconoRes = iconoRes;
        this.colorInicio = colorInicio;
        this.colorFin = colorFin;
        this.destino = destino;
    }

    public boolean estaActivo() {
        return destino != null;
    }

    /** Todos los módulos del menú, en orden. */
    public static List<Modulo> todos() {
        List<Modulo> m = new ArrayList<>();
        m.add(new Modulo("pokedex", "a", "Pokédex", "Busca Pokémon", "📱", R.drawable.ic_pokedex, 0xFFC81E0A, 0xFFFF7A45, PokedexActivity.class));
        m.add(new Modulo("emulator", "b", "Battle Emulator", "Duelo simulado", "⚔️", 0, 0xFF5B21B6, 0xFFA855F7, BattleEmulatorActivity.class));
        m.add(new Modulo("versus", "c", "Battle Versus", "2 jugadores", "🆚", 0, 0xFF1D4ED8, 0xFF60A5FA, null));
        m.add(new Modulo("torre_batalla", "", "Torre de Batalla", "Un jefe por piso", "🏰", 0, 0xFF7C2D12, 0xFFEA580C, null));
        m.add(new Modulo("torre", "d", "Torre Pokémon", "6 niveles", "🗼", 0, 0xFF0F766E, 0xFF2DD4BF, null));
        m.add(new Modulo("quien", "e", "¿Quién es ese Pokémon?", "Adivina la silueta", "❓", 0, 0xFF1E1B4B, 0xFF4F46E5, null));
        m.add(new Modulo("safari", "f", "Safari Pokémon", "Captura Pokémon", "🌿", 0, 0xFF166534, 0xFF4ADE80, null));
        m.add(new Modulo("tipos", "g", "Maestro de Tipos", "Ventajas de tipo", "🔥", 0, 0xFFB91C1C, 0xFFF59E0B, null));
        m.add(new Modulo("memory", "h", "PokéMemory", "Encuentra parejas", "🃏", 0, 0xFF9D174D, 0xFFF472B6, null));
        m.add(new Modulo("favoritos", "i", "Favoritos", "En la nube ☁️", "⭐", 0, 0xFFB45309, 0xFFFACC15, FavoritosActivity.class));
        m.add(new Modulo("historial", "j", "Historial", "Tus batallas", "📜", 0, 0xFF374151, 0xFF9CA3AF, null));
        m.add(new Modulo("medallas", "k", "Medallas", "10 logros", "🏅", 0, 0xFF92400E, 0xFFEAB308, null));
        m.add(new Modulo("perfil", "l", "Mi perfil", "Tu progreso", "🧢", R.drawable.ic_gorra, 0xFF0E7490, 0xFF22D3EE, PerfilActivity.class));
        m.add(new Modulo("config", "m", "Configuración", "Tema y sonido", "⚙️", 0, 0xFF1E293B, 0xFF64748B, null));
        return m;
    }
}
