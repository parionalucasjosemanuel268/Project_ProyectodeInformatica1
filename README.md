# PACMAN 3D · "Ya no está aquí" (Java)

Videojuego de terror y acción en **primera persona y 3D real**, ambientado en una ciudad laberíntica de Halloween.
Hecho 100 % en Java, **sin librerías externas**: el motor 3D (triángulos, perspectiva, z-buffer, texturas, niebla,
transparencias, multihilo) está escrito desde cero en `com.pacman3d.gfx`.

## Cómo ejecutarlo en Visual Studio Code
Necesitas un **JDK 17 o superior** y la extensión *Extension Pack for Java*.

* **Opción A (Maven):** abre la carpeta `game` en VS Code → abre `Main.java` → botón ▶ *Run*
  (o `mvn -q compile exec:java` en la terminal; o `F5` con la configuración "PACMAN 3D").
* **Opción B (sin Maven):** ejecuta `run.bat` (Windows) o `./run.sh` (Linux/Mac) dentro de `game`.

Si el juego va lento, pulsa **F2** para cambiar la calidad (se ajusta sola si detecta pocos FPS). **F11** = pantalla completa.
En macOS el ratón necesita permiso de *Accesibilidad* para capturarse; mientras tanto puedes girar con las flechas.

## Controles
| Tecla | Acción |
|---|---|
| W A S D | Moverte |
| Ratón / flechas | Mirar |
| Clic izq. / Espacio | Disparar / atacar (en el arco, mantén para tensar y suelta) |
| 1 – 8 / rueda | Elegir arma del inventario |
| E | Abrir cofre |
| TAB (mantener) | Liberar el ratón para hacer clic en el inventario o en *Abandonar partida* |
| V | Cambiar entre **primera y tercera persona** |
| Clic derecho (mantener) | **ZOOM real** al apuntar (pistola 1.7x, rifle 3.5x con mira telescópica, arco 2.6x, minigun 1.6x) |
| ESC | Pausa (Continuar / Abandonar partida) |

## Reglas implementadas (todo lo de tu documento)
* Pac-Man (color elegible en la **Tienda** del menú; las estrellas recogidas se acumulan y compran colores) · **500 de vida · 3 vidas** (si mueres reapareces donde caíste).
* Empiezas **sin arma**. Cofres repartidos por el mapa dan armas, munición, botiquines, **poder** (inmunidad 30 s, raro) o la **llave**.
* Armas con aspecto y comportamiento propios: pistola (bala lenta), rifle (automático, más daño), arco (se tensa, flecha con caída), minigun (se acelera, daño medio, cadencia alta),
  espada (cuerpo a cuerpo, 2 golpes = 1 tiro de rifle, infinita) y granada (arco, rebote, mecha, mata todo en el radio de explosión).
* Munición compartida por todas las armas (menos la espada; la granada gasta 5). Contador en tiempo real y aviso **¡SE ACABARON LAS BALAS!** (no se puede disparar).
* Balas y botiquines también aparecen en el suelo. Balas, botiquines y poderes se usan automáticamente; el inventario de 8 espacios es solo para armas.
* **Estrellas** = más velocidad (se ve el % en pantalla).
* **3 niveles** (laberintos cada vez más grandes y con menos atajos): **20, 40 y 60 fantasmas**. Pasar el nivel 1 lleva solo al 2 y al 3.
* Para salir: eliminar a **todos** los fantasmas **o** encontrar la **llave** y llegar a la **única puerta**. Al cruzarla aparece **¡GANASTE!** con velocidad máxima, fantasmas eliminados, estrellas y vidas restantes.
* **Cronómetro de 5:00** en cada nivel. Si se acaba el tiempo o pierdes las 3 vidas, vuelves al **nivel 1**.
* **Fantasmas con la IA del Pac-Man original**: modos dispersión/persecución, no giran en U, y cada tipo con su objetivo
  (Blinky directo, Pinky embosca, Inky flanquea, Clyde tímido) — pero aquí se les mata a tiros.
* Menú: título, START, tienda, comandos/explicación, créditos. En partida: barra de vida, vidas, % de velocidad, estrellas, tiempo, fantasmas, minimapa, inventario inferior y botón *Abandonar partida*.
* Sonido: música de menú y de juego, ambiente, sustos aleatorios, disparos de cada arma, muerte de fantasmas, muerte de Pac-Man, victoria, recoger estrellas, clics arcade, latidos y truenos.

