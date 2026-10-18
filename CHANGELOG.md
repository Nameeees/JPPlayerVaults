# Changelog

All notable changes to JPPlayerVaults are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/) and the project uses
[Semantic Versioning](https://semver.org/).

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

