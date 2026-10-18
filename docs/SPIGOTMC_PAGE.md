# SpigotMC submission pack

Everything needed to publish JPPlayerVaults 1.1.0 as a **free** resource on SpigotMC.
Copy the BBCode block into the resource description field and fill the form with the values below.

---

## 1. Form values

| Field | Value |
| --- | --- |
| Resource name | `JPPlayerVaults` |
| Tagline | `Private, rank-based player vaults for 1.8 → 26.3. One jar, no dependencies, anti-dupe.` |
| Category | `Tools and Utilities` (alternatives: `Gameplay` → `Mechanics`) |
| Version | `1.1.0` |
| Native Minecraft version | `26.3` (current latest at release time — update on every Minecraft release) |
| Supported Minecraft versions | `1.8` through `26.3` (select the full range) |
| Price | Free |
| External download | No — upload `JPPlayerVaults-1.1.0.jar` directly |
| License | MIT (source is included and unobfuscated) |
| Source / issues | `https://github.com/Nameeees/JPPlayerVaults` (source) · `https://nameeees.github.io/jpvaults-docs/` (docs) · `https://discord.gg/VQSvfQw8v8` (support) |
| Tags (pick 4–5) | `player vaults`, `vaults`, `storage`, `enderchest`, `permissions` |

> SpigotMC requires the description to be in English (forum rule 9). The in-game text is English by
> default, so no language caveat is needed; Spanish is included as an option.

## 2. Images

### 2.1 Infographics included in the repository

All of them are original artwork created for this plugin, 1280 px wide (crisp on the resource page),
same dark/green style. Upload them to the resource and insert them with `[IMG]…[/IMG]` at the
suggested spot; they carry short labels only, the actual explanation stays in the description text
(SpigotMC guideline: no substantive text inside images).

| File | Size | Where to place it |
| --- | --- | --- |
| `docs/assets/banner.png` | 1280×420 | Top of the description, right under the title. |
| `docs/assets/features.png` | 1280×720 | After the intro, next to “Why JPPlayerVaults?”. |
| `docs/assets/versions.png` | 1280×720 | In the “Compatibility” section. |
| `docs/assets/permissions.png` | 1280×720 | In the “Permissions” section. |
| `docs/assets/commands.png` | 1280×720 | In the “Commands” section. |
| `docs/assets/colours.png` | 1280×720 | In the “Messages and colours” section (new in 1.2.0). |
| `docs/assets/safety.png` | 1280×720 | In the “Data safety” section. |
| `docs/assets/setup.png` | 1280×720 | In the “Installation” section. |
| `docs/assets/discord.png` | 1280×260 | End of the description: Discord invite (see 2.3). |
| `docs/assets/documentation.png` | 1280×260 | End of the description: documentation site (see 2.3). |
| `docs/assets/icon.png` | 512×512 | Resource icon (uploaded in the form, not in the description). |

Suggested reading order if you prefer fewer images: `banner` → `features` → `versions` →
`colours` →
`permissions` → `safety` → `setup` → `commands`.

You can regenerate or tweak them with the scripts in `docs/assets/` (`generate_banner_icon.py`,
`generate_infographics.py` and `generate_cta.py`, Pillow, Windows fonts); the PNGs are committed.

### 2.2 Real in-game screenshots (recommended)

Infographics explain, screenshots prove. Take three or four on a test server (1.21+ looks best, but
one 1.8 screenshot proves the range):

1. A vault open in game with a few items (title and 6 rows visible).
2. `/pv help` in chat showing “Vaults available: 3”.
3. The rank permissions in LuckPerms (`/lp group vip permission set jpvaults.vaults.3 true`).
4. Optional: the same vault opened on a 1.8 server, to prove the compatibility claim.

Do not put important text inside the screenshots either: a caption in the description is enough.

### 2.3 Clickable support banners (Discord & documentation)

`discord.png` and `documentation.png` are call-to-action strips: upload them and append this block
at the **end** of the description. SpigotMC asks that anything promotional stays below the fold, so
keep it after the FAQ. Both links are already the real ones:

