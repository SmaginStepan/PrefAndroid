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
    explicitNulls = false // keep Act/State payloads free of "field":null noise
    classDiscriminator = "t"
}

/** What input the actor is being asked for. */
@Serializable
data class Ask(
    val kind: String, // bid | contract | vist | opening | discard | play | confirm
    val bids: List<Game.Bid>? = null,
    val allowed: List<Card>? = null
)

/** Score standing shown between deals (already rotated per viewer). */
@Serializable
data class ScoreSnap(
    val names: List<String>,
    val pulya: List<Int>,
    val gora: List<Int>,
    /** visty[i][j] = whists player i has written on player j (diagonal 0). */
    val visty: List<List<Int>>,
    val limit: Int,
    /** who deals the next deal (viewer-relative); lets a guest save a resumable pulka */
    val dealer: Int = 0
)

/** A pending agreement offer, viewer-relative. */
@Serializable
data class OfferSnap(
    /** display name of the player who made the offer */
    val by: String,
    /** the agreed final trick counts (index = viewer-relative seat) */
    val taken: List<Int>,
    /** this viewer must answer accept/decline */
    val youRespond: Boolean = false
)

/** One completed trick, viewer-relative (-1 prev / 0 you / 1 next). */
@Serializable
data class TakeSnap(
    val first: Int,
    val taker: Int,
    val my: Card? = null,
    val prev: Card? = null,
    val next: Card? = null,
    val prikup: Card? = null
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
        val ended: Boolean = false,
        /** the score sheet everyone must look at (deal end / game end) */
        val scores: ScoreSnap? = null,
        /** completed tricks of the deal, for the guest tricks viewer */
        val takes: List<TakeSnap>? = null,
        /** the layout-and-discard view (contractor's open hand + possible talon) */
        val layout: List<PlacedCard>? = null,
        /** current standings for the on-demand score peek */
        val standings: ScoreSnap? = null,
        /** an agreement offer is pending: the table is frozen */
        val offer: OfferSnap? = null
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
        val confirm: Boolean? = null,
        /** propose an agreement: final trick counts, viewer-relative */
        val offer: List<Int>? = null,
        /** answer to a pending offer */
        val agree: Boolean? = null
    ) : GameMsg
}
