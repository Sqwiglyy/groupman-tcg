# Groupman TCG privacy contract

This document describes Groupman TCG's intended data boundaries. A change that
adds a network field or destination must update this document and add or update
tests before release.

## Data flow

| Data | Local RuneLite profile | RuneLite Party payload | Self-hosted Worker | OSRS Wiki |
| --- | --- | --- | --- | --- |
| RuneScape character name | Yes, for local profile binding | No additional field | Never | Never |
| GIM name and roster | Yes, for local verification | Opaque group hash only | Never | Never |
| Jagex password, bank PIN, session token | Never read | Never | Never | Never |
| Stats, XP, world, location, chat, clan data | Never uploaded | Never | Never | Never |
| Inventory, bank, equipment contents | Inspected only for visible lock overlays | Never | Never | Never |
| OSRS TCG shared card set | Yes | Compact card bitset | Card names/unlocks | Never |
| Pack contents | Yes | Card name/foil/new flags | Card name/foil/new flags and time | Never |
| Original-puller label | Local display only | Never | Never | Never |
| Raw OSRS TCG instance ID | Local only | Never | Never; SHA-256-derived opaque ID only | Never |
| Groupman bearer token/invite | Local token/invite as needed | Never | Received over HTTPS; only hashes stored in D1 | Never |
| Card artwork request | Cached locally when opted in | Never | Never | Fixed image URL and connection metadata |

RuneLite Party itself supplies party display names to Party participants. The
plugin uses those local Party objects to show teammate names but does not add
the names to its own custom message fields.

## Privacy defaults

- Hosted sync is disabled by default and has no default URL.
- Card artwork downloads are disabled by default.
- A hosted profile token is bound to its issuing Worker URL.
- An old token without an issuing URL is discarded instead of being redirected.
- Worker observability is disabled by the reusable server template.
- Worker error logs do not include exception objects or request data.

## Hosted minimum data

The multiplayer service cannot work without sharing TCG card information. When
a player explicitly enables hosted sync, it sends only the TCG data required
for shared unlocks, private per-member history, and missed pack reveals, plus
Groupman-specific authentication and random identifiers. It does not send
RuneScape account identity or unrelated gameplay telemetry.

Cloudflare still processes IP and connection metadata as the network provider.
The group member who owns the Cloudflare account controls retention and account
settings. See the
[server privacy guide](https://github.com/Sqwiglyy/groupman-tcg-server#privacy-design).

## Release checks

Before release:

1. Search request DTOs and Party message classes for new identity fields.
2. Run `gradlew clean build` and confirm the hosted request tests pass.
3. Confirm `Hosted server URL` is blank by default.
4. Confirm hosted sync and artwork downloads are disabled by default.
5. Apply all server migrations to a fresh local D1 database.
6. Confirm the server has no RSN/original-puller request or response fields.
7. Review the current RuneLite Plugin Hub security and rejected-feature rules.