```bbcode
[HR][/HR]
[CENTER]
[SIZE=4][B]Community & documentation[/B][/SIZE]

[URL='https://discord.gg/VQSvfQw8v8'][IMG]https://YOUR-IMAGE-HOST/discord.png[/IMG][/URL]
[URL='https://nameeees.github.io/jpvaults-docs/'][IMG]https://YOUR-IMAGE-HOST/documentation.png[/IMG][/URL]
[/CENTER]
```

Both banners embed the official platform marks (Discord's Clyde and GitHub's Octocat) taken from
`docs/assets/icons/`; see the README there for the source and licence. They are used only to
identify the link target.

The documentation site is live at <https://nameeees.github.io/jpvaults-docs/> and is built from the
`Nameeees/jpvaults-docs` repository (MkDocs Material + GitHub Pages). To change the text printed
inside the banners — for example if the invite ever changes — regenerate them:

```bash
python docs/assets/generate_cta.py \
  --discord-url "discord.gg/VQSvfQw8v8" \
  --docs-url "nameeees.github.io/jpvaults-docs"
```

Running the script with no arguments produces the generic versions with no URL printed.

## 3. Description (BBCode — paste as-is)

```bbcode
[SIZE=6][B]JPPlayerVaults[/B][/SIZE]
[SIZE=4][COLOR=#00a84f]Private player vaults for Minecraft 1.8 to the latest version. One jar, no dependencies.[/COLOR][/SIZE]

[B]JPPlayerVaults[/B] gives every player a set of private chests: [B]/pv 1[/B], [B]/pv 2[/B], and so on. How many vaults a player can open is decided by their rank, not by the plugin, and each vault is a normal inventory of up to [B]54 slots[/B] (6 rows) — the largest Minecraft allows.

The same jar runs from Minecraft 1.8.8 to the latest release on Spigot, Paper and forks. It is built against the Spigot 1.8.8 API, uses no NMS and no third-party libraries, and sends nothing over the network: no bStats, no update checker, no external calls.

[HR][/HR]

[SIZE=5][B]Why JPPlayerVaults?[/B][/SIZE]
[LIST]
[*][B]One jar, every version.[/B] 1.8, 1.12, 1.16, 1.21 and the new 26.x releases all run the same file. No NMS, no reflection hacks that break on update — only stable Bukkit API, with the few version-specific bits (off-hand key, renamed methods and sounds) detected at runtime.
[*][B]No dependencies.[/B] No Vault, no economy plugin, no database, no ProtocolLib. Drop the jar in and configure the permissions.
[*][B]Rank-based access.[/B] Give a rank [B]jpvaults.vaults.5[/B] and its players get 5 vaults. The plugin uses the highest number the player has, with a configurable default for players without any permission.
[*][B]Built to protect items.[/B] Closing a vault waits for the file to be on disk, `Q` and outside clicks are blocked in the GUI, the off-hand swap is guarded, stale GUIs are closed on reload, and every save is written atomically.
[*][B]Readable data.[/B] One YAML file per player ([B]data/<uuid>.yml[/B]) that an administrator can open, back up or repair. Lowering the vault size never deletes hidden rows.
[*][B]Database storage, optional.[/B] Prefer MySQL or MariaDB? Set [B]storage.type: mysql[/B] and the plugin creates its two tables, writes every vault inside a transaction and keeps the server running (on YAML) if the database is down. [B]/pv admin migrate[/B] copies your existing files into the database.
[*][B]Administrator tools.[/B] Open another player's vault — online or offline, by name or UUID — inspect a player or the plugin, wipe a single vault and migrate the storage. Everything is logged and sits behind [B]jpvaults.admin[/B].
[*][B]Actually configurable.[/B] Size, default and maximum vault count, autosave, sounds, messages and language are all in config files. Every visible text lives in [B]messages_<language>.yml[/B], with English and Spanish included.
[/LIST]

[SIZE=5][B]Features[/B][/SIZE]
[LIST]
[*][B]/pv[/B] opens a [B]vault picker[/B]: a menu with one icon per vault, showing how many items each one holds.
[*][B]/pv <number>[/B] opens that vault directly; [B]/pv help[/B] shows the help and how many vaults the player has.
[*][B]Storage that survives Minecraft updates[/B]: on Paper 1.20.5+ items are saved as NBT (the format Paper recommends for data migrations); elsewhere the Bukkit format is used, and data saved by older versions keeps loading.
[*][B]Modern colours on Paper[/B]: any message can use MiniMessage (gradients, hex, hover and click) while [B]&[/B] messages keep working everywhere. [B]messages-format: auto[/B] by default.
[*][B]No driver needed for the database[/B]: drop the JDBC driver .jar into [B]plugins/JPPlayerVaults/lib/[/B].
[*]Configurable size from [B]9 to 54 slots[/B] (always a multiple of 9).
[*]Behaves like a real chest: shift-click, hotbar keys, drag and drop, double-click collect.
[*]Automatic saving of open vaults, plus saving on close, quit and server shutdown.
[*]Autosave interval configurable; [B]0[/B] disables it.
[*]Sounds for open, success and denied actions, with the right sound name for each Minecraft version.
[*]English and Spanish messages out of the box; add any other language with one file.
[*][B]/pv reload[/B] reloads the configuration and the messages without restarting.
[*]Legacy [B]&[/B] colors and [B]&#RRGGBB[/B] hex colors in every message.
[*]Tab completion for vault numbers, [B]help[/B] and [B]reload[/B].
[/LIST]

[SIZE=5][B]Commands[/B][/SIZE]
[TABLE]
[TR][TH]Command[/TH][TH]What it does[/TH][TH]Permission[/TH][/TR]
[TR][TD]/pv[/TD][TD]Opens the vault picker (one icon per vault).[/TD][TD]jpvaults.use[/TD][/TR]
[TR][TD]/pv <number>[/TD][TD]Opens your vault with that number.[/TD][TD]jpvaults.use[/TD][/TR]
[TR][TD]/pv help[/TD][TD]Shows the help and your available vaults.[/TD][TD]jpvaults.use[/TD][/TR]
[TR][TD]/pv reload[/TD][TD]Reloads config.yml and the messages.[/TD][TD]jpvaults.reload[/TD][/TR]
[TR][TD]/pv admin open <player|uuid> <number>[/TD][TD]Opens that player's vault (online or offline).[/TD][TD]jpvaults.admin[/TD][/TR]
[TR][TD]/pv admin info [player|uuid][/TD][TD]Storage status or a report for one player.[/TD][TD]jpvaults.admin[/TD][/TR]
[TR][TD]/pv admin delete <player|uuid> <number> confirm[/TD][TD]Wipes one vault.[/TD][TD]jpvaults.admin[/TD][/TR]
[TR][TD]/pv admin migrate [force][/TD][TD]Copies the YAML files into the database.[/TD][TD]jpvaults.admin[/TD][/TR]
[/TABLE]

Aliases: [B]/playervaults[/B], [B]/jpvaults[/B].

[SIZE=5][B]Permissions[/B][/SIZE]
[TABLE]
[TR][TH]Permission[/TH][TH]Description[/TH][TH]Default[/TH][/TR]
[TR][TD]jpvaults.use[/TD][TD]Allows using /pv and opening your own vaults.[/TD][TD]true[/TD][/TR]
[TR][TD]jpvaults.vaults.<n>[/TD][TD]Gives the rank access to n vaults; the highest number wins.[/TD][TD]false[/TD][/TR]
[TR][TD]jpvaults.reload[/TD][TD]Allows /pv reload.[/TD][TD]OP[/TD][/TR]
[TR][TD]jpvaults.admin[/TD][TD]Allows the /pv admin subcommands (open, info, delete, migrate).[/TD][TD]OP[/TD][/TR]
[/TABLE]

[B]LuckPerms example[/B]
[CODE]
# VIP: 3 vaults
/lp group vip permission set jpvaults.vaults.3 true

# MVP: 6 vaults
/lp group mvp permission set jpvaults.vaults.6 true
[/CODE]

Players without any [B]jpvaults.vaults.<n>[/B] permission get [B]vaults.default-amount[/B] (1 by default). If your top rank needs more than [B]vaults.max-amount[/B] vaults, raise that value in config.yml. Permission numbers above the ones listed in plugin.yml work too.

[SIZE=5][B]Configuration[/B][/SIZE]
[CODE]
# plugins/JPPlayerVaults/config.yml
language: en            # en or es; messages_<language>.yml

vaults:
  size: 54              # 9-54, multiple of 9
  default-amount: 1     # vaults for players without a rank permission
  max-amount: 10        # highest jpvaults.vaults.<n> checked

storage:
  type: yaml            # yaml or mysql (database over JDBC)
  folder: 'data'
  autosave-seconds: 300 # 0 disables autosave
  mysql:                # only used when type is mysql
    host: '127.0.0.1'
    port: 3306
    database: 'jpvaults'
    username: 'root'
    password: ''
    table-prefix: 'jpvaults_'

sounds:
  enabled: true
  volume: 0.7
  pitch: 1.4
[/CODE]

Invalid values are clamped and reported in the console instead of breaking the plugin.

[SIZE=5][B]Installation[/B][/SIZE]
[LIST=1]
[*]Drop [B]JPPlayerVaults-1.1.0.jar[/B] into the server's [B]plugins/[/B] folder.
[*]Start the server (or run [B]/reload confirm[/B]).
[*]Grant [B]jpvaults.vaults.<n>[/B] to your ranks.
[*]Adjust [B]plugins/JPPlayerVaults/config.yml[/B] if needed.
[*]Optional: set [B]language: es[/B] and run [B]/pv reload[/B] for Spanish messages.
[/LIST]

[SIZE=5][B]Data safety[/B][/SIZE]
Vaults usually hold a server's most valuable items, so the plugin is designed not to duplicate or lose them:
[LIST]
[*][B]Durable save on close.[/B] Closing a vault or quitting waits until the data is written. If the server dies right after, the content is already safe.
[*][B]Q and Ctrl+Q blocked[/B] in the vault GUI; clicks outside the window do not drop the cursor item.
[*][B]Off-hand key (F)[/B] is cancelled while a vault is open; the classic 1.16.x cursor desync is blocked.
[*][B]No zombie GUIs[/B]: open vaults are closed on reload, and surviving views are closed on enable.
[*][B]Atomic writes[/B] on YAML: every save writes a .tmp file and moves it over the real file. On a database, every vault is written inside a transaction.
[*][B]One viewer per vault[/B]: an administrator cannot open a vault its owner has open, and a vault that is open cannot be deleted.
[*][B]No empty vaults on errors[/B]: if stored data cannot be read, the plugin refuses to open the vault and tells the player to contact an administrator, instead of showing it empty and overwriting the real items on the next save.
[*][B]No cross-session cache[/B]: data is read from storage on open and dropped on quit.
[*][B]Autosave[/B] limits the loss if the server crashes while a vault is open.
[/LIST]

[B]Known limits, stated plainly:[/B] if the process is killed while a player has a vault open, the last save is restored (hence autosave); saving on every click would tax the whole server. Do not share one data/ folder (or one database) between several running servers — the plugin does not sync servers live.

[SIZE=5][B]Compatibility[/B][/SIZE]
[TABLE]
[TR][TH]Item[/TH][TH]Value[/TH][/TR]
[TR][TD]Minecraft[/TD][TD]1.8 through the latest version (26.3 at release)[/TD][/TR]
[TR][TD]Server software[/TD][TD]Spigot, Paper and forks that implement the Bukkit API[/TD][/TR]
[TR][TD]Java[/TD][TD]Java 8+ for the plugin; the server itself needs the Java version its Minecraft release requires (1.21+ → 21, 26.x → 25)[/TD][/TR]
[TR][TD]Dependencies[/TD][TD]None[/TD][/TR]
[TR][TD]Built against[/TD][TD]Spigot API 1.8.8, Java 8 bytecode[/TD][/TR]
[TR][TD]Tested on[/TD][TD]Paper 1.8.8 (integration test + a real 1.8 client) and Paper 26.3 (integration test)[/TD][/TR]
[/TABLE]

[SIZE=5][B]FAQ[/B][/SIZE]
[B]Do I need Vault or an economy plugin?[/B]
No. Access is controlled only by permissions.

[B]Can a player have more vaults than max-amount?[/B]
Raise [B]vaults.max-amount[/B] (up to 100) and grant the permission with that number.

[B]Can I edit a player's vault from the console?[/B]
Yes. [B]/pv admin open <player|uuid> <number>[/B] opens it in game, and [B]/pv admin info[/B] shows what a player has stored. On YAML storage you can also edit [B]plugins/JPPlayerVaults/data/<uuid>.yml[/B] with the server stopped or the player offline.

[B]What happens if the server crashes while a vault is open?[/B]
The last save is restored. Autosave (300 seconds by default) limits how much can be lost.

[B]Does it work with MySQL?[/B]
Yes, optionally: [B]storage.type: mysql[/B] stores the vaults in a database over JDBC (MySQL, MariaDB or anything compatible). YAML stays the default, and [B]/pv admin migrate[/B] copies your existing files into the database. If the database is unreachable the plugin logs the error and keeps running on YAML instead of leaving the server without vaults.

[B]Does it work with Folia?[/B]
No, it is a normal Bukkit plugin that uses the regular scheduler.

[B]Can I translate it?[/B]
Yes. Copy [B]messages_en.yml[/B] to [B]messages_<code>.yml[/B], translate it and set [B]language: <code>[/B]. Missing keys fall back to English and the console lists them.

[SIZE=5][B]Support[/B][/SIZE]
Bug reports and questions: the discussion thread below, the community Discord, or the documentation site for setup guides. Please include the server version, the plugin version, your config.yml and the relevant console log. The source is unobfuscated and MIT licensed.

[SIZE=5][B]License[/B][/SIZE]
MIT. You may use, modify and redistribute the plugin, including on commercial servers, as long as the copyright notice is kept.

[HR][/HR]
[SIZE=3][COLOR=#808080]JPPlayerVaults is not affiliated with Mojang or Microsoft. "Minecraft" is a trademark of Mojang AB.[/COLOR][/SIZE]
```

