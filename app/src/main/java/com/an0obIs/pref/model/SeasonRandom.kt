package com.an0obIs.pref.model

import kotlinx.serialization.Serializable
import java.nio.ByteBuffer
import java.security.MessageDigest

/** Source of the random numbers behind deals and bot decisions. */
interface PrefRandom {
    fun nextInt(bound: Int): Int
    fun nextDouble(): Double
}

/** Ordinary games: the platform generator. */
object DefaultRandom : PrefRandom {
    override fun nextInt(bound: Int): Int = kotlin.random.Random.Default.nextInt(bound)
    override fun nextDouble(): Double = kotlin.random.Random.Default.nextDouble()
}

/**
 * The season generator. Every season player must get the same deals, so this
 * is specified exactly, bit for bit, and must be reproduced identically on
 * every platform (the iOS port copies this contract):
 *
 *  - stream seed: SHA-256 of the UTF-8 text "<seedHex>|<purpose>|<index>",
 *    first 8 bytes read big-endian as an unsigned 64-bit state;
 *  - purposes: "deal" (index = deal number), "bots" (index = deal number),
 *    "config" (index = 0);
 *  - next(): SplitMix64 — state += 0x9E3779B97F4A7C15; z = state;
 *    z = (z ^ (z >> 30)) * 0xBF58476D1CE4E5B9;
 *    z = (z ^ (z >> 27)) * 0x94D049BB133111EB; return z ^ (z >> 31);
 *    all arithmetic is unsigned 64-bit, wrapping, shifts are logical;
 *  - nextInt(bound) = (next() >> 1) % bound;
 *  - nextDouble() = (next() >> 11) * 2^-53.
 */
@Serializable
class SeasonRandom(var state: Long = 0L) : PrefRandom {

    private fun next(): Long {
        state += 0x9E3779B97F4A7C15uL.toLong()
        var z = state
        z = (z xor (z ushr 30)) * 0xBF58476D1CE4E5B9uL.toLong()
        z = (z xor (z ushr 27)) * 0x94D049BB133111EBuL.toLong()
        return z xor (z ushr 31)
    }

    override fun nextInt(bound: Int): Int = ((next() ushr 1) % bound).toInt()

    override fun nextDouble(): Double = (next() ushr 11) * (1.0 / 9007199254740992.0)

    companion object {
        fun forPurpose(seedHex: String, purpose: String, index: Int): SeasonRandom {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest("$seedHex|$purpose|$index".toByteArray(Charsets.UTF_8))
            return SeasonRandom(ByteBuffer.wrap(digest, 0, 8).long)
        }
    }
}
