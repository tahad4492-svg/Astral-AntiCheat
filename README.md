# MrTahaDarvish's AntiCheat (Astral)

An anti-cheat and anti-macro plugin for **Paper 1.21.11** (Java 21).

Astral reads the exact position of every client tick. Since Minecraft 1.21.2 the client
sends a "tick end" packet after each tick, and Paper exposes it as `ClientTickEndEvent`.
Astral uses it to rerun vanilla physics (gravity, friction, acceleration, jump, step) and
compare the result with what the client claims. Combat checks use lag compensation (the
target's position history matched to the attacker's ping). Timing-based checks count client
ticks, so network jitter does not affect them.

---

## Installation

1. Build it (see below) or take `MrTahaDarvishAntiCheat-<version>.jar`.
2. Put the jar in your server's `plugins/` folder (Paper **1.21.11**, Java 21).
3. Start the server. The config is written to `plugins/MrTahaDarvishAntiCheat/`.

Optional: **ViaVersion** (shows the real client version of players on other versions) and
**Floodgate/Geyser** (Bedrock players are detected and skipped by default).

### Building

```bash
mvn clean package
# -> target/MrTahaDarvishAntiCheat-1.0.0.jar
```

---

## Commands

Main command: `/astral` (aliases: `/astralac`, `/anticheat`, `/mtdac`, `/ac`)

