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

The multiplayer transport has no external server. At least two members must be
online together in the same RuneLite Party for new unlocks to cross between
their local caches; a member carrying the combined cache can relay it later.
Pack-opening popups are also live-only and are accepted only from RuneLite
Party members on the official in-game GIM roster. Sharing and receiving pack
reveals can be disabled independently, and the popup duration is configurable.
Top Trumps challenges are likewise live-only, targeted to one verified group
member, and require explicit acceptance before cards are drawn.
Card artwork is loaded on demand from the OSRS Wiki URLs in OSRS TCG's public
card catalog and cached under RuneLite's `Groupman-TCG/card-art-v1` directory;
the names and card frames still display if artwork is unavailable.

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
