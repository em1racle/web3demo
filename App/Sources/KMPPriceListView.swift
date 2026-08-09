import SwiftUI
import Shared

struct KMPPriceListView: View {
    @State private var viewModel = KMPPriceFeedViewModel()

    var body: some View {
        NavigationStack {
            List(viewModel.rows) { row in
                KMPPriceRowView(row: row)
                    .equatable()
            }
            .navigationTitle("Live Prices (KMP)")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    KMPConnectionBadge(label: viewModel.stateLabel, colorName: viewModel.stateColorName)
                }
            }
        }
        .task { viewModel.start() }
        .onDisappear { viewModel.stop() }
    }
}

private struct KMPPriceRowView: View, Equatable {
    let row: KMPPriceFeedViewModel.Row

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

private struct KMPConnectionBadge: View {
    let label: String
    let colorName: String

    var body: some View {
        HStack(spacing: 6) {
            Circle().fill(color).frame(width: 8, height: 8)
            Text(label).font(.caption)
        }
    }

    private var color: Color {
        switch colorName {
        case "green": .green
        case "yellow": .yellow
        case "orange": .orange
        default: .red
        }
    }
}