## 4. Before pressing “Submit”

- [ ] `mvn clean package` produces `target/JPPlayerVaults-1.1.0.jar`.
- [ ] The jar was tested on a **fresh** server folder (no leftover config) on the latest Minecraft.
- [ ] `plugin.yml` shows version `1.1.0` (it is filtered from `pom.xml`).
- [ ] The description above is pasted and renders correctly in the preview.
- [ ] The seven infographics from section 2.1 are uploaded and placed as suggested.
- [ ] At least 3 real in-game screenshots are uploaded (section 2.2).
- [ ] The Discord and documentation banners are uploaded and linked at the end of the description (section 2.3).
- [ ] The Discord invite never expires (Edit invite → Expires: Never).
- [ ] The documentation site responds: <https://nameeees.github.io/jpvaults-docs/>.
- [ ] The support URL (discussion thread / GitHub) is filled in.
- [ ] The resource is marked **free**.
- [ ] The latest Minecraft version is selected as native and in the supported range.
- [ ] No server IP, advertisement or external download link is in the description (no advertising,
      direct upload only).
- [ ] The jar is not obfuscated (it is not) and contains no DRM.

## 5. After publishing

- Reply to reviews and bug reports in the discussion thread.
- Keep the native Minecraft version updated when a new release comes out (SpigotMC requires support
  for the latest stable version).
