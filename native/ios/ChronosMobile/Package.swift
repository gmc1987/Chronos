// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "ChronosMobile",
    platforms: [.iOS(.v16)],
    products: [.library(name: "ChronosMobileCore", targets: ["ChronosMobileCore"])],
    targets: [.target(name: "ChronosMobileCore", path: "Sources/ChronosMobileCore")]
)
