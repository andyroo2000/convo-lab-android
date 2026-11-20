package com.cdcg.convolab.journal

import java.math.BigInteger
import java.security.SecureRandom
import java.util.Locale

object JournalIds {
    private const val ALPHABET = "0123456789abcdefghjkmnpqrstvwxyz"
    private val valid = Regex("[0-7][0-9a-hjkmnp-tv-z]{25}")
    private val random = SecureRandom()

    fun normalize(value: String): String = value.lowercase(Locale.ROOT).also {
        require(valid.matches(it)) { "Invalid Journal identifier" }
    }

    fun newId(timestamp: Long = System.currentTimeMillis()): String {
        require(timestamp in 0..0xFFFFFFFFFFFFL)
        val bytes = ByteArray(16).also(random::nextBytes)
        for (index in 0..5) bytes[index] = (timestamp ushr (8 * (5 - index))).toByte()
        var number = BigInteger(1, bytes)
        val result = CharArray(26)
        for (index in 25 downTo 0) {
            result[index] = ALPHABET[number.and(BigInteger.valueOf(31)).toInt()]
            number = number.shiftRight(5)
        }
        return String(result)
    }
}
