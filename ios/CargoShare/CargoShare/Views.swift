import SwiftUI

private let forest = Color(red: 0.15, green: 0.35, blue: 0.30)
private let pale = Color(red: 0.96, green: 0.96, blue: 0.94)
private let sage = Color(red: 0.88, green: 0.92, blue: 0.89)

struct RootView: View {
    @EnvironmentObject private var api: CargoAPI
    @State private var tab = 0
    @State private var selectedVehicle: Vehicle? = nil

    var body: some View {
        TabView(selection: $tab) {
            catalog
                .tabItem { Label("Каталог", systemImage: "truck.box") }.tag(0)
            bookingsView
                .tabItem { Label("Мои брони", systemImage: "list.bullet.rectangle") }.tag(1)
            profileView
                .tabItem { Label("Профиль", systemImage: "person.crop.circle") }.tag(2)
        }
        .tint(forest)
        .task { await api.load() }
        .sheet(item: $selectedVehicle) { truck in
            BookingSheet(vehicle: truck, tab: $tab)
                .environmentObject(api)
                .presentationDetents([.large])
        }
        .alert("Сообщение", isPresented: Binding(
            get: { api.error != nil }, set: { if !$0 { api.error = nil } }
        )) { Button("Закрыть") { api.error = nil } }
        message: { Text(api.error ?? "") }
    }

    private var catalog: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    demoNotice
                    Label("Нальчик и КБР", systemImage: "location.fill")
                        .font(.subheadline).foregroundStyle(forest)
                    Text("Грузовой транспорт\nрядом с вами")
                        .font(.system(size: 32, weight: .bold, design: .rounded))
                    Text("Аренда без водителя · от 2 часов")
                        .font(.subheadline).foregroundStyle(.secondary)
                    HStack(spacing: 12) {
                        Image(systemName: "checkmark.shield.fill").font(.title2)
                        VStack(alignment: .leading) {
                            Text("Прозрачная стоимость").fontWeight(.semibold)
                            Text("Цена рассчитывается до бронирования").font(.caption)
                        }
                    }
                    .foregroundStyle(forest)
                    .padding(18)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(sage, in: RoundedRectangle(cornerRadius: 18))
                    Text("Доступный транспорт").font(.title2.bold())
                    if api.loading { ProgressView("Загружаем каталог") }
                    ForEach(api.vehicles) { v in
                        Button { selectedVehicle = v } label: { VehicleCard(vehicle: v) }
                            .buttonStyle(.plain)
                    }
                }
                .padding(20)
            }
            .background(pale)
            .toolbar(.hidden, for: .navigationBar)
        }
    }

    private var demoNotice: some View {
        Text("ДЕМО · Автомобили и тарифы приведены для примера")
            .font(.caption2).foregroundStyle(forest)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(10).background(sage, in: RoundedRectangle(cornerRadius: 10))
    }

    private var bookingsView: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 12) {
                    if api.bookings.isEmpty {
                        ContentUnavailableView("Пока нет бронирований", systemImage: "calendar", description: Text("Выберите грузовой автомобиль в каталоге."))
                    }
                    ForEach(api.bookings) { b in
                        VStack(alignment: .leading, spacing: 8) {
                            Text(api.vehicles.first(where: { $0.id == b.vehicleId })?.name ?? "Автомобиль").font(.headline)
                            Text(statusTitle(b.status)).foregroundStyle(forest)
                            Text("\(b.totalRub) ₽ · \(b.startAt.prefix(16).replacingOccurrences(of: "T", with: " "))")
                                .font(.caption).foregroundStyle(.secondary)
                            if b.status == "PENDING" || b.status == "CONFIRMED" {
                                Button("Отменить заявку", role: .destructive) { Task { await api.cancel(b) } }
                                    .font(.subheadline)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(18)
                        .background(.white, in: RoundedRectangle(cornerRadius: 18))
                    }
                }.padding(20)
            }
            .background(pale)
            .navigationTitle("Мои бронирования")
        }
    }

    private func statusTitle(_ status: String) -> String {
        switch status {
        case "PENDING": return "Ожидает подтверждения"
        case "CONFIRMED": return "Подтверждено"
        case "CANCELLED": return "Отменено"
        default: return status
        }
    }

    private var profileView: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text("Демонстрационный режим").font(.title3.bold())
                    Text("Регистрация по SMS, проверка водительского удостоверения, оплата и договор аренды будут добавлены перед запуском настоящего сервиса.")
                        .foregroundStyle(.secondary)
                    Text("Регион: Нальчик · Кабардино-Балкария").foregroundStyle(forest)
                }
                .padding(20)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(.white, in: RoundedRectangle(cornerRadius: 18))
                .padding(20)
            }
            .background(pale)
            .navigationTitle("Профиль")
        }
    }
}

