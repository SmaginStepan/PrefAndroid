package com.an0obIs.pref.ai

class ContractHidden : HiddenPlay() {
    override fun createEstimation(): Estimation {
        val est = ContractEstimation()
        est.isHidden = true
        return est
    }
}
