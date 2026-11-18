// swift-tools-version: 6.4
import PackageDescription
let flags: [SwiftSetting] = [.define("ZenzaiCPU"), .enableUpcomingFeature("InternalImportsByDefault")]
let package = Package(
    name: "AndroidTyping",
    products: [.library(name: "AndroidTyping", type: .dynamic, targets: ["AndroidTyping"])],
    dependencies: [
        .package(url: "https://github.com/apple/swift-algorithms", exact: "1.2.1"),
        .package(url: "https://github.com/apple/swift-collections", exact: "1.7.6")
    ],
    targets: [
        .systemLibrary(name: "llama"),
        .target(name: "SwiftUtils", dependencies: [.product(name: "Algorithms", package: "swift-algorithms")], path: "Vendor/AzooKey/Sources/SwiftUtils", swiftSettings: flags),
        .target(name: "EfficientNGram", path: "Sources/Personalization", swiftSettings: flags),
        .target(name: "KanaKanjiConverterModule", dependencies: ["SwiftUtils", "EfficientNGram", "llama", .product(name: "Collections", package: "swift-collections")], path: "Vendor/AzooKey/Sources/KanaKanjiConverterModule", swiftSettings: flags),
        .target(name: "AndroidTyping", dependencies: ["KanaKanjiConverterModule"], swiftSettings: flags)
    ]
)
