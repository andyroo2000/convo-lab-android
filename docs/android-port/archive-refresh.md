# Journal archive refresh — November 22, 2026

## Cause

The Pixel retained a local copy of an entry that the server no longer returned. The user identified it as a duplicate deleted from another client. The earlier diagnostic found 404 for that entry and its feedback/conversation, while the archive and other eight cached entries returned 200. This is consistent with deletion; the available client evidence does not identify which client performed it or when.

Android already refreshed when opening Contents and when pressing Refresh. `JournalRepository.mergePage` only upserted returned entries, and Contents displayed every cached root record. Therefore even a successful refresh could not remove a remotely deleted entry. iOS replaces its entry list on refresh. The server's archive excludes soft-deleted entries and returns only live root entries, with cursor pagination.

## Change

Persist the server list's IDs and next cursor separately from offline journal records. A successful first-page refresh replaces list membership; Load more appends only to the matching cursor. Contents displays that list plus roots containing local drafts, pending saves/deletes, unsent replies containing writing, or pending/unreadable feedback work. Automatically created empty reply placeholders do not retain an otherwise absent root entry. The full cached records remain available to an already-open entry and are never deleted by a list refresh. An unreadable journal record conservatively keeps all potential parent entries reachable.

Failed requests keep the last successful list, including across app restarts. Before a successful index exists, the legacy offline cache remains visible. Older cached pages stay on disk and become listed when Load more fetches them, matching the iOS pagination model. No absence from a single page is interpreted as proof of deletion, and no server writes are made by refresh.

The derived index shares the existing account-owned journal directory and is excluded from per-entry JSON enumeration. Saves add acknowledged entries to the list. Generation checks reject stale refresh responses after a newer refresh, deletion, or save acknowledgement. Returning to the foreground refreshes Contents when it is open.

## Verification

Regression coverage includes remote deletion, pagination replacement/append, empty versus failed refreshes, restart while offline, local drafts and child replies, pending feedback recovery, stale concurrent responses, save/index write failure, account isolation, interrupted index writes, and index corruption. Physical-device checks exercise Contents refresh while retaining locally typed writing, plus existing submission, correction, conversation, and process-recovery flows.

The production account is read-only during verification. Fixture tests use isolated storage and fake services.

Final local checks: 178 unit tests and 24 focused Pixel tests passed, along with build and lint. Loading a 250-entry fixture plus its workflow files took 194 ms on the Pixel, off the main thread. The live account had nine cached roots and eight server-listed roots; the excluded root had an empty auto-created child draft. After the fix, only the surviving same-title entry appeared in Contents. Cached writing remained intact.
