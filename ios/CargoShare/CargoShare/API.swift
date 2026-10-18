import Foundation

struct Vehicle: Codable, Identifiable {
    let id: String
    let name: String
    let type: String
    let payloadKg: Int
    let cargoVolumeM3: Int
    let hourlyPriceRub: Int
    let dailyPriceRub: Int
    let transmission: String
    let pickupLocation: String
    let description: String
    let imageUrl: String
    let isPublished: Bool
    let isDemo: Bool
}

struct Booking: Codable, Identifiable {
    let id: String
    let vehicleId: String
    let startAt: String
    let endAt: String
    let totalRub: Int
    let status: String
}

struct Quote: Codable {
    let vehicleId: String
    let totalRub: Int
    let isAvailable: Bool
}

private struct VehicleList: Decodable { let data: [Vehicle] }
private struct BookingList: Decodable { let data: [Booking] }
private struct DemoSession: Decodable { let accessToken: String }

private struct BookingBody: Encodable {
    let vehicleId: String
    let startAt: String
    let endAt: String
}

private struct APIError: Decodable { let detail: String? }

@MainActor
final class CargoAPI: ObservableObject {
    @Published var vehicles: [Vehicle] = []
    @Published var bookings: [Booking] = []
    @Published var loading = false
    @Published var error: String? = nil
    private var demoToken: String? = nil

    // For iOS Simulator. Configure real HTTPS endpoint before shipping to users.
    #if DEBUG
    private let baseURL = URL(string: "http://127.0.0.1:8000")!
    #else
    private let baseURL = URL(string: "https://api.example.invalid")!
    #endif

    func load() async {
        loading = true
        defer { loading = false }
        do {
            await refreshVehicles()
            #if DEBUG
            demoToken = try await request(path: "/v1/demo/session", method: "POST", as: DemoSession.self).accessToken
            await refreshBookings()
            #endif
        } catch {
            self.error = "Сервер недоступен: \(error.localizedDescription)"
        }
    }

    func refreshVehicles() async {
        do { vehicles = try await request(path: "/v1/vehicles", as: VehicleList.self).data }
        catch { self.error = "Не удалось обновить каталог: \(error.localizedDescription)" }
    }

    func refreshBookings() async {
        guard let demoToken else { return }
        do { bookings = try await request(path: "/v1/bookings", token: demoToken, as: BookingList.self).data }
        catch { self.error = error.localizedDescription }
    }

    func quote(for vehicle: Vehicle, start: Date, hours: Int) async throws -> Quote {
        let body = try JSONEncoder().encode(bookingBody(for: vehicle, start: start, hours: hours))
        return try await request(path: "/v1/quotes", method: "POST", body: body, as: Quote.self)
    }

    func book(vehicle: Vehicle, start: Date, hours: Int) async throws {
        guard let demoToken else { throw NSError(domain: "Demo", code: 401, userInfo: [NSLocalizedDescriptionKey: "Нужен вход по SMS (в демо — локальный сервер)"]) }
        let body = try JSONEncoder().encode(bookingBody(for: vehicle, start: start, hours: hours))
        let _: Booking = try await request(path: "/v1/bookings", method: "POST", body: body, token: demoToken, idempotencyKey: UUID().uuidString, as: Booking.self)
        await refreshBookings()
    }

    func cancel(_ booking: Booking) async {
        guard let demoToken else { return }
        do {
            let _: Booking = try await request(path: "/v1/bookings/\(booking.id)/cancel", method: "POST", token: demoToken, as: Booking.self)
            await refreshBookings()
        } catch { self.error = error.localizedDescription }
    }

    private func bookingBody(for vehicle: Vehicle, start: Date, hours: Int) -> BookingBody {
        let fmt = ISO8601DateFormatter()
        let end = start.addingTimeInterval(Double(hours) * 3600)
        return BookingBody(vehicleId: vehicle.id, startAt: fmt.string(from: start), endAt: fmt.string(from: end))
    }

    private func request<T: Decodable>(path: String, method: String = "GET", body: Data? = nil, token: String? = nil, idempotencyKey: String? = nil, as type: T.Type) async throws -> T {
        guard let endpoint = URL(string: path, relativeTo: baseURL) else { throw URLError(.badURL) }
        var req = URLRequest(url: endpoint)
        req.httpMethod = method
        req.timeoutInterval = 10
        req.addValue("application/json", forHTTPHeaderField: "Accept")
        if let body {
            req.httpBody = body
            req.addValue("application/json", forHTTPHeaderField: "Content-Type")
        }
        if let token { req.addValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        if let idempotencyKey { req.addValue(idempotencyKey, forHTTPHeaderField: "Idempotency-Key") }
        let (data, response) = try await URLSession.shared.data(for: req)
        guard let http = response as? HTTPURLResponse else { throw URLError(.badServerResponse) }
        guard (200..<300).contains(http.statusCode) else {
            let detail = (try? JSONDecoder().decode(APIError.self, from: data))?.detail
            throw NSError(domain: "CargoShareAPI", code: http.statusCode, userInfo: [NSLocalizedDescriptionKey: detail ?? "HTTP \(http.statusCode)"])
        }
        return try JSONDecoder().decode(type, from: data)
    }
}