| Command | Description | Permission |
|---|---|---|
| `/astral help` | List all commands | `astral.command.help` (everyone) |
| `/astral version [player]` | Plugin, server and **client version** (yours, or another player's) | `astral.command.version` (everyone), `astral.command.version.others` |
| `/astral reload` | Reload `config.yml` and `messages.yml` | `astral.command.reload` |
| `/astral alerts [on\|off]` | Toggle receiving flags | `astral.command.alerts` |
| `/astral verbose` | See **every** flag (no cooldown, no minimum VL) | `astral.command.verbose` |
| `/astral checks [category]` | List all checks (click to toggle) | `astral.command.checks` |
| `/astral toggle <check> [on\|off]` | Enable / disable a check (saved to config) | `astral.command.toggle` |
| `/astral info <player>` | Full profile: client, ping, CPS, bypass, VL | `astral.command.info` |
| `/astral violations <player>` | Violation level per check | `astral.command.violations` |
| `/astral logs <player> [page]` | Recent flags (hover for details) | `astral.command.logs` |
| `/astral reset <player\|*> [check]` | Reset violations | `astral.command.reset` |
| `/astral bypass <player\|list>` | Toggle anti-cheat bypass for a player (saved) | `astral.command.bypass` |
| `/astral kick <player> [reason]` | Kick with the Astral screen | `astral.command.kick` |
| `/astral ban <player> [time] [reason]` | Ban (`30m`, `12h`, `7d`, `1mo`, `perm`) | `astral.command.ban` |
| `/astral unban <player>` | Unban | `astral.command.unban` |
| `/astral freeze <player>` | Freeze / unfreeze for a screenshare | `astral.command.freeze` |
| `/astral tp <player>` | Teleport to a player | `astral.command.tp` |
| `/astral cps <player>` | Show a player's CPS in your action bar | `astral.command.cps` |
| `/astral ping <player>` | Show ping | `astral.command.ping` |
| `/astral client <player>` | Client version, protocol, brand and Java/Bedrock | `astral.command.client` |
| `/astral gui` | Staff menu: players sorted by VL, actions, check toggles | `astral.command.gui` |
| `/astral stats` | Uptime, flags, punishments, TPS | `astral.command.stats` |
| `/astral debug <check>` | See every flag of one check | `astral.command.debug` |

## Permissions

| Permission | Default | What it does |
|---|---|---|
| `astral.bypass` | false | **Never checked, never flagged, never set back, never blocked.** |
| `astral.bypass.<check>` | false | Bypass one check only, e.g. `astral.bypass.reach` |
| `astral.alerts` | op | Receive flags |
| `astral.verbose` | op | Use verbose / debug output |
| `astral.notify.punish` | op | Get told when someone is punished |
| `astral.admin` | op | Every command and alert (does **not** include bypass) |
| `astral.*` | false | Everything **including** bypass |

> OPs are **not** bypassed by default, so you can test the anti-cheat as OP.
> Give `astral.bypass` (LuckPerms: `/lp user <name> permission set astral.bypass true`) or use
> `/astral bypass <name>` for players who should be ignored.

---

## Checks

| Category | Check | Detects | Blocking |
|---|---|---|---|
| Combat | **Reach** | Hitting further than the vanilla range (lag compensated, spear `attack_range` aware) | Cancels the hit |
| Combat | **Hitbox** | Hits where the crosshair never pointed at the hitbox (hitbox expand) | Blocks attacks |
| Combat | **KillAura** | Multi (2 targets in 1 tick), Post (attack after movement packet), NoSwing, Walls | Cancels / blocks attacks |
| Combat | **AutoBlock** | Attacking while blocking, eating or drawing a bow | Cancels the hit |
| Combat | **Criticals** | Critical hits while standing on the ground (packet crits) | Cancels the hit |
| Combat | **Velocity** | Anti-knockback (vertical and horizontal) | Flag |
| Combat | **Aim** | Rotations that no mouse sensitivity can produce (GCD), robotic steps, snap aim | Flag (no punish by default) |
| Movement | **Flight** | Gravity prediction: fly, glide, air jump, spider, hover, high jump, mini jump, fast fall | Setback |
| Movement | **Speed** | Friction / acceleration prediction (speed, bhop, long jump) and air strafe | Setback |
| Movement | **NoFall** | On-ground spoofing | Applies the fall damage |
| Movement | **Timer** | Game running faster than 20 ticks/s (client tick balance) | Setback |
| Movement | **Step** | Stepping up more than the step height | Setback |
| Movement | **Jesus** | Walking on water / lava | Setback |
| Movement | **FastClimb** | Climbing faster than 0.1176 blocks/tick | Setback |
| Movement | **ElytraFly** | Elytra hover / climb without speed / impossible speed | Stops gliding + setback |
| Movement | **NoSlow** | Full speed while eating / blocking / sneaking | Setback |
| Movement | **OmniSprint** | Sprinting backwards / sideways (key input) or while starving | Stops sprint + setback |
| Movement | **Phase** | Moving through full solid blocks | Setback |
| Movement | **BoatFly** | Boats rising in the air | Ejects + resets boat |
| Player | **InventoryMove** | Moving / turning with an inventory open (keys are sent by 1.21.2+ clients) | Cancels the click |
| Player | **FastBow** | Bow spam | Cancels the shot |
| Player | **ClientBrand** | Known hacked client brands / plugin channels | Kick |
| World | **FastBreak** | Mining faster than vanilla (tool, enchants, haste, water, ground) | Cancels the break |
| World | **Nuker** | Several blocks per tick, too many per second, blocks outside the view | Cancels the break |
| World | **BlockReach** | Breaking / placing too far | Cancels |
| World | **GhostHand** | Breaking / placing through walls | Cancels |
| World | **FastPlace** | Too many blocks per tick / second, robotic timing | Cancels |
| World | **Scaffold** | Air place, clicking hidden faces, placing without looking at the block | Cancels / blocks placing |
| World | **XRay** | Rare ore ratio far above normal | Alert only |
| Macro | **AutoClicker** | Sustained inhuman CPS, repeating click rhythm of auto clickers | Cancels hits above `cancel-cps` |
| Macro | **AutoTotem** | Totem put back in the off-hand a few ticks after popping | Cancels the click |
| Macro | **ChestStealer** | Container looting with perfectly timed shift clicks | Cancels the click |
| Macro | **HotbarMacro** | Swap-hit macros (switching slot after attacking in the same tick), slot spam | Flag |
| Macro | **AutoFish** | Robotic fishing reaction times | Flag |
| Macro | **ChatSpam** | Spam, duplicate messages, chat macros, command spam | Cancels the message |

### False-flag protection

* Lag protection: no flags while the server TPS is below `settings.lag-protection.min-tps`.
* Exemptions for teleports, knockback, explosions / wind charges, elytra, riptide, vehicles,
  pistons, slime / honey / beds, liquids, ladders, cobwebs, powder snow, ghost blocks, spears.
* Every check uses a buffer, so a single odd tick never flags.
* The violation level (VL) decays over time (`decay` per check).

---

## Configuration

`config.yml`: global settings plus a section per check with:

```yaml
reach:
  enabled: true
  alert-vl: 2.0        # staff are alerted from this VL
  mitigate: true       # block the cheat (cancel / setback)
  mitigate-vl: 0.0     # VL needed before blocking starts
  punish: true
  punish-vl: 25
  punish-commands: ["astral kick %player% Unfair Advantage (Reach)"]
  decay: 2.0           # VL removed every violations.decay-interval-seconds
```

Punish commands support `%player% %uuid% %check% %type% %vl% %ping%`.
Example auto-ban: `"astral ban %player% 30d Cheating (%check%)"`.

`messages.yml`: every message, alert format (with hover and click-to-teleport),
kick/ban screens and the punish broadcast. Colours: `&a` codes and `&#RRGGBB` hex.

Logs are written to `plugins/MrTahaDarvishAntiCheat/logs/<date>.log`.

---

## Notes

* No anti-cheat can catch everything. Use the alerts, `/astral logs` and `/astral info`, and
  tune `config.yml` for your server.
* **Aim** and **XRay** are statistical, so they alert staff and do not punish by default.
* **Velocity** is also flag-only by default: knockback can't be undone after the fact.
