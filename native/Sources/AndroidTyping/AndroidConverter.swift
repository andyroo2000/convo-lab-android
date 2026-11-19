import Foundation
import KanaKanjiConverterModule

// The Kotlin owner serializes all calls, including destruction.
final class AndroidConverter {
    private let converter: KanaKanjiConverter
    private let options: ConvertRequestOptions
    private let modelURL: URL

    init(directory: String) {
        let root = URL(fileURLWithPath: directory)
        modelURL = root.appendingPathComponent("model.gguf")
        converter = KanaKanjiConverter(dictionaryURL: root.appendingPathComponent("Dictionary"))
        let scratch = root.appendingPathComponent("disabled-learning")
        options = ConvertRequestOptions(
            needTypoCorrection: false, requireJapanesePrediction: false,
            requireEnglishPrediction: false, keyboardLanguage: .ja_JP,
            learningType: .nothing, memoryDirectoryURL: scratch,
            sharedContainerURL: scratch, textReplacer: .empty,
            specialCandidateProviders: KanaKanjiConverter.defaultSpecialCandidateProviders,
            zenzaiMode: .on(weight: modelURL, inferenceLimit: 1,
                           personalizationMode: nil, versionDependentMode: .v3(.init())),
            metadata: .init(versionString: "ConvoLab Android playground")
        )
    }

    func convert(_ input: Input) throws -> Output {
        guard (1...120).contains(input.text.utf16.count),
              !input.text.unicodeScalars.contains(where: CharacterSet.controlCharacters.contains)
        else { throw ConversionFailure.tooLong }
        var composition = ComposingText()
        let roman = input.text.lowercased()
            .replacingOccurrences(of: "n'", with: "ん")
            .replacingOccurrences(of: "n’", with: "ん")
        composition.insertAtCursorPosition(roman, inputStyle: .roman2kana)
        if input.finalize {
            composition.insertAtCursorPosition([.init(piece: .compositionSeparator, inputStyle: .roman2kana)])
        }
        guard composition.convertTarget.count <= 80 else { throw ConversionFailure.tooLong }
        let start = ContinuousClock.now
        converter.stopComposition()
        let reset = start.duration(to: .now).components
        let resetMs = Double(reset.seconds) * 1000 + Double(reset.attoseconds) / 1e15
        let result = converter.requestCandidates(composition, options: options)
        guard converter.zenzStatus == "load \(modelURL.absoluteString)" else {
            throw ConversionFailure.modelUnavailable
        }
        let elapsed = start.duration(to: .now).components
        var seen = Set<String>()
        let candidates = result.mainResults.map(\.text).filter { seen.insert($0).inserted }
        return Output(reading: composition.convertTarget, candidates: Array(candidates.prefix(10)),
                      engineMs: Double(elapsed.seconds) * 1000 + Double(elapsed.attoseconds) / 1e15,
                      resetMs: resetMs)
    }
}

struct Input: Decodable {
    let text: String
    let finalize: Bool
}

struct Output: Encodable {
    let reading: String
    let candidates: [String]
    let engineMs: Double
    let resetMs: Double
}

enum ConversionFailure: Error {
    case tooLong, modelUnavailable
}
