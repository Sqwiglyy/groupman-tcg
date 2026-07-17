# Changelog

## Unreleased

- Rank approved private-server members by the combined OSRS TCG score of their
  unique cards, with collection size shown alongside each leaderboard entry.
- Complete the automated and two-account RuneLite Plugin Hub launch checks.
- Replace internal launch paperwork with a shorter fork-and-run guide.
- Open the selected shared or player collection in a full, resizable OSRS TCG-style
  album with card faces, missing-card dimming, copy/foil counts, search, filters,
  sorting, and 21-card paging.
- Show the selected collection's 20 most recent non-debug card pulls when the
  sidebar search field is empty, retaining duplicates and foil markers.
- Delay remote pack previews until at least 15 seconds after the server receives
  the opening, while continuing to suppress the preview on the opener's client.
- Show the opener's approved RuneScape display name in remote pack previews.

## v0.1.0-rc.4 - 2026-07-17

- Rename the user-facing plugin from Groupman TCG to Group TCG while retaining
  internal configuration keys so existing local profiles continue to load.
- Remove the official GIM/HCGIM roster and RuneLite Party requirements.
- Make approved private-server membership the authority for shared unlocks,
  individual collection browsing, pack reveals, and player matching.
- Let every player independently choose shared-server or solo unlocks while
  remaining eligible for server Top Trumps.
- Move consent, random card draws, and results to authenticated server events;
  add sidebar challenges and retain in-game right-click challenges.
- Store the RuneScape display name on the explicitly selected private server
  and document the new privacy boundary.

## v0.1.0-rc.3 - 2026-07-17

- Adapt OSRS TCG's real card-face renderer for group pack reveals and Top
  Trumps, including its rarity styling, examine text, score, and foil effects.
- Bundle namespaced copies of OSRS TCG's card-back, lock badge, and standard
  pack artwork from its current RuneLite Plugin Hub commit.
- Preserve Groupman-specific multiplayer labels, badges, countdown, and winner
  treatment around the upstream card design.
- Expand BSD attribution and add a manual visual release gate.

## v0.1.0-rc.2 - 2026-07-17

- Protect first hosted-group creation with a private Worker setup key sent only
  in the create-request header and never stored by the plugin.
- Add an owner-only password prompt plus setup, privacy, and troubleshooting
  documentation for the API v3 backend.
- Document the visual boundary: RuneLite fonts, custom mini-card frames, an
  attributed OSRS TCG catalog snapshot, and opt-in OSRS Wiki artwork.

## v0.1.0-rc.1 - 2026-07-17

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
- Add private security-reporting instructions and document the multiplayer
  privacy boundary for review.

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
