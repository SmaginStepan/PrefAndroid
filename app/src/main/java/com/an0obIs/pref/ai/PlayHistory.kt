package com.an0obIs.pref.ai

import com.an0obIs.pref.model.Card

class PlayHistory {
    var discardsByMe: MutableList<Card> = mutableListOf()
    var firstMovesByMe: MutableList<Card> = mutableListOf()

    var discardsByPrev: MutableList<Card> = mutableListOf()
    var firstMovesByPrev: MutableList<Card> = mutableListOf()

    var discardsByNext: MutableList<Card> = mutableListOf()
    var firstMovesByNext: MutableList<Card> = mutableListOf()

    private fun fill(move: Move) {
        move.myMove?.let {
            if (it.coatColor != move.playColor)
                discardsByMe.add(it)
            if (move.firstMovePerformer == 0)
                firstMovesByMe.add(it)
        }
        move.nextMove?.let {
            if (it.coatColor != move.playColor)
                discardsByNext.add(it)
            if (move.firstMovePerformer == 1)
                firstMovesByNext.add(it)
        }
        move.prevMove?.let {
            if (it.coatColor != move.playColor)
                discardsByPrev.add(it)
            if (move.firstMovePerformer == -1)
                firstMovesByPrev.add(it)
        }
    }

    private fun fill(move: AIInfo.Take) {
        move.myMove?.let {
            if (it.coatColor != move.playColor)
                discardsByMe.add(it)
            if (move.firstMovePerformer == 0)
                firstMovesByMe.add(it)
        }
        move.nextMove?.let {
            if (it.coatColor != move.playColor)
                discardsByNext.add(it)
            if (move.firstMovePerformer == 1)
                firstMovesByNext.add(it)
        }
        move.prevMove?.let {
            if (it.coatColor != move.playColor)
                discardsByPrev.add(it)
            if (move.firstMovePerformer == -1)
                firstMovesByPrev.add(it)
        }
    }

    constructor(takes: Iterable<AIInfo.Take>, lastMove: Move) {
        for (take in takes) {
            fill(take)
        }
        fill(lastMove)
    }

    constructor(takes: Iterable<AIInfo.Take>, moves: Iterable<Move>) {
        for (take in takes) {
            fill(take)
        }
        for (move in moves) {
            fill(move)
        }
    }
}
