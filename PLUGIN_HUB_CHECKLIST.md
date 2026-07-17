# RuneLite Plugin Hub launch checklist

This checklist separates repository readiness from the manual multiplayer and
RuneLite review steps that cannot be proven by unit tests.

## Repository checks

- [x] Public standalone plugin repository.
- [x] BSD 2-Clause license.
- [x] OSRS TCG renderer/assets retain BSD attribution and are pinned to its
  current Plugin Hub commit in `THIRD_PARTY_NOTICES.md`.
- [x] Java 11 source and `latest.release` RuneLite dependency.
- [x] `runelite-plugin.properties` contains display name, author, description,
  tags, main plugin class, and `build=standard`.
- [x] No custom runtime dependency or non-Java plugin source.
- [x] No reflection, JNI, subprocess execution, runtime source download, or
  socket server.
- [x] README includes install, solo, shared server group, updates,
  privacy, and troubleshooting instructions.
- [x] Hosted backend and artwork network features are opt-in.
- [x] Hosted API sends only the active RuneScape display name needed for
  membership/right-click matching and omits all Jagex credentials, session,
  account, inventory, bank, location, stats, clan, and chat data.
- [x] First group creation requires a private Worker setup key that is sent in
  a header, never in the JSON body, and is not stored by the plugin.
- [x] Plugin tests run without the removed MockWebServer dependency.
- [x] GitHub Actions rebuilds with Java 11 and rejects tracked production
  Worker URLs.
- [x] Private security-reporting instructions and a RuneLite pre-clearance brief
  are included.
- [x] Public `main` and release-tag history starts from a privacy-clean release
  candidate; the superseded development branches were removed after private
  Git bundles were verified.

## Completed deployment checks

- [x] Confirmed the production D1 database contained zero groups, members,
  packs, or card instances before migration.
- [x] Exported and checksummed an off-OneDrive pre-v2 database backup.
- [x] Applied migrations 0003 and 0004 and deployed Worker API v3 with
  observability disabled.
- [x] Verified `/health` reports v3, setup protection is ready, and the final
  production schema containing no original-puller columns.
- [x] Backed up the empty production D1, applied migrations 0005 and 0006,
  deployed Worker API v4, and repeatedly verified healthy v4 responses with
  setup protection ready.

## Manual release checks

- [ ] Install the exact release commit on two normal RuneLite character profiles.
- [ ] Verify fresh install with no existing Group TCG configuration.
- [ ] Verify OSRS TCG detection and card unlocks after restart.
- [ ] Verify shared server collection sync in both directions without a
  RuneLite Party or GIM account.
- [ ] Verify pack reveal sender/receiver controls and popup queueing.
- [ ] Verify OSRS TCG card frames, rarity colours, foil animation, pack art, and
  Wiki-art-off fallback at normal RuneLite canvas sizes.
- [ ] Verify private Worker create, join, label confirmation, approval, revoke,
  invite rotation, offline pack replay, and a second-group rejection.
- [ ] Verify D1 contains only the expected RuneScape display names and no Jagex
  credentials, session data, account IDs, inventory, bank, location, stats,
  clan, chat, or original-puller names.
- [ ] Verify Top Trumps accept, decline, expiry, winner, and tie-break on both
  clients.
- [ ] Verify every enabled restriction category and the LMS bypass with normal
  gameplay; record any honour-mode gaps in the README.
- [ ] Verify plugin shutdown/re-enable, RuneLite restart, and profile switching.
- [ ] Re-read RuneLite's current rejected/rolled-back list and Jagex client
  rules immediately before submission.
- [ ] Ask RuneLite reviewers in the development Discord whether the opt-in,
  private-server TCG sync and self-imposed interaction blocking are
  acceptable before opening the submission PR.

## Submission

1. Merge and tag the tested plugin commit.
2. Fork `runelite/plugin-hub` and create one branch.
3. Add one marker under `plugins/` containing the HTTPS repository URL and the
   full tested commit SHA.
4. Open one Plugin Hub pull request and describe the privacy boundaries,
   optional self-hosted backend, OSRS TCG dependency, and honour-mode click
   blocking.
5. Keep fixes in that same pull request and update its marker SHA after every
   plugin commit.
6. Wait for both the build and RuneLite Plugin Hub review checks.

Official instructions:
[runelite/plugin-hub](https://github.com/runelite/plugin-hub#submitting-a-plugin).
