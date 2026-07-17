# Groupman TCG

Groupman TCG is a group-first RuneLite challenge plugin powered by the
[OSRS TCG](https://runelite.net/plugin-hub/show/osrs-tcg) card collection.
Tracked NPCs and items stay locked until their matching card is collected.

For official Group Ironman and Hardcore Group Ironman teams, every collected
card becomes a permanent shared unlock. Live collection snapshots synchronise
through RuneLite Party and are accepted only from display names on the
in-game GIM roster.

## Restriction coverage

- Block attacks and item/spell interactions with locked tracked NPCs.
- Block taking, telegrabbing, equipping, buying, withdrawing, and using locked
  tracked items.
- **Visual locks:** tracked locked NPCs receive a configurable grey model
  outline; locked ground items receive an individual model outline; and locked
  inventory, bank, and equipment items receive a translucent grey wash and
  padlock marker. Each visual can be switched off independently.
- **Gathering:** Woodcutting trees require their log cards; Mining rocks require
  their ore/resource cards; Fishing spots can require any or every possible
  catch card; Runecrafting altars require talisman/tiara alternatives and
  optionally the rune card.
- **Processing:** Cooking supports cooked and burnt-food requirements;
  Firemaking supports logs and Tinderbox; Smelting and Smithing independently
  configure input/output requirements; Crafting, Enchanting, Fletching, and
  Herblore support inputs, outputs, both, or off.
- **Farming:** separate raking, planting, produce, and compost requirements.
- **Hunter:** birds, butterflies, implings, salamanders, pitfalls,
  chinchompas, and optional full rumour-master assignment pools.
- **Slayer:** master cards, complete assignment pools, and optional superior
  variants can be enabled independently.
- **Thieving:** loot-only or NPC-plus-loot pickpocket rules, a dedicated Master
  Farmer difficulty, and any/all market-stall loot modes.
- **Sailing:** tiered ship-part/material requirements and shipwreck salvage
  cards.
- Solo or official Group Ironman collection modes.
- Grow-only, profile-scoped group unlock cache.
- Compact RuneLite Party snapshots with catalog compatibility checks.
- Authenticated Cloudflare sync through Sqwiglyy's public service by default,
  with an optional self-hosted Worker URL for groups that want to own their
  database, durable unlocks, provenance, and offline pack history.
- Sidebar collection browser with a shared view and one cached collection per
  verified group member. Shared search results identify the current owner;
  hovering the owner shows locally available copy, foil, original-puller, and
  pull-date details.
- Verified group pack reveals: a few seconds after a teammate opens a pack,
  show all five pulled cards in a small top-centre window, including duplicate,
  `NEW`, and `FOIL` status. Multiple reveals queue rather than overwrite one
  another.
- **Top Trumps duels:** right-click an online verified group member to challenge
  them. After they accept in the chatbox, two distinct cards are drawn from the
  shared collection and compared using OSRS TCG's own value/level/override score
  formula. Both players receive the same illustrated result and a deterministic
  tie-break guarantees a winner.
- Sidebar collection search and group synchronisation status.

Items and NPCs with no OSRS TCG card are never restricted. Enforcement is
client-side and therefore remains an honour-system challenge.

RuneLite Party remains the low-latency live transport. The optional hosted sync
stores approved members' unlocks and card-instance provenance so everyone
converges after reconnecting even when no two members were online together.
Pack reveals received through RuneLite Party appear immediately; authenticated
server history supplies reveals missed while offline. Both paths are filtered
against the official in-game GIM roster. Sharing and receiving pack reveals can
be disabled independently, and the popup duration is configurable.
Top Trumps challenges are likewise live-only, targeted to one verified group
member, and require explicit acceptance before cards are drawn.
Card artwork is loaded on demand from the OSRS Wiki URLs in OSRS TCG's public
card catalog and cached under RuneLite's `Groupman-TCG/card-art-v1` directory;
the names and card frames still display if artwork is unavailable.

OSRS TCG records a card instance ID, card name, foil state, original puller and
pull time. It does not retain the exact booster type or RuneScape activity that
awarded the pack, and a traded card retains its original pull metadata. The
companion server schema preserves those fields and labels debug-granted cards,
but must not claim that it can distinguish a direct pull from a later trade.

## Hosted group setup

1. Decide whether to use the default Sqwiglyy service or
   [deploy the companion Worker](https://github.com/Sqwiglyy/groupman-tcg-server)
   to one teammate's Cloudflare account.
2. If self-hosting, every teammate sets **Hosted server URL** to the same
   `https://...workers.dev` address before joining or creating the group.
3. Log into the Group Ironman account and open the Groupman TCG sidebar.
4. One teammate chooses **Create hosted group**, then copies the displayed
   group ID and invite code.
5. Each teammate chooses **Join hosted group** and enters those two values.
6. The owner approves only names that the plugin confirms are on the official
   GIM roster.

The server selected when a profile creates or joins a group is saved with that
profile. Its bearer token is always sent back to that same server, even if the
global URL setting is later changed. To move to another server, disconnect the
hosted profile, choose the new URL, and create or join a group there.

The bearer token is stored in that RuneScape profile's local RuneLite
configuration and is never displayed in the sidebar or written to plugin logs.
It is not a Jagex credential. Disconnecting removes the local token while
leaving the grow-only unlock cache intact.

The service receives the RuneScape display name, hosted group membership, OSRS
TCG card instance IDs/names, foil state, original-puller label, pull timestamp,
and pack contents. It does not receive a Jagex password, bank PIN, chat, game
session token, or general gameplay telemetry.

Production restrictions consume RuneLite menu clicks. Keyboard shortcuts that
confirm a make-X interface without producing a menu click remain honour-system.
The general item lock applies to acquiring, equipping, consuming, and unrelated
item actions; recognised skill interactions follow their dedicated skill mode.
Coins are exempt from the general item lock by default, and the exemption list
is configurable. Restrictions automatically suspend inside live Last Man
Standing matches unless that safety option is disabled.

Visual styling represents the general NPC/item card lock and follows those
restriction toggles and item exemptions. Context-sensitive skill rules are
explained when an action is attempted because the same item may be valid for
one recipe and locked for another.

## Development

Requires Java 11. Run the development client with:

```powershell
$env:JAVA_HOME='C:\path\to\jdk-11'
.\gradlew.bat run
```

Maintained by **Sqwiglyy**. Complete a live two-account GIM test before the
first Plugin Hub submission.
