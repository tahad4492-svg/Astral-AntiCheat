# Astral AntiCheat

An anti-cheat and anti-macro plugin for **Paper 1.21.11** (Java 21), by MrTahaDarvish.

**Astral blocks cheats, it does not ban.** Out of the box the hits of combat cheaters do not
register, flyers and speeders are pulled back, silent swaps, auto attribute swaps, auto stun
slams and mace exploits are cancelled, and macros are stopped. Nobody is kicked or banned
automatically. Staff get **one short alert line per player** that says what they might be
using ("Steve may be using KillAura (silent rotations) x6, Silent swap x2"), and every flag is
logged.

Minigames that move players themselves (a battle royale **battle bus**, launch pads, rides,
cutscenes) are handled automatically, see [Minigames and the battle bus](#minigames-and-the-battle-bus).

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
# -> target/Astral-1.1.0.jar   (also runs the cheat-client simulation tests)
```

---

## Block mode vs watch mode

| Setting | Default | Meaning |
|---|---|---|
| `settings.mitigation` | `true` | `true` = **block cheats**: cancel hits, set back movement, cancel swaps / block breaks / clicks. `false` = watch only (alerts + logs). |
| `settings.max-setback-distance` | `12` | A setback never moves a player further than this. Further away (e.g. after a long fall from a bus) only the illegal move is undone. |
| `punishments.enabled` | `false` | `false` = Astral **never** kicks or bans anyone by itself. |
| `checks.<check>.punish` | `false` | Per-check auto punish (only used when `punishments.enabled` is on). |

Staff can still kick or ban by hand with `/astral kick` and `/astral ban`.

> **Updating from 1.0?** Astral 1.0 only watched. On the first start, 1.1 updates your
> `config.yml` (`config-version: 2`): blocking is switched on, new options are added, your other
> values are kept. Set `settings.mitigation: false` if you only want to watch.

---

## Compact alerts

Staff with `astral.alerts` no longer get one chat line per flag. Astral groups the flags of a
player and sends one line that says what he is most likely using:

```
[Astral] Steve may be using KillAura (silent rotations) x6, Silent swap x2 [VL 14.0 | 45ms | blocked]
```

Hover it for the details (every check and type, the last measurement, ping, TPS, client), click
it to teleport. When lines are sent (`config.yml` -> `alerts`):

| Option | Default | Meaning |
|---|---|---|
| `compact` | `true` | `false` = old style, one line per flag |
| `gather-seconds` | `1` | The first flags of a player are collected this long, then sent as one line |
| `interval-seconds` | `5` | A **new** cheat of the same player is reported at most this often |
| `repeat-seconds` | `30` | The **same** cheats are only reminded this often (with the new count) |
| `reset-after-seconds` | `120` | After this long without flags everything counts as new again |

A cheater flagging 10 times a second for a minute gives 2 lines instead of 600. `/astral verbose`
and `/astral debug <check>` still show every single flag. The names ("KillAura (silent
rotations)", "Auto attribute swap" ...) can be changed in `messages.yml` under `cheat-names`.

---

## Minigames and the battle bus

Battle royale buses, launch pads, rides and cutscenes move players in ways no client can. Astral
recognises all the usual ways a plugin does that, with any ping:

* **Riding** an entity the plugin moves (armor stand / display / minecart / boat seat, happy
  ghast), and the jump off it: no flags while riding, the fall after it is checked normally.
* **Standing on** a moving platform made of shulkers, boats or minecarts.
* **Teleports every tick** along a route (Paper freezes the player until the client confirms a
  teleport; Astral waits for that instead of seeing a hovering player).
* **setVelocity every tick**, launch pads, double jump launchers (also when the plugin cancels
  the flight toggle).
* **setGravity(false)**, slow falling parachutes, levitation, gravity attribute changes (the
  client gets them half a ping later, Astral allows both for that moment).
* **Spectator** on the bus, back to survival in the air; elytra drops; plugin rockets.
* Boats a plugin lifts or moves are never "BoatFly".
* Setbacks never teleport a player back up to the bus or across the map.

For anything else:

* `settings.exempt-worlds: [lobby, bus_world]` - nobody is checked in these worlds.
* `/astral exempt <player|*> <seconds|off> [movement|combat|player|world|macro|all]` - skip checks
  for a while (default: movement). Works from the console, so a minigame can run it, e.g.
  `astral exempt %player% 20 movement` when a player boards the bus.

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
| `/astral exempt <player\|*> <seconds\|off> [category]` | Skip checks for a while (minigames, battle bus). Console friendly | `astral.command.exempt` |
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

The "When blocking" column is what happens with `settings.mitigation: true` (the default). In
watch mode every check only alerts and logs.

| Category | Check | Detects | When blocking |
|---|---|---|---|
| Combat | **Reach** | Hitting further than the vanilla range (lag compensated, spear `attack_range` aware) | Cancels the hit |
| Combat | **Hitbox** | Hits where the crosshair never pointed at the hitbox (hitbox expand, auras without rotations) | Blocks attacks |
| Combat | **KillAura** | Multi (2 targets in 1 tick), **Post** (attack after the movement packet = silent rotations), **Switch** (snapping between targets), NoSwing, Walls | Cancels / blocks attacks |
| Combat | **Aim** | **SilentAim** (flick to the target and straight back), GCD (rotations no mouse sensitivity can make), Robotic (identical steps) | SilentAim / Robotic: hits stop registering for a while. GCD: alert only |
| Combat | **Mace** | **FakeFall**: mace smash with a fall distance the player never fell (extra up/down packets before the hit = mace kill) | Cancels the smash |
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
| Movement | **BoatFly** | A boat the player drives rising in the air (boats a plugin moves are ignored) | Puts the boat back (nobody is thrown out) |
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
| Macro | **AutoClicker** | Sustained inhuman CPS, and the repeating rhythm of fixed-delay clickers (any CPS) | Hits stop registering for a while |
| Macro | **SilentSwap** | **SilentSwap**: slot switch after an attack / use in the same tick (switch, hit, switch back). **AttributeSwap**: swap to the other weapon in the hit tick and swap back a fixed time later, every time (auto attribute swap). **StunSlam**: axe breaks the shield + swap to the mace in the same tick (auto stun slam), or the mace always exactly 1 tick after the axe. Post / Spam slot switches | The impossible swap is cancelled and the hits after it do not register; a caught attribute swapper / stun slammer's swap hits do not register for 30 s. The 1-tick stun slam streak only alerts (`stun-slam.block-streaks`) |
| Macro | **AutoTotem** | Totem put back in the off-hand a few ticks after popping | Cancels the click |
| Macro | **ChestStealer** | Container looting with perfectly timed shift clicks | Cancels the click |
| Macro | **AutoFish** | Robotic fishing reaction times | Flag only |
| Macro | **ChatSpam** | Spam, duplicate messages, chat macros, command spam | Cancels the message |

### False-flag protection

* Lag protection: no flags while the server TPS is below `settings.lag-protection.min-tps`.
* Exemptions for teleports, knockback, explosions / wind charges, elytra, riptide, vehicles,
  moving entity platforms, pistons, slime / honey / beds, liquids, ladders, cobwebs, powder
  snow, ghost blocks, spears. Windows scale with the player's ping.
* No gravity, slow falling, levitation and gravity attribute changes are simulated, including
  the half-ping delay before the client applies them.
* Every check uses a buffer, so a single odd tick never flags.
* Combat checks only block when the order of packets is impossible for a vanilla client, or a
  pattern repeated many times; players on clients older than 1.21.2 (ViaVersion) are not judged
  by packet order at all.
* The violation level (VL) decays over time (`decay` per check).

---

## Tested against simulated cheat clients

`mvn test` runs the real Astral code (every check, listener and the violation manager)
against simulated players on a voxel world with vanilla physics. Each simulated client sends
its packets in the same order a real client does, so cheats like silent aim, silent swap and
post-packet auras look exactly like they do on a real server.

* **Legit players** (wandering on terrain, bunny hopping, ice tunnels, long falls, human PvP
  fights with knockback, building, mining, crystal PvP, eating while walking, hotbar
  scrolling, inventory use, mace smashes, attribute swapping and stun slams by hand) must
  produce **zero** flags.
* **Battle bus** scenarios at 50 to 800 ms ping (riding the bus, standing on a moving shulker
  floor, teleported / pushed along the route, gravity off, spectator, slow falling parachute,
  elytra drop, launch pads, a boat a plugin lifts) must produce **zero** flags and **zero**
  setbacks with blocking on. The same scenarios against Astral 1.0 fail 10 times (gravity off,
  slow falling, launch pads and teleport streams at high ping), which is the kind of flood
  servers with a battle bus saw.
* **Cheat clients** (flight, speed, bhop, timer, phase, blink, reach, hitbox, kill aura,
  silent aim, silent rotations, silent swap, auto attribute swap, auto stun slam, mace kill,
  auto clicker, auto totem, chest stealer, scaffold, nuker, auto pot and more) must be flagged by
  the right check, and with blocking on their hits must stop registering / they get pulled back.
* **Alerts**: 600 flags in a minute must give at most 3 alert lines.
* **Watch mode** is tested too: a cheater is flagged but never cancelled, set back or kicked.

After a run the full table is written to `target/astral-test-report.md`.

<!-- TEST-RESULTS -->
**Result of the last run: 128 / 128 scenarios passed** (60 legit scenarios with zero flags and zero setbacks, 62 cheats caught, 6 mode / alert / config checks).

| Cheat simulated | Result | Flagged as |
|---|---|---|
| Aimbot / aim assist (rotations without mouse steps) | PASS | Aim(GCD), Reach(Blatant), Reach(Distance) |
| Air jump / jetpack | PASS | Flight(Ascend) |
| Air strafe | PASS | Speed(Strafe) |
| AirPlace (placing against air) | PASS | BlockReach(Place), Scaffold(Face) |
| Attribute / mace swap after the hit | PASS | SilentSwap(SilentSwap) |
| Aura without rotations (hits outside the crosshair) | PASS | Hitbox(Miss) |
| Auto attribute swap (swap in the hit tick, swap back 2 ticks later), hits blocked after 11 hits, last 20 hits registered 0 | PASS | SilentSwap(AttributeSwap) |
| Auto clicker 12.5 CPS fixed delay | PASS | AutoClicker(Pattern) |
| Auto clicker 40 CPS | PASS | AutoClicker(CPS) |
| Auto pot (use item after the movement packet) | PASS | BadPackets(Multi), BadPackets(PostUse) |
| Auto stun slam (axe + mace in one tick), mace hits registered 0/6 | PASS | SilentSwap(SilentSwap), SilentSwap(Spam), SilentSwap(StunSlam) |
| Auto stun slam (mace exactly 1 tick after the axe, every time) | PASS | SilentSwap(StunSlam) |
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
| FastPlace (a block every tick) | PASS | Scaffold(Face) |
| Fly (hover) | PASS | Flight(Hover) |
| Fly (vertical / creative fly) | PASS | Flight(Ascend) |
| Fly / hover after jumping off the battle bus (300ms) | PASS | Flight(Hover) |
| Fly / hover after jumping off the battle bus (50ms) | PASS | Flight(Hover) |
| GhostHand (mining behind a wall) | PASS | GhostHand(Break) |
| Glide / slow fall | PASS | Flight(Glide) |
| Hacked client brand / channel | PASS | ClientBrand(Brand), ClientBrand(Channel) |
| Hotbar spam (4 slots per tick) | PASS | SilentSwap(Spam) |
| Inventory packets while attacking (auto armor / auto totem) | PASS | InventoryMove(Action), InventoryMove(Combo) |
| InventoryMove (clicking while running) | PASS | InventoryMove(Input) |
| Jesus (walking on water) | PASS | Flight(Hover), NoFall(GroundSpoof) |
| Mace kill (fake 10 block fall before the hit), smashes registered 0/6 | PASS | BadPackets(Multi), Mace(FakeFall) |
| Multi aura (2 targets per tick) | PASS | KillAura(Multi) |
| NoFall (on-ground spoof) | PASS | NoFall(GroundSpoof) |
| NoFall landing from the battle bus (300ms) | PASS | NoFall(GroundSpoof) |
| NoFall landing from the battle bus (50ms) | PASS | NoFall(GroundSpoof) |
| NoSlow (eating at full speed) | PASS | NoSlow(Item) |
| NoSwing | PASS | KillAura(NoSwing) |
| Nuker (3 blocks per tick) | PASS | Nuker(Multi) |
| OmniSprint (sprinting backwards) | PASS | OmniSprint(Omni) |
| Packet criticals | PASS | Criticals(Ground) |
| Phase / NoClip through a wall | PASS | BadPackets(Multi), Phase(Clip), Speed(Limit) |
| Reach 4.2 blocks | PASS | Reach(Distance) |
| Rotation aura with constant steps | PASS | Aim(Robotic) |
| Scaffold rotate-place-rotate back (extra rotation packet) | PASS | BadPackets(Multi), BlockReach(Place), Scaffold(Post) |
| Scaffold with silent rotations (place after the movement packet) | PASS | BlockReach(Place), Scaffold(Post) |
| Silent aim (flick to target and back) | PASS | Aim(SilentAim) |
| Silent rotation aura (aim inside the movement packet) | PASS | Aim(SilentAim) |
| Silent rotation aura (attack after movement) | PASS | Aim(SilentAim) |
| Silent swap (switch, hit, switch back) | PASS | SilentSwap(SilentSwap) |
| Silent swap in one tick: swaps cancelled 10/10, mace hits registered 0 | PASS | SilentSwap(SilentSwap) |
| Slot swap after the movement packet | PASS | SilentSwap(Post) |
| Speed x1.5 (ground) | PASS | Speed(Limit) |
| Step (1 block) | PASS | Flight(HighJump), Step(Height) |
| Switch aura (front/back every tick) | PASS | Aim(Robotic), KillAura(Switch) |
| Timer 1.25x | PASS | Timer(Balance) |
| Velocity / anti knockback | PASS | Velocity(Vertical) |
| Mixed: Human vs aura user in one fight: only the aura user flagged | PASS | human: no flags / aura: Aim(SilentAim)  KillAura(Post) |
| Mode: Watch mode: reach is flagged but the hits are NOT cancelled | PASS | Hitbox(Miss)  Reach(Blatant) |
| Mode: Block mode (mitigation: true): reach hits cancelled, flyer pulled back down | PASS | Flight(Ascend) |
| Alerts: 600 Fly flags in one minute -> 2 alert lines | PASS | lines=2 flags shown=311 |
| Alerts: New cheat while flagging: reported at once, listed first | PASS | KillAura (silent rotations) |
| Config: config.yml from Astral 1.0 updated to blocking + compact alerts, own values kept | PASS | mitigation=true compact=true |

Legit scenarios (all with **no flags**; battle bus ones at 50 / 150 / 300 / 500 / 800 ms ping and with no setbacks): attribute swapping by hand (60 hits); building a wall and mining it; carried by plugin velocity, slow falling parachute; crystal PvP at human speed; double jump launch pad (flight toggle cancelled by the plugin); driving a boat a plugin lifts into the air; eating while walking; fast stun slams by hand (30 combos, 1-3 ticks); human PvP fight, attacker; human PvP fight, defender; ice tunnel jump boosting (123.73 blocks); inventory sorting + manual totem; mace smash after falling off a 9 block pillar; plugin turned gravity off, then on again mid-air; random wandering; riding the battle bus, jumping off, free fall; spectating the bus, survival mid-air; sprint jumping non stop; standing on a moving shulker bus floor, walking off; switching hotbar slots then hitting; teleported along the bus route, elytra drop; walking off a 26 block tower.

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
