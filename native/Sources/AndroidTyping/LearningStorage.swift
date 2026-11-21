import Foundation

// The Android owner supplies an account-scoped directory inside noBackupFilesDir.
enum LearningStorage {
  static func prepare(_ directory: URL) throws {
    try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
  }

  static func protect(_ directory: URL) throws {
    // Android app-private storage supplies access control and backup exclusion.
  }
}
