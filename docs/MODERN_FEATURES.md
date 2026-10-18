# Modern-version feature audit

What the newest Minecraft/Paper APIs offer that JPPlayerVaults does **not** use yet, what the plugin
uses today, and what is worth adopting — keeping the single jar working from 1.8.

The point of this document is that a plugin which only *runs* on modern versions is not the same as
a plugin that takes advantage of them. Everything below was checked against the real API jars, not
from memory.

## Status after 1.2.0

| Item | Priority then | Status |
| --- | --- | --- |
| NBT item storage (`serializeAsBytes` / `serializeItemsAsBytes`) | P1 | **Done.** `storage.item-format: auto \| nbt \| bukkit`, format byte per blob, old blobs still load. The YAML backend has `storage.yaml-format: readable \| nbt`. |
| `getOfflinePlayerIfCached` for admin lookups | P1 | **Done.** Used when present, with the cache scan as fallback. |
| Paper `libraries:` for the JDBC driver | P2 | **Superseded.** Loading any `.jar` from `plugins/JPPlayerVaults/lib/` solves the same problem without making every start need internet. The `libraries:` line is documented, commented out, in `plugin.yml`. |
| Adventure components + MiniMessage | P2 | **Done.** `messages-format: legacy \| minimessage \| auto`; chat, action bars, inventory titles and the picker item names/lore. |
| Native dialogs as a vault picker | P3 | **Replaced by an inventory picker.** The dialog APIs differ per platform (`net.md_5.bungee.api.dialog.Dialog` on Spigot, `io.papermc.paper.dialog.Dialog` + Adventure's `showDialog` on Paper) and the Spigot route needs the `bungeecord-chat` artifact this project deliberately does not compile against. A menu works on every version instead. |
| Folia support | P3 | **Implemented, not declared.** The schedulers are region-aware (`GlobalRegionScheduler`, `EntityScheduler`), but the plugin does not claim `folia-supported: true` until it is tested on a real Folia server. |
| PersistentDataContainer tagging | P4 | **Not done, on purpose.** It only pays off with a concrete feature that needs it; there is none yet. |

## 0. How the facts were verified

| Artifact | Version |
| --- | --- |
| `spigot-api` | `26.3-R0.1-20261004.024323-9` |
| `paper-api` | `26.3.build.205-stable` |
| `paper-api` sources | `26.3.build.205-stable` (javadoc read from the source jar) |
| `adventure-api` | `5.2.0` |

Method: `javap` over the jars for the exact method signatures, and the **source jar** for the
javadoc contracts (for example, what Paper says about its own item serialization). On the plugin
side, an audit of `src/main/java` with file and line references.

## 1. Verified API facts for 26.3

| API | Spigot 26.3 | Paper 26.3 |
| --- | --- | --- |
| `Inventory#getTitle()` | **Removed** | Removed |
| `InventoryView#getTitle()` / `setTitle(String)` | Present | Present, plus `title()` returning an Adventure `Component` |
| `Bukkit.createInventory(holder, size, String)` | Present | Present but **deprecated** in favour of the `Component` overload |
| `ItemStack` serialization | `serialize()` / `deserialize(Map)` only | Adds `serializeAsBytes()`, `deserializeBytes()`, `serializeItemsAsBytes(Collection)`, `deserializeItemsFromBytes(byte[])` |
| `ItemStack#isEmpty()` | Not in the API | Present |
| `Material` | Still an enum, with `isAir()` and `isItem()` | Same |
| `Sound` | Still an enum: `valueOf` works, our three names exist (`BLOCK_ENDER_CHEST_OPEN`, `ENTITY_PLAYER_LEVELUP`, `ENTITY_VILLAGER_NO`) | Same, plus `getKey()` |
| `ChatColor` | Still present (`stripColor`, `translateAlternateColorCodes`) | Same (deprecated) |
| `Bukkit.getOfflinePlayerIfCached(String)` | Not in the API | Present: *“will not make a web request … will not block … returns null if the player is not cached”* |
| Schedulers | `getScheduler()` | `getScheduler()` **plus** `getGlobalRegionScheduler()`, `getRegionScheduler()`, `getAsyncScheduler()` (Folia-compatible) |
| Plugin metadata / logging | `getDescription()` | `getPluginMeta()`, `getComponentLogger()` |
| `plugin.yml` `libraries:` | Not supported | Supported (`PluginDescriptionFile#getLibraries`, `LibraryLoader`): Paper downloads Maven artifacts and links them to the plugin classloader |
| Native dialogs (1.21.6+) | `Player#showDialog(NamespacedKey)` and `showDialog(net.md_5.bungee.api.dialog.Dialog)` — the latter needs `bungeecord-chat` on the classpath | `io.papermc.paper.dialog.Dialog` + `DialogRegistryEntry.Builder`, shown through Adventure (`Audience#showDialog(DialogLike)`, verified in adventure-api 5.2.0) |
| Adventure / MiniMessage | — | `Audience#sendMessage(Component)`, `sendActionBar(Component)`. MiniMessage ships with the **server**, not with `paper-api`: declare it or use `libraries:` |

### The most important javadoc in this document

Paper documents its own NBT byte format (`ItemStack#serializeAsBytes`) like this:

> *“NBT is safer for data migrations as it will use the built-in data converter instead of bukkits
> dangerous serialization system.”*

That is exactly the mechanism the plugin uses today to store items: Bukkit's map/`ConfigurationSerializable`
serialization, through `ConfigurationSection` (YAML backend) and `BukkitObjectOutputStream` (SQL
backend).

## 2. What the plugin uses today

| Where | Uses | Notes |
| --- | --- | --- |
| `VaultManager.java:123` | `Bukkit.createInventory(holder, size, String)` | Deprecated on Paper; the title is also capped at 32 chars for 1.8–1.12 clients |
| `VaultCodec.java` | `BukkitObjectOutputStream` / `BukkitObjectInputStream` | SQL blob storage, built on Bukkit's map serialization |
| `YamlVaultStorage.java` | `ConfigurationSection#set/getItemStack` | Same serialization, human-readable files |
| `UiSound.java:28` | `Sound.valueOf(name)` with fallbacks | Works on 26.3 (verified); `getKey()` is the future-proof path |
| `Text.java:25,30` | `ChatColor.translateAlternateColorCodes` / `stripColor` | Works everywhere, deprecated on modern |
| `PlayerVaultCommand.java:354,376,384` | `Bukkit.getOfflinePlayers()` linear scan | Runs on the main thread for `/pv admin open <name>` |
| `JPPlayerVaults.java:355`, `StorageMigration.java:85` | `getScheduler().runTaskTimer` | Legacy scheduler; not Folia-compatible |
| `VaultManager.java:342`, `VaultListener.java:132`, `StorageMigration.java:149` | `getType() != Material.AIR` | Works everywhere; `isAir()` (all versions) or `isEmpty()` (Paper) read better |
| `Inventory#getTitle()` | **Not used** (0 matches in `src/main/java`) | The integration harness reads it, and already tolerates its removal on 26.3 |

## 3. What is missing, in priority order

### P1 — NBT item storage when the server offers it

**What**: store vault contents with `ItemStack.serializeItemsAsBytes(ItemStack[])` /
`deserializeItemsFromBytes(byte[])` when those methods exist (Paper 1.20.5+), and keep the current
Bukkit serialization as the fallback for Spigot and old versions.

**Why**: this is the difference between “my items survived the Minecraft update” and a support
thread. The array format also preserves slot order and nulls by design, which is exactly what a
54-slot vault needs, and it is far more compact than YAML.

**How**: give every stored blob a one-byte format header (`0x01` = Bukkit stream, `0x02` = NBT
array) so both kinds stay readable; detect the API by reflection once and cache the result. Add
`storage.item-format: auto | bukkit | nbt` so an administrator can force the portable format before
moving a world from Paper to Spigot, where the NBT API does not exist.

**Risk**: medium. A blob written as NBT cannot be decoded by a Spigot server, hence the explicit
config switch and a clear error message instead of silently empty vaults.

**Effort**: ~120 lines in `VaultCodec` + format column/header, plus tests.

### P1 — `getOfflinePlayerIfCached` for administrator commands

**What**: use `Bukkit.getOfflinePlayerIfCached(name)` when available, instead of walking
`Bukkit.getOfflinePlayers()`.

**Why**: it is non-blocking, makes no web request, and is O(1) instead of an array scan of every
player who ever joined — on the main thread, every time an administrator types a name.

**Risk**: low. Reflection with the current loop as fallback.

**Effort**: ~30 lines in `PlayerVaultCommand`.

### P2 — Paper `libraries:` to ship the JDBC driver

**What**: declare the MySQL/MariaDB driver in `plugin.yml` under `libraries:`.

**Why**: today the database backend depends on the driver being present on the server. On Paper,
declaring the artifact removes that caveat completely and keeps the jar itself dependency-free (the
library is downloaded by the server, not shaded).

**Risk**: low, but it only applies to Paper; Spigot keeps using the server-provided driver.

**Effort**: a few lines plus documentation.

### P2 — Adventure components and MiniMessage

**What**: send `Component`s when the server has Adventure (`Audience#sendMessage(Component)`,
`sendActionBar`), and build inventory titles with `Bukkit.createInventory(holder, size, Component)`.

**Why**: clickable links and hover in the help (`/pv admin`, docs, Discord), gradients and hex
colours without the legacy `&#RRGGBB` dance, and no 32-character cap on servers whose clients are
all modern (`Inventory#getTitle()` is gone, but the component path is the supported one).

**How**: keep `messages_<language>.yml` as the single source; parse it to legacy on old servers and
to components (optionally MiniMessage) on modern ones. The 32-char cap stays for the legacy path.

**Risk**: medium — two rendering paths to keep in sync, and MiniMessage is not in `paper-api`.

**Effort**: ~200 lines (text pipeline + title path + tests).

### P3 — Native dialogs as a vault picker

**What**: on 1.21.6+, offer `/pv` with no argument a native dialog listing the player's vaults.

**Why**: it is the modern replacement for “type `/pv 3` and remember which chest is which”, and it
is cheaper than building a menu inventory with placeholder items.

**How**: two different APIs (verified): Spigot exposes it through Bungee's `Dialog` class — which
means re-adding the `bungeecord-chat` artifact to the compile classpath, the very dependency this
project excluded because it is no longer served by the Spigot/Sonatype repositories — while Paper
uses `io.papermc.paper.dialog.Dialog` plus Adventure's `Audience#showDialog`. Detect both by
reflection; keep the current command behaviour as the fallback.

**Risk**: medium. Two APIs and an artifact availability problem for the Spigot side.

**Effort**: ~150 lines.

### P3 — Folia support

**What**: `folia-supported: true` plus `getGlobalRegionScheduler()` / `getAsyncScheduler()` for the
autosave timer and the migration batches.

**Why**: Folia servers cannot use the legacy scheduler for global tasks; declaring support needs the
scheduling to be correct per region.

**Risk**: medium-high: the vault logic mutates inventories and caches, so every entry point has to
run on the right thread. Worth doing only with real Folia testing.

**Effort**: ~80 lines plus a test story.

### P4 — PersistentDataContainer tagging

**What**: tag items written by the plugin (1.14+) for dupe detection and future item-based features.

**Why**: it is the sanctioned way to attach data without abusing display names or NBT hacks.

**Risk**: low, but it only matters if a concrete feature needs it. Not worth it on its own.

## 4. Things **not** to do

| Do not | Why |
| --- | --- |
| Raise `api-version` from `1.13` | Servers between 1.13 and 1.20 would refuse to load the plugin, and the legacy mode is what makes Bukkit convert the material names that changed in 1.13+. |
| Use `serializeAsBytes` on Spigot | It does not exist there (verified); it must stay behind a capability check. |
| Replace `ChatColor`/`Sound` outright | Both still work on 26.3 (verified). They are the compatibility layer for 1.8; modern paths should be added *next to* them, not instead of them. |
| Move to the Paper API as a hard dependency | The plugin's selling point is one jar for Spigot and Paper from 1.8; Paper-only calls have to be optional and reflective. |
| Drop the readable YAML backend | It is a documented feature (administrators repair files by hand). If NBT storage is added, keep it as an option for the SQL backend and as the default for YAML, or make it explicit in `config.yml`. |

## 5. Recommended order

1. **NBT item storage (P1)** — it protects the thing the plugin exists for, and it is the item that
   matters when someone upgrades Minecraft.
2. **`getOfflinePlayerIfCached` (P1)** — small, safe, removes a main-thread scan from an admin
   command.
3. **Paper `libraries:` for the JDBC driver (P2)** — small and removes a documented limitation of
   the database backend.
4. Components/MiniMessage (P2), then dialogs and Folia (P3) as feature work.

### How each one would be tested

The integration harness already runs the real plugin on **Paper 1.8.8** and **Paper 26.3**:

- Item format: round-trip a vault with item meta on both servers, plus a **backward-compatibility
  check** that a blob written in the old (Bukkit) format still loads after the change, and that a
  forced `item-format: bukkit` writes the portable format.
- Offline lookup: an administrator command with a name that is in the usercache and one that is not,
  asserting the same “player not found” behaviour on both paths.
- Libraries: start Paper 26.3 with `storage.type: mysql` and no driver on the classpath and confirm
  the plugin reaches the database.
