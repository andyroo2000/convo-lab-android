// Score every complete candidate, reusing exact CPU token-prefix scores and KV.
// Sorting is internal only; returned scores retain the caller's original order.
private enum BoundaryPrefixFailure: Error { case invalidInput, missingLogits, cancelled }

private struct ScoredPrefix {
    var tokens: [llama_token] = []
    var scores: [Float] = []
}

extension ZenzContext {
    func evaluateCandidatePrefixes(_ texts: [String], prompt: String, cancelled: () -> Bool = { false }) throws -> [Float] {
        let count = tokenize(text: prompt, add_bos: true, add_eos: false).count
        let sentences = texts.enumerated().map { (index: $0.offset, tokens: tokenize(text: prompt + $0.element, add_bos: true, add_eos: true)) }
        let ordered = sentences.sorted { $0.tokens.lexicographicallyPrecedes($1.tokens) }
        var previous = ScoredPrefix()
        var results = Array(repeating: Float(0), count: texts.count)
        for sentence in ordered {
            guard !cancelled() else { throw BoundaryPrefixFailure.cancelled }
            previous = try scorePrefix(sentence.tokens, promptCount: count, previous: previous)
            results[sentence.index] = previous.scores.reduce(0, +)
        }
        return results
    }

    private func scorePrefix(_ tokens: [llama_token], promptCount: Int, previous: ScoredPrefix) throws -> ScoredPrefix {
        guard promptCount > 0, promptCount < tokens.count, tokens.count <= 512 else { throw BoundaryPrefixFailure.invalidInput }
        let common = zip(tokens, previous.tokens).prefix { $0 == $1 }.count
        let first = min(max(common - 1, promptCount - 1), tokens.count - 2)
        var scores = Array(previous.scores.prefix(max(0, first + 1 - promptCount)))
        guard let logits = get_logits(tokens: Array(tokens.dropLast()), logits_start_index: first) else { throw BoundaryPrefixFailure.missingLogits }
        let vocabulary = Int(llama_vocab_n_tokens(vocab))
        for index in (first + 1)..<tokens.count {
            let row = (index - first - 1) * vocabulary
            scores.append(logits[row + Int(tokens[index])] - BoundaryNormalization.logarithm(logits.advanced(by: row), vocabulary: vocabulary))
        }
        return ScoredPrefix(tokens: tokens, scores: scores)
    }
}