private struct VehicleCard: View {
    let vehicle: Vehicle

    var body: some View {
        VStack(alignment: .leading, spacing: 13) {
            Image(systemName: "truck.box.fill")
                .resizable().scaledToFit()
                .frame(height: 90)
                .foregroundStyle(forest)
                .frame(maxWidth: .infinity)
                .frame(height: 120)
                .background(sage, in: RoundedRectangle(cornerRadius: 16))
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(vehicle.name).font(.title3.bold())
                    Text(vehicle.type).font(.subheadline).foregroundStyle(.secondary)
                }
                Spacer()
                Image(systemName: "arrow.up.right").foregroundStyle(forest)
            }
            Text("\(vehicle.payloadKg) кг · \(vehicle.cargoVolumeM3) м³ · \(vehicle.transmission)")
                .font(.subheadline).foregroundStyle(.secondary)
            HStack(alignment: .firstTextBaseline) {
                Text("от \(vehicle.hourlyPriceRub) ₽").font(.title2.bold()).foregroundStyle(forest)
                Text("/ час").font(.caption).foregroundStyle(.secondary)
                Spacer()
                Text("\(vehicle.dailyPriceRub) ₽ / сутки").font(.caption).foregroundStyle(.secondary)
            }
        }
        .padding(16)
        .background(.white, in: RoundedRectangle(cornerRadius: 22))
        .shadow(color: .black.opacity(0.04), radius: 8, y: 3)
    }
}

private struct BookingSheet: View {
    @EnvironmentObject private var api: CargoAPI
    @Environment(\.dismiss) private var dismiss
    let vehicle: Vehicle
    @Binding var tab: Int
    @State private var start = Date().addingTimeInterval(24 * 3600)
    @State private var duration = 4
    @State private var quote: Quote? = nil
    @State private var checking = false
    @State private var message: String? = nil
    private let durations = [2, 4, 8, 24]

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 17) {
                    Text(vehicle.name).font(.largeTitle.bold())
                    Text(vehicle.type).foregroundStyle(.secondary)
                    if !vehicle.description.isEmpty {
                        Text(vehicle.description).font(.subheadline).foregroundStyle(.secondary)
                    }
                    Text("\(vehicle.payloadKg) кг · \(vehicle.cargoVolumeM3) м³ · \(vehicle.transmission)")
                        .foregroundStyle(forest)
                    Label(vehicle.pickupLocation, systemImage: "mappin")
                        .font(.caption).foregroundStyle(.secondary)
                    DatePicker("Начало аренды", selection: $start, in: Date()..., displayedComponents: [.date, .hourAndMinute])
                        .datePickerStyle(.compact)
                        .onChange(of: start) { _, _ in quote = nil }
                    Text("Продолжительность").font(.headline)
                    HStack(spacing: 8) {
                        ForEach(durations, id: \.self) { h in
                            Button(h == 24 ? "Сутки" : "\(h) ч") { duration = h; quote = nil }
                                .font(.subheadline.bold())
                                .foregroundStyle(duration == h ? .white : forest)
                                .padding(.vertical, 10).frame(maxWidth: .infinity)
                                .background(duration == h ? forest : sage, in: RoundedRectangle(cornerRadius: 12))
                        }
                    }
                    if let q = quote {
                        Text(q.isAvailable ? "Стоимость: \(q.totalRub) ₽" : "Автомобиль занят в выбранное время")
                            .font(.title3.bold()).foregroundStyle(forest)
                    }
                    if let message { Text(message).font(.caption).foregroundStyle(.red) }
                    Button {
                        Task {
                            checking = true
                            defer { checking = false }
                            do { quote = try await api.quote(for: vehicle, start: start, hours: duration); message = nil }
                            catch { message = error.localizedDescription }
                        }
                    } label: {
                        Text(checking ? "Проверяем…" : "Проверить цену и доступность")
                            .frame(maxWidth: .infinity).padding(5)
                    }
                    .buttonStyle(.borderedProminent).tint(forest).disabled(checking)
                    if quote?.isAvailable == true {
                        Button("Отправить заявку") {
                            Task {
                                checking = true
                                defer { checking = false }
                                do {
                                    try await api.book(vehicle: vehicle, start: start, hours: duration)
                                    dismiss()
                                    tab = 1
                                } catch { message = error.localizedDescription }
                            }
                        }
                        .buttonStyle(.bordered).tint(forest).disabled(checking)
                    }
                    Text("Демонстрационная бронь без оплаты и выдачи автомобиля.")
                        .font(.caption2).foregroundStyle(.secondary)
                }
                .padding(20)
            }
            .background(pale)
            .navigationTitle("Бронирование")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("Закрыть") { dismiss() } } }
        }
    }
}
