package com.an0obIs.pref.model

class CalcList {

    class Calc {
        var limit: Int = 0
        var playersCount: Int = 0
        var created: Long = 0L
    }

    var calcs: MutableList<Calc> = mutableListOf()

    fun load() {
        calcs = mutableListOf()
        for (fileName in PrefStorage.listFiles("pulya_").sortedDescending()) {
            val shortName = fileName.substringBeforeLast('.')
            val ss = shortName.substring(6).split('_')
            try {
                val date = Calculation.parseFileDate(ss[0])
                val players = ss[1].toInt()
                val limit = ss[2].toInt()
                calcs.add(Calc().also {
                    it.created = date
                    it.playersCount = players
                    it.limit = limit
                })
            } catch (e: Exception) {
                // skip malformed file names
            }
        }
    }
}
