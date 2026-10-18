# Verification record — JPPlayerVaults 1.0.0

This file records what was actually executed for the 1.0.0 release, on which software, and with
which result. It is deliberately specific: claims on the SpigotMC page should be backed by one of
the tests below or by a manual check from [QA_CHECKLIST.md](QA_CHECKLIST.md).

- Date: 2026-10-18
- Build host: Windows, Temurin JDK 21 (Maven 3.9.16)
- Release artifact: `JPPlayerVaults-1.0.0.jar`, 36,008 bytes
  SHA-256 `7c361b64892a62ea03e8a3c7f9bc382bf44fc22febb0bb92e46025b5212c9a35`
- Continuous integration: GitHub Actions run #4 on `Nameeees/JPPlayerVaults` — **success**
  (the first runs failed because `bungeecord-chat:1.8-SNAPSHOT`, a transitive dependency of
  `spigot-api`, is no longer served by the Spigot or Sonatype repositories; it is now resolved from
  PaperMC and excluded from the compile classpath because the plugin does not use BungeeCord chat)

## 1. Build verification

| Check | Result |
| --- | --- |
| `mvn clean package` | BUILD SUCCESS |
| `plugin.yml` version substituted from `pom.xml` | `version: 1.0.0` |
| Jar contents | `config.yml`, `plugin.yml`, `messages_en.yml`, `messages_es.yml`, 12 plugin classes, no shaded libraries |
| Bytecode | `major version: 52` (Java 8) |
| Reproducible build | Two consecutive clean builds produced the same SHA-256 |
| Network/metrics code | No `bstats`, `mcstats`, `http://` or `https://` strings in any class |
| Packaging | No embedded Maven descriptor, so editing `pom.xml` cannot change the released jar |

> **Note on the artifact**: the 1.0.0 jar was rebuilt once while fixing the CI dependency, and the
> packaging was changed at the same time (the `META-INF/maven` descriptor was dropped). The
> `*.class` files and the YAML resources of the earlier and the final jar are byte-for-byte
> identical — verified by comparing both jars entry by entry — and every test in this document was
> re-run against the final jar.

## 2. Integration harness (real server)

`testing/harness` is a test-only plugin that loads next to JPPlayerVaults, drives the real classes
and the real `/pv` command, prints `CHECK OK` / `CHECK FAILED` per assertion and stops the server.
It is not part of the release jar.

### Paper 1.8.8 build 445 — Temurin 8u504

Result: **PASS — 20 checks, 0 failures.**

Covered on this version:

- Storage round-trip of all 54 slots, stack amount, non-ASCII display name and lore, last slot,
  empty slots.
- Removed items disappear after saving again; vault numbers stay independent.
- Data is marked clean only after a successful write and dirty again after a change.
- A corrupted YAML file is backed up as `<name>.<timestamp>.bak` instead of being overwritten.
- Player file is written with the player UUID.

The `/pv` command path cannot be driven on 1.8 through a Java dynamic proxy: the 1.8 API declares
`getHealth()` with incompatible return types in the `Player` hierarchy, so
`java.lang.reflect.Proxy` refuses to create the fake player. Those checks run on 26.3 instead, and
the same inventory flow was validated on 1.8.8 with a real client (section 3).

### Paper 26.3 build 205 — Temurin 25.0.4.1

Result: **PASS — 44 checks, 0 failures.**

Everything above, plus:

- `jpvaults.vaults.3` grants 3 vaults; `/pv 4` is rejected as locked.
- `/pv 1` opens a 54-slot inventory with title containing `#1` and a `VaultHolder`.
- `/pv abc` is rejected, `/pv help` lists the available vaults, `/pv reload` is denied without
  `jpvaults.reload`.
- Closing a vault writes `data/<uuid>.yml`; reopening returns the same items and item meta.
- A 60-character inventory title is shortened to 32 visible characters.
- Deleting `messages_en.yml` regenerates it; `language: es` switches the locked message to Spanish
  and switching back restores English.

