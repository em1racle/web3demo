// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "RealtimeFeed",
    platforms: [.macOS(.v14), .iOS(.v17)],
    products: [
        .library(name: "RealtimeFeed", targets: ["RealtimeFeed"]),
        .executable(name: "RealtimeFeedDemo", targets: ["RealtimeFeedDemo"]),
    ],
    targets: [
        .target(name: "RealtimeFeed"),
        .executableTarget(name: "RealtimeFeedDemo", dependencies: ["RealtimeFeed"]),
        .testTarget(name: "RealtimeFeedTests", dependencies: ["RealtimeFeed"]),
    ]
)
