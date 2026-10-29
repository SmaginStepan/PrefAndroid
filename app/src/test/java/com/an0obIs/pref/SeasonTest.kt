package com.an0obIs.pref

import com.an0obIs.pref.model.Calculation
import com.an0obIs.pref.model.ConsolationSum
import com.an0obIs.pref.model.ConsolationType
import com.an0obIs.pref.model.Deal
import com.an0obIs.pref.model.EndingType
import com.an0obIs.pref.model.Game
import com.an0obIs.pref.model.RaspasyExit
import com.an0obIs.pref.model.RaspasyProgression
import com.an0obIs.pref.model.RulesGameType
import com.an0obIs.pref.model.SeasonConfig
import com.an0obIs.pref.model.SeasonRandom
import com.an0obIs.pref.model.SeasonState
import com.an0obIs.pref.model.Seasons
import com.an0obIs.pref.model.PrefStorage
import com.an0obIs.pref.model.VistType
import com.an0obIs.pref.mp.HostGameSession
import com.an0obIs.pref.mp.SeatKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

/**
 * The season contract. The reference numbers were computed with an independent
 * implementation (Node.js BigInt); the iOS port must reproduce them exactly.
 */
class SeasonTest {

    private val seed = "00112233445566778899aabbccddeeff"

    @Before
    fun setUp() {
        PrefStorage.init(Files.createTempDirectory("pref-season-test").toFile())
    }

    private fun ids(d: Deal) = d.hands.map { h -> h.cards.map { it.id } }

    @Test
    fun generatorMatchesTheReferenceVectors() {
        assertEquals(6927180097158232044L, SeasonRandom.forPurpose(seed, "deal", 0).state)

        val r = SeasonRandom.forPurpose(seed, "deal", 0)
        assertEquals(listOf(5, 4, 11, 27, 0, 25), List(6) { r.nextInt(32) })

        val d = SeasonRandom.forPurpose(seed, "bots", 7)
        assertEquals(0.32107593295955716, d.nextDouble(), 0.0)
        assertEquals(0.1639204827480535, d.nextDouble(), 0.0)
    }

    @Test
    fun dealsMatchTheReferenceAndAreRepeatable() {
        val deal = Deal().also { it.shuffle(SeasonRandom.forPurpose(seed, "deal", 3)) }
        assertEquals(
            listOf(
                listOf(311, 212, 12, 210, 313, 107, 108, 113, 209, 13),
                listOf(110, 11, 312, 309, 310, 114, 214, 14, 8, 211),
                listOf(10, 7, 314, 9, 112, 308, 207, 213, 307, 111)
            ),
            ids(deal)
        )
        assertEquals(listOf(208, 109), deal.prikup.cards.map { it.id })

        // the same deal number is the same deal; another number is another deal
        val again = Deal().also { it.shuffle(SeasonRandom.forPurpose(seed, "deal", 3)) }
        assertEquals(ids(deal), ids(again))
        val other = Deal().also { it.shuffle(SeasonRandom.forPurpose(seed, "deal", 4)) }
        assertNotEquals(ids(deal), ids(other))
        // another season seed gives other deals
        val elsewhere = Deal().also { it.shuffle(SeasonRandom.forPurpose("ffee$seed", "deal", 3)) }
        assertNotEquals(ids(deal), ids(elsewhere))
    }

    @Test
    fun configIsDerivedFromTheSeed() {
        val c = SeasonConfig.derive(seed)
        assertEquals(40, c.limit)
        assertEquals(RulesGameType.Rostov, c.rules.gameType)
        // Rostov applies its presets and forces "no progression"
        assertEquals(RaspasyProgression.NoProgression1, c.rules.raspasyProgression)
        assertEquals(EndingType.Each, c.rules.ending)
        assertEquals(ConsolationSum.Max10, c.rules.consolationBonus)
        assertEquals(RaspasyExit.Hard678, c.rules.raspasyExit)
        assertEquals(true, c.rules.miserRaspExit)
        assertEquals(VistType.FullResponsibility, c.rules.vist)
        assertEquals(ConsolationType.Gentlemen, c.rules.consolation)
        assertEquals(false, c.rules.prikupConsolation)
        assertEquals(0, c.firstDealer)
    }

    @Test
    fun seasonsVaryAcrossTheWholeMenu() {
        val configs = (0 until 400).map { SeasonConfig.derive("season-$it") }
        assertEquals(setOf(10, 20, 40, 60), configs.map { it.limit }.toSet())
        assertEquals(RulesGameType.values().toSet(), configs.map { it.rules.gameType }.toSet())
        assertEquals(RaspasyProgression.values().toSet(), configs.map { it.rules.raspasyProgression }.toSet())
        assertEquals(setOf(0, 1, 2), configs.map { it.firstDealer }.toSet())
    }

