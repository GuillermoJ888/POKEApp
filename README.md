# PokéApp V2

Aplicación Android en **Java + XML** que consume la [PokéAPI](https://pokeapi.co) e integra **Firebase** (Authentication y Cloud Firestore). Proyecto integrador de **Programación Móvil 2** — CESBA, Licenciatura en Sistemas Computacionales.

## Módulos

| Botón | Módulo | Estado |
|---|---|---|
| — | SplashScreen + Login / Registro (correo y Google) | ✅ |
| — | Menú principal | ✅ |
| A | **Pokédex**: búsqueda con sugerencias, normal/shiny, tipos, stats, línea evolutiva, cry y favoritos | ✅ |
| B | **Battle Emulator**: elegir Pokémon A y B, batalla automática a pantalla completa, historial | ✅ |
| C | Battle Versus | ⏳ |
| D | Torre Pokémon | ⏳ |
| E–H | ¿Quién es ese Pokémon?, Safari, Maestro de Tipos, PokéMemory | ⏳ |
| I | **Favoritos** (en la nube) | ✅ |
| J | Historial | ⏳ (el del Battle Emulator ya funciona) |
| K | Medallas | ⏳ |
| L | **Mi perfil** | ✅ |
| M | Configuración | ⏳ |

## Battle Emulator

1. Se buscan los dos Pokémon por nombre o número (con sugerencias, populares o 🎲 al azar). No se puede elegir el mismo para A y B.
2. **Iniciar batalla** abre la arena a pantalla completa con un escenario al azar (bosque, montaña nevada, desierto, playa, gimnasio o bosque encantado).
3. Los turnos avanzan solos con animaciones, barras de vida con % y registro de cada ataque.
4. Al terminar regresa al Emulator y muestra el ganador (imagen, HP restante y número de turnos) con opciones de revancha, elegir otro Pokémon o volver al menú.
5. Cada batalla se guarda en el **Historial** (`usuarios/{uid}/historial`), agrupado por día y con el detalle de los turnos.

### Motor de batalla compartido (`battle/BattleEngine.java`)

Pensado para reutilizarse en Battle Versus y Torre Pokémon. Usa los **stats reales de la PokéAPI**:

- Empieza el Pokémon con mayor **Velocidad** (empate: al azar) y luego se alternan.
- Movimiento del tipo del atacante que más le afecte al defensor (si ninguno le afecta, usa *Forcejeo*).
- **Daño = 12 × (Ataque / Defensa) × 1.5 (mismo tipo) × efectividad de tipo × 1.5 si es crítico × variación 0.85–1.00** (mínimo 1).
- Probabilidad de crítico = Velocidad / 512. El HP nunca baja de 0.

## Tecnologías

Android Studio · Java 11 · XML · Retrofit + Gson · Glide · PokéAPI · Firebase Authentication · Cloud Firestore · Credential Manager (Google Sign-In)

## Estructura

```
app/src/main/java/com/example/pokeapp/
├── *Activity.java        Pantallas (Splash, Login, Menu, Pokedex, BattleEmulator, BattleArena, Historial…)
├── *Adapter.java         Listas (RecyclerView)
├── battle/               Motor de batalla: BattleEngine, BattlePokemon, BattleTurn, TypeChart, Escenario
└── data/                 PokéAPI (ApiClient, PokeApiService, modelos) y Firestore (Favoritos, Usuario, Historial)
```

## Cómo compilarlo

El archivo `app/google-services.json` **no está en el repositorio** (contiene la configuración del proyecto de Firebase). Para correr la app:

1. Crea un proyecto en [Firebase Console](https://console.firebase.google.com) y agrega una app Android con el paquete `com.example.pokeapp`.
2. Descarga tu `google-services.json` y colócalo en `app/`.
3. En **Authentication** habilita *Correo/contraseña* y *Google* (para Google agrega la huella SHA-1 de tu llave de depuración).
4. En **Firestore Database** crea la base de datos y publica las reglas de [`firestore.rules`](firestore.rules).
5. Abre el proyecto en Android Studio y ejecútalo.

## Créditos

- Datos e imágenes de Pokémon: [PokéAPI](https://pokeapi.co). Pokémon y sus nombres son marcas de Nintendo, Creatures Inc. y GAME FREAK inc.; este es un proyecto escolar sin fines de lucro.
- Basado en el documento *PokéApp V2* del Prof. Víctor Ricardo Vargas Ávila (CESBA).
