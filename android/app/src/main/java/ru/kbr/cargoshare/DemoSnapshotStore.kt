package ru.kbr.cargoshare

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Local, non-sensitive demo data. No passport photos, card numbers, tokens or real phone numbers. */
class DemoSnapshotStore(context: Context) {
    private val prefs = context.getSharedPreferences("gruzsher_demo_v2", Context.MODE_PRIVATE)

    fun load(): Pair<List<Booking>, DemoState> = runCatching {
        val root = JSONObject(prefs.getString("snapshot", "{}") ?: "{}")
        val saved = root.optJSONArray("bookings") ?: JSONArray()
        val trips = (0 until saved.length()).map { n ->
            val it = saved.getJSONObject(n)
            Booking(
                it.getString("id"), it.getString("vehicleId"), it.getString("status"),
                it.getString("startAt"), it.getString("endAt"), it.getInt("totalRub")
            )
        }
        val st = root.optJSONObject("demo") ?: JSONObject()
        val log = st.optJSONArray("events") ?: JSONArray()
        val events = (0 until log.length()).map { log.optString(it) }
        val state = DemoState(
            loggedIn = st.optBoolean("loggedIn"),
            documentsVerified = st.optBoolean("documentsVerified"),
            paymentLinked = st.optBoolean("paymentLinked"),
            tripId = st.optString("tripId").ifBlank { null },
            tripStatus = st.optString("tripStatus", "NONE"),
            doorsOpen = st.optBoolean("doorsOpen"),
            beforePhotos = st.optBoolean("beforePhotos"),
            returnPhotos = st.optBoolean("returnPhotos"),
            extraHours = st.optInt("extraHours"),
            fuelPercent = st.optInt("fuelPercent", 72),
            receiptReady = st.optBoolean("receiptReady"),
            supportRequested = st.optBoolean("supportRequested"),
            incidentCount = st.optInt("incidentCount"),
            notifications = st.optBoolean("notifications", true),
            rated = st.optBoolean("rated"),
            events = events.takeLast(24),
        )
        // Never restore a ride that is no longer in the local bookings list.
        Pair(trips, if (state.tripId == null || trips.any { it.id == state.tripId }) state else state.copy(
            tripId = null, tripStatus = "NONE", doorsOpen = false, receiptReady = false
        ))
    }.getOrElse { Pair(emptyList(), DemoState()) }

    fun save(bookings: List<Booking>, state: DemoState) {
        val root = JSONObject()
        val trips = JSONArray()
        bookings.take(100).forEach { b ->
            trips.put(JSONObject().apply {
                put("id", b.id); put("vehicleId", b.vehicleId); put("status", b.status)
                put("startAt", b.startAt); put("endAt", b.endAt); put("totalRub", b.totalRub)
            })
        }
        val s = JSONObject().apply {
            put("loggedIn", state.loggedIn)
            put("documentsVerified", state.documentsVerified)
            put("paymentLinked", state.paymentLinked)
            put("tripId", state.tripId ?: "")
            put("tripStatus", state.tripStatus)
            put("doorsOpen", state.doorsOpen)
            put("beforePhotos", state.beforePhotos)
            put("returnPhotos", state.returnPhotos)
            put("extraHours", state.extraHours)
            put("fuelPercent", state.fuelPercent)
            put("receiptReady", state.receiptReady)
            put("supportRequested", state.supportRequested)
            put("incidentCount", state.incidentCount)
            put("notifications", state.notifications)
            put("rated", state.rated)
            put("events", JSONArray(state.events.takeLast(24)))
        }
        root.put("bookings", trips)
        root.put("demo", s)
        prefs.edit().putString("snapshot", root.toString()).apply()
    }
}
