package com.cdcg.convolab.journal

import com.cdcg.convolab.journal.feedback.WorkflowController

/** Opening an entry may restore feedback; acknowledging an autosave must not replace its editor. */
internal suspend fun NotebookController.followFeedback(feedback: WorkflowController) {
    var selection: Pair<String, Long>? = null
    state.collect { current ->
        if (current.loading) return@collect
        val nextSelection = current.draft.id to current.selectionRevision
        val opened = selection != nextSelection
        selection = nextSelection
        val entry = current.savedEntry()
        when {
            entry == null && feedback.state.value.entry != null -> feedback.cancel()
            entry != null && opened -> feedback.open(entry)
        }
    }
}

private fun NotebookState.savedEntry(): JournalEntry? = records.firstOrNull { it.id == draft.id }?.entry?.takeIf {
    !pendingDelete && it.version == draft.expectedVersion && it.body == draft.body && it.title == draft.title
}
