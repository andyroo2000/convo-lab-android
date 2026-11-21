import Foundation

@main
struct BoundaryNormalizationTests {
    static func main() {
        var ordinary: [Float] = [0.1, -0.2, 2]
        var denominator: Float = 0
        for value in ordinary { denominator += expf(value) }
        let expected = logf(denominator)
        let actual = ordinary.withUnsafeMutableBufferPointer {
            BoundaryNormalization.logarithm($0.baseAddress!, vocabulary: $0.count)
        }
        precondition(actual.bitPattern == expected.bitPattern, "Finite model scores must retain their original rounding")
        checkExtreme([1000, 1001], expected: 1001 + logf(1 + expf(-1)))
        checkExtreme([-1000, -1001], expected: -1000 + logf(1 + expf(-1)))
        print("Boundary normalization: finite parity, overflow and underflow passed")
    }
    private static func checkExtreme(_ source: [Float], expected: Float) {
        var values = source
        let result = values.withUnsafeMutableBufferPointer {
            BoundaryNormalization.logarithm($0.baseAddress!, vocabulary: $0.count)
        }
        precondition(result.isFinite && abs(result - expected) < 0.0001)
    }
}
