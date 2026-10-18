# Verification record — JPPlayerVaults 1.2.0

This file records what was actually executed for the 1.2.0 release, on which software, and with
which result. It is deliberately specific: claims on the SpigotMC page should be backed by one of
the tests below or by a manual check from [QA_CHECKLIST.md](QA_CHECKLIST.md).

- Date: 2026-10-18
- Build host: Windows, Temurin JDK 21 (Maven 3.9.16)
- Release artifact: `JPPlayerVaults-1.2.0.jar`, 91,975 bytes (89.8 KB)
  SHA-256 `949898703fbb4edd17e9cd4de9a12b2cd4ab957c77a6fc325528fa8fc7bf08c1`
- Continuous integration: GitHub Actions on `Nameeees/JPPlayerVaults`
- Test servers: Paper 1.8.8 build 445 (Temurin 8u504) and Paper 26.3 build 205 (Temurin 25.0.4.1)

## 1. Build verification

| Check | Result |
| --- | --- |
| `mvn clean package` | BUILD SUCCESS |
| `plugin.yml` version substituted from `pom.xml` | `version: 1.2.0` |
| Bytecode | `major version: 52` (Java 8) |
| Reproducible build | Two consecutive clean builds produce the same SHA-256 |
| No shaded libraries | Neither Adventure, nor MiniMessage, nor a JDBC driver is inside the jar; everything modern is used through reflection when the server provides it |
| `plugin.yml` `libraries:` | Present but commented out on purpose: enabling it would make every server start need internet |

## 2. Integration harness (real server)

`testing/harness` is a test-only plugin that loads next to JPPlayerVaults, drives the real classes
and commands inside a running server, prints `CHECK OK` / `CHECK FAILED` per assertion and stops the
server. It is not part of the release jar.

New in 1.2.0, on top of the 1.1.0 coverage (both backends, every command path, admin views):

- **Item format codec**: a Bukkit-format round-trip, an NBT round-trip (Paper 1.20.5+), the header
  byte of each format, and — the one that matters — a **blob written without a header, as 1.0.0 and
  1.1.0 wrote them, still loads**.
- **Text utilities**: MiniMessage tag detection, legacy code detection, tag stripping for servers
  without Adventure, and a title truncation that does not eat colour codes.
- **Adventure detection**: which of `Component`, `MiniMessage` and `LegacyComponentSerializer` the
  plugin can see, logged as one line.
- **MiniMessage end to end**: with `messages-format: auto`, a message written with tags becomes a
  Component and a message written with `&` does not (it keeps its colours); with
  `messages-format: minimessage`, the tagged title becomes a Component that renders as plain text
  without leaking tags, and the config is restored afterwards so the legacy path is verified to come
  back.
- **Vault picker**: `/pv` with no arguments opens the picker, it lists one icon per available vault,
  and picking an icon opens that vault.
- **Schedulers**: the timer abstraction returns a cancellable handle, the timer actually fires, and
  cancelling it stops it. Folia is reported as absent on Paper.

### Paper 1.8.8 build 445 — Temurin 8u504

Result: **PASS — 64 checks, 0 failures.**

The `Player` interface of the 1.8 API declares `getHealth()` with incompatible return types, so
`java.lang.reflect.Proxy` cannot create a fake player: the player-driven checks (title, picker,
admin views) are skipped here and run on the modern server instead. Everything else runs, including
the codec checks with the NBT round-trip reported as skipped (the API does not exist on 1.8).

```
[17:09:43 INFO]: [JPPlayerVaults] Enabled: 54 slots per vault, up to 10 vaults per rank, language 'en', text: auto (legacy only), storage: YAML .../plugins/JPPlayerVaults/data.
[17:09:44 INFO]: [VaultStorageHarness] PROBE: Adventure classes seen by the plugin: [Component missing] [MiniMessage missing] [LegacyComponentSerializer missing]
[17:09:44 INFO]: [VaultStorageHarness] Codec: NBT item API NOT available (old server)
[17:09:44 INFO]: [VaultStorageHarness] CHECK OK: Codec: data saved by 1.1.0 still loads
[17:09:44 INFO]: [VaultStorageHarness] CHECK SKIP: the NBT round-trip needs Paper 1.20.5+
[17:09:44 INFO]: [VaultStorageHarness] Text system: legacy only
[17:09:49 INFO]: [VaultStorageHarness] HARNESS RESULT checks=64 failures=0
[17:09:49 INFO]: [VaultStorageHarness] HARNESS PASS
```

This run also used the **`config.yml` left behind by 1.1.0** (without `messages-format`,
`vaults.picker`, `item-format` or `yaml-format`), which is the upgrade path a real server follows:
the plugin starts, applies the documented defaults and logs no warning.

### Paper 26.3 build 205 — Temurin 25.0.4.1

Result: **PASS — 102 checks, 0 failures.**

Server side:

```
[17:09:35 INFO]: [JPPlayerVaults] SQL storage ready: jdbc:h2:mem:jpvaults_harness;MODE=MySQL;DB_CLOSE_DELAY=-1 (tablas 'test_players' y 'test_vaults') using MEDIUMBLOB, items as nbt.
[17:09:35 INFO]: [JPPlayerVaults] Enabled: 54 slots per vault, up to 10 vaults per rank, language 'en', text: auto (Adventure + MiniMessage), storage: YAML .../plugins/JPPlayerVaults/data.
```

Selected harness lines:

```
[17:09:36 INFO]: [VaultStorageHarness] PROBE: Adventure classes seen by the plugin: Component MiniMessage LegacyComponentSerializer
[17:09:36 INFO]: [VaultStorageHarness] Codec: NBT item API available
[17:09:36 INFO]: [VaultStorageHarness] CHECK OK: Codec: data saved by 1.1.0 still loads
[17:09:36 INFO]: [VaultStorageHarness] CHECK OK: Codec: nbt round-trip keeps all 54 slots
[17:09:36 INFO]: [VaultStorageHarness] CHECK OK: /pv without arguments opens the picker
[17:09:36 INFO]: [VaultStorageHarness] CHECK OK: picking an icon opens that vault
[17:09:36 INFO]: [VaultStorageHarness] Text system: Adventure + MiniMessage
[17:09:36 INFO]: [VaultStorageHarness] CHECK OK: MiniMessage: in auto mode a tagged message becomes a Component
[17:09:36 INFO]: [VaultStorageHarness] CHECK OK: MiniMessage: in auto mode a legacy message stays as text
[17:09:36 INFO]: [VaultStorageHarness] CHECK OK: MiniMessage: in auto mode the legacy colours are still applied
[17:09:36 INFO]: [VaultStorageHarness] CHECK OK: MiniMessage: the Component renders as plain text
[17:09:36 INFO]: [VaultStorageHarness] CHECK OK: MiniMessage: after restoring the config the legacy path is back
[17:09:37 INFO]: [VaultStorageHarness] CHECK OK: Schedulers: a global timer fires
[17:09:37 INFO]: [VaultStorageHarness] CHECK OK: Schedulers: cancelling stops the timer
[17:09:37 INFO]: [VaultStorageHarness] HARNESS RESULT checks=102 failures=0
[17:09:37 INFO]: [VaultStorageHarness] HARNESS PASS
```

Two things this run proves that are easy to get wrong, and did fail first:

- Adventure 5 changed the API the plugin reflects on: `LegacyComponentSerializer.legacySection()`
  returns the serializer itself (not a `Builder`), and `MiniMessage` has no single-argument
  `deserialize(String)` — it is varargs `deserialize(String, TagResolver...)`. Both shapes are
  handled now, and the harness fails loudly if a future server changes them again.
- The NBT format returns empty slots as empty items instead of `null`, so the codec normalises them;
  that is what makes "empty slot stays empty" pass for both backends.

## 3. Real client end-to-end (Paper 1.8.8)

**Not re-run for 1.2.0**: the headless client (mineflayer) is not installed in this environment. It
was run for 1.1.0 and passed:

```
[BOT] spawned as TestBot
[BOT] window slots=90 title="\"§8» §a§lVault §f#1 §8«\""
[BOT] moving the diamond from window slot 81 into vault slot 0
[BOT] vault slot 0 now holds 5 diamond
[BOT] disconnected: the plugin must drop its cache and reload from disk
[BOT] after reconnecting, vault slot 0 = {"name":"diamond","count":5}
[BOT] PASS
```

What covers that path for 1.2.0:

- The inventory creation moved into one helper (`VaultManager#createInventory`). On servers without
  Adventure the helper calls exactly what the plugin called before (`Bukkit.createInventory` with
  the legacy title truncated to `vaults.title-max-length`, 32 by default), and the harness verifies
  on 26.3 that the title still shows the vault number.
- The trajectory from the 1.1.0 run (open, put an item in, disconnect, reconnect and read it back
  from disk) is untouched by this release: the storage codec keeps writing what it wrote before
  unless `item-format: nbt` applies, and the harness re-verifies both codecs and the read-back.
- Re-running the client test is a checklist item for the next release:
  [QA_CHECKLIST.md](QA_CHECKLIST.md).

## 4. Not covered automatically

- **A real MySQL/MariaDB server.** The SQL layer is exercised end to end against H2 in MySQL mode
  (same DDL, same statements, same pooling and codec), but the final acceptance on a live database —
  credentials, the driver shipped by the server or dropped in `lib/`, network hiccups — is a manual
  check: see the SQL rows in [QA_CHECKLIST.md](QA_CHECKLIST.md).
- **A real Folia server.** The region-aware schedulers are implemented and the abstraction is
  verified, but Folia itself is not tested here, so the plugin does not declare
  `folia-supported: true`.
- **A real MiniMessage look.** The harness proves a tagged message becomes a Component with the right
  plain text; how a gradient or a hover renders belongs to a client, and to the manual checklist.
- Shift-click, drag, hotbar keys and the `Q` / `F` guards, which need a real client per version.
- LuckPerms integration, rank changes at runtime, and two or more players with vaults open under load.
- Migrating a large YAML folder (thousands of players) on slow hardware.

## 5. How to reproduce

```bash
mvn clean package
```

Then, in a test server folder (with the Java version that Minecraft release needs):

```
plugins/JPPlayerVaults-1.2.0.jar
plugins/VaultStorageHarness.jar        # built from testing/harness, with the H2 jar shaded in
eula=true
online-mode=false
```

The harness runs on enable, prints the result and stops the server. Build instructions, including the
H2 jar for the SQL checks, are in [testing/README.md](../testing/README.md).

## 6. Version matrix

| Minecraft | Server build | Java | Harness | Modern features in use |
| --- | --- | --- | --- | --- |
| 1.8.8 | Paper 445 | Temurin 8u504 | PASS (64 checks) | none: legacy colours, Bukkit item format, Bukkit scheduler |
| 26.3 | Paper 205 | Temurin 25.0.4.1 | PASS (102 checks) | Adventure + MiniMessage, NBT item storage, global/entity schedulers, cached offline lookup |

Minecraft 26.1 and newer require Java 25 to run the server itself; the plugin jar is still Java 8
bytecode, so 1.8 servers on Java 8 load exactly the same file.
