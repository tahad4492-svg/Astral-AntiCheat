# Astral AntiCheat

An anti-cheat and anti-macro plugin for **Paper 1.21.11** (Java 21), by MrTahaDarvish.

**Astral watches, it does not punish.** Out of the box nobody is kicked, banned, set back or
blocked. Staff get an alert for every cheat, and every flag is logged, so you can see exactly
who is cheating and decide yourself. Blocking and automatic punishments can be turned on in
`config.yml` if you ever want them.

Astral reads the exact position of every client tick. Since Minecraft 1.21.2 the client
sends a "tick end" packet after each tick, and Paper exposes it as `ClientTickEndEvent`.
Astral uses it to rerun vanilla physics (gravity, friction, acceleration, jump, step) and
compare the result with what the client claims. It also knows the order in which a vanilla
client sends its packets inside a tick (slot change, actions, swing, movement, tick end), so
anything sent **after** the movement packet or **between** actions gives away silent
rotations and silent swaps. Combat checks use lag compensation (the target's position history
matched to the attacker's ping).

---

## Installation

1. Build it (see below) or take `Astral-<version>.jar`.
2. Put the jar in your server's `plugins/` folder (Paper **1.21.11**, Java 21).
3. Start the server. The config is written to `plugins/Astral/`.

Optional: **ViaVersion** (shows the real client version of players on other versions) and
**Floodgate/Geyser** (Bedrock players are detected and skipped by default).

### Building

```bash
mvn clean package
# -> target/Astral-1.0.0.jar   (also runs the cheat-client simulation tests)
```

---

## Watch mode vs block mode

| Setting | Default | Meaning |
|---|---|---|
| `settings.mitigation` | `false` | `false` = **watch only** (alerts + logs). `true` = also block cheats: cancel hits, set back movement, cancel block breaks, etc. |
| `punishments.enabled` | `false` | `false` = Astral **never** kicks or bans anyone by itself. |
| `checks.<check>.punish` | `false` | Per-check auto punish (only used when `punishments.enabled` is on). |

Staff can still kick or ban by hand with `/astral kick` and `/astral ban`.

---

## Commands

Main command: `/astral` (aliases: `/astralac`, `/anticheat`, `/ac`)

| Command | Description | Permission |
|---|---|---|
| `/astral help` | List all commands | `astral.command.help` (everyone) |
| `/astral version [player]` | Astral version, mode, server version and **client version** (yours, or another player's) | `astral.command.version` (everyone), `astral.command.version.others` |
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
| `/astral kick <player> [reason]` | Kick with the Astral screen (manual only) | `astral.command.kick` |
| `/astral ban <player> [time] [reason]` | Ban (`30m`, `12h`, `7d`, `1mo`, `perm`) (manual only) | `astral.command.ban` |
| `/astral unban <player>` | Unban | `astral.command.unban` |
| `/astral freeze <player>` | Freeze / unfreeze for a screenshare | `astral.command.freeze` |
| `/astral tp <player>` | Teleport to a player | `astral.command.tp` |
| `/astral cps <player>` | Show a player's CPS in your action bar | `astral.command.cps` |
| `/astral ping <player>` | Show ping | `astral.command.ping` |
| `/astral client <player>` | Client version, protocol, brand and Java/Bedrock | `astral.command.client` |
| `/astral gui` | Staff menu: players sorted by VL, violations, logs, check toggles | `astral.command.gui` |
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

The "When blocking" column only applies with `settings.mitigation: true`. In the default
watch mode every check only alerts and logs.

| Category | Check | Detects | When blocking |
|---|---|---|---|
| Combat | **Reach** | Hitting further than the vanilla range (lag compensated, spear `attack_range` aware) | Cancels the hit |
| Combat | **Hitbox** | Hits where the crosshair never pointed at the hitbox (hitbox expand, auras without rotations) | Blocks attacks |
| Combat | **KillAura** | Multi (2 targets in 1 tick), **Post** (attack after the movement packet = silent rotations), **Switch** (snapping between targets), NoSwing, Walls | Cancels / blocks attacks |
| Combat | **Aim** | **SilentAim** (flick to the target and straight back), GCD (rotations no mouse sensitivity can make), Robotic (identical steps) | Flag only |
| Combat | **CrystalAura** | Breaking an end crystal in the same tick it was placed (predict) | Cancels the hit |
| Combat | **AutoBlock** | Attacking while blocking, eating or drawing a bow | Cancels the hit |
| Combat | **Criticals** | Critical hits while standing on the ground (packet crits) | Cancels the hit |
| Combat | **Velocity** | Anti-knockback (vertical and horizontal) | Flag only |
| Movement | **Flight** | Gravity prediction: fly, glide, air jump, spider, hover, high jump, fast fall | Setback |
| Movement | **Speed** | Friction / acceleration prediction (speed, bhop, long jump, air strafe) | Setback |
| Movement | **NoFall** | On-ground spoofing | Applies the fall damage |
| Movement | **Timer** | Game running faster than 20 ticks/s (client tick balance) | Setback |
| Movement | **Step** | Stepping up more than the step height | Setback |
| Movement | **Jesus** | Walking on water / lava | Setback |
| Movement | **FastClimb** | Climbing faster than 0.1176 blocks/tick | Setback |
| Movement | **ElytraFly** | Elytra hover / climb without speed / impossible speed | Stops gliding + setback |
| Movement | **NoSlow** | Full speed while eating / blocking / sneaking | Setback |
| Movement | **OmniSprint** | Sprinting backwards / sideways (key input) or while starving | Stops sprint + setback |
| Movement | **Phase** | Moving through solid blocks, ending up inside a block (noclip / vclip) | Setback |
| Movement | **BoatFly** | Boats rising in the air | Ejects + resets boat |
| Player | **InventoryMove** | Moving / turning with an inventory open, inventory clicks in the same tick as attacks or after the movement packet (inventory macros) | Cancels the click |
| Player | **BadPackets** | Using items, digging or interacting after the movement packet (auto pot, silent rotation macros); more than one movement packet in a tick (rotate-act-rotate back, blink) | Cancels the action |
| Player | **FastBow** | Bow spam | Cancels the shot |
| Player | **ClientBrand** | Known hacked client brands / plugin channels | Kick (block mode only) |
| World | **FastBreak** | Mining faster than vanilla (tool, enchants, haste, water, ground) | Cancels the break |
| World | **Nuker** | Several blocks per tick, too many per second, blocks outside the view | Cancels the break |
| World | **BlockReach** | Breaking / placing too far | Cancels |
| World | **GhostHand** | Breaking / placing through walls | Cancels |
| World | **FastPlace** | Too many blocks per tick / second, robotic timing | Cancels |
| World | **Scaffold** | Air place, clicking hidden faces, placing without looking at the block, placing after the movement packet | Cancels / blocks placing |
| World | **XRay** | Rare ore ratio far above normal | Alert only |
| Macro | **AutoClicker** | Sustained inhuman CPS, and the repeating rhythm of fixed-delay clickers (any CPS) | Cancels hits above `cancel-cps` |
| Macro | **SilentSwap** | Switching hotbar slot in the same tick as an attack / use (swap macros, auto weapon, mace swap), slot switches after the movement packet, slot spam | Flag only |
| Macro | **AutoTotem** | Totem put back in the off-hand a few ticks after popping | Cancels the click |
| Macro | **ChestStealer** | Container looting with perfectly timed shift clicks | Cancels the click |
| Macro | **AutoFish** | Robotic fishing reaction times | Flag only |
| Macro | **ChatSpam** | Spam, duplicate messages, chat macros, command spam | Cancels the message |

### False-flag protection

* Lag protection: no flags while the server TPS is below `settings.lag-protection.min-tps`.
* Exemptions for teleports, knockback, explosions / wind charges, elytra, riptide, vehicles,
  pistons, slime / honey / beds, liquids, ladders, cobwebs, powder snow, ghost blocks, spears.
* Every check uses a buffer, so a single odd tick never flags.
* The violation level (VL) decays over time (`decay` per check).

---

## Tested against simulated cheat clients

`mvn test` runs the real Astral code (every check, listener and the violation manager)
against simulated players on a voxel world with vanilla physics. Each simulated client sends
its packets in the same order a real client does, so cheats like silent aim, silent swap and
post-packet auras look exactly like they do on a real server.

* **Legit players** (wandering on terrain, bunny hopping, ice tunnels, long falls, human PvP
  fights with knockback, building, mining, crystal PvP, eating while walking, hotbar
  scrolling, inventory use) must produce **zero** flags.
* **Cheat clients** (flight, speed, bhop, timer, phase, blink, reach, hitbox, kill aura,
  silent aim, silent rotations, silent swap, mace swap, auto clicker, auto totem, chest stealer,
  scaffold, nuker, auto pot and more) must be flagged by the right check.
* **Watch mode** is tested too: a cheater is flagged but never cancelled, set back or kicked.

After a run the full table is written to `target/astral-test-report.md`.

**Result of the last run: 75 / 75 scenarios passed** (19 legit scenarios with zero flags, 53 cheats caught, 3 mode / mixed checks).

| Cheat simulated | Result | Flagged as |
|---|---|---|
| Aimbot / aim assist (rotations without mouse steps) | PASS | Aim(GCD), Hitbox(Miss), Reach(Blatant), Reach(Distance) |
| Air jump / jetpack | PASS | Flight(Ascend) |
| Air strafe | PASS | Speed(Strafe) |
| AirPlace (placing against air) | PASS | BlockReach(Place), Scaffold(Face), Scaffold(Rotation) |
| Attribute / mace swap after the hit | PASS | SilentSwap(SilentSwap) |
| Aura without rotations (hits outside the crosshair) | PASS | Hitbox(Miss) |
| Auto clicker 12.5 CPS fixed delay | PASS | AutoClicker(Pattern) |
| Auto clicker 40 CPS | PASS | AutoClicker(CPS) |
| Auto pot (use item after the movement packet) | PASS | BadPackets(Multi), BadPackets(PostUse) |
| AutoBlock (hit while blocking) | PASS | AutoBlock(Shield) |
| AutoFish (same reaction every bite) | PASS | AutoFish(Reaction) |
| AutoTotem (totem back in 1 tick) | PASS | AutoTotem(Speed) |
| Bhop speed x1.25 | PASS | Speed(Limit) |
| Blink (movement held back, then sent at once) | PASS | BadPackets(Multi), Speed(Limit) |
| BlockReach (7 blocks) | PASS | BlockReach(Break) |
| Chest stealer (perfect 2 tick delay) | PASS | ChestStealer(Steal) |
| Crystal aura (ID predict / instant break) | PASS | CrystalAura(Predict) |
| ElytraFly (hovering) | PASS | ElytraFly(Hover) |
| FastBow (20 arrows per second) | PASS | FastBow(Rate) |
| FastBreak (4x speed mine) | PASS | FastBreak(Time) |
| FastClimb (ladder) | PASS | FastClimb(Speed) |
| FastPlace (a block every tick) | PASS | FastPlace(Robotic), Scaffold(Face), Scaffold(Rotation) |
| Fly (hover) | PASS | Flight(Hover) |
| Fly (vertical / creative fly) | PASS | Flight(Ascend) |
| GhostHand (mining behind a wall) | PASS | GhostHand(Break) |
| Glide / slow fall | PASS | Flight(Glide) |
| Hacked client brand / channel | PASS | ClientBrand(Brand), ClientBrand(Channel) |
| Hotbar spam (4 slots per tick) | PASS | SilentSwap(Spam) |
| Inventory packets while attacking (auto armor / auto totem) | PASS | InventoryMove(Action), InventoryMove(Combo) |
| InventoryMove (clicking while running) | PASS | InventoryMove(Input) |
| Jesus (walking on water) | PASS | Flight(Hover), Jesus(Walk), NoFall(GroundSpoof) |
| Multi aura (2 targets per tick) | PASS | Hitbox(Miss), KillAura(Multi) |
| NoFall (on-ground spoof) | PASS | NoFall(GroundSpoof) |
| NoSlow (eating at full speed) | PASS | NoSlow(Item) |
| NoSwing | PASS | KillAura(NoSwing) |
| Nuker (3 blocks per tick) | PASS | Nuker(Multi), Nuker(Speed) |
| OmniSprint (sprinting backwards) | PASS | OmniSprint(Omni) |
| Packet criticals | PASS | Criticals(Ground) |
| Phase / NoClip through a wall | PASS | BadPackets(Multi), Phase(Clip), Speed(Limit) |
| Reach 4.2 blocks | PASS | Reach(Distance) |
| Rotation aura with constant steps | PASS | Aim(Robotic), Hitbox(Miss) |
| Scaffold rotate-place-rotate back (extra rotation packet) | PASS | BadPackets(Multi), Scaffold(Post) |
| Scaffold with silent rotations (place after the movement packet) | PASS | Scaffold(Post) |
| Silent aim (flick to target and back) | PASS | Aim(SilentAim) |
| Silent rotation aura (aim inside the movement packet) | PASS | Aim(SilentAim), KillAura(Post) |
| Silent rotation aura (attack after movement) | PASS | Aim(SilentAim), KillAura(Post) |
| Silent swap (switch, hit, switch back) | PASS | SilentSwap(SilentSwap) |
| Slot swap after the movement packet | PASS | SilentSwap(Post) |
| Speed x1.5 (ground) | PASS | Speed(Limit) |
| Step (1 block) | PASS | Flight(HighJump), Step(Height) |
| Switch aura (front/back every tick) | PASS | Aim(Robotic), KillAura(Switch) |
| Timer 1.25x | PASS | Timer(Balance) |
| Velocity / anti knockback | PASS | Velocity(Vertical) |
| Mixed: Human vs aura user in one fight: only the aura user flagged | PASS | human: no flags / aura: Aim(SilentAim)  KillAura(Post) |
| Mode: Watch mode: reach is flagged but the hits are NOT cancelled | PASS | Hitbox(Miss)  Reach(Blatant) |
| Mode: Block mode (mitigation: true): reach hits cancelled, flyer pulled back down | PASS | Flight(Ascend) |

Legit scenarios (all with **no flags**): building a wall and mining it; crystal PvP at human speed; eating while walking; human PvP fight, attacker; human PvP fight, defender; ice tunnel jump boosting (123.73 blocks); inventory sorting + manual totem; random wandering; sprint jumping non stop; switching hotbar slots then hitting; walking off a 26 block tower.

> These are simulations of how cheat clients behave, not recordings of real clients on a
> real server. They prove the checks react to those behaviours and stay quiet for normal
> play, but tune `config.yml` on your own server too.

---

## Configuration

`config.yml`: global settings plus a section per check with:

```yaml
reach:
  enabled: true
  alert-vl: 2.0        # staff are alerted from this VL
  mitigate: true       # block the cheat (only used when settings.mitigation is true)
  mitigate-vl: 0.0     # VL needed before blocking starts
  punish: false        # auto punish (only used when punishments.enabled is true)
  punish-vl: 25
  punish-commands: []  # e.g. ["astral ban %player% 30d Cheating (%check%)"]
  decay: 2.0           # VL removed every violations.decay-interval-seconds
```

Punish commands support `%player% %uuid% %check% %type% %vl% %ping%`.

`messages.yml`: every message, alert format (with hover and click-to-teleport),
kick/ban screens and the punish broadcast. Colours: `&a` codes and `&#RRGGBB` hex.

Logs are written to `plugins/Astral/logs/<date>.log`.

---

## Notes

* No anti-cheat can catch everything. Use the alerts, `/astral logs`, `/astral info` and
  `/astral gui`, and tune `config.yml` for your server.
* **Aim** and **XRay** are statistical, so treat them as hints and watch the player.
