# Changelog

## Unreleased

- Made a group-owned, self-hosted Cloudflare Worker URL mandatory for new
  hosted groups; no public server is configured by default.
- Bound each profile token to the server that issued it so changing the global
  URL cannot redirect an existing credential.

- Add profile-scoped hosted group creation, invite joining, roster-gated owner
  approval, and token-free sidebar management.
- Upload exact personal collection snapshots and pack events to the Sqwiglyy
  Cloudflare API, with retry-safe event IDs and grow-only shared unlocks.
- Download missed pack reveals, individual member collections, and remote pull
  provenance after reconnecting.
- Add a shared/personal collection selector with one collection per verified
  group member.
- Cache individual member ownership offline and identify owners in shared card
  search results.
- Show local copy, foil, original-puller, and pull-date provenance on card
  results while preserving the permanent shared unlock union.
- Add consent-based Top Trumps challenges from the player right-click menu.
- Draw two distinct cards from the shared group collection and compare them
  with the OSRS TCG power formula.
- Show both players an illustrated winner overlay with deterministic tie-breaks.

## v0.1.0 — development

- Created an independent Groupman TCG RuneLite plugin.
- Added read-only OSRS TCG collection interoperability.
- Added solo and official GIM/HCGIM shared collection modes.
- Added permanent cached unlock unions and RuneLite Party synchronisation.
- Added core NPC and item interaction restrictions.
- Added sidebar group status and card lookup.
- Added 769 data-backed specialist rule records spanning Woodcutting, Mining,
  Fishing, Cooking, Farming, Hunter, Slayer, Thieving, Runecrafting, Sailing,
  Firemaking, Smelting, Smithing, Crafting, Enchanting, Fletching, and Herblore.
- Added per-skill difficulty modes, role-based alternative requirements, and
  input/output recipe scopes.
- Added configurable core item exemptions (Coins by default) and an LMS safety
  bypass for temporary match equipment.
- Added grey NPC and ground-item model outlines plus shaded, padlocked
  inventory, bank, and equipment items.
- Added verified multiplayer pack-reveal popups with all pulls, duplicates,
  foil/new badges, queued display, and on-demand OSRS Wiki artwork.
