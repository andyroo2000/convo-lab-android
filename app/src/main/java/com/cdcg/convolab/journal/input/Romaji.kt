package com.cdcg.convolab.journal.input

import java.util.Base64
import java.util.Locale

/** Deterministic kana composition; no model inference or network on the UI thread. */
object Romaji {
    private class Node(var value: String? = null, val children: MutableMap<Char, Node> = mutableMapOf())
    private val finalTree by lazy { load("final") }
    private val imeTree by lazy { load("ime") }

    fun hiragana(raw: String, final: Boolean = false): String {
        val input = raw.lowercase(Locale.ROOT)
        val convertEnding = final || raw.endsWith("nn")
        val root = if (convertEnding) finalTree else imeTree
        return buildString {
            var cursor = 0
            while (cursor < input.length) {
                val token = next(input, cursor, root, convertEnding)
                append(token.second)
                cursor = token.first
            }
        }
    }
    fun katakana(reading: String): String = reading.map { if (it in 'ぁ'..'ゖ') (it.code + 0x60).toChar() else it }.joinToString("")

    private fun next(input: String, start: Int, root: Node, convertEnding: Boolean): Pair<Int, String> {
        var cursor = start + 1
        var node = root.children[input[start]]
        var output = node?.value ?: input[start].toString()
        while (cursor < input.length) {
            val child = node?.children?.get(input[cursor]) ?: break
            output = child.value ?: (output + input[cursor])
            node = child
            cursor++
        }
        if (incomplete(cursor == input.length, convertEnding, node)) output = input.substring(start)
        return cursor to output
    }
    private fun incomplete(atEnd: Boolean, final: Boolean, node: Node?) = atEnd && !final && !node?.children.isNullOrEmpty()

    private fun load(kind: String): Node {
        val root = Node()
        val resource = checkNotNull(javaClass.getResourceAsStream("/typing/romaji-$kind.tsv"))
        resource.bufferedReader().useLines { lines -> lines.forEach { line ->
            val parts = line.split('\t', limit = 2)
            val key = decode(parts[0])
            var node = root
            for (letter in key) node = node.children.getOrPut(letter) { Node() }
            node.value = decode(parts[1])
        } }
        return root
    }
    private fun decode(encoded: String) = String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)
}
