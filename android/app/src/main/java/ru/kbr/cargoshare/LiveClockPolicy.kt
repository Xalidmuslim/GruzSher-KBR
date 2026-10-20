package ru.kbr.cargoshare

/** A 1 Hz timer is only needed when the active simulation has time-dependent
 * state or visible progress. Idle screens must not recompose once per second.
 */
internal fun needsLiveClock(phase: LivePhase): Boolean = phase in setOf(
    LivePhase.DOCUMENTS, LivePhase.PAYMENT, LivePhase.APPROVAL,
    LivePhase.WALK, LivePhase.UNLOCK, LivePhase.DRIVING,
    LivePhase.RETURNING, LivePhase.RETURN
)
