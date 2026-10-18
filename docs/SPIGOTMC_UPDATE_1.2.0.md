# SpigotMC update post — 1.2.0

Ready to paste into **Resource → Post an update** on the resource page.

| Field | Value |
| --- | --- |
| Version | `1.2.0` |
| Update Title | `1.2.0 — Vault picker, MiniMessage colours and NBT storage` |

## Update Message (BBCode — paste as-is)

```bbcode
[SIZE=5][B]JPPlayerVaults 1.2.0[/B][/SIZE]
This update makes the plugin feel at home on modern servers: a vault menu, the new colour format and a storage format that survives Minecraft updates. The same jar still runs on 1.8.

[HR][/HR]

[SIZE=4][B]New: vault picker[/B][/SIZE]
[LIST]
[*][B]/pv[/B] with no arguments now opens a menu with one icon per vault, showing how many items each one holds. Click an icon and the vault opens.
[*][B]/pv 3[/B] still opens that vault directly, and [B]/pv help[/B] shows the text help.
[*]Prefer the old behaviour? [B]vaults.picker: false[/B] in config.yml.
[/LIST]

[SIZE=4][B]New: MiniMessage colours (1.16+ / Paper)[/B][/SIZE]
[LIST]
[*]Messages can now use the modern format: gradients, hex, hover and click. Example:
[CODE]vault-title: '<gradient:#2ec4b6:#12727f><bold>Vault</bold></gradient> <gray>#{number}</gray>'[/CODE]
[*][B]messages-format: auto[/B] (default) means your current [B]&[/B] messages keep working exactly as they do today on every version, and only messages you rewrite with tags are sent as Components.
[*]It also applies to the inventory title and to the picker's item names and lore.
[*]On a server without Adventure the tags are stripped so the text stays readable, and the console tells you once.
[/LIST]

[SIZE=4][B]New: storage that survives Minecraft updates[/B][/SIZE]
[LIST]
[*]On Paper 1.20.5+ vaults are stored as [B]NBT[/B], the format Paper itself recommends: it uses Minecraft's own data converter instead of Bukkit's serialization, and it keeps slot order and gaps by design.
[*][B]storage.item-format: auto | nbt | bukkit[/B]. Auto uses NBT where the server has it.
[*]Every stored vault records which format it uses, so [B]data saved by 1.0.0 / 1.1.0 keeps loading[/B]: nothing to migrate.
[*]If you ever move a world from Paper to Spigot, set [B]item-format: bukkit[/B] on Paper first so the data stays portable.
[*]The YAML backend also has an optional compact mode: [B]storage.yaml-format: nbt[/B].
[/LIST]

[SIZE=4][B]Smaller improvements[/B][/SIZE]
[LIST]
[*][B]No driver needed for the database:[/B] drop the JDBC driver .jar into [B]plugins/JPPlayerVaults/lib/[/B] and the plugin loads it, so a server without the driver still works.
[*][B]Better on big servers:[/B] [B]/pv admin[/B] resolves names with Paper's cached lookup instead of scanning every player who ever joined.
[*][B]Paper/Folia schedulers:[/B] autosave and migrations use the modern schedulers, and anything that touches a player runs on that player's region thread.
[*][B]Longer titles if you want them:[/B] [B]vaults.title-max-length[/B] (default 32 for 1.8-1.12 clients, 0 = no limit).
[/LIST]

[SIZE=4][B]Updating[/B][/SIZE]
[LIST=1]
[*]Back up [B]plugins/JPPlayerVaults/[/B] (or dump your database).
[*]Replace the jar and restart. No configuration changes are required; your existing config.yml and messages files work as they are.
[*]Optional: try the new picker with [B]/pv[/B], and if you are on Paper, add MiniMessage to a message of your choice.
[/LIST]

[SIZE=4][B]Verified[/B][/SIZE]
Integration harness on [B]Paper 1.8.8[/B] (64 checks) and [B]Paper 26.3[/B] (102 checks): both storage backends, both item formats, NBT round-trip, opening vaults saved by the previous version, MiniMessage in both modes, the picker, the schedulers and every command path.

[HR][/HR]
[B]Docs:[/B] [URL='https://nameeees.github.io/jpvaults-docs/']nameeees.github.io/jpvaults-docs[/URL] · [B]Colours:[/B] [URL='https://nameeees.github.io/jpvaults-docs/reference/colours/']messages and colours[/URL] · [B]Storage:[/B] [URL='https://nameeees.github.io/jpvaults-docs/getting-started/storage/']storage backends[/URL]
[B]Changelog:[/B] [URL='https://github.com/Nameeees/JPPlayerVaults/blob/main/CHANGELOG.md']CHANGELOG.md[/URL] · [B]Source:[/B] [URL='https://github.com/Nameeees/JPPlayerVaults']GitHub[/URL] · [B]Support:[/B] [URL='https://discord.gg/VQSvfQw8v8']Discord[/URL]
[SIZE=3][COLOR=#808080]Jar: JPPlayerVaults-1.2.0.jar · 89.8 KB · SHA-256 949898703fbb4edd17e9cd4de9a12b2cd4ab957c77a6fc325528fa8fc7bf08c1[/COLOR][/SIZE]
```

## Before posting

- [ ] Upload `JPPlayerVaults-1.2.0.jar` as the new version file.
- [ ] Re-upload **all** the images: the banner and the six infographics now print `v1.2.0`, and
      `features`, `commands` and `setup` also changed their content. There is a **new image**,
      `colours.png`, for the MiniMessage section.
- [ ] Add the new blocks to the description (picker, MiniMessage, item formats, `lib/` folder).
- [ ] Post the update.

## Alternative titles

- `v1.2.0 - Vault menu, MiniMessage support and safer item storage`
- `1.2.0 released: picker, modern colours, NBT storage`
