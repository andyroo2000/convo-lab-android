import Foundation

@_cdecl("typing_create")
public func typingCreate(_ directory: UnsafePointer<CChar>) -> UnsafeMutableRawPointer? {
    let converter = AndroidConverter(directory: String(cString: directory))
    return Unmanaged.passRetained(converter).toOpaque()
}

@_cdecl("typing_destroy")
public func typingDestroy(_ handle: UnsafeMutableRawPointer) {
    Unmanaged<AndroidConverter>.fromOpaque(handle).release()
}

@_cdecl("typing_convert")
public func typingConvert(_ handle: UnsafeMutableRawPointer, _ json: UnsafePointer<CChar>) -> UnsafeMutablePointer<CChar>? {
    let converter = Unmanaged<AndroidConverter>.fromOpaque(handle).takeUnretainedValue()
    do {
        let input = try JSONDecoder().decode(Input.self, from: Data(String(cString: json).utf8))
        let output = try JSONEncoder().encode(converter.convert(input))
        return strdup(String(decoding: output, as: UTF8.self))
    } catch {
        return strdup("{\"error\":\"Native conversion failed\"}")
    }
}

@_cdecl("typing_free")
public func typingFree(_ text: UnsafeMutablePointer<CChar>) {
    free(text)
}
