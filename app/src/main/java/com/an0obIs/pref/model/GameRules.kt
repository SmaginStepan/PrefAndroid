package com.an0obIs.pref.model

import kotlinx.serialization.Serializable

enum class RulesGameType { Sochy, Leningrad, Rostov }
enum class RaspasyProgression { NoProgression1, Arifm1233, Geom1244 }
enum class RaspasyExit { Easy6, Med677, Hard678 }
enum class VistType { HalfResponsibility, FullResponsibility }
enum class ConsolationType { Gentlemen, Zlob }
enum class EndingType { Sum, Each }
enum class ConsolationSum { Normal, Max10 }
enum class ScoreType { Normal, Leningrad }

@Serializable
class GameRules {
    var gameType: RulesGameType = RulesGameType.Sochy
    var raspasyProgression: RaspasyProgression = RaspasyProgression.Arifm1233
    var raspasyExit: RaspasyExit = RaspasyExit.Med677
    var miserRaspExit: Boolean = true
    var vist: VistType = VistType.FullResponsibility
    var consolation: ConsolationType = ConsolationType.Zlob
    var vistTakeOnRaspas: Int = 5
    var ending: EndingType = EndingType.Each
    var scoring: ScoreType = ScoreType.Normal
    var consolationBonus: ConsolationSum = ConsolationSum.Normal
    var prikupConsolation: Boolean = true
    var stalindgrad: Boolean = true

    // Note: the original Clone() silently skipped scoring/consolationBonus/
    // prikupConsolation/stalindgrad, so those settings never reached a new game.
    // Fixed here: all fields are copied.
    fun clone(): GameRules = GameRules().also {
        it.gameType = gameType
        it.raspasyProgression = raspasyProgression
        it.raspasyExit = raspasyExit
        it.miserRaspExit = miserRaspExit
        it.vist = vist
        it.consolation = consolation
        it.vistTakeOnRaspas = vistTakeOnRaspas
        it.ending = ending
        it.scoring = scoring
        it.consolationBonus = consolationBonus
        it.prikupConsolation = prikupConsolation
        it.stalindgrad = stalindgrad
    }
}
