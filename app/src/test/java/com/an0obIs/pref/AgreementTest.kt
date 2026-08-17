package com.an0obIs.pref

import com.an0obIs.pref.model.Game
import com.an0obIs.pref.model.GamePhase
import com.an0obIs.pref.model.PrefStorage
import com.an0obIs.pref.mp.Agreements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

/** Agreement («расписать») engine flow and the conservative bot rule. */
class AgreementTest {

    @Before
    fun setUp() {
        PrefStorage.init(Files.createTempDirectory("pref-agree-test").toFile())
    }

    /** Drives a fresh externally-driven game into the play of 6♠ with one whister. */
    private fun gameAtPlay(): Game {
        val game = Game.create()
        game.externalDriver = true
        return driveToPlay(game)
    }

    private fun driveToPlay(game: Game): Game {
        game.next()
        val bid6 = Game.Bid().also { it.contract = 6; it.trump = 2 } // 6 diamonds: no Stalingrad auto-whist
        game.makeBid(bid6); game.next()
        game.makeBid(Game.Bid().also { it.pas = true }); game.next()
        game.makeBid(Game.Bid().also { it.pas = true }); game.next()
        assertEquals(GamePhase.PrikupOpened, game.phase)
        while (game.phase == GamePhase.PrikupOpened) {
            game.prikupClose(); game.next()
        }
        assertEquals(GamePhase.Discarding, game.phase)
        val hand = game.deal.hands[game.contractor].cards.toList()
        game.discardCard(hand[0]); game.discardCard(hand[1]); game.next()
        assertEquals(GamePhase.GameChoose, game.phase)
        game.setContract(Game.Bid().also { it.contract = 6; it.trump = 2 }); game.next()
        assertEquals(GamePhase.VistNegotiations, game.phase)
        game.setVist(true); game.next()
        if (game.phase == GamePhase.VistNegotiations) {
            game.setVist(false); game.next()
        }
        if (game.phase == GamePhase.OpeningChoose) {
            game.setOpeningChoice(false); game.next()
        }
        assertEquals(GamePhase.Playing, game.phase)
        return game
    }

    @Test
    fun agreementScoresLikeAPlayedDeal() {
        val game = gameAtPlay()
        val c = game.contractor
        val whister = game.isVister.filterValues { it }.keys.first()
        val passer = (0..2).first { it != c && it != whister }
        // agreed: declarer makes exactly the contract
        val taken = mapOf(c to 6, whister to 4, passer to 0)
        game.applyAgreement(taken)
        assertEquals(GamePhase.EndPlay, game.phase)
        repeat(3) { game.endConfirm(); game.next() }
        assertEquals(GamePhase.ScoreView, game.phase)
        assertEquals("pulya for the made 6-game", 2, game.calc.scores[c].pulya)
        assertEquals("whister writes 4 tricks x 2", 8, game.calc.scores[whister].visty[c] ?: 0)
        assertEquals("passer writes nothing", 0, game.calc.scores[passer].visty[c] ?: 0)
    }

    @Test
    fun surrenderWritesMountainOnlyAndVoidsWhists() {
        val game = gameAtPlay()
        val c = game.contractor
        val whister = game.isVister.filterValues { it }.keys.first()
        val passer = (0..2).first { it != c && it != whister }
        // «без 3 (застрелиться)»: unilateral, whists voided
        val taken = mapOf(c to 3, whister to 7, passer to 0)
        game.applyAgreement(taken, noVists = true)
        repeat(3) { game.endConfirm(); game.next() }
        assertEquals(GamePhase.ScoreView, game.phase)
        assertEquals("mountain for 3 undertricks", 6, game.calc.scores[c].gora)
        assertEquals("no pulya", 0, game.calc.scores[c].pulya)
        assertEquals("whists voided", 0, game.calc.scores[whister].visty[c] ?: 0)
    }

