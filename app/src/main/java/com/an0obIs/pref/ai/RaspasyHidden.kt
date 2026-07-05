package com.an0obIs.pref.ai

class RaspasyHidden : HiddenPlay() {
    override fun createEstimation(): Estimation {
        return RaspasyEstimation()
    }
}
