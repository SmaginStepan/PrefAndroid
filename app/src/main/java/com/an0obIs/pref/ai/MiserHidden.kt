package com.an0obIs.pref.ai

class MiserHidden : HiddenPlay() {
    override fun createEstimation(): Estimation {
        val est = MiserEstimation()
        est.isHidden = true
        return est
    }
}
