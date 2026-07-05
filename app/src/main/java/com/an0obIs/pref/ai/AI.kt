package com.an0obIs.pref.ai

import com.an0obIs.pref.model.Card
import com.an0obIs.pref.model.Game
import com.an0obIs.pref.model.GamePhase
import com.an0obIs.pref.model.GameType
import kotlin.math.floor
import kotlin.random.Random

object AI {

    fun makeMove(game: Game) {
        val rnd = Random.Default
        val info: AIInfo
        when (game.phase) {
            GamePhase.Discarding -> {
                info = game.aIs[game.playerInTurn]!!
                info.myHand.visibleHand = game.deal.hands[game.playerInTurn]

                val discardingCards = getDiscard(info, game)
                game.discardCard(discardingCards.first)
                game.discardCard(discardingCards.second)

                game.next()
            }
            GamePhase.Ended -> {
                // Невозможно!!!
            }
            GamePhase.EndPlay -> {
                game.endConfirm()
                game.next()
            }
            GamePhase.EndTurn -> {
                game.turnClose()
                game.next()
            }
            GamePhase.GameChoose -> {
                info = game.aIs[game.playerInTurn]!!
                info.myHand.visibleHand = game.deal.hands[game.playerInTurn]
                val bbid = getContract(info, game.getAllowedBids())
                game.setContract(bbid)
                game.next()
            }
            GamePhase.Negotiations -> {
                val bid = getBid(game.aIs[game.playerInTurn]!!, game.getAllowedBids())
                game.makeBid(bid)
                game.next()
            }
            GamePhase.NotStarted -> {
                // Невозможно!!!
            }
            GamePhase.OpeningChoose -> {
                if (rnd.nextInt(10) < 9)
                    game.setOpeningChoice(true)
                else
                    game.setOpeningChoice(false)
                game.next()
            }
            GamePhase.Playing -> {
                info = game.aIs[game.playerInTurn]!!
                info.writeOutOfPlay(game)
                val cardToPlay = playCard(info, game)!!
                info.myHand.visibleHand!!.cards = info.myHand.visibleHand!!.cards
                    .filter { it.coatColor != cardToPlay.coatColor || it.value != cardToPlay.value }.toMutableList()
                info.myHand.visibleHand!!.sort()
                game.playCard(cardToPlay)
                game.next()
            }
            GamePhase.PrikupOpened -> {
                game.prikupClose()
                game.next()
            }
            GamePhase.ScoreView -> {
                game.scoreClose()
                game.next()
            }
            GamePhase.VistNegotiations -> {
                // Всегда предлагаем вистануть другому
                if (game.isVister.isEmpty() || game.isVister.values.first())
                    game.setVist(false)
                else {
                    // TODO: Проверить, стоит ли вистовать?
                    game.setVist(true)
                }
                game.next()
            }
        }
    }

    fun getDiscard(info: AIInfo, game: Game): Pair<Card, Card> {
        return if (game.currentGameType == GameType.Miser) {
            getCardsForMiserDiscard(info, game)
        } else {
            getBestCardsForNormalDiscard(info, game.getAllowedBids())
        }
    }

    fun playCard(info: AIInfo, game: Game): Card? {
        val rnd = Random.Default
        val allowedMoves = game.getAllowedMoves()
        val cardToPlay: Card?
        when (game.currentGameType) {
            GameType.Raspasy -> {
                cardToPlay = RaspasyHidden().play(info, game, allowedMoves)
            }
            GameType.Normal -> {
                cardToPlay = if (game.playerInTurn != game.contractor) {
                    if (game.isOpened)
                        VistyOpen().play(info, game, allowedMoves)
                    else
                        VistyHidden().play(info, game, allowedMoves)
                } else {
                    if (game.isOpened)
                        ContractOpen().play(info, game, allowedMoves)
                    else
                        ContractHidden().play(info, game, allowedMoves)
                }
            }
            GameType.Miser -> {
                cardToPlay = if (game.playerInTurn != game.contractor) {
                    if (game.isOpened)
                        MiserAntiOpen().play(info, game, allowedMoves)
                    else
                        throw Exception("НОНСЕНС!!!")
                } else {
                    if (game.isOpened)
                        MiserOpen().play(info, game, allowedMoves)
                    else
                        MiserHidden().play(info, game, allowedMoves)
                }
            }
            else -> {
                cardToPlay = allowedMoves[rnd.nextInt(allowedMoves.size)]
            }
        }
        return cardToPlay
    }