## Qué se mejoró respecto a tu proyecto anterior
* El juego anterior era un *ray-casting* 2.5D (paredes dibujadas con Java2D). Ahora todo es **geometría 3D real**: ciudad con edificios de alturas distintas, ventanas encendidas,
  farolas, calabazas con luz, tumbas, árboles muertos, ataúdes, cofres con tapa que se abre, estrellas y llaves flotantes, murciélagos, relámpagos, niebla y una lámpara que parpadea.
* Se **conservan tus recursos**: música (`menu_theme`, `pacman_gameplay`), todos los efectos de sonido (recortados) y tus ilustraciones de menú, tienda, comandos, créditos, niveles y resultados.
* Se eliminaron los 5 héroes y los 20 villanos y las monedas: ahora es solo Pac-Man (personalizable) contra fantasmas, como pediste.

## Estructura
```
game/
 pom.xml · run.sh · run.bat · .vscode/
 src/main/java/com/pacman3d/
   Main.java
   gfx/    Renderer (rasterizador), Mesh, MeshBuilder, Textures (texturas por código), Models (fantasma, Pac-Man, objetos)
   world/  Maze (generador de laberinto), Level (ciudad, luces, decoración, aparición de objetos)
   game/   Game (reglas), Ghost (IA), Weapon, Projectile, Chest, Pickup, Palette ...
   audio/  Audio (mezclador propio)
   ui/     App (ventana, menús), Hud, SceneRenderer, WeaponView (armas en 1ª persona), Input, Save
 src/main/resources/assets/  sfx · music · images
```
Valores fáciles de ajustar: `Level` (tamaño del laberinto, fantasmas, cofres), `Weapon` (daño/cadencia), `Game` (`BASE_SPEED`, `LEVEL_TIME`, vida, probabilidad del poder en `openChest`).
El progreso de la tienda se guarda en `~/.pacman3d/save.properties`.


## NOVEDADES DE LA VERSIÓN 2
* **Menú NIVELES:** elige Nivel 1, 2 o 3 y luego **JUGAR SOLO** o **CON ALGUIEN MÁS (MULTIJUGADOR)**.
* **Multijugador real para 2 personas (por red, TCP puerto 7777):**
  1. Uno elige nivel → *Con alguien más* → **Crear partida**. Verá su(s) IP en pantalla.
  2. El otro elige *Con alguien más* → **Unirse a una partida** → escribe esa IP (puede pegar con Ctrl+V) → *Conectar*.
  3. Cuando aparece "¡JUGADOR 2 CONECTADO!", el anfitrión pulsa **COMENZAR PARTIDA**.
  * Misma red Wi-Fi/LAN: funciona directo. Por internet: usa una VPN de juegos (Radmin VPN, Hamachi, ZeroTier) o abre el puerto 7777 TCP en el router.
  * Los dos comparten el mapa, los fantasmas, los cofres, la llave, la puerta y el cronómetro. Cada uno tiene su vida, vidas, inventario, munición y estadísticas.
    Se ven el uno al otro (Pac-Man con su color, zapatillas, arma en mano y etiqueta P1/P2) y en el minimapa. Si uno se queda sin vidas puede mirar a su compañero y revive en el siguiente nivel.
  * El anfitrión simula el juego (fantasmas, balas, daño); el otro jugador recibe 30 actualizaciones por segundo y se mueve sin retraso.
* **Empiezas con una PISTOLA y 24 balas.**
* **Tercera persona con V** (cámara sobre el hombro que no atraviesa paredes; Pac-Man camina con zapatillas, mueve la boca y sostiene el arma). V otra vez vuelve a primera persona.
* **Mucho más 3D y detalle:** fachadas con zócalo, cornisas, parapetos, pilastras, toldos y bajantes; ventanas encendidas que iluminan la calle; halos de luz en farolas, calabazas y velas;
  niebla baja en movimiento; lluvia; sombras bajo fantasmas, jugadores, cofres y objetos; silueta de ciudad lejana en el horizonte; vallas de hierro, velas, espantapájaros, alcantarillas, charcos y hojas;
  ondas expansivas en explosiones, muertes y cofres; estelas en balas y flechas; restos de ectoplasma que brillan en el suelo; estela espectral tras los fantasmas; ojos que pulsan;
  marcador de impacto en la mira e indicador de dirección del daño; sonido de lluvia.
