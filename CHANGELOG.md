# Changelog

## Unreleased

- Made a group-owned, self-hosted Cloudflare Worker URL mandatory for new
  hosted groups; no public server is configured by default.
- Bound each profile token to the server that issued it so changing the global
  URL cannot redirect an existing credential.
- Add profile-scoped hosted group creation, invite joining, owner approval,
  private member labels, and token-free sidebar management.
- Removed RuneScape names, GIM names, original-puller labels, and raw OSRS TCG
  instance IDs from hosted requests and responses.
- Replaced the GIM name in custom RuneLite Party messages with an opaque key and
  removed redundant player-name fields from Top Trumps messages.
- Made hosted sync and OSRS Wiki artwork downloads explicit opt-ins.
- Upload privacy-reduced personal collection snapshots and pack events to the
  group-owned Cloudflare API, with retry-safe event IDs and grow-only unlocks.
- Download missed pack reveals, individual member collections, and remote pull
  provenance identified only by private labels after reconnecting.
- Add a shared/personal collection selector with one collection per verified
  group member.
- Cache individual member ownership offline and identify owners in shared card
  search results.
- Show local original-puller details only on the current client; hosted history
  contains copy, foil/debug, and date metadata without player names.
- Removed the extra MockWebServer test dependency for Plugin Hub `standard`
  build compatibility.
- Add consent-based Top Trumps challenges from the player right-click menu.
- Draw two distinct cards from the shared group collection and compare them
  with the OSRS TCG power formula.
- Show both players an illustrated winner overlay with deterministic tie-breaks.
- Add continuous Java 11 build/test checks and deployment-value privacy guards.
- Add private security-reporting instructions and a prepared RuneLite reviewer
  pre-clearance brief.

## v0.1.0 - development

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
