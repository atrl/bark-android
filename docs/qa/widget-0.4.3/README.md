# Sampled quote widget verification — 2026-10-09

Bark Android 0.4.3 / version code 11.

- Local `:core:test :app:testDebugUnitTest :app:assembleDebug`: 140 core and 107 app tests passed.
- Regression coverage includes one stale maturity alongside a fresh one, quote-time regression with new history, restart and HTTP 304, same-source-time session transitions, same-day official 15:00 close replacing a 15:00:21 sample, missing quote retention, and real-contract rollover.
- API 35 ARM64 emulator `bark_ui_api35` inflated and drew the production RemoteViews at 220×160, 140×120 and 320×240 dp. Labels, four/two values, curves and absolute timestamps fit.
- The three synthetic screenshots below are **state fixtures for rendering only**. The public IC API supplied 2026-10-08 close values and curves; only `kind` and `freshness.state` were changed to exercise `sampled` with `closed`, `stale` and `paused`. They are not evidence of current quotes or a current market session.
- `已收盘采样` describes the server's session state; it does not claim the quote is the official closing value. The original source timestamp remains visible.
- No physical handset was attached. WorkManager still uses a 15-minute periodic request that Android may defer; the foreground card requests data every minute. These checks do not verify installation or OEM timing on the user's phone.

![Closed-state fixture, four contracts](synthetic-closed-220x160.png)
![Delayed-state fixture, two contracts](synthetic-stale-140x120.png)
![Lunch-state fixture, four contracts](synthetic-paused-320x240.png)

## Today's validated NAS snapshot

A candidate mobile-summary built from the real NAS archive was also parsed and rendered
on the same emulator at all three sizes. Its four IC quotes are `sampled/stale`,
with futures source time **2026-10-09 13:30:07 +08:00** and paired index time
**13:29:50**. The standard widget shows 7.36%, 8.50%, 8.69% and 8.92%, marked
**行情延迟** and retaining the daily-close curves. This checks candidate API/client
compatibility and rendering; it is not evidence of a restored current upstream feed,
public deployment, or installation on a physical phone.

![Today's NAS sample, four contracts](nas-snapshot-220x160.png)
![Today's NAS sample, minimum size](nas-snapshot-140x120.png)
![Today's NAS sample, large size](nas-snapshot-320x240.png)