* La calidad se ajusta sola según tu equipo (F2 para cambiarla a mano).


## NOVEDADES DE LA VERSIÓN 3
* **Zoom con clic derecho** en todas las armas que lo permiten: pistola (mira de hierro), **rifle con mira telescópica real** (retícula, puntos de medida, vista a 3.5x), arco y minigun.
  Al apuntar baja la dispersión, bajan la sensibilidad y la velocidad, y el arma pasa al centro. La espada y la granada no tienen zoom (no son armas de apuntar).
* **Más cofres:** 16 / 22 / 28 por nivel (antes 9 / 13 / 17).
* **Trampas:** charcos de **ÁCIDO** (verde, burbujeante: -10 de vida por segundo mientras estás encima) y camas de **PÚAS** (-50 por segundo). Se ven en el minimapa una vez descubiertas. La inmunidad te protege.
* **3 tiendas por nivel** (una junto al centro y dos repartidas), cada una con aspecto distinto y su tendero:
  **ARMERÍA** (puesto de mercado con toldo a rayas y armas en los estantes), **BOTICA** (cabaña de bruja con caldero, pociones brillantes y tendera con sombrero) y **BAZAR** (carromato con lona, ruedas, farolillos y tesoros).
  En cada una hay un **COFRE ARCOÍRIS**: por estrellas (6 / 8 / 10 según el nivel) te da un **arma aleatoria** con más probabilidad de las raras y poderosas (rifle, minigun, arco, granada). Puedes comprar otra vez, gastando más estrellas. Si ya tienes el arma te da +60 balas.
  Las estrellas que gastas salen de tu saldo (se muestra en el panel); la velocidad que ya ganaste no baja.
* **Almas perdidas (NPC):** 20 por nivel, hombres y mujeres translúcidos y brillantes que corren por el laberinto huyendo de los fantasmas. Si un fantasma las alcanza (o las matas por error con balas/explosiones) **mueren y quedan tiradas con un charco de sangre**.
  Si las **salvas tocándolas**, ganas 2 estrellas, el alma se eleva al cielo y aparece el mensaje **"HE VISTO LA LLAVE hacia el ESTE (a unos 42 m)"** junto con un **haz y aura dorados** que señalan la dirección de la llave y se ven desde cualquier lugar durante **2 segundos**.
* El multijugador sincroniza almas, tiendas, sangre y las pistas de la llave (versión de red 4: los dos jugadores deben tener esta versión).


## NOVEDADES DE LA VERSIÓN 4
* **Logo de la Universidad Europea** abajo a la derecha en el menú principal, NIVELES (y sus pantallas de modo/multijugador), TIENDA, COMANDOS y CRÉDITOS.
* **Créditos** con los integrantes del proyecto: Pariona Lucas Jose Manuel, Sergio Martínez Gargantilla y Araque Márquez Darío José (con sus códigos).
* **Almas perdidas infinitas:** cada vez que una muere o se salva, a los ~1 segundos aparece otra en un punto cualquiera del laberinto (siempre lejos de los jugadores). Los cuerpos y la sangre de las muertas se quedan en el suelo.
* **Fantasmas más agresivos, como en el Pac-Man:** persiguen siempre (ya no hay zona de "vigilancia"), pasan menos tiempo dispersándose, embisten cuando están cerca, hacen más daño y se aceleran poco a poco durante el nivel.
  **1 de cada 5 es VELOZ** (+30 % de velocidad, brillan en rojo y dejan estela). En el nivel 3 los veloces igualan a Pac-Man sin estrellas: ¡recoge estrellas!
* **Susto animado:** cada 1 a 2 minutos la pantalla se oscurece y parpadea, un fantasma gigante con ojos rojos se lanza contra la cámara con un grito y desaparece a los ~2 segundos.
* **Multijugador:** si cualquiera de los dos mata a todos los fantasmas (o usa la llave) y cruza la puerta, **el nivel se completa para los dos** y ambos pasan a la pantalla de resultados; el anfitrión pulsa "Siguiente nivel".
