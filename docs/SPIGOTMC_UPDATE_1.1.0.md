# SpigotMC update post — 1.1.0

Ready to paste into **Resource → Post an update** on the resource page.

| Field | Value |
| --- | --- |
| Version | `1.1.0` |
| Update Title | `1.1.0 — MySQL storage and /pv admin commands` |

## Update Message (BBCode — paste as-is)

```bbcode
[SIZE=5][B]JPPlayerVaults 1.1.0[/B][/SIZE]
This update adds an optional database backend and the administrator tools that were missing: open another player's vault, inspect a player, wipe a single vault and migrate your stored data.

[HR][/HR]

[SIZE=4][B]New: database storage (optional)[/B][/SIZE]
[LIST]
[*]Set [B]storage.type: mysql[/B] and the vaults live in a database instead of files. MySQL, MariaDB or anything reachable over JDBC.
[*]The two tables are created automatically ([B]<prefix>players[/B] and [B]<prefix>vaults[/B]) and each vault is written inside a transaction, so a failure halfway cannot leave it half saved.
[*]No library is bundled: the plugin uses the driver that already ships with Spigot/Paper, and falls back to MySQL/MariaDB drivers automatically.
[*]If the database cannot be reached at startup, the plugin logs the error and [B]keeps running on YAML[/B] instead of leaving the server without vaults.
[*][B]YAML stays the default[/B]: nothing changes for existing installations.
[/LIST]

[SIZE=4][B]New: /pv admin commands[/B][/SIZE] (permission [B]jpvaults.admin[/B], OP by default)
[LIST]
[*][B]/pv admin open <player|uuid> <number>[/B] — opens that player's vault, online or offline, by name or UUID. Changes are saved to the owner's data.
[*][B]/pv admin info [player][/B] — backend in use and where it points, or a report for one player: UUID, state, vaults with items, stored items, rank and who has a vault open right now.
[*][B]/pv admin delete <player|uuid> <number> confirm[/B] — wipes one vault; the [B]confirm[/B] word is required on purpose.
[*][B]/pv admin migrate [force][/B] — copies your YAML files into the database in batches, with progress and a summary. The original files are never deleted.
[*]Everything is logged in the console.
[/LIST]

[SIZE=4][B]Safer by design[/B][/SIZE]
[LIST]
[*]If stored data cannot be read, the plugin now [B]refuses to open that vault[/B] and asks the player to contact an administrator, instead of showing it empty and overwriting the real items on the next save. The damaged file is still backed up as [B].bak[/B].
[*]A vault can only be open for one person at a time, and a vault that is open cannot be deleted.
[/LIST]

[SIZE=4][B]Updating[/B][/SIZE]
[LIST=1]
[*]Back up [B]plugins/JPPlayerVaults/[/B] (or dump your database).
[*]Replace the jar and restart the server. No configuration changes are required.
[*]Optional: to move to the database, fill in [B]storage.mysql[/B], set [B]storage.type: mysql[/B], restart and run [B]/pv admin migrate[/B].
[*]To let a helper group use the admin commands: [B]/lp group helper permission set jpvaults.admin true[/B].
[/LIST]

[SIZE=4][B]Verified[/B][/SIZE]
Tested with an integration harness on [B]Paper 1.8.8[/B] (47 checks) and [B]Paper 26.3[/B] (69 checks) — both storage backends and every command path — plus a real 1.8.8 client opening a vault, disconnecting and getting the items back from storage.

[HR][/HR]
[B]Docs:[/B] [URL='https://nameeees.github.io/jpvaults-docs/']nameeees.github.io/jpvaults-docs[/URL] · [B]Storage:[/B] [URL='https://nameeees.github.io/jpvaults-docs/getting-started/storage/']storage backends[/URL] · [B]Admin commands:[/B] [URL='https://nameeees.github.io/jpvaults-docs/reference/admin-commands/']/pv admin[/URL]
[B]Changelog:[/B] [URL='https://github.com/Nameeees/JPPlayerVaults/blob/main/CHANGELOG.md']CHANGELOG.md[/URL] · [B]Source:[/B] [URL='https://github.com/Nameeees/JPPlayerVaults']GitHub[/URL] · [B]Support:[/B] [URL='https://discord.gg/VQSvfQw8v8']Discord[/URL]
[SIZE=3][COLOR=#808080]Jar: JPPlayerVaults-1.1.0.jar · SHA-256 3b6d8f1dc7ad1bd6aa3ca5f4b2589cbb1b0918a58e37e72250e1e6954d2755cd[/COLOR][/SIZE]
```

## Before posting

- [ ] Upload `JPPlayerVaults-1.1.0.jar` as the new version file.
- [ ] Re-upload the images that changed (the banner and the six infographics now print `v1.1.0`;
      `features`, `commands`, `permissions` and `setup` also gained the new features).
- [ ] Update the description with the 1.1.0 blocks from [SPIGOTMC_PAGE.md](SPIGOTMC_PAGE.md)
      (database storage, admin commands, new permission, MySQL FAQ).
- [ ] Keep the native Minecraft version at the current latest and the supported range unchanged.
- [ ] Post the update, then reply to the thread with the same message if you keep one.

## Alternative titles

- `v1.1.0 - Database support + admin tools`
- `1.1.0 released: MySQL/MariaDB storage and /pv admin`
