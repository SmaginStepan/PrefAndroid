package com.an0obIs.pref.mp

import com.an0obIs.pref.model.Card
import com.an0obIs.pref.model.Game
import com.an0obIs.pref.ui.game.PlacedCard
import com.an0obIs.pref.ui.game.TableInfo
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * Host <-> guest game messages, carried inside the lobby relay's opaque
 * `data` field. Everything a guest renders arrives pre-rotated (the guest is
 * always seat 0 of its own view) and pre-redacted (hidden hands are null cards).
 */

@OptIn(ExperimentalSerializationApi::class)
val gameJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "t"
}

/** What input the actor is being asked for. */
@Serializable
data class Ask(
    val kind: String, // bid | contract | vist | opening | discard | play | confirm
    val bids: List<Game.Bid>? = null,
    val allowed: List<Card>? = null
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("t")
sealed interface GameMsg {

    /** Full render state for one viewer. */
    @Serializable
    @SerialName("state")
    data class State(
        val field: List<PlacedCard>,
        val info: TableInfo,
        val yourTurn: Boolean,
        val ask: Ask? = null,
        val badMove: Boolean = false,
        val ended: Boolean = false
    ) : GameMsg

    /** A guest's answer to an Ask. Exactly one field is set. */
    @Serializable
    @SerialName("act")
    data class Act(
        val bid: Game.Bid? = null,
        val contract: Game.Bid? = null,
        val vist: Boolean? = null,
        val opening: Boolean? = null,
        val discard: List<Card>? = null,
        val play: Card? = null,
        val confirm: Boolean? = null
    ) : GameMsg
}