- Publish the same text on the GitHub release page; keep `CHANGELOG.md` in sync.

## 6. Publishing an update

Every release after the first one is published from **Resource → Post an update**, which asks for a
version, an update title and a message. The message accepts BBCode and is what users see in their
alerts, so it should stand on its own.

Structure that works:

1. **Version heading** and one sentence on what the update is about.
2. **New features**, in `[SIZE=4]` blocks with `[LIST]`, most important first.
3. **Behaviour changes** and anything that could surprise an existing installation.
4. **Updating**, numbered: back up, replace the jar, restart, optional steps, new permissions.
5. **Verified**, the versions actually tested.
6. **Footer** with docs, changelog, source, Discord and the jar's SHA-256.

Rules of thumb:

- Write it for someone who has never read the changelog: say what changed *for them*, not which
  classes were touched.
- Say explicitly when no configuration change is needed; most users only want to know that.
- Mention new permissions and how to grant them, so admins do not discover them from a bug report.
- If the resource images changed, re-upload them — the description keeps pointing at the old
  picture otherwise.
- Keep the update title short: `1.1.0 — MySQL storage and /pv admin commands`.

A filled-in example for 1.1.0 is in [SPIGOTMC_UPDATE_1.1.0.md](SPIGOTMC_UPDATE_1.1.0.md).
