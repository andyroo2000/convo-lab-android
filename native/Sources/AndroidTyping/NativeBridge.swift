import Foundation

@_cdecl("typing_create")
public func typingCreate(_ directory: UnsafePointer<CChar>) -> UnsafeMutableRawPointer? {
    guard let converter = try? AndroidConverter(configuration: String(cString: directory)) else { return nil }
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
        return strdup(try converter.process(String(cString: json)))
    } catch {
        return strdup("{\"error\":\"Native conversion failed\"}")
    }
}

@_cdecl("typing_free")
public func typingFree(_ text: UnsafeMutablePointer<CChar>) {
    free(text)
}
