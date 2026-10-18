# Release QA checklist

Version under test: **1.0.0** · Date: **2026-10-18**

This checklist is the minimum gate before uploading a jar to SpigotMC. The automated part runs
inside a real server; the manual part needs a Minecraft client and is the one that catches GUI
regressions.

## 1. Build gate

- [x] `mvn clean package` finishes with `BUILD SUCCESS`.
- [x] The jar is `target/JPPlayerVaults-1.0.0.jar` and its `plugin.yml` contains `version: 1.0.0`.
- [x] The jar contains `config.yml`, `plugin.yml`, `messages_en.yml`, `messages_es.yml`.
- [x] Every class is Java 8 bytecode (`major version: 52`), so 1.8 servers on Java 8 can load it.
- [x] Two clean builds produce the same SHA-256 (reproducible build).
- [x] No `messages.yml` from older versions is left in the jar.
- [x] The jar is not obfuscated and contains no shaded dependencies.

## 2. Automated integration test

The `testing/harness` plugin (not shipped) loads next to JPPlayerVaults on a real server, drives the
real classes and commands, prints one `CHECK OK`/`CHECK FAILED` line per assertion and then stops
the server. Run it on the oldest and the newest supported Minecraft version.

Covered automatically:

- Storage round-trip of 54 slots, stack amounts, display name and lore with non-ASCII text, last
  slot, empty slots.
- Removing items and saving again actually removes them.
- Independent vault numbers.
- Data marked clean only after a successful write; dirty again after a change.
- Corrupt YAML file is backed up instead of overwritten.
- `/pv`, `/pv 1`, `/pv 4` (locked), `/pv abc` (invalid), `/pv help`, `/pv reload` (denied).
- Rank permissions decide the vault count (3 vaults from `jpvaults.vaults.3`).
- Opening a vault creates a 54-slot inventory with the configured title and a `VaultHolder`.
- Closing writes to `data/<uuid>.yml`; reopening returns the same items and meta.
- Long `vault-title` is shortened to 32 visible characters.
- Deleting `messages_en.yml` regenerates it; switching `language` to `es` changes the messages and
  switching back restores English.

Results:

| Server | Java | Result |
| --- | --- | --- |
| Paper 1.8.8 build 445 | Temurin 8u504 | **PASS** — 20 checks, 0 failures (command checks skipped: this API cannot proxy `Player`; the real-client test below covers the inventory flow) |
| Paper 26.3 build 205 | Temurin 25.0.4 | **PASS** — 44 checks, 0 failures |

Logs and commands are recorded in [VERIFICATION.md](VERIFICATION.md).

## 3. Real client end-to-end

A headless Minecraft 1.8.8 client (mineflayer, offline mode, operator) joins the test server and
performs the real player flow. Run it on every release that touches the GUI, the close event or the
storage layer.

- [x] `/pv 1` opens a 54-slot container (90 window slots).
- [x] The inventory title contains `#1`.
- [x] An item can be clicked from the player inventory into the vault.
- [x] Disconnecting with the vault closed saves the item.
- [x] Reconnecting (cache dropped) reloads the item from disk.
- [x] The server disables cleanly and flushes pending writes.

Result on Paper 1.8.8: **PASS**. Transcript and log excerpt in
[VERIFICATION.md](VERIFICATION.md).

## 4. Manual in-game matrix

Needs a real client on a test server. Tick each row on every release.

### Basics

- [ ] `/pv 1` opens a 6-row vault titled “Vault #1”.
- [ ] Put items in random slots, close with `Escape`, reopen: the items are there.
- [ ] Restart the server, reopen: the items are there.
- [ ] Shift-click items from the player inventory into the vault and back.
- [ ] Drag items across slots; double-click to collect a stack.
- [ ] Move items with the number keys (hotbar swap).

### Anti-dupe and item safety

- [ ] Press `Q` on a vault slot: nothing is dropped, a deny sound plays.
- [ ] Press `Ctrl+Q` on a vault slot: nothing is dropped.
- [ ] Hold an item on the cursor and press `Q`: nothing is dropped.
- [ ] Hold an item on the cursor and click outside the window: nothing is dropped.
- [ ] Hold an item on the cursor and press `F` (1.9+): the swap is blocked.
- [ ] Close the vault with an item on the cursor and a full inventory: the item is not deleted.
- [ ] `/pv 1` then `/pv 2` without closing: both vaults keep their contents.
- [ ] `/reload confirm` with a vault open: no dupe, reopening shows the saved contents.
- [ ] Quit with a vault open, reconnect, reopen: contents are saved.
- [ ] Stop the server with a vault open, start it again: contents are saved.
- [ ] Two players open different vaults at the same time; each sees their own items.

### Commands and permissions

- [ ] Player with `jpvaults.vaults.1` cannot open `/pv 2` (locked message).
- [ ] Player with `jpvaults.vaults.6` can open `/pv 6` but not `/pv 7`.
- [ ] Player without `jpvaults.vaults.*` gets `vaults.default-amount` vaults.
- [ ] Player denied `jpvaults.use` cannot use `/pv`.
- [ ] Player without `jpvaults.reload` gets the no-permission message.
- [ ] Console running `pv` gets the player-only message.
- [ ] Tab completion suggests numbers, `help` and `reload` (for admins).

### Configuration and messages

- [ ] Change `vaults.size` to 27, `/pv reload`, open a vault: 3 rows; items in hidden rows are kept
      and reappear when the size goes back to 54.
- [ ] Change `vaults.default-amount` to 0: a player without rank permissions gets the no-vault
      message.
- [ ] Set `language: es`, `/pv reload`: chat, help and title are Spanish.
- [ ] Empty `command.opened` in the messages file, `/pv reload`: opening a vault does not chat.
- [ ] Set a title longer than 32 characters: it is shortened, clients do not crash.
- [ ] Invalid `vaults.size: 33` in config: the console warns and 27 slots are used.
- [ ] Set `storage.autosave-seconds: 0`: no autosave, but close/quit/shutdown still save.

### Compatibility

- [ ] 1.8 client on a 1.8 server: vault opens, title renders, no client crash.
- [ ] 1.8 client on a modern server (ViaVersion, if used): title renders.
- [ ] Latest client on the latest server: everything works.
- [ ] `Q`/`F` protections behave on both 1.8 and modern clients.

## 5. Performance sanity check

- [ ] With 20+ players and a vault open each, `/tps` stays at 20 during autosave.
- [ ] Opening a vault on a data folder with 1,000 player files is instant (files load per player).
- [ ] Closing a vault does not create a noticeable lag spike (the write is a few milliseconds; the
      bounded wait is 5 seconds).

## 6. Sign-off

- [ ] `CHANGELOG.md` describes the release.
- [ ] `README.md` and the SpigotMC description match the actual behaviour.
- [ ] `docs/SPIGOTMC_PAGE.md` checklist completed (screenshots, links, version, free).
- [ ] SHA-256 of the uploaded jar recorded in the release notes.
