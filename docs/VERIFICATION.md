# Verification record — JPPlayerVaults 1.1.0

This file records what was actually executed for the 1.1.0 release, on which software, and with
which result. It is deliberately specific: claims on the SpigotMC page should be backed by one of
the tests below or by a manual check from [QA_CHECKLIST.md](QA_CHECKLIST.md).

- Date: 2026-10-18
- Build host: Windows, Temurin JDK 21 (Maven 3.9.16)
- Release artifact: `JPPlayerVaults-1.1.0.jar`, 68,758 bytes
  SHA-256 `3b6d8f1dc7ad1bd6aa3ca5f4b2589cbb1b0918a58e37e72250e1e6954d2755cd`
- Continuous integration: GitHub Actions on `Nameeees/JPPlayerVaults` — **success**

## 1. Build verification

| Check | Result |
| --- | --- |
| `mvn clean package` | BUILD SUCCESS |
| `plugin.yml` version substituted from `pom.xml` | `version: 1.1.0` |
| Jar contents | `config.yml`, `plugin.yml`, `messages_en.yml`, `messages_es.yml`, 17 plugin classes, no shaded libraries |
| Bytecode | `major version: 52` (Java 8) |
| Reproducible build | Two consecutive clean builds produce the same SHA-256 |
| Network/metrics code | No `bstats`, `mcstats`, `http://` or `https://` strings in any class |
| Packaging | No embedded Maven descriptor, so editing `pom.xml` cannot change the released jar |

## 2. Integration harness (real server)

`testing/harness` is a test-only plugin that loads next to JPPlayerVaults, drives the real classes
and commands inside a running server, prints `CHECK OK` / `CHECK FAILED` per assertion and stops the
server. It is not part of the release jar.

What it covers:

- **YAML backend**: 54-slot round-trip with stack amounts, non-ASCII display name and lore, last
  slot, empty slots, independence between vault numbers, `deleteVault`, `knownOwners`, and a
  corrupted file (backed up as `.bak` and refused instead of loading empty).
- **SQL backend**: the same contract against an in-memory H2 database in MySQL mode, with the driver
  passed as an object exactly like the plugin does with the driver that ships with the server. The
  schema created is the production one (`MEDIUMBLOB`, portable `UPDATE`-then-`INSERT` upsert,
  transactional delete + insert), so the statements MySQL will run are the ones under test.
- **Command layer**: `/pv`, `/pv help`, `/pv 4` locked, `/pv abc`, `/pv reload`, and every
  `/pv admin` path (info, info with a UUID, delete with and without `confirm`, open, migrate,
  unknown subcommand, permission denied), including the messages each one produces.
- **Admin views**: opening another player's vault tags the holder as an admin view, a second viewer
  is refused while it is open, deleting an open vault is refused, and after closing, the stored item
  count is correct and the delete empties the vault.

### Paper 1.8.8 build 445 — Temurin 8u504

Result: **PASS — 47 checks, 0 failures.**

The `Player` interface of the 1.8 API declares `getHealth()` with incompatible return types, so
`java.lang.reflect.Proxy` cannot create a fake player and the player-driven checks are skipped on
this version; everything else (both backends and all command paths through a `CommandSender`) runs.

### Paper 26.3 build 205 — Temurin 25.0.4.1

Result: **PASS — 69 checks, 0 failures.** Same as above plus the player-driven checks: inventory
title, admin view flag, second viewer refused and delete of an open vault refused.

Selected log lines (Paper 26.3):

```
[15:45:43 INFO]: [JPPlayerVaults] SQL storage ready: jdbc:h2:mem:jpvaults_harness;MODE=MySQL;DB_CLOSE_DELAY=-1 (tablas 'test_players' y 'test_vaults') using MEDIUMBLOB.
[15:45:43 INFO]: [VaultStorageHarness] CHECK OK: SQL: all 54 slots round-trip
[15:45:43 INFO]: [VaultStorageHarness] CHECK OK: SQL: deleteVault reports success
[15:45:43 INFO]: [VaultStorageHarness] CHECK OK: /pv admin open <uuid> opens the vault of that player
[15:45:43 INFO]: [VaultStorageHarness] CHECK OK: a second viewer is refused while the vault is open
[15:45:43 INFO]: [VaultStorageHarness] CHECK OK: admin delete removes the vault
[15:45:43 INFO]: [VaultStorageHarness] HARNESS RESULT checks=69 failures=0
[15:45:43 INFO]: [VaultStorageHarness] HARNESS PASS
```

## 3. Real client end-to-end (Paper 1.8.8)

A headless Minecraft 1.8.8 client (mineflayer 4.39.0, offline mode, operator) performs the real
player flow: open, put an item in, close, disconnect (which drops the plugin's cache) and reconnect.

```
[BOT] spawned as TestBot
[BOT] window slots=90 title="\"§8» §a§lVault §f#1 §8«\""
[BOT] moving the diamond from window slot 81 into vault slot 0
[BOT] vault slot 0 now holds 5 diamond
[BOT] disconnected: the plugin must drop its cache and reload from disk
[BOT] after reconnecting, vault slot 0 = {"name":"diamond","count":5}
[BOT] PASS
```

Server side:

```
[15:46:20 INFO]: [JPPlayerVaults] Enabled: 54 slots per vault, up to 10 vaults per rank, language 'en', storage: YAML .../plugins/JPPlayerVaults/data.
[15:46:34 INFO]: [JPPlayerVaults] Disabled: open vaults were saved and written to disk.
```

The inventory title is decoded here; the headless client's console mangles non-ASCII characters when
it prints the raw title.

## 4. Not covered automatically

- **A real MySQL/MariaDB server.** The SQL layer is exercised end to end against H2 in MySQL mode
  (same DDL, same statements, same pooling and codec), but the final acceptance on a live database —
  credentials, the driver shipped by the server, network hiccups — is a manual check: see the SQL
  rows in [QA_CHECKLIST.md](QA_CHECKLIST.md).
- Shift-click, drag, hotbar keys and the `Q` / `F` guards, which need a real client per version.
- LuckPerms integration and rank changes at runtime.
- Two or more players with vaults open at the same time under load.
- Migrating a large YAML folder (thousands of players) on slow hardware.

## 5. How to reproduce

```bash
mvn clean package
```

Then, in a test server folder (with the Java version that Minecraft release needs):

```
plugins/JPPlayerVaults-1.1.0.jar
plugins/VaultStorageHarness.jar        # built from testing/harness, with the H2 jar shaded in
eula=true
online-mode=false
```

The harness runs on enable, prints the result and stops the server. Build instructions, including the
H2 jar for the SQL checks, are in [testing/README.md](../testing/README.md).

## 6. Version matrix

| Minecraft | Server build | Java | Harness | Real client |
| --- | --- | --- | --- | --- |
| 1.8.8 | Paper 445 | Temurin 8u504 | PASS (47 checks) | PASS (mineflayer) |
| 26.3 | Paper 205 | Temurin 25.0.4.1 | PASS (69 checks) | not supported by mineflayer |

Minecraft 26.1 and newer require Java 25 to run the server itself; the plugin jar is still Java 8
bytecode, so 1.8 servers on Java 8 load exactly the same file.
