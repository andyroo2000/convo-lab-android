package com.cdcg.convolab.journal.feedback

class WorkflowRecovery(val saved: WorkflowSnapshot?) {
    val text: String get() = saved?.pendingFinalEdit?.text ?: saved?.pendingAnswer?.text.orEmpty()
    val message: String get() = if (saved == null) "Saved feedback could not be opened. Its original file will be kept in a recovery archive."
        else "An earlier version has an unconfirmed answer or final edit. Copy it before continuing. Its original file will also be kept in a recovery archive."
}

class WorkflowRecoveryFailure(val recovery: WorkflowRecovery) : Exception("Saved Journal work needs recovery")
