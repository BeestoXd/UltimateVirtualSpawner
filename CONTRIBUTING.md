# Contributing to UltimateVirtualSpawner

Thanks for taking the time to help out. This document covers how to report a problem, how to get
a development build running, and what a pull request needs to pass CI.

By contributing you agree that your work is licensed under the [MIT License](LICENSE.md).

- 💬 Questions and setup help: [Discord](https://dsc.gg/hellstarr) — not the issue tracker.
- 🐛 Bugs and feature requests: [GitHub Issues](https://github.com/BeestoXd/UltimateVirtualSpawner/issues).

## Reporting issues

Blank issues are disabled; pick the template that matches (bug report, feature request, or
documentation). Before opening one:

1. Search existing issues, including closed ones.
2. Reproduce on the latest build — the [nightly pre-release](https://github.com/BeestoXd/UltimateVirtualSpawner/releases) is rebuilt on every push to `main`.
3. Test with only UltimateVirtualSpawner (plus Vault / PlaceholderAPI if the bug involves them)
   installed, so another plugin is not the actual cause.

A useful bug report includes the exact server software and version (`/version`), the plugin
version (`/spawner version`), Java version, the full stack trace from `logs/latest.log` rather
than a screenshot of chat, and the relevant section of any config you changed.

**Security issues** — do not open a public issue for anything exploitable (dupe bugs, permission
bypasses, economy exploits). Report them privately on Discord instead.

## Development setup

Requirements: **JDK 21+** and **Maven**. Everything else is pulled from the repositories declared
in `pom.xml`.

```bash
git clone https://github.com/BeestoXd/UltimateVirtualSpawner.git
```

Build:

```bash
mvn clean package
```

On Windows, `build.bat` runs the same thing. The jar lands in
`target/UltimateVirtualSpawner-<version>.jar`; drop it into a test server's `plugins/` folder.

Run just the tests:

```bash
mvn test
```

### Testing against a real server

Behaviour changes need a live server — unit tests only cover version parsing and money maths.
Test on **Paper** at minimum. If your change touches world access, scheduling, or anything in
`tasks/` or `listeners/`, also test on **Folia**: the region scheduler behaves differently and a
change that works on Paper can deadlock or throw there.

Supported ranges are Paper/Spigot/Bukkit `1.21.10`–`26.2` and Folia `1.21.11`–`26.2`. To start on
an unsupported build for testing, set `COMPATIBILITY.STRICT: false` or `COMPATIBILITY.ENABLED: false`
in `config.yml`.

## Project layout

```
src/main/java/com/bx/ultimateVirtualSpawner/
├── commands/    command executors and tab completion
├── compat/      server/version detection and the compatibility gate
├── economy/     balances, providers (internal / Vault), money arithmetic
├── hooks/       PlaceholderAPI and Vault integration
├── listeners/   Bukkit event handling
├── managers/    spawner, storage, config and database state
├── menus/       GUI construction and click handling
├── models/      plain data types
├── tasks/       scheduled work (spawn cycles, saving)
└── utils/       shared helpers

src/main/resources/   config.yml, spawners.yml, menus.yml, messages.yml, sounds.yml, plugin.yml
src/test/java/        JUnit 5 tests
```

## Code style

Match the surrounding code rather than reformatting it.

- 4-space indentation, braces on the same line, UTF-8, LF endings.
- Classes not designed for extension are `final`; fields are `private final` where possible.
- No wildcard imports.
- Comments explain *why*, not *what*. Most methods need none.
- All world, block, and entity access goes through the region scheduler — never assume the main
  thread. This is the single most common source of Folia breakage.
- No new hard dependencies. Vault and PlaceholderAPI are soft-depends and the plugin must keep
  working without them.

### Changing configuration files

Missing keys are back-filled from the packaged defaults on every load, so updating never wipes a
server owner's values. To keep that true:

- **Add** keys — never rename or remove one without a migration path.
- Add the key to the packaged default in `src/main/resources/` with a comment explaining it.
- Text goes in `messages.yml`, GUI layout in `menus.yml`, sounds in `sounds.yml`. Do not hardcode
  player-facing strings in Java.
- If a new option only takes effect on restart (like `ECONOMY.PROVIDER`), say so in its comment.

## Pull requests

Work on a branch off `main`, one logical change per PR. CI runs on `src/**`, `pom.xml`, and
`.github/workflows/**`.

**PR titles must follow [Conventional Commits](https://www.conventionalcommits.org/)** — the
`pr-title-check` job fails otherwise. Allowed types:

`feat`, `fix`, `chore`, `docs`, `style`, `refactor`, `perf`, `test`, `ci`, `build`

```
feat: add per-world spawner limits
fix: prevent XP duplication when claiming during a sell
docs: document the AUTO economy provider
```

Scopes are optional. The description must be at least 10 characters — an empty PR body fails CI.
Fill in the template (or pick a specific one from `.github/PULL_REQUEST_TEMPLATE/` by appending
`?template=bug_fix.md`, `feature.md`, or `maintenance.md` to the PR URL).

Before requesting review:

- [ ] `mvn clean test` passes.
- [ ] `mvn package` succeeds.
- [ ] Tested on a live Paper server — and Folia, if the change touches scheduling or world access.
- [ ] Added tests for anything with logic worth pinning down (version parsing, money maths, filters).
- [ ] Updated the relevant `.yml` defaults and `README.md` if the change is user-visible.
- [ ] No unrelated reformatting in the diff.

CI must be green (title check, description check, build & test on JDK 21, and dependency review)
before a PR can be merged.

## Adding tests

Tests are JUnit 5 in `src/test/java/`, mirroring the main package structure. Bukkit is not mocked,
so tests target pure logic — see `MinecraftVersionTest`, `ServerCompatibilityTest`, and
`MoneyMathTest` for the pattern. If a bug fix can be expressed as a failing test, add one; it is
the clearest way to show the fix works.

## Translations

Everything player-facing lives in `messages.yml`. Translations are welcome as a PR that adds a
language file — keep every key from the English default present so nothing falls back to a blank
string.
