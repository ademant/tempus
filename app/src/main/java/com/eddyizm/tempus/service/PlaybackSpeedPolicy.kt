package com.eddyizm.tempus.service

/** Pitch that goes with a playback speed. Pure so the rules can be unit tested. */
internal object PlaybackSpeedPolicy {

    /**
     * Pitch is kept at 1 unless pitch following is on. With a manual pitch set it uses that value,
     * otherwise it follows the speed.
     */
    fun pitchFor(
        speed: Float,
        pitchEnabled: Boolean,
        manualPitchEnabled: Boolean,
        manualPitch: Float,
    ): Float {
        if (!pitchEnabled) return 1.0f
        return if (manualPitchEnabled) manualPitch else speed
    }
}