    private fun runAllBotMatch(): Triple<Int, List<String>, Int> {
        val calc = Calculation(3, 10)
        for (i in 0..2) calc.scores[i].name = "B$i"
        calc.dealer = 1
        val session = HostGameSession(
            seats = List(3) { SeatKind.BOT },
            names = listOf("B0", "B1", "B2"),
            matchCalc = calc,
            sendToSeat = { _, _ -> },
            onLocalTurn = { }
        )
        session.game.seasonSeed = seed
        session.game.seasonDealIndex = 0
        session.start()
        val sheet = (0..2).map { i ->
            val s = calc.scores[i]
            "${s.pulya}/${s.gora}/${s.visty.toSortedMap()}"
        }
        return Triple(calc.gameLog.size, sheet, session.game.seasonDealIndex)
    }

    @Test
    fun aSeasonMatchPlaysOutIdenticallyEveryTime() {
        // same deals AND seeded bots: two complete bot matches must agree on
        // every pool, whist and mountain entry
        val first = runAllBotMatch()
        val second = runAllBotMatch()
        assertTrue("the match must have played deals", first.first > 5)
        assertEquals(first, second)
    }

    @Test
    fun aSavedSeasonGameKeepsItsPlaceInTheSequence() {
        val state = SeasonState().also { it.seed = seed; it.seasonId = "2026-11" }
        val game = Seasons.newPoolGame(state, "Me", "West", "East")
        game.newDeal()
        game.newDeal()
        val json = PrefStorage.json.encodeToString(Game.serializer(), game)
        val back = PrefStorage.json.decodeFromString(Game.serializer(), json)
        assertEquals(seed, back.seasonSeed)
        assertEquals(2, back.seasonDealIndex)
        assertEquals(game.botRng!!.state, back.botRng!!.state)
        assertEquals(ids(game.deal), ids(back.deal))
    }

    @Test
    fun aClosedPoolCountsOnceAndTheNextPoolContinuesTheSequence() {
        val state = SeasonState().also { it.seed = seed; it.seasonId = "2026-11" }
        val game = Seasons.newPoolGame(state, "Me", "West", "East")
        repeat(7) { game.newDeal() } // the pool took deals 0..6
        game.calc.dealer = 2
        Seasons.poolClosed(state, game, 12.5)
        Seasons.poolClosed(state, game, 12.5) // a second tap on Close changes nothing
        assertEquals(125L, state.total10)
        assertEquals(1, state.poolsClosed)
        assertEquals(7, state.nextDealIndex)
        assertEquals(2, state.nextDealer)

        val next = Seasons.newPoolGame(state, "Me", "West", "East")
        assertEquals(7, next.seasonDealIndex)
        assertEquals(2, next.calc.dealer)
        next.newDeal()
        // deal number 7 is the same cards for everyone
        val reference = Deal().also { it.shuffle(SeasonRandom.forPurpose(seed, "deal", 7)) }
        assertEquals(ids(reference), ids(next.deal))

        Seasons.poolClosed(state, next, -4.0)
        assertEquals(125L - 40, state.total10)
        assertEquals(2, state.poolsClosed)
    }

    @Test
    fun everyClosedPoolIsPostedEvenWhenTheTotalIsNegative() {
        val state = SeasonState().also { it.seed = seed; it.seasonId = "2026-11" }
        assertEquals("total:2026-11", Seasons.boardName(state))
        assertTrue("nothing to post before the first pool closes", !Seasons.needsSubmit(state))

        val game = Seasons.newPoolGame(state, "Me", "West", "East")
        repeat(5) { game.newDeal() }
        Seasons.poolClosed(state, game, -30.0)
        assertTrue(state.total10 < 0)
        assertTrue("a negative total must still be posted", Seasons.needsSubmit(state))

        state.submittedPools = state.poolsClosed // the server accepted it
        assertTrue(!Seasons.needsSubmit(state))

        repeat(4) { game.newDeal() }
        Seasons.poolClosed(state, game, 30.0) // back to exactly zero
        assertEquals(0L, state.total10)
        assertTrue("a total of zero is posted too", Seasons.needsSubmit(state))
    }

    @Test
    fun daysLeftRoundsUp() {
        val s = SeasonState().also { it.endsAt = 1_000_000 }
        assertEquals(1, Seasons.daysLeft(s, 1_000_000 - 3600))
        assertEquals(2, Seasons.daysLeft(s, 1_000_000 - 86400 - 1))
        assertEquals(0, Seasons.daysLeft(s, 1_000_000))
        assertTrue(Seasons.isOver(s, 1_000_001))
    }
}