Selected log lines (Paper 26.3):

```
[13:10:01 INFO]: [VaultStorageHarness] CHECK OK: all 54 slots round-trip
[13:10:01 INFO]: [VaultStorageHarness] CHECK OK: closed vault content is on disk
[13:10:01 INFO]: [VaultStorageHarness] CHECK OK: reopened vault keeps the items
[13:10:01 INFO]: [VaultStorageHarness] CHECK OK: long inventory title is shortened to 32 chars
[13:10:01 INFO]: [VaultStorageHarness] CHECK OK: Spanish locked message is used
[13:10:01 INFO]: [VaultStorageHarness] HARNESS RESULT checks=44 failures=0
[13:10:01 INFO]: [VaultStorageHarness] HARNESS PASS
```

## 3. Real client end-to-end (Paper 1.8.8)

A headless Minecraft 1.8.8 client (mineflayer 4.39.0, offline mode, operator) performed the real
player flow. This is the end-to-end proof that the GUI, the close event, the durable write and the
disk reload all work on the oldest supported version.

Transcript:

```
[BOT] spawned as TestBot
[BOT] window slots=90 title="\"§8» §a§lVault §f#1 §8«\""
[BOT] moving the diamond from window slot 81 into vault slot 0
[BOT] vault slot 0 now holds 5 diamond
[BOT] disconnected: the plugin must drop its cache and reload from disk
[BOT] after reconnecting, vault slot 0 = {"name":"diamond","count":5}
[BOT] PASS
```

The inventory title is decoded here; the headless client's console mangles non-ASCII characters when it prints the raw title.

Server side:

```
[14:18:04 INFO]: [JPPlayerVaults] Enabled: 54 slots per vault, up to 10 vaults per rank, language 'en'.
[14:18:18 INFO]: [JPPlayerVaults] Disabling JPPlayerVaults v1.0.0
[14:18:18 INFO]: [JPPlayerVaults] Disabled: open vaults were saved and written to disk.
```

What this proves:

- The vault opens as a 54-slot container (90 window slots: 54 vault + 36 player inventory).
- Clicking items into the vault works (the anti-dupe click filter does not block normal clicks).
- Closing the window and disconnecting saves the item.
- Reconnecting reloads the data from disk (the cache is dropped on quit), and the item is there.
- The plugin disables cleanly and flushes pending writes.

## 4. Not covered automatically

These require a real client and are listed as manual checks in [QA_CHECKLIST.md](QA_CHECKLIST.md):

- Shift-click, drag and hotbar-key item movement.
- `Q` / `Ctrl+Q` and outside-click blocking, `F` guard feedback (sound).
- Permission plugin integration (LuckPerms) and rank changes at runtime.
- Two or more players with vaults open at the same time.
- Rendering of non-ASCII characters in the inventory title on a 1.8 client (the bot receives the
  title text, but rendering is client-side).
- `/reload confirm` and autosave under load.

## 5. How to reproduce

```bash
mvn clean package
```

Then, in a test server folder (Java version required by that Minecraft version):

```
plugins/JPPlayerVaults-1.0.0.jar
plugins/VaultStorageHarness.jar        # built from testing/harness
eula=true
online-mode=false
```

Start the server; the harness runs on enable and stops the server after printing the result. See
[testing/README.md](../testing/README.md) for build instructions and the bot test.

## 6. Version matrix summary

| Minecraft | Server build | Java | Harness | Real client |
| --- | --- | --- | --- | --- |
| 1.8.8 | Paper 445 | Temurin 8u504 | PASS (20 checks) | PASS (mineflayer) |
| 26.3 | Paper 205 | Temurin 25.0.4.1 | PASS (44 checks) | not supported by mineflayer |

Minecraft 26.1 and newer require Java 25 to run the server itself; the plugin jar is still Java 8
bytecode, so 1.8 servers on Java 8 load exactly the same file.
