import Foundation
import KanaKanjiConverterModule

// The Kotlin owner serializes every call, including learning and destruction.
final class AndroidConverter {
    private let engine: PlaygroundEngine
    private let cancellation: NativeCancellation

    init(configuration: String) throws {
        let config = try EngineConfiguration.read(configuration)
        let signal = NativeCancellation()
        cancellation = signal
        engine = try PlaygroundEngine(directory: URL(fileURLWithPath: config.directory),
            learningDirectory: config.learningDirectory.map { URL(fileURLWithPath: $0) }, cancelled: { signal.isCancelled })
    }

    func beginOperation() { cancellation.begin() }
    func cancelOperation() { cancellation.cancel() }

    func process(_ json: String) throws -> String {
        if cancellation.isCancelled { throw PlaygroundFailure.cancelled }
        let data = Data(json.utf8)
        let request = try JSONDecoder().decode(EngineRequest.self, from: data)
        switch request.action {
        case "benchmarkBoundary":
            return try JournalScoringDiagnostics.compare(converter: engine.converter, modelPath: engine.modelURL.path, json: json)
        case "learn":
            return try engine.learn(json) ? "{\"learned\":true}" : "{\"learned\":false}"
        case "resetLearning":
            try engine.resetLearning()
            return "{\"reset\":true}"
        case nil, "convert":
            return try convertRequest(request, json: json)
        default: throw PlaygroundFailure.invalidReading
        }
    }

    private func convertRequest(_ request: EngineRequest, json: String) throws -> String {
        if request.text != nil { return try legacyConversion(Data(json.utf8)) }
        return try engine.convert(json)
    }

    // Preserve the romaji playground contract while Journal uses iOS clauses/units.
    private func legacyConversion(_ data: Data) throws -> String {
        let input = try JSONDecoder().decode(LegacyInput.self, from: data)
        let reading = try input.reading()
        let request = ConversionInput(reading: reading, expanded: nil, boundaries: nil)
        let json = String(decoding: try JSONEncoder().encode(request), as: UTF8.self)
        let result = try engine.convert(json)
        let rich = try JSONDecoder().decode(LegacyResult.self, from: Data(result.utf8))
        let output = LegacyOutput(reading: reading, candidates: rich.candidates.map(\.text), engineMs: rich.engineMs, resetMs: engine.lastResetMs)
        return String(decoding: try JSONEncoder().encode(output), as: UTF8.self)
    }
}

private struct EngineConfiguration: Decodable {
    let directory: String
    let learningDirectory: String?
    static func read(_ value: String) throws -> Self {
        if value.hasPrefix("{") { return try JSONDecoder().decode(Self.self, from: Data(value.utf8)) }
        return Self(directory: value, learningDirectory: nil)
    }
}
private struct EngineRequest: Decodable { let action: String?; let text: String? }
private struct LegacyResult: Decodable {
    struct Candidate: Decodable { let text: String }
    let candidates: [Candidate]
    let engineMs: Double
}
private struct LegacyOutput: Encodable {
    let reading: String
    let candidates: [String]
    let engineMs: Double
    let resetMs: Double
}
private struct LegacyInput: Decodable {
    let text: String
    let finalize: Bool

    func reading() throws -> String {
        guard (1...120).contains(text.utf16.count),
              !text.unicodeScalars.contains(where: CharacterSet.controlCharacters.contains)
        else { throw PlaygroundFailure.invalidReading }
        var composition = ComposingText()
        let roman = text.lowercased().replacingOccurrences(of: "n'", with: "ん").replacingOccurrences(of: "n’", with: "ん")
        composition.insertAtCursorPosition(roman, inputStyle: .roman2kana)
        if finalize { composition.insertAtCursorPosition([.init(piece: .compositionSeparator, inputStyle: .roman2kana)]) }
        guard composition.convertTarget.count <= 80 else { throw PlaygroundFailure.invalidReading }
        return composition.convertTarget
    }
}
