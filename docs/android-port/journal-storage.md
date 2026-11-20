# Journal persistence and versioned API

Implements the storage/API foundation in #7. UI integration follows separately.

Each account owns a no-backup journal directory. Each entry is one atomic JSON record containing its acknowledged server version, optional newer local draft, and optional frozen save request. A request is durable before being sent. New edits can continue while it is in flight; acknowledgement clears only the matching edit and advances a newer draft's expected version.

The production backend's `SaveJournalDraft::persist` accepts exact retries without advancing the version, including unchanged saves. Android retains the original ID, text, title, date and expectedVersion until acknowledgement. A conflicting remote edit is surfaced, with the original local data preserved; saving a copy creates a fresh ULID and retains the original recovery record. Local server caches never replace a newer draft. Pending saves must be resolved before deletion; server-backed entries are removed locally only after server acknowledgement.

Corrupt or unknown-version files are reported and left intact. Reads, writes and deletion reject invalid IDs and refuse to overwrite unreadable data. The gateway captures one session and verifies it before and after every request, rejecting late results after sign-out/account replacement. Body/title constraints count Unicode code points, matching the backend rather than UTF-16 units.

## Verification

- Nine repository tests cover edits during save, unchanged-save acknowledgement, retry after reopening, failed network/delete/disk writes, newer server caches and copy recovery.
- Seven Android instrumentation tests exercise actual AtomicFile recovery, account isolation, preserved corrupt/future records, path rejection, exact JSON field names, uppercase-to-canonical IDs, cursor escaping, DELETE versions, and stale-session responses.
- Build, lint and all 35 JVM tests pass. Instrumentation uses isolated temporary directories and fake network responses; it never changes production Journal data.

Reference contract: `learning-os` origin/main `app/Domain/Journal/Actions/SaveJournalDraft.php`, `Support/JournalDraftInput.php` and `Http/Resources/Journal/JournalEntryResource.php`; iOS reference 6dae16a.
