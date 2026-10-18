# Release QA checklist

Version under test: **1.1.0** · Date: **2026-10-18**

This checklist is the minimum gate before uploading a jar to SpigotMC. The automated part runs
inside a real server; the manual part needs a Minecraft client and is the one that catches GUI
regressions.

## 1. Build gate

- [x] `mvn clean package` finishes with `BUILD SUCCESS`.
- [x] The jar is `target/JPPlayerVaults-1.1.0.jar` and its `plugin.yml` contains `version: 1.1.0`.
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

- Both backends (YAML and SQL over JDBC, the latter against H2 in MySQL mode with the production
  schema and statements): 54-slot round-trip, stack amounts, non-ASCII display name and lore, last
  slot, empty slots, independence between vault numbers, `deleteVault` and `knownOwners`.
- Data marked clean only after a successful write; dirty again after a change.
- A corrupt YAML file is backed up and refused instead of loaded empty.
- `/pv`, `/pv 1`, `/pv 4` (locked), `/pv abc` (invalid), `/pv help`, `/pv reload` (denied).
- Rank permissions decide the vault count (3 vaults from `jpvaults.vaults.3`).
- Opening a vault creates a 54-slot inventory with the configured title and a `VaultHolder`.
- Closing writes to `data/<uuid>.yml`; reopening returns the same items and meta.
- Long `vault-title` is shortened to 32 visible characters.
- Deleting `messages_en.yml` regenerates it; switching `language` to `es` changes the messages and
  switching back restores English.
- Every `/pv admin` path: info (plugin and player), delete with and without `confirm`, open,
  migrate, unknown subcommand and permission denied.
- Admin views: the holder is tagged as an admin view, a second viewer is refused while it is open,
  deleting an open vault is refused, and after closing, the delete empties the vault.

Results:

| Server | Java | Result |
| --- | --- | --- |
| Paper 1.8.8 build 445 | Temurin 8u504 | **PASS** — 47 checks, 0 failures (player-driven checks skipped: this API cannot proxy `Player`; the real-client test below covers the inventory flow) |
| Paper 26.3 build 205 | Temurin 25.0.4 | **PASS** — 69 checks, 0 failures |

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

### Database backend (needs a real MySQL/MariaDB — the automated test uses H2)

- [ ] Set `storage.type: mysql` with valid credentials, restart: the console logs
      `SQL storage ready: jdbc:mysql://… using MEDIUMBLOB` and the two tables exist.
- [ ] Open `/pv 1`, put items in, close, restart the server: the items are still there.
- [ ] Stop MySQL while the server is running, open a vault: the player gets the storage error message
      and the console logs the failure (no empty vault is shown).
- [ ] Start the server with MySQL down: the console warns and the plugin runs on YAML.
- [ ] With MySQL down and YAML active, play a bit, then start MySQL, restart and run
      `/pv admin migrate`: the data written while it was down ends up in the database.
- [ ] `/pv admin migrate` with 200+ YAML files: progress every 25 players, no lag spikes, summary at
      the end, files left in place.
- [ ] `/pv admin migrate force` overwrites rows that already exist.
- [ ] Delete a vault from the database by hand and reopen it in game: it comes back empty (no error).

### Administrator commands

- [ ] `/pv admin info` shows the backend, the number of players and the settings.
- [ ] `/pv admin info <player>` shows UUID, state, vaults with items and stored items.
- [ ] `/pv admin open <player> 1` opens that player's vault with their name in the title; moving
      items saves to *their* data, not yours.
- [ ] While that vault is open, the owner cannot open it (and the other way round): the message names
      who has it open.
- [ ] `/pv admin delete <player> 1` without `confirm` only prints the usage line.
- [ ] `/pv admin delete <player> 1 confirm` empties that vault and leaves the others untouched.
- [ ] Deleting a vault that is open is refused.
- [ ] A player without `jpvaults.admin` cannot run any `/pv admin` subcommand.
- [ ] Everything above appears in the console log with the administrator's name.

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
- [ ] With the database backend, opening a vault on a remote database does not stall the server
      thread (the read happens on open; measure with a slow connection if possible).
- [ ] `/pv admin migrate` on a large folder keeps the tick rate stable (it processes 5 players per
      tick).

## 6. Sign-off

- [ ] `CHANGELOG.md` describes the release.
- [ ] `README.md` and the SpigotMC description match the actual behaviour.
- [ ] `docs/SPIGOTMC_PAGE.md` checklist completed (screenshots, links, version, free).
- [ ] SHA-256 of the uploaded jar recorded in the release notes.

## Vault picker (1.2.0)

- [ ] `/pv` opens the menu; the number of icons matches the vaults the rank grants.
- [ ] The icon lore shows how many items each vault holds, and it updates after using one.
- [ ] Clicking an icon opens that vault; `/pv 3` still opens vault 3 directly.
- [ ] `vaults.picker: false` + `/pv reload` brings back the text help.
- [ ] Shift-clicking inside the picker does not move anything (all clicks are cancelled).

## Item formats (1.2.0)

- [ ] With `storage.item-format: bukkit` on Paper, save a vault and reopen it: items are intact.
- [ ] Switch to `item-format: nbt`, restart, open the same vault: it still loads (previous format).
- [ ] Save again, restart, open: still intact (NBT written by this server).
- [ ] `storage.yaml-format: nbt` writes `vaults.<n>.nbt` in the player file and the vault still opens.
- [ ] Restore both settings to `auto` and `readable` and confirm the vaults still open.

## MiniMessage colours (1.2.0)

- [ ] On Paper, set `messages-format: minimessage` and give a message a gradient: it renders as a
      gradient in game (no tags visible).
- [ ] With `messages-format: auto`, a `&` message still shows its legacy colours.
- [ ] A `<click:run_command:'/pv 1'>` in the help is clickable and runs the command.
- [ ] On a server without Adventure (Spigot/1.8), the same message shows its text without tags and
      the console warns once.
- [ ] `/pv admin info` reports `text: auto (Adventure + MiniMessage)` on Paper and `legacy only` on
      a server without Adventure.

## Real client re-run (pending from 1.1.0)

- [ ] `testing/bot`: open `/pv 1`, move an item in, disconnect, reconnect and confirm the item came
      back from disk (needs `npm install mineflayer`).
