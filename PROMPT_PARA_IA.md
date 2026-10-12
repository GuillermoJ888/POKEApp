# Prompt para la IA de quien quiera conectar su app

Copia todo lo de abajo y pégaselo a tu IA junto con los 3 archivos
(`ConexionBatalla.java`, `Protocolo.java` y `PROTOCOLO_BATALLA.md`).

---

Quiero que mi app Android de Pokémon (usa la PokéAPI) pueda pelear por turnos contra la app
de un compañero, entre dos celulares cercanos, usando Nearby Connections. Su app ya funciona
y definió un protocolo; mi app tiene que ser compatible con ese protocolo, no inventar otro.

Te paso 3 archivos:

1. `PROTOCOLO_BATALLA.md` — la especificación completa: dependencia, permisos, cómo conectarse
   y los 8 mensajes JSON. **Léelo completo antes de programar; es la fuente de verdad.**
2. `ConexionBatalla.java` — clase ya hecha que encuentra al otro celular y manda/recibe mensajes.
3. `Protocolo.java` — clase ya hecha que arma los mensajes JSON.

Reglas obligatorias:

- Copia `ConexionBatalla.java` y `Protocolo.java` a mi proyecto **sin cambiar su lógica**; solo
  ajusta la línea `package` a mi paquete. Si mi proyecto es Kotlin, déjalos en Java (son compatibles).
- No cambies el ID de servicio (`cesba.pokebatalla.v1`), la estrategia (`P2P_POINT_TO_POINT`),
  los nombres de los mensajes ni los de sus campos. Si cambias algo, las apps no se encuentran
  o no se entienden.
- Agrega la dependencia y TODOS los permisos del documento al `AndroidManifest.xml`, y pide en
  tiempo de ejecución los de `ConexionBatalla.permisos()` antes de crear o buscar sala.
  (En Android 17 falta de `ACCESS_LOCAL_NETWORK` da el error "8: INTERNAL_ERROR"; sin ubicación da 8034.)
- Quien crea la sala es el **anfitrión** y calcula toda la batalla con las reglas de su app.
  El **invitado** no calcula nada: solo manda lo que elige y dibuja lo que el anfitrión le dice.
  Mi app debe poder ser las dos cosas.

Lo que tienes que programar en mi app (adaptándolo a mis pantallas y a mi motor de batalla):

1. **Pantalla de sala**: elegir mi equipo (3 Pokémon), botón "Crear sala", botón "Buscar sala"
   con la lista de salas encontradas, y una ventana para aceptar/rechazar mostrando el código.
   Al conectar, mandar el mensaje `equipo` y, cuando ya mandé el mío y llegó el del rival,
   bajar sus Pokémon de la PokéAPI por ID y abrir la batalla.
2. **Batalla como invitado**: esperar `inicio` (trae HP y ataques de los dos equipos: usar ESOS,
   no los de mi app), responder `listo`, y luego: en cada `turno` que sea mío mostrar mis ataques
   o pedir reemplazo y mandar `accion`; dibujar cada `ataque`, `entra` y `fin` que llegue.
3. **Batalla como anfitrión**: mandar `inicio` con HP y ataques de los 6 Pokémon, esperar `listo`,
   y usar mi motor de batalla; antes de cada decisión mandar `turno`; cuando le toque al rival,
   esperar su `accion` (validando que sea su turno y que el índice exista); después de cada cosa
   que pase mandar `ataque` o `entra`, y al final `fin`.
4. **Revancha y desconexión**: tras `fin` la conexión sigue abierta; si los dos vuelven a mandar
   `equipo`, empieza otra batalla. Si se corta la conexión a media batalla, se cancela.

Detalles que suelen fallar:

- Los lados en los mensajes son siempre `"anfitrion"` / `"invitado"`, nunca "yo" / "rival":
  hay que traducirlos según quién sea este celular.
- Los índices empiezan en 0 y siguen el orden del mensaje `equipo` / `inicio`.
- `tipo` puede no venir en un ataque (ataque sin tipo) y `precision` 0 significa que nunca falla.
- Ignora los campos o mensajes que no conozcas en vez de fallar.
- Cuando la pantalla de sala abre la batalla, la conexión NO se debe cerrar: `ConexionBatalla`
  es una sola para toda la app y guarda los mensajes que lleguen mientras ninguna pantalla escucha
  (se entregan al llamar `setEscucha`).
- Solo se puede probar con dos celulares físicos (el emulador no sirve), con Bluetooth, WiFi y
  ubicación encendidos.

Antes de escribir código, revisa cómo está hecha mi app (pantallas, motor de batalla, cómo cargo
los Pokémon) y dime tu plan: qué archivos vas a crear y cuáles vas a modificar.
