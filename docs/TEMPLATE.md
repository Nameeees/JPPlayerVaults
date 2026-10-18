# Using this repository as a template

JPPlayerVaults is meant to be the reference for the next plugins: the same layout, the same CI, the
same documentation site, the same SpigotMC pack. This file is the playbook.

Everything here was actually executed for this plugin — commands, API calls and the mistakes that
cost time — so it can be repeated without rediscovering it.

---

## 1. Start a new plugin

1. On GitHub, open <https://github.com/Nameeees/JPPlayerVaults> and click **Use this template →
   Create a new repository** (the repository is flagged as a template for this reason). Name it after
   the new plugin, public, without initialising anything.
2. Clone it and do the branding pass of section 2.
3. The first `git push` already builds in CI and publishes nothing else — no other setup is needed
   for the plugin repository itself.

> “Use this template” copies the files in a single commit, so the new repository does not inherit
> this project's history, releases or issues.

## 2. Branding pass: what to change

| File / folder | Action | Notes |
| --- | --- | --- |
| `pom.xml` | Change `groupId`, `artifactId`, `name`, `description` | Keep the rest: Spigot 1.8.8 API, `--release 8`, PaperMC repo, `bungeecord-chat` exclusion, `project.build.outputTimestamp`, no Maven descriptor in the jar. That combination is what makes the build reproducible and CI-proof. |
| `src/main/java/<package>/` | Rename the package, main class and every import | Use an IDE refactor; the plugin package appears in `plugin.yml` (`main:`) too. |
| `src/main/resources/plugin.yml` | `name`, `main`, `authors`, `description`, commands, permission prefix | The permission prefix is the plugin's public API: choose it once (`myplugin.…`). |
| `src/main/resources/config.yml` | Rewrite keys and English comments | Keep `config-version` for future migrations. |
| `src/main/resources/messages_en.yml` + `messages_es.yml` | Rewrite the texts, keep the keys | Ship English first; other languages are optional files. |
| `src/main/java/**` (code) | Rename log prefixes and class names referring to the old plugin | `getLogger()` already prefixes the plugin name. |
| `LICENSE` | Keep MIT or change it, update the copyright holder | The SpigotMC page states the licence. |
| `README.md` | Rewrite the product sections, keep the skeleton | Badges, Gallery, Features, Compatibility, Installation, Commands, Permissions, Configuration, Storage, Anti-dupe, Building, Support, License. |
| `CHANGELOG.md` | Reset to an “Unreleased”/1.0.0 entry | Keep the Keep a Changelog structure. |
| `docs/SPIGOTMC_PAGE.md` | Rewrite the description; update the form-value table | The submission pack: field values, image plan, checklist. |
| `docs/QA_CHECKLIST.md` | Keep the structure, adapt the manual matrix | It is the gate before every release. |
| `docs/VERIFICATION.md` | Replace with the new plugin's evidence | Never claim something that is not in this file. |
| `docs/README.es.md` | Optional Spanish summary | Delete if the plugin is English-only. |
| `docs/assets/*.png` | Regenerate after editing the `BRAND` block of the three scripts | See section 3. |
| `docs/assets/icons/` | Reusable as-is (licence in its README) | The Discord and GitHub marks are platform icons, not plugin icons. |
| `testing/harness/` | Rewrite the reflection targets for the new classes | Copy the **pattern**: drive the real classes and commands inside a server, print `CHECK OK`, stop the server. |
| `testing/bot/` | Change port, username and version if needed | The reconnect test (quit → cache dropped → rejoin) is the valuable part. |
| `.github/workflows/build.yml` | Keep as-is | Builds and verifies the jar contents on every push. |
| `.github/workflows/deploy.yml` | Lives in the **docs repository**, not here | See section 5. |

## 3. Regenerating the artwork

Three scripts under `docs/assets/` produce every image (Pillow, Windows fonts by default — change
`BOLD`/`SEMI`/`REGULAR` for another OS):

