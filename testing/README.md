# Testing tools

These files are **not** part of the release jar. They exist so the release claims can be reproduced
on a real server instead of being taken on faith.

- `harness/` — a tiny Bukkit plugin that drives the real JPPlayerVaults classes and `/pv` command
  inside a running server, prints one `CHECK OK` / `CHECK FAILED` line per assertion and stops the
  server.
- `bot/` — a headless Minecraft 1.8.8 client (mineflayer) that opens `/pv 1`, moves an item into the
  vault, disconnects, reconnects and verifies the item came back from disk.

Results for 1.0.0 are recorded in [../docs/VERIFICATION.md](../docs/VERIFICATION.md).

## 1. Build the harness

The harness is compiled against the same Spigot 1.8.8 API as the plugin, so it also works on modern
servers (it only touches API present in every version).

```bash
# from the repository root
mkdir -p testing/harness/classes
javac --release 8 -encoding UTF-8 -cp "$HOME/.m2/repository/org/spigotmc/spigot-api/1.8.8-R0.1-SNAPSHOT/spigot-api-1.8.8-R0.1-SNAPSHOT.jar" \
  -d testing/harness/classes testing/harness/src/jpvtest/StorageHarness.java
cp testing/harness/plugin.yml testing/harness/classes/
jar cf testing/harness/VaultStorageHarness.jar -C testing/harness/classes .
```

On Windows, run the same steps with `javac.exe` / `jar.exe` from any JDK 17+ and the API jar from
`%USERPROFILE%\.m2\...`.

## 2. Run it

1. Create a test server folder for the Minecraft version you want to verify, install the server jar
   (Paper is fine) and accept the EULA.
2. Set `online-mode=false` in `server.properties` so no account is needed.
3. Put `JPPlayerVaults-<version>.jar` and `VaultStorageHarness.jar` in `plugins/`.
4. Start the server with the Java version that Minecraft version needs (Java 8 for 1.8, Java 25 for
   26.x).

The harness runs on enable, prints the result and stops the server after two seconds. Look for:

```
[VaultStorageHarness] CHECK OK: ...
[VaultStorageHarness] HARNESS RESULT checks=N failures=0
[VaultStorageHarness] HARNESS PASS
```

Remove `VaultStorageHarness.jar` for the bot test: the harness stops the server too early.

## 3. Run the real-client test (1.8.8)

```bash
cd testing/bot
npm install mineflayer
# The bot must be an operator to use /give and /stop.
# Offline UUID for the name "TestBot": 30fecbe1-2271-3418-8553-d3ded0e95f56
# (UUID.nameUUIDFromBytes("OfflinePlayer:TestBot")). Add it to the server's ops.json:
# [{"uuid":"30fecbe1-2271-3418-8553-d3ded0e95f56","name":"TestBot","level":4,"bypassesPlayerLimit":false}]
MC_PORT=25570 node vault-test.js
```

Expected output:

```
[BOT] spawned as TestBot
[BOT] window slots=90 title="...Vault ...#1..."
[BOT] vault slot 0 now holds 5 diamond
[BOT] after reconnecting, vault slot 0 = {"name":"diamond","count":5}
[BOT] PASS
```

The reconnect is the important part: quitting drops the plugin's in-memory cache, so the item can
only reappear if it was really written to `plugins/JPPlayerVaults/data/<uuid>.yml`.

## 4. Notes

- The harness stops the server itself; do not point it at a server you care about.
- The bot only supports versions mineflayer supports (up to 26.1 at the time of writing). Modern
  versions are covered by the harness instead.
- `StorageHarness.java` uses reflection for the plugin's internal classes on purpose: it keeps the
  test independent from the plugin's package layout.
