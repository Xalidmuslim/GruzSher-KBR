package ru.kbr.cargoshare

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** On-device training progress only. No actual phone number, passwords, cards or documents. */
class LiveDemoStorage(context: Context) {
    private val prefs = context.getSharedPreferences("gruzsher_live_demo_v1", Context.MODE_PRIVATE)

    fun load(): LiveDemoSession = runCatching {
        val j = JSONObject(prefs.getString("progress", "{}") ?: "{}")
        val phase = runCatching { LivePhase.valueOf(j.optString("phase", "CHOOSE")) }.getOrDefault(LivePhase.CHOOSE)
        val angles = j.optJSONArray("inspected") ?: JSONArray()
        val events = j.optJSONArray("events") ?: JSONArray()
        LiveDemoSession(
            phase = phase,
            carId = j.optString("carId"),
            phone = "", // never persist phone input
            codeRequestedAt = j.optLong("codeRequestedAt"),
            passportStartedAt = j.optLong("passportStartedAt"),
            licenceStartedAt = j.optLong("licenceStartedAt"),
            documentIssue = j.optString("documentIssue"),
            paymentStartedAt = j.optLong("paymentStartedAt"),
            durationHours = j.optInt("durationHours", 2),
            approvalStartedAt = j.optLong("approvalStartedAt"),
            approachStartedAt = j.optLong("approachStartedAt"),
            inspected = (0 until angles.length()).map { angles.getString(it) }.toSet(),
            damageRecorded = j.optBoolean("damageRecorded"),
            unlockingAt = j.optLong("unlockingAt"),
            drivingAt = j.optLong("drivingAt"),
            returnRequestedAt = j.optLong("returnRequestedAt"),
            returnStartFraction = j.optDouble("returnStartFraction", 0.0).toFloat(),
            finishedAt = j.optLong("finishedAt"),
            returnPhotoAt = j.optLong("returnPhotoAt"),
            lockingAt = j.optLong("lockingAt"),
            locked = j.optBoolean("locked"),
            extraHours = j.optInt("extraHours"),
            pauseStartedAt = j.optLong("pauseStartedAt"),
            pausedSeconds = j.optLong("pausedSeconds"),
            assistance = j.optBoolean("assistance"),
            incident = j.optBoolean("incident"),
            rating = j.optInt("rating"),
            bookingId = j.optString("bookingId"),
            events = (0 until events.length()).map { events.getString(it) }.takeLast(30)
        )
    }.getOrDefault(LiveDemoSession())

    fun save(s: LiveDemoSession) {
        val j = JSONObject().apply {
            put("phase", s.phase.name)
            put("carId", s.carId)
            put("codeRequestedAt", s.codeRequestedAt)
            put("passportStartedAt", s.passportStartedAt)
            put("licenceStartedAt", s.licenceStartedAt)
            put("documentIssue", s.documentIssue)
            put("paymentStartedAt", s.paymentStartedAt)
            put("durationHours", s.durationHours)
            put("approvalStartedAt", s.approvalStartedAt)
            put("approachStartedAt", s.approachStartedAt)
            put("inspected", JSONArray(s.inspected.toList()))
            put("damageRecorded", s.damageRecorded)
            put("unlockingAt", s.unlockingAt)
            put("drivingAt", s.drivingAt)
            put("returnRequestedAt", s.returnRequestedAt)
            put("returnStartFraction", s.returnStartFraction.toDouble())
            put("finishedAt", s.finishedAt)
            put("returnPhotoAt", s.returnPhotoAt)
            put("lockingAt", s.lockingAt)
            put("locked", s.locked)
            put("extraHours", s.extraHours)
            put("pauseStartedAt", s.pauseStartedAt)
            put("pausedSeconds", s.pausedSeconds)
            put("assistance", s.assistance)
            put("incident", s.incident)
            put("rating", s.rating)
            put("bookingId", s.bookingId)
            put("events", JSONArray(s.events.takeLast(30)))
        }
        prefs.edit().putString("progress", j.toString()).apply()
    }
}