    private fun getBestCardsForNormalDiscard(info: AIInfo, bidsIn: List<Game.Bid>): Pair<Card, Card> {
        val rnd = Random.Default
        val bids = bidsIn.dropWhile { it.pas || it.miser }.take(5)
        val hand = info.myHand.visibleHand!!.clone()
        var max = -Double.MAX_VALUE
        val list = mutableListOf<Pair<Card, Card>>()
        for (i in 0 until 4) {
            var cards: List<Card> = hand.cardByCoat[i]!!.sortedBy { it.value }
            val firstCard = cards.firstOrNull() ?: continue
            for (j in i until 4) {
                cards = hand.cardByCoat[j]!!.sortedBy { it.value }
                if (i == j)
                    cards = cards.drop(1)
                val secondCard = cards.firstOrNull() ?: continue

                info.myHand.visibleHand = hand.clone()
                for (card in info.myHand.visibleHand!!.cards.toList()) {
                    if ((card.value == firstCard.value && card.coatColor == firstCard.coatColor)
                        || (card.value == secondCard.value && card.coatColor == secondCard.coatColor)
                    ) {
                        info.myHand.visibleHand!!.cards.remove(card)
                    }
                }
                info.myHand.visibleHand!!.sort()

                val nt = NormalTakes(info)

                for (bid in bids) {
                    val cnt = nt.getMaxContract(bid.trump, info.myHand.visibleHand!!, info.myFirstMove, true)
                    val v = (cnt.takes - bid.contract) * 10000 + cnt.turns
                    if (v > max) {
                        max = v
                        list.clear()
                        list.add(Pair(firstCard, secondCard))
                    }
                }
            }
        }
        info.myHand.visibleHand = hand
        return list[rnd.nextInt(list.size)]
    }

    private fun getCardsForMiserDiscard(info: AIInfo, game: Game): Pair<Card, Card> {
        val list = Helper.getPotentialDiscards(info, game)!!
        val rnd = Random.Default
        val pSum = list.sumOf { it.probability }
        val pos = rnd.nextDouble() * pSum
        var cPos = 0.0
        for (discard in list) {
            cPos += discard.probability
            if (pos < cPos)
                return Pair(discard.firstCard!!, discard.secondCard!!)
        }

        throw Exception("Неверный рассчёт вероятностей")
    }

    fun getBid(info: AIInfo, bidsIn: List<Game.Bid>): Game.Bid {
        val nt = NormalTakes(info)
        val pas = Game.Bid().also { it.pas = true }
        if (bidsIn.firstOrNull { it.miser } != null) {
            // Мы можем объявить мизер
            val mt = MiserTakes(info)
            if (mt.totalTakes <= 1.5)
                return Game.Bid().also { it.miser = true }
        }
        val bids = bidsIn.dropWhile { it.pas || it.miser }.take(5)
        if (bids.isEmpty())
            return pas
        for (bid in bids) {
            if (nt.getMaxContract(bid.trump, info.myHand.visibleHand!!, info.myFirstMove, false).maxContract >= bid.contract) {
                return bids.first()
            }
        }
        return pas
    }

    fun getContract(info: AIInfo, bidsIn: List<Game.Bid>): Game.Bid {
        val nt = NormalTakes(info)
        val bids = bidsIn.dropWhile { it.pas || it.miser }.take(5)
        if (bids.isEmpty())
            throw Exception("Нет доступного контракта!")
        var max = -Double.MAX_VALUE
        var bestBid: Game.Bid? = null
        for (bid in bids) {
            val cnt = nt.getMaxContract(bid.trump, info.myHand.visibleHand!!, info.myFirstMove, false)
            var v = cnt.takes * 10000 + cnt.turns
            if (cnt.takes >= bid.contract)
                v += 100000
            if (v > max) {
                max = v
                bestBid = bid
                if (cnt.takes > bestBid.contract)
                    bestBid.contract = floor(cnt.takes).toInt()
            }
        }

        return bestBid!!
    }
}
