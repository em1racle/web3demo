import SwiftUI
import RealtimeFeed

struct PriceListView: View {
    @State private var viewModel = PriceFeedViewModel()

    var body: some View {
        NavigationStack {
            List(viewModel.rows) { row in
                PriceRowView(row: row)
                    .equatable()
            }
            .navigationTitle("Live Prices")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    ConnectionBadge(state: viewModel.connectionState)
                }
            }
        }
        .task { viewModel.start() }
        .onDisappear { viewModel.stop() }
    }
}

private struct PriceRowView: View, Equatable {
    let row: PriceFeedViewModel.Row

    nonisolated static func == (lhs: Self, rhs: Self) -> Bool { lhs.row == rhs.row }

    var body: some View {
        HStack {
            Text(row.symbol).font(.headline)
            Spacer()
            Text(row.price, format: .number.precision(.fractionLength(2)))
                .font(.system(.body, design: .monospaced))
        }
    }
}

private struct ConnectionBadge: View {
    let state: ConnectionState

    var body: some View {
        HStack(spacing: 6) {
            Circle().fill(color).frame(width: 8, height: 8)
            Text(label).font(.caption)
        }
    }

    private var color: Color {
        switch state {
        case .connected: .green
        case .connecting: .yellow
        case .reconnecting: .orange
        case .disconnected: .red
        }
    }

    private var label: String {
        switch state {
        case .connected: "Live"
        case .connecting: "Connecting…"
        case .reconnecting(let attempt, _): "Reconnecting #\(attempt)"
        case .disconnected: "Offline"
        }
    }
}

#Preview {
    PriceListView()
}
