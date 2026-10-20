package app.onlyshots.core.model

/**
 * Maps source time to video time for one recording.
 *
 * At the trigger the recorder notes the camera presentation time of the trigger relative to the start of the file
 * ([triggerVideoUs], which includes the pre-roll) and the source clock at the same moment ([triggerSourceUs]). Every
 * later sample is placed by the difference.
 */
data class ClockSync(val triggerVideoUs: Long, val triggerSourceUs: Long) {
    fun toVideoUs(sourceUs: Long): Long = triggerVideoUs + (sourceUs - triggerSourceUs)

    fun toSourceUs(videoUs: Long): Long = triggerSourceUs + (videoUs - triggerVideoUs)
}