| Script | Output | What to edit |
| --- | --- | --- |
| `generate_banner_icon.py` | `banner.png`, `icon.png` | The `BRAND` block: name, tagline, highlights, footline. |
| `generate_infographics.py` | `features`, `versions`, `permissions`, `safety`, `setup`, `commands` | The `BRAND` block plus the per-image text, which is product-specific. |
| `generate_cta.py` | `discord.png`, `documentation.png` | `BRAND` plus the optional `--discord-url` / `--docs-url` arguments that print the addresses inside the strip. |

```bash
python docs/assets/generate_banner_icon.py
python docs/assets/generate_infographics.py
python docs/assets/generate_cta.py --discord-url "discord.gg/INVITE" --docs-url "user.github.io/repo"
```

The output is deterministic: regenerating with the same text produces byte-identical PNGs, so a
diff only appears when something really changed.

## 4. Consistency check

The plugin name and the permission prefix leak into many files. Before the first release:

```bash
# leftover references to the template plugin (should return nothing)
grep -rn --exclude-dir=target --exclude-dir=.git -i "jpvaults\|JPPlayerVaults" . | grep -v "docs/TEMPLATE.md"
```

Also check, in this order: `pom.xml` → `plugin.yml` → `messages_*.yml` → `README.md` → docs site
`mkdocs.yml` → SpigotMC pack → image scripts.

## 5. GitHub setup

### Plugin repository

```bash
git init -b main
git add -A && git commit -m "MyPlugin 1.0.0"
git remote add origin https://github.com/<user>/<repo>.git
git push -u origin main
```

With the GitHub CLI (`gh auth login` once):

```bash
gh repo create <user>/<repo> --public --source=. --push \
  --description "…" --homepage "https://<user>.github.io/<repo>-docs/"
gh repo edit --add-topic minecraft,spigot,paper,bukkit,plugin
gh repo edit --template          # make it a template for the next plugin
```

Without `gh`, the same through the REST API (the token needs the `repo` and `workflow` scopes):

```http
POST /user/repos            {"name":"MyPlugin","private":false,"has_wiki":false,"auto_init":false}
PUT  /repos/{owner}/{repo}/topics   {"names":["minecraft","spigot","paper","bukkit","plugin"]}
PATCH /repos/{owner}/{repo} {"is_template":true}
```

### Documentation repository

One repository per plugin, named `<plugin>-docs`, built with MkDocs Material and served by GitHub
Pages. `Nameeees/jpvaults-docs` is flagged as a GitHub template too, so *Use this template* on that
repository gives the same site structure for the new plugin; the pages then get rewritten with the
new plugin's content. The structure to keep:

```text
mkdocs.yml            site_name, site_url, repo_url, theme, nav
requirements.txt      mkdocs-material>=9.5,<10
docs/                 index.md, getting-started/, reference/, faq.md, changelog.md, assets/
.github/workflows/    deploy.yml (build + upload-pages-artifact + deploy-pages)
```

Publishing:

```bash
git init -b main && git add -A && git commit -m "Initial documentation site"
git remote add origin https://github.com/<user>/<repo>-docs.git
git push -u origin main
```

Then enable Pages once — **before** the first workflow run, or the deploy job fails with
“Pages site not found”:

```http
POST /repos/{owner}/{repo}/pages   {"build_type":"workflow"}
```

or manually: **Settings → Pages → Build and deployment → Source: GitHub Actions**.

Validate locally with the same strictness the CI uses; it fails on broken links and on pages missing
from `nav`:

```bash
mkdocs build --strict
```

The three URLs to change in `mkdocs.yml`: `site_url`, `repo_url`, `repo_name` (plus `edit_uri` if
the branch is not `main`).

### Release

```bash
git tag v1.0.0 && git push origin v1.0.0
gh release create v1.0.0 target/MyPlugin-1.0.0.jar --title "MyPlugin 1.0.0" --notes-file CHANGELOG.md
```

Or by API, which is what was used here:

```http
POST /repos/{owner}/{repo}/releases              {"tag_name":"v1.0.0","name":"…","body":"…"}
POST https://uploads.github.com/repos/{owner}/{repo}/releases/{id}/assets?name=MyPlugin-1.0.0.jar
PATCH /repos/{owner}/{repo}/releases/{id}        {"body":"… SHA-256 …"}
```

