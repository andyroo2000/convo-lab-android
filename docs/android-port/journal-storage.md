# Journal persistence and versioned API

Implements the storage/API foundation in #7. UI integration follows separately.

Each account owns a no-backup journal directory. Each entry is one atomic JSON record containing its acknowledged server version, optional newer local draft, and optional frozen save request. A request is durable before being sent. New edits can continue while it is in flight; acknowledgement clears only the matching edit and advances a newer draft's expected version.

The production backend's `SaveJournalDraft::persist` accepts exact retries without advancing the version, including unchanged saves. Android retains the original ID, text, title, date and expectedVersion until acknowledgement. A conflicting remote edit is surfaced, with the original local data preserved; saving a copy creates a fresh ULID and retains the original recovery record. Local server caches never replace a newer draft. Pending saves must be resolved before deletion; server-backed entries are removed locally only after server acknowledgement.

Corrupt or unknown-version files are reported and left intact. Reads, writes and deletion reject invalid IDs and refuse to overwrite unreadable data. The gateway captures one session and verifies it before and after every request, rejecting late results after sign-out/account replacement. Body/title constraints count Unicode code points, matching the backend rather than UTF-16 units.

## Verification

- Eighteen repository tests cover edits during save, unchanged-save acknowledgement, retry after reopening, failed network/delete/disk writes, newer server caches and copy recovery.
- Eight Android instrumentation tests exercise actual AtomicFile recovery, account isolation, preserved corrupt/future records, path rejection, exact JSON field names, uppercase-to-canonical IDs, cursor escaping, DELETE versions, and stale-session responses.
- Build, lint and all 44 JVM tests pass. Instrumentation uses isolated temporary directories and fake network responses; it never changes production Journal data.

Reference contract: `learning-os` origin/main `app/Domain/Journal/Actions/SaveJournalDraft.php`, `Support/JournalDraftInput.php` and `Http/Resources/Journal/JournalEntryResource.php`; iOS reference 6dae16a.

Deletion review: a pending-delete flag is persisted before the server call and survives a lost response. Edits/saves are blocked until deletion is retried; the backend retains tombstones so exact DELETE retries also retry audio cleanup. Unsaved child replies or unreadable records stop deletion before any network request. A removal generation rejects stale refreshes that might otherwise recreate a deleted cache. Malformed response JSON becomes a typed recoverable I/O failure. Saving a copy intentionally creates a new root entry, preserving the original recovery record.

Definitive 400/403/404/409/422 write rejections clear only the pending-operation flag and keep the draft; ambiguous I/O, cancellation, session changes and throttling retain exact retries. A missing server entry (DELETE 404) completes local removal. Draft expectedVersion is intentionally not rebased by a remote refresh: doing so could silently overwrite another device’s edits. Only acknowledgement rebases a newer draft; conflicts offer an explicit copy recovery. Public repository lookups take validated canonical JournalId values.

A record also remembers the last version acknowledged by this installation. A delayed editor snapshot can advance to that version, while a server-only refresh never silently advances its editing base. Unchanged cached entries skip disk writes; known unreadable records are skipped during archive refresh so other entries still load.

Acknowledging an older save also preserves a higher server version already refreshed into the cache, while rebasing newer local writing only to the acknowledged version. This ordering is covered by a controlled in-flight-save test.
