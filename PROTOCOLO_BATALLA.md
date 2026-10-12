# Batalla entre dispositivos — protocolo v1

Guía para que **dos apps distintas** (cada quien con su proyecto) puedan pelear entre sí.
Solo hace falta que las dos usen la PokéAPI (mismos ID de Pokémon) y sigan este documento.

## 1. Qué copiar

De `app/src/main/java/com/example/pokeapp/red/` copia a tu proyecto, tal cual:

| Archivo | Para qué |
|---|---|
| `ConexionBatalla.java` | Encuentra al otro celular y manda/recibe mensajes (Nearby Connections) |
| `Protocolo.java` | Arma los mensajes JSON de este documento |

Solo cambia la primera línea (`package ...`) por el paquete de tu app. No dependen de ninguna otra clase.

**build.gradle (app):**
```kotlin
implementation("com.google.android.gms:play-services-nearby:19.3.0")
```

**AndroidManifest.xml:**
```xml
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.NEARBY_WIFI_DEVICES" />
<uses-permission android:name="android.permission.ACCESS_LOCAL_NETWORK" />
```
Antes de crear o buscar sala hay que pedir al usuario los permisos de `ConexionBatalla.permisos()`.
En los dos celulares deben estar encendidos **Bluetooth, WiFi y ubicación** (no hace falta internet para
conectarse, pero sí para bajar los Pokémon de la PokéAPI).

## 2. Conexión

Las dos apps deben usar el mismo servicio (`cesba.pokebatalla.v1`) y la misma estrategia
(`P2P_POINT_TO_POINT`); ya vienen en `ConexionBatalla`.

```java
ConexionBatalla red = ConexionBatalla.obtener(this);
red.setEscucha(escucha);          // ConexionBatalla.Escucha: sobrescribe solo lo que uses

red.crearSala("Javi");            // ANFITRIÓN: se anuncia y espera
red.buscarSalas("Ana");           // INVITADO: llega alEncontrarSala(id, nombre) → red.unirse(id)

// En los dos llega alPedirConexion(rival, codigo) → red.aceptar() o red.rechazar()
// Luego alConectar(rival). Desde ahí: red.enviar(mensaje) y alRecibir(mensaje).
```

## 3. La regla principal

**El anfitrión (quien creó la sala) calcula toda la batalla con sus reglas. El invitado solo manda
lo que elige y dibuja lo que el anfitrión le dice.** Por eso no importa que cada app tenga su
propia fórmula de daño, sus stats o sus ataques: en cada batalla valen los del anfitrión.

- Los lados siempre se llaman `"anfitrion"` e `"invitado"` (nunca "yo"/"rival").
- Los ID de Pokémon y los tipos (`"electric"`, `"fire"`…) son los de la PokéAPI.
- Todos los índices empiezan en 0.
- Cada mensaje es un JSON con el campo `"t"` (tipo). Los campos que no conozcas, ignóralos.

## 4. Mensajes, en orden

| # | Quién → quién | Mensaje |
|---|---|---|
| 1 | ambos | `{"t":"equipo","nombre":"Javi","pokemon":[25,6,9]}` |
| 2 | anfitrión → invitado | `{"t":"inicio","escenario":2,"anfitrion":[P,P,P],"invitado":[P,P,P]}` |
| 3 | invitado → anfitrión | `{"t":"listo"}` |
| 4 | anfitrión → invitado | `{"t":"turno","lado":"invitado","pide":"accion"}` |
| 5 | invitado → anfitrión | `{"t":"accion","hace":"atacar","indice":2}` |
| 6 | anfitrión → invitado | `{"t":"ataque","lado":"invitado","numero":1,"nombre":"Rayo","tipo":"electric","potencia":90,"precision":100,"danio":18,"efectividad":2.0,"critico":false,"fallo":false,"hp":60}` |
| 7 | anfitrión → invitado | `{"t":"entra","lado":"anfitrion","indice":1,"porDecision":false}` |
| 8 | anfitrión → invitado | `{"t":"fin","gana":"invitado","turnos":12}` |

`P` (un Pokémon dentro de `inicio`):
```json
{"id":25,"hp":35,"ataques":[{"nombre":"Rayo","tipo":"electric","potencia":90,"precision":100}]}
```

### Detalle

1. **equipo** — Al conectarse, cada celular manda su nombre y los ID de su equipo (de 1 a 6; esta app usa 3).
   Cada quien baja de la PokéAPI los Pokémon del rival para tener sus imágenes.
2. **inicio** — El anfitrión dicta el HP y los ataques de los 6 Pokémon (en el mismo orden de `equipo`).
   El invitado dibuja los botones de ataque con esa lista. `escenario` es opcional (puede ignorarse).
   `tipo` puede faltar (ataque sin tipo) y `precision` 0 significa que nunca falla.
3. **listo** — El invitado avisa que ya dibujó todo. El anfitrión no manda turnos antes de recibirlo.
4. **turno** — Antes de cada decisión el anfitrión dice a qué lado le toca:
   - `"pide":"accion"` → elegir un ataque o cambiar de Pokémon.
   - `"pide":"reemplazo"` → su Pokémon cayó y debe mandar otro.
   Si el lado es `"anfitrion"`, el invitado solo muestra "Esperando…".
5. **accion** — Respuesta del invitado cuando le toca:
   - `{"hace":"atacar","indice":n}` → usa su ataque número n (de la lista de `inicio`).
   - `{"hace":"cambiar","indice":n}` → manda a su Pokémon número n (sirve para cambio y para reemplazo).
   El anfitrión ignora acciones que lleguen fuera de turno o con índices no válidos.
6. **ataque** — Resultado de un ataque del Pokémon activo de `lado` contra el activo del otro lado.
   `hp` = HP con que queda el defensor (si es 0, se debilitó). `numero` = número de turno.
7. **entra** — Ese lado manda al Pokémon `indice`. `porDecision` true = usó "Cambiar" (gastó su turno);
   false = entró porque el anterior cayó. Si solo le queda uno vivo, el anfitrión lo manda sin preguntar.
8. **fin** — Quién ganó. La conexión sigue abierta.

**Revancha:** después de `fin`, cada quien vuelve a mandar `equipo` (puede ser otro equipo).
Cuando los dos lo mandaron, empieza otra batalla desde el paso 2.

**Desconexión:** si se corta la conexión a media batalla (`alDesconectar`), la batalla se cancela
y no cuenta para nadie.

## 5. Qué programa cada quien

Lo que **sí** es de cada app (no se comparte): sus pantallas, su motor de batalla (solo se usa cuando
es anfitrión) y cómo dibuja los ataques. En esta app: `MultijugadorActivity` (sala) y
`BattleArenaActivity` (arena; busca "Batalla entre dispositivos").

Lista mínima para ser compatible:

- [ ] Como **invitado**: mandar `equipo`, esperar `inicio`, mandar `listo`, responder cada `turno`
      con una `accion`, y dibujar `ataque`, `entra` y `fin`.
- [ ] Como **anfitrión**: mandar `equipo`, luego `inicio`, esperar `listo`, y por cada paso de la
      batalla mandar `turno`, `ataque`, `entra` y al final `fin`.

Para probar hacen falta **dos celulares físicos** (el emulador no tiene Bluetooth/WiFi cercano).
