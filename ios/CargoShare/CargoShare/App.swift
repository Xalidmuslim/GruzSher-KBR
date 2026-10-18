import SwiftUI

@main
struct CargoShareApp: App {
    @StateObject private var api = CargoAPI()

    var body: some Scene {
        WindowGroup {
            RootView().environmentObject(api)
        }
    }
}
