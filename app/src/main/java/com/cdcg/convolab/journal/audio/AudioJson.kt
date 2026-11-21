package com.cdcg.convolab.journal.audio

import com.cdcg.convolab.journal.nullableString
import org.json.JSONException
import org.json.JSONArray
import org.json.JSONObject

object AudioJson {
    fun voice(json: JSONObject) = NarrationVoice(json.getString("id"), json.getString("name"), json.nullableString("avatarUrl"))
    fun sentences(json: JSONObject): List<SentenceTiming> = try { decodeSentences(json) }
        catch (_: JSONException) { emptyList() }

    private fun decodeSentences(json: JSONObject): List<SentenceTiming> {
        val array = json.optJSONArray("sentences") ?: return emptyList()
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            SentenceTiming(item.getString("text"), item.getDouble("start"), item.getDouble("end"))
        }
    }
    fun encodeSentences(sentences: List<SentenceTiming>) = JSONArray(sentences.map {
        JSONObject().put("text", it.text).put("start", it.start).put("end", it.end)
    })
    fun options(json: JSONObject): VoiceOptions {
        val voices = json.getJSONArray("voices")
        return VoiceOptions((0 until voices.length()).map { voice(voices.getJSONObject(it)) }, json.getString("selected"))
    }
    fun encodeOptions(options: VoiceOptions) = JSONObject().put("selected", options.selected).put("voices", JSONArray(options.voices.map {
        JSONObject().put("id", it.id).put("name", it.name).put("avatarUrl", it.avatarUrl ?: JSONObject.NULL)
    }))
}
