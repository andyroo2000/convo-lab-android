import Foundation

// A stable fallback preserves the pinned engine's exact finite-score rounding.
enum BoundaryNormalization {
    static func logarithm(_ logits: UnsafeMutablePointer<Float>, vocabulary: Int) -> Float {
        var denominator: Float = 0
        for word in 0..<vocabulary { denominator += expf(logits[word]) }
        if denominator.isFinite && denominator > 0 { return logf(denominator) }
        // Preserve pinned-model rounding when finite; shift only for overflow/underflow.
        var maximum = -Float.infinity
        for word in 0..<vocabulary { maximum = max(maximum, logits[word]) }
        denominator = 0
        for word in 0..<vocabulary { denominator += expf(logits[word] - maximum) }
        return maximum + logf(denominator)
    }
}
