# OnePlayerSleep+

A Paper/Spigot plugin that skips the night when enough players sleep, instead
of requiring everyone in bed.

The requirement is either a fixed number of sleepers or a percentage of online
players. When it is met, time advances smoothly to morning with particle and
sound effects at each sleeper's bed, and an action bar shows progress toward
the requirement while anyone is in bed. An optional restriction limits skips
to every other night.

Published on Spigot: <https://www.spigotmc.org/resources/124265/>

## Requirements

- Paper or Spigot 1.21+
- Java 21

## Installation

Drop the jar into your server's `plugins` folder and restart. The plugin
writes `config.yml` and `messages.yml` into `plugins/OnePlayerSleepPlus/` on
first start.

## Configuration

`config.yml`:

- `sleep_requirement.mode` — `fixed` or `percentage`.
- `sleep_requirement.fixed_players` — sleepers needed in `fixed` mode.
- `sleep_requirement.percentage` — share of online players needed in
  `percentage` mode.
- `announce.enabled` — broadcast a message when the night is skipped.
- `action_bar.enabled` — show sleep progress in the action bar.
- `time_skip_effects` — particle and sound shown during the skip.
- `night_skip_restriction.every_other_night` — allow skipping only every
  other night.
- `auto_updater.enabled` — check for new versions on startup and download
  them for install at shutdown.

All player-facing text lives in `messages.yml` and supports `&` color codes.
Removing a message disables it.

## Permissions

- `oneplayersleepplus.update.notify` — receive update notifications
  (default: op).

## Building

The plugin depends on two libraries that install to your local Maven
repository:

```bash
git clone https://github.com/bradley-t-t/coreapi && mvn -f coreapi install
git clone https://github.com/bradley-t-t/updaterapi && mvn -f updaterapi install
```

Then build the plugin jar:

```bash
mvn package
```

The shaded jar lands in `target/`.