    @Test
    fun guestOffersAndAnswersRouteThroughActs() {
        // regression: onRemoteAct must route Act.offer / Act.agree (they used
        // to fall through to the turn gate and be silently dropped)
        val calc = com.an0obIs.pref.model.Calculation(3, 10)
        val names = listOf("P0", "P1", "P2")
        for (i in 0..2) calc.scores[i].name = names[i]
        val states = mutableMapOf<Int, com.an0obIs.pref.mp.GameMsg.State>()
        val session = com.an0obIs.pref.mp.HostGameSession(
            seats = List(3) { com.an0obIs.pref.mp.SeatKind.REMOTE },
            names = names,
            matchCalc = calc,
            sendToSeat = { seat, msg -> states[seat] = msg },
            onLocalTurn = { }
        )
        driveToPlay(session.game)
        val game = session.game
        val c = game.contractor
        val whister = game.isVister.filterValues { it }.keys.first()
        val passer = (0..2).first { it != c && it != whister }
        val takenAbs = mapOf(c to 6, whister to 4, passer to 0)
        // the whister offers, viewer-relative from their seat
        val rel = List(3) { i -> takenAbs[(i + whister) % 3]!! }
        session.onRemoteAct(whister, com.an0obIs.pref.mp.GameMsg.Act(offer = rel))
        assertTrue("offer must reach the session", session.offerPending)
        assertTrue("the declarer must be asked to respond",
            states[c]?.offer?.youRespond == true)
        assertTrue("the passer must not respond",
            states[passer]?.offer?.youRespond == false)
        // the declarer accepts via an act: the deal ends on the result screen
        session.onRemoteAct(c, com.an0obIs.pref.mp.GameMsg.Act(agree = true))
        assertEquals(GamePhase.EndPlay, session.game.phase)
        assertEquals(6, session.game.deal.hands[c].taken)
    }

    @Test
    fun restMineEndsRaspasyUnilaterally() {
        val calc = com.an0obIs.pref.model.Calculation(3, 10)
        val names = listOf("P0", "P1", "P2")
        for (i in 0..2) calc.scores[i].name = names[i]
        val session = com.an0obIs.pref.mp.HostGameSession(
            seats = List(3) { com.an0obIs.pref.mp.SeatKind.REMOTE },
            names = names,
            matchCalc = calc,
            sendToSeat = { _, _ -> },
            onLocalTurn = { }
        )
        session.start()
        // everyone passes -> распасы
        val pas = Game.Bid().also { it.pas = true }
        repeat(3) {
            session.onRemoteAct(session.game.turnController(), com.an0obIs.pref.mp.GameMsg.Act(bid = pas))
        }
        assertEquals(com.an0obIs.pref.model.GameType.Raspasy, session.game.currentGameType)
        assertEquals(GamePhase.Playing, session.game.phase)
        // «остальные мои» from seat 1: instant, no confirmations
        session.onRemoteAct(1, com.an0obIs.pref.mp.GameMsg.Act(restMine = true))
        assertEquals(GamePhase.EndPlay, session.game.phase)
        assertEquals("offerer holds every remaining trick", 10, session.game.deal.hands[1].taken)
        assertEquals(0, session.game.deal.hands[0].taken)
        assertEquals(0, session.game.deal.hands[2].taken)
        // through the result confirms into the score: raspasy is written
        repeat(3) { session.confirmSeat(it) }
        assertEquals("mountain for ten tricks on raspasy", 10, calc.scores[1].gora)
        assertTrue("non-takers write the pulya", calc.scores[0].pulya > 0 && calc.scores[2].pulya > 0)
    }

    @Test
    fun conservativeBotRule() {
        val game = gameAtPlay()
        val c = game.contractor
        val whister = game.isVister.filterValues { it }.keys.first()
        val passer = (0..2).first { it != c && it != whister }
        // at the start of play: 0 taken, 10 remaining
        // whister-bot accepts only "declarer stopped cold + I take everything"
        assertTrue(Agreements.botAccepts(whister, game, mapOf(c to 0, whister to 10, passer to 0)))
        assertTrue(!Agreements.botAccepts(whister, game, mapOf(c to 6, whister to 4, passer to 0)))
        assertTrue(!Agreements.botAccepts(whister, game, mapOf(c to 0, whister to 9, passer to 1)))
        // declarer-bot accepts only "I take everything remaining"
        assertTrue(Agreements.botAccepts(c, game, mapOf(c to 10, whister to 0, passer to 0)))
        assertTrue(!Agreements.botAccepts(c, game, mapOf(c to 9, whister to 1, passer to 0)))
    }
}
