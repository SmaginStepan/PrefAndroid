package com.an0obIs.pref.ai

class VistyHidden : HiddenPlay() {
    override fun createEstimation(): Estimation {
        val est = VistyEstimation()
        est.isHidden = true
        return est
    }
}
