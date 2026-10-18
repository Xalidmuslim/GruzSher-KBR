package ru.kbr.cargoshare

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

// The API is deliberately enabled only for a local demonstration backend.
data class Vehicle(
    val id: String,
    val name: String,
    val type: String,
    val payloadKg: Int,
    val cargoVolumeM3: Int,
    val hourlyPriceRub: Int,
    val dailyPriceRub: Int,
    val transmission: String,
    val pickupLocation: String,
    val description: String,
    val imageUrl: String,
    val latitude: Double?,
    val longitude: Double?,
)

data class BookingTimelineEvent(val type: String, val at: String)

data class Booking(
    val id: String,
    val vehicleId: String,
    val status: String,
    val startAt: String,
    val endAt: String,
    val totalRub: Int,
)

class Api(private val baseUrl: String = BuildConfig.API_URL) {
    private suspend fun request(path: String, method: String = "GET", body: JSONObject? = null, token: String? = null, idempotencyKey: String? = null): JSONObject = withContext(Dispatchers.IO) {
        val conn = (URL("$baseUrl$path").openConnection() as HttpURLConnection)
        try {
            conn.requestMethod = method
            conn.connectTimeout = 7000
            conn.readTimeout = 7000
            conn.setRequestProperty("Accept", "application/json")
            if (token != null) conn.setRequestProperty("Authorization", "Bearer $token")
            if (idempotencyKey != null) conn.setRequestProperty("Idempotency-Key", idempotencyKey)
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val response = (if (code < 400) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val detail = runCatching { JSONObject(response).optString("detail", "Ошибка сервера") }.getOrDefault("Ошибка соединения")
                throw IllegalStateException("$detail ($code)")
            }
            JSONObject(response)
        } finally {
            conn.disconnect()
        }
    }

    suspend fun demoSession(): String = request("/v1/demo/session", method = "POST").getString("accessToken")

    suspend fun vehicles(): List<Vehicle> {
        val data: JSONArray = request("/v1/vehicles").getJSONArray("data")
        return (0 until data.length()).map { index ->
            val v = data.getJSONObject(index)
            Vehicle(v.getString("id"), v.getString("name"), v.getString("type"),
                v.getInt("payloadKg"), v.getInt("cargoVolumeM3"), v.getInt("hourlyPriceRub"),
                v.getInt("dailyPriceRub"), v.getString("transmission"), v.getString("pickupLocation"),
                v.optString("description", ""), v.optString("imageUrl", ""),
                if (v.isNull("latitude")) null else v.getDouble("latitude"),
                if (v.isNull("longitude")) null else v.getDouble("longitude"))
        }
    }

    suspend fun bookings(token: String): List<Booking> {
        val data = request("/v1/bookings", token = token).getJSONArray("data")
        return (0 until data.length()).map { i ->
            val b = data.getJSONObject(i)
            Booking(b.getString("id"), b.getString("vehicleId"), b.getString("status"),
                b.getString("startAt"), b.getString("endAt"), b.getInt("totalRub"))
        }
    }

    suspend fun quote(vehicleId: String, start: ZonedDateTime, end: ZonedDateTime): Pair<Int, Boolean> {
        val result = request("/v1/quotes", method = "POST", body = bookingBody(vehicleId, start, end))
        return Pair(result.getInt("totalRub"), result.getBoolean("isAvailable"))
    }

    suspend fun book(vehicleId: String, start: ZonedDateTime, end: ZonedDateTime, token: String, idempotencyKey: String): Booking {
        val b = request("/v1/bookings", method = "POST", body = bookingBody(vehicleId, start, end), token = token, idempotencyKey = idempotencyKey)
        return Booking(b.getString("id"), b.getString("vehicleId"), b.getString("status"),
            b.getString("startAt"), b.getString("endAt"), b.getInt("totalRub"))
    }

    suspend fun timeline(id: String, token: String): List<BookingTimelineEvent> {
        val events = request("/v1/bookings/$id/timeline", token = token).getJSONArray("events")
        return (0 until events.length()).map { index ->
            val e = events.getJSONObject(index)
            BookingTimelineEvent(e.getString("type"), e.getString("at"))
        }
    }

    suspend fun cancel(id: String, token: String) {
        request("/v1/bookings/$id/cancel", method = "POST", token = token)
    }

    private fun bookingBody(id: String, start: ZonedDateTime, end: ZonedDateTime) = JSONObject().apply {
        put("vehicleId", id)
        put("startAt", start.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
        put("endAt", end.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
    }
}
