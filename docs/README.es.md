# JPPlayerVaults

Vaults privados por jugador para servidores **Spigot/Paper de Minecraft 1.8 hasta la última
versión**, con una sola build y sin dependencias (ni Vault, ni NMS, ni base de datos).

Cada jugador abre su cofre con `/pv <número>`, y la cantidad de vaults a la que tiene acceso la
decide el rango (`jpvaults.vaults.<n>`). Cada vault es un inventario de hasta **54 slots** (6 filas).

> La documentación completa y actualizada está en inglés: [README.md](../README.md).
> Esta página resume lo esencial en español.

## Características

- `/pv 1`, `/pv 2`, `/pv 3`… abre el vault privado del jugador.
- La cantidad de vaults la otorga el permiso del rango: `jpvaults.vaults.1` … `jpvaults.vaults.<n>`.
  El plugin usa el número más alto que tenga el jugador.
- Inventario de 54 slots por defecto (configurable, múltiplo de 9).
- Se comporta como un cofre normal: shift-click, teclas de hotbar, arrastrar objetos.
- Guardado automático de los vaults abiertos, al cerrar, al salir y al apagar el servidor.
- Antidupe: guardado duradero al cerrar, `Q` y clics fuera de la ventana bloqueados, tecla `F`
  protegida, cierre de vistas al recargar y escritura atómica.
- Mensajes en inglés y español (`language: en` o `es`), ampliables con cualquier idioma.
- Una sola build de 1.8 a la última versión; sin NMS.

## Instalación

1. Copia `JPPlayerVaults-1.0.0.jar` a la carpeta `plugins/` del servidor.
2. Reinicia el servidor (o `/reload confirm`).
3. Da `jpvaults.vaults.<n>` a tus rangos y ajusta `plugins/JPPlayerVaults/config.yml`.
4. Para español: `language: es` y `/pv reload`.

## Comandos

| Comando | Descripción | Permiso |
| --- | --- | --- |
| `/pv <número>` | Abre tu vault privado con ese número. | `jpvaults.use` |
| `/pv` / `/pv help` | Muestra la ayuda y cuántos vaults tienes. | `jpvaults.use` |
| `/pv reload` | Recarga `config.yml` y los mensajes. | `jpvaults.reload` (OP) |

Alias: `playervaults`, `jpvaults`.

## Permisos

| Permiso | Descripción | Por defecto |
| --- | --- | --- |
| `jpvaults.use` | Permite usar `/pv`. | `true` |
| `jpvaults.vaults.<n>` | El rango obtiene acceso a `n` vaults (se usa el número más alto). | `false` |
| `jpvaults.reload` | Permite `/pv reload`. | OP |

Ejemplo con LuckPerms:

```bash
# VIP: 3 vaults
/lp group vip permission set jpvaults.vaults.3 true
# MVP: 6 vaults
/lp group mvp permission set jpvaults.vaults.6 true
```

Los jugadores sin ningún permiso `jpvaults.vaults.<n>` reciben `vaults.default-amount` (1 por
defecto). Si tu rango más alto necesita más de `vaults.max-amount` vaults, sube ese valor.

## Configuración (`config.yml`)

| Clave | Descripción | Defecto |
| --- | --- | --- |
| `language` | Idioma de los mensajes (`messages_<idioma>.yml`): `en`, `es`. | `en` |
| `vaults.size` | Slots por vault (múltiplo de 9, 9–54). | `54` |
| `vaults.default-amount` | Vaults para jugadores sin permiso de rango. | `1` |
| `vaults.max-amount` | Número más alto de `jpvaults.vaults.<n>` que se comprueba. | `10` |
| `storage.folder` | Carpeta de datos (requiere reinicio si la cambias). | `data` |
| `storage.autosave-seconds` | Cada cuánto se guardan los vaults abiertos (`0` lo desactiva). | `300` |
| `sounds.*` | Sonidos de interfaz (activado, volumen, tono). | — |

## Almacenamiento

- Un archivo por jugador: `plugins/JPPlayerVaults/data/<uuid>.yml`.
- Formato legible: `vaults.<número>.items.<slot>: <objeto>`.
- Bajar `vaults.size` temporalmente no borra nada: los objetos de las filas ocultas se conservan.
- Si un archivo no se puede leer, se guarda una copia `.bak` antes de que el plugin pueda
  sobrescribirlo.

## Antidupe

- Guardado duradero al cerrar (espera a que el archivo esté en disco).
- `Q`/`Ctrl+Q` bloqueados en el GUI, clics fuera de la ventana bloqueados, `F` protegida.
- Sin GUIs zombi al recargar; cambio de vault sin pérdidas; escritura atómica.
- Autoguardado cada `storage.autosave-seconds`.

Límites conocidos: si el proceso muere mientras un vault está abierto se recupera el último
guardado; no compartas la misma carpeta `data/` entre varios servidores encendidos.

## Compilar

Requiere JDK 17 o superior (el `.jar` resultante es Java 8):

```bash
mvn clean package
```

El `.jar` queda en `target/JPPlayerVaults-<versión>.jar`.

## Enlaces

- Documentación completa (inglés): <https://nameeees.github.io/jpvaults-docs/>
- Código fuente y descargas: <https://github.com/Nameeees/JPPlayerVaults>
- Discord de la comunidad: <https://discord.gg/HATd9QgukF>
- Página del recurso en SpigotMC: el enlace se añade al publicar.

## Licencia

MIT. Puedes usarlo, modificarlo y redistribuirlo, también en servidores comerciales, manteniendo el
aviso de copyright.
