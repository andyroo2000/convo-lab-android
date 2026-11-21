package com.cdcg.convolab.journal

import org.json.JSONObject

enum class JournalMethod { GET, POST, PUT, DELETE }
data class JournalRequest(val method: JournalMethod, val path: String, val body: JSONObject? = null)
