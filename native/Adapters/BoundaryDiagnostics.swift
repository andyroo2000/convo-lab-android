// Appended to ZenzContext.swift so diagnostics can compare against its unchanged
// upstream scorer. Only the standalone public-fixture tests invoke this entry.
public enum JournalScoringDiagnostics {
    public static func compare(converter: KanaKanjiConverter, modelPath: String, json: String) throws -> String {
        let request = try JSONDecoder().decode(ScoringProbe.self, from: Data(json.utf8))
        try request.validate()
        guard let context = converter.getModel(modelURL: URL(fileURLWithPath: modelPath))?.zenzContext else { throw ScoringProbeFailure.invalidInput }
        let result = try context.compareScores(request)
        return String(decoding: try JSONEncoder().encode(result), as: UTF8.self)
    }
}

private struct ScoringProbe: Decodable {
    let reading: String
    let texts: [String]
    let threads: Int
    func validate() throws {
        guard (1...120).contains(reading.count), (1...160).contains(texts.count), (1...8).contains(threads),
              texts.allSatisfy({ (1...120).contains($0.count) }) else { throw ScoringProbeFailure.invalidInput }
    }
}
private struct ScoringProbeResult: Encodable {
    let reference: [Float]
    let optimized: [Float]
    let referenceMs: Double
    let optimizedMs: Double
}
private enum ScoringProbeFailure: Error { case invalidInput, missingLogits }

extension ZenzContext {
    fileprivate func compareScores(_ request: ScoringProbe) throws -> ScoringProbeResult {
        llama_set_n_threads(context, Int32(request.threads), Int32(request.threads))
        defer { llama_set_n_threads(context, Self.ctx_params.n_threads, Self.ctx_params.n_threads_batch); resetForTyping() }
        let kana = request.reading.applyingTransform(.hiraganaToKatakana, reverse: false) ?? request.reading
        let prompt = "\u{EE00}\(kana)\u{EE01}"
        resetForTyping()
        let start = ContinuousClock.now
        let reference = request.texts.map { evaluate(text: prompt + $0, ignorePrompt: prompt) }
        let referenceEnd = ContinuousClock.now
        resetForTyping()
        let optimized = try evaluateCandidatePrefixes(request.texts, prompt: prompt)
        return ScoringProbeResult(reference: reference, optimized: optimized,
            referenceMs: elapsed(start, referenceEnd), optimizedMs: elapsed(referenceEnd, .now))
    }

    private func elapsed(_ start: ContinuousClock.Instant, _ end: ContinuousClock.Instant) -> Double {
        let parts = start.duration(to: end).components
        return Double(parts.seconds) * 1000 + Double(parts.attoseconds) / 1e15
    }
}

