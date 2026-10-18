# Changelog

All notable changes to JPPlayerVaults are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/) and the project uses
[Semantic Versioning](https://semver.org/).

## [1.2.0] - 2026-10-18

Modern-version features: the plugin now takes advantage of what new servers offer while keeping the
same jar working on 1.8.

### Added

- **Vault picker**: `/pv` without arguments opens a menu with one icon per vault (item count in the
  lore). `/pv 3` opens a specific vault as always and `/pv help` shows the text help;
  `vaults.picker: false` restores the old behaviour.
- **MiniMessage colours** (`messages-format: auto | legacy | minimessage`): on servers with
  Adventure (Paper and its forks) a message written with tags such as `<gradient:#2ec4b6:#12727f>`,
  `<#ff8800>`, `<click:run_command:'/pv 1'>` or `<hover:show_text:'...'>` is sent as a Component.
  It applies to chat, action bars, inventory titles and the picker item names/lore. `auto` is the
  default: messages that use `&` keep working exactly as before, on every version.
- **NBT item storage** for the SQL backend (`storage.item-format: auto | nbt | bukkit`). On Paper
  1.20.5+ vaults are stored with `ItemStack.serializeItemsAsBytes`, which Paper documents as *safer
  for data migrations* than Bukkit's own serialization, and which keeps slot order and gaps by
  design. Every blob records its format, so data written by 1.0.0-1.1.0 still loads.
- `storage.yaml-format: readable | nbt`: the YAML backend can store each vault as Base64 NBT instead
  of hand-editable slots (readable stays the default).
- `storage.mysql.driver-jar` and automatic loading of any `.jar` placed in
  `plugins/JPPlayerVaults/lib/`: the database backend no longer depends on the server shipping the
  JDBC driver. (`plugin.yml` documents Paper's `libraries:` as an alternative, commented out because
  it makes every server start need internet.)
- `vaults.title-max-length` (default 32, `0` = no limit): with Components available the 1.8-1.12
  title cap can be lifted.
- Paper/Folia scheduler support: the autosave and the migration use `GlobalRegionScheduler` when it
  exists, and anything that touches a player (closing a vault, saving it) is scheduled on that
  player's region thread via `EntityScheduler`.
- `/pv admin` looks names up with Paper's `getOfflinePlayerIfCached` instead of walking the whole
  player cache.

### Changed

- Messages are only sent as Components when the message actually uses MiniMessage; legacy messages
  keep the plain string path, so nothing about existing texts changes.
- The integration harness grew to cover the new surface: **102 checks on Paper 26.3** and **64 on
  Paper 1.8.8**, including an NBT round-trip, the compatibility of pre-1.2.0 blobs, MiniMessage in
  both modes, the picker and the schedulers.

### Notes

- Folia scheduling is implemented (region-aware), but the plugin does **not** declare
  `folia-supported: true` yet because it has not been tested on a real Folia server.
- On a server without Adventure the MiniMessage tags of a message are stripped so the text stays
  readable, and a warning is logged once.

## [1.1.0] - 2026-10-18

Database storage and administrator tools.

### Added

- **Optional SQL storage** (`storage.type: mysql`) alongside the YAML files. Any database reachable
  over JDBC works; MySQL and MariaDB are preconfigured, tables are created automatically and the
  data is written in transactions (one row per vault).
- **`/pv admin` subcommands**, all behind the new `jpvaults.admin` permission (OP by default):
    - `/pv admin open <player|uuid> <number>` — open another player's vault, online or offline.
    - `/pv admin info [player]` — plugin status (storage, players with data, settings) or a report
      for one player: UUID, state, vaults with items, stored items, rank and who has it open.
    - `/pv admin delete <player|uuid> <number> confirm` — wipe one vault.
    - `/pv admin migrate [force]` — copy the YAML files into the database, in batches, without
      deleting the originals.
- Storage settings in `config.yml`: host, port, database, credentials, table prefix, pool size,
  connection timeout, driver class and a raw JDBC URL for other engines.
- `StorageException`: when stored data cannot be read, the plugin now refuses to open the vault and
  tells the player to contact an administrator, instead of showing an empty vault that the next save
  would write over the real items.
- `/pv reload` reports when storage settings changed and a restart is needed.

### Changed

- The YAML backend implements the new storage contract (`deleteVault`, `knownOwners`, `describe`).
- A vault can only be open for one person at a time, so an administrator cannot edit a vault while
  its owner has it open (and vice versa).
- Player files that cannot be parsed are still backed up as `<name>.<timestamp>.bak`, and now also
  refuse to load until an administrator restores them.

## [1.0.0] - 2026-10-18

First public release.

### Added

- Private vaults per player with `/pv <number>`, aliases `/playervaults` and `/jpvaults`.
- Rank-based vault count: the plugin grants the highest `jpvaults.vaults.<n>` permission the
  player has, with `vaults.default-amount` for players without one.
- Configurable vault size from 9 to 54 slots (multiple of 9).
- Autosave of open vaults plus saving on close, quit and server shutdown.
- YAML storage, one readable file per player (`data/<uuid>.yml`), with atomic writes.
- Anti-dupe protections: durable save on close, `Q`/`Ctrl+Q` blocked in the GUI, clicks outside
  the window blocked, off-hand swap guarded (1.9+), stale vault views closed after a reload.
- English and Spanish messages, selected with `language` in `config.yml`; any other language can
  be added with a `messages_<code>.yml` file.
- `/pv reload` for config and messages.
- `jpvaults.use` permission (default true) to enable or disable the command per group.

### Safety and robustness

- Vault data is read from disk when a vault is opened and dropped when the player quits, so a
  restart always starts from the real file.
- If a player file cannot be parsed, a timestamped `.bak` copy is kept before it can be
  overwritten; a single item that fails to deserialize does not block the rest of the vault.
- Asynchronous saves mark data clean only after the write succeeds and only if the data did not
  change meanwhile, so a failed write is retried instead of being silently dropped.
- Invalid configuration values (vault size, amount, sound volume/pitch) are clamped and reported
  in the console instead of breaking the plugin.
- Inventory titles are capped at 32 visible characters so a long title cannot crash 1.8–1.12
  clients.
- `/pv` without `jpvaults.use`, a missing `pv` command in `plugin.yml`, and a language file that
  does not exist are handled gracefully.

### Compatibility

- One jar for Minecraft 1.8 through the latest version, built against the Spigot 1.8.8 API with
  Java 8 bytecode.
- No NMS, no Vault, no database, no bStats, no update checker, no network access.
- Tested on Paper 1.8.8 (Java 8) and Paper 26.3 (Java 25); see [docs/VERIFICATION.md](docs/VERIFICATION.md).

