# Compatibility and publication audit

Checked 2026-11-15 using authenticated GitHub CLI and the public Modrinth v2 API. Remote results describe the published commits, not the unpushed local work.

| Project | Published versions | Published loader/game targets | Local prepared version |
|---|---|---|---|
| [Fiw Custom Items](https://modrinth.com/mod/fiw-custom-items) | 1.3.3 | 7 | deferred |
| [Fiw Admin Utilities](https://modrinth.com/mod/fiw-admin-utilitys) | 1.3.1 | 8 | 1.4.0 |
| [Fiw Bosses](https://modrinth.com/mod/fiw-bosses) | 1.3.1 | 8 | deferred |
| [Fiw Clock](https://modrinth.com/mod/fiw-clock) | 1.1.0 | 6 | 1.2.0 |
| [Fiw Graves](https://modrinth.com/mod/fiw-graves) | 1.2.0, v1.1.0 | 3 | 1.3.0 |
| [Fiw AntiCheat](https://modrinth.com/mod/fiw-anticheat) | 2.2.0 | 6 | deferred |
| [Fiw Warp](https://modrinth.com/mod/fiw-warp) | v1.1.0 | 2 | 1.2.0 |

All seven Modrinth projects are approved. All seven GitHub repositories have **zero open issues and zero open pull requests**, and their latest main-branch Actions run completed successfully. This is an issue/CI status audit, not proof that their complete gameplay is bug-free.

Clock, Admin Tools, Graves and Warp now target Fabric 26.1.2; Fabric + NeoForge 1.21.11, 1.21.8 and 1.21.1; Fabric + Forge 1.20.1. AntiCheat, Custom Tools, Bosses and the Bedrock addon remain deferred and unmodified.

Existing Clock/Admin main release automation is retained. Graves/Warp gain automatic publishing from successful trusted main-push build artifacts, retaining tag recovery workflows. All four active repositories already contain the required publishing secret: `MODRINTH_TOKEN`, except Admin Tools uses `MODRINTH_API_KEY` mapped to the script token environment.

The four publishers were exercised offline with a fake curl transport: nine payloads each; exact loader/game matrix; root version; release channel; required/optional dependencies; description sync; then a second run that skipped all nine existing versions. No release was published during verification.

Graves currently has uneven published versions: NeoForge 1.21.1 is 1.2.0; both 1.21.11 downloads are v1.1.0 (NeoForge beta, Fabric release). The local 1.3.0 prepares a unified release.