Always publish the SHA-256 of the jar in the release notes: with
`project.build.outputTimestamp` fixed and no Maven descriptor inside the jar, the same source always
produces the same hash, so anyone can verify the download.

## 6. SpigotMC publishing

The rules that shaped this pack (checked against the SpigotMC rules and the premium guidelines):

- **Descriptions must be in English** (forum rule 9). In-game text in another language is allowed if
  the description says so.
- **“Vault, backpack, portable inventory and similar” plugins are on the common-genre list** that is
  not approved as a *premium* resource unless it has substantial unique functionality. Publishing
  such a plugin **for free** avoids that wall entirely.
- **Resources must support the latest stable version.** Update the native version on every Minecraft
  release; the supported range is selected in the form.
- **No obfuscation, no DRM, direct upload.** Upload the jar; do not link a pay-per-click host.
- **Keep anything promotional below the fold**, and never put substantive text inside the images.
  The infographics carry labels; the explanation lives in the description.
- Java per Minecraft release: Java 8 for 1.8, Java 21 for 1.21+, Java 25 for 26.x — mention it,
  because “the plugin is Java 8” does not mean the server can be.

Publish with `docs/SPIGOTMC_PAGE.md`: form values, the BBCode description, the image plan with the
placement of every asset, the clickable support banners and the pre-submit checklist.

## 7. Gotchas worth remembering

| Symptom | Cause and fix |
| --- | --- |
| CI fails with `Could not find artifact net.md-5:bungeecord-chat:1.8-SNAPSHOT` | Spigot and Sonatype stopped serving it. Add `https://repo.papermc.io/repository/maven-public/` and exclude `bungeecord-chat` from `spigot-api` (the plugin does not use BungeeCord chat). |
| The jar hash changes after editing only `pom.xml` | Maven embeds the pom in `META-INF/maven`. Set `<addMavenDescriptor>false</addMavenDescriptor>` in `maven-jar-plugin`. |
| A Java dynamic proxy of `Player` throws `methods with same signature getHealth()` on 1.8 | The 1.8 API has incompatible return types in the hierarchy. Run command-level checks on a modern server and cover the oldest version with a real client (the bot test). |
| The headless client cannot join the newest Minecraft | mineflayer supported up to 26.1 when this was written. Use the bot on the oldest version and the harness everywhere. |
| Clients crash when opening a vault with a long title | 1.8–1.12 accept at most 32 visible characters. Cap the title and log a warning once. |
| `mkdocs build --strict` aborts | A relative link is wrong or a page is missing from `nav`. Both are errors, not warnings. |
| `deploy-pages` fails on the first push | Pages was not enabled yet. Enable it with `build_type: workflow`, then re-run the job. |
| Configuration changes silently break servers | Clamp and report invalid values in the console instead of throwing; keep `config-version` for migrations. |
| Accents turn into `Ã©` / `â€"` and an invisible BOM appears at the top of a file | Windows PowerShell 5.1 `Get-Content -Raw` + `Set-Content -Encoding UTF8` reads and writes with the ANSI code page and prepends a BOM. For Markdown/YAML use an editor, `git apply`, or Python (`path.read_bytes().decode("utf-8")`). A quick audit: flag files whose bytes start with `EF BB BF`, or that contain `Â`, `Ã` or `â€`. |
| The published images still show the previous version | The banner and the infographics print the version in their footer (`FOOTLINE` in `generate_banner_icon.py` and `generate_infographics.py`). Add "regenerate the artwork" to the release checklist for every version bump, and copy `banner.png` into the documentation repository, which keeps its own copy. |

## 8. Release checklist (short form)

1. `mvn clean package`, twice, same SHA-256.
2. Harness PASS on the oldest and the newest supported Minecraft.
3. Real-client test on the oldest version (bot) for the GUI/save path.
4. Documentation updated: `README.md`, `CHANGELOG.md`, docs site `changelog.md`.
5. `docs/VERIFICATION.md` records what was actually run, with the final hash.
6. Images regenerated if anything visible changed.
7. GitHub release created with the jar and its SHA-256.
8. SpigotMC form filled from `docs/SPIGOTMC_PAGE.md`, screenshots uploaded, native version = latest.
9. Discord invite set to never expire; documentation site responding.
