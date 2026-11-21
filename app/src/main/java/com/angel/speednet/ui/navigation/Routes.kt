package com.angel.speednet.ui.navigation

object Routes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val ARG_MEASUREMENT_ID = "measurementId"
    const val RESULTS = "results?$ARG_MEASUREMENT_ID={$ARG_MEASUREMENT_ID}"

    fun results(measurementId: Long): String = "results?$ARG_MEASUREMENT_ID=$measurementId"
}
