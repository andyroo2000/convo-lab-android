package com.cdcg.convolab.settings

import org.json.JSONArray
import org.json.JSONObject
import com.cdcg.convolab.journal.nullableString

object KnownKanjiJson {
    fun decode(json: JSONObject): KnownKanjiSnapshot {
        val characters = json.getJSONArray("kanji")
        val kanji = (0 until characters.length()).map { characters.getString(it) }
            .filter { it.codePointCount(0, it.length) == 1 }.map { it.codePointAt(0) }.toSet()
        val connection = json.getJSONObject("wanikani")
        return KnownKanjiSnapshot(json.getLong("version"), kanji, connection.getBoolean("connected"), connection.nullableString("lastSyncedAt"))
    }

    fun encode(snapshot: KnownKanjiSnapshot) = JSONObject().put("version", snapshot.version)
        .put("kanji", JSONArray(snapshot.kanji.sorted().map { String(Character.toChars(it)) }))
        .put("wanikani", JSONObject().put("connected", snapshot.connected)
            .put("lastSyncedAt", snapshot.lastSyncedAt ?: JSONObject.NULL))
}
