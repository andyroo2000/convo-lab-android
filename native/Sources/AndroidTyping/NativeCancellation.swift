import Synchronization

final class NativeCancellation: Sendable {
    private let flag = Atomic(false)
    var isCancelled: Bool { flag.load(ordering: .relaxed) }
    func begin() { flag.store(false, ordering: .relaxed) }
    func cancel() { flag.store(true, ordering: .relaxed) }
}
