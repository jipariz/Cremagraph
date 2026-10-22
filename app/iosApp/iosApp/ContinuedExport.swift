import BackgroundTasks
import Foundation
import OnlyShotsApp

/**
 * iOS 26's continued processing for the export (`ContinuedProcessing` in app/shared's iosMain): started from the
 * Export tap, it keeps the render going in the background and shows the system's progress, with its own cancel, on the
 * lock screen and in the Dynamic Island. Swift's, so the class is only touched on iOS 26.
 */
@available(iOS 26.0, *)
final class ContinuedExport: NSObject, ContinuedProcessing {
    private var task: BGContinuedProcessingTask?
    private var registered = Set<String>()
    private var expired: (() -> Void)?
    /** Progress that came before the system started the task, so it starts there. */
    private var fraction = 0.0

    func start(id: String, title: String, subtitle: String, onExpired: @escaping () -> Void) -> Bool {
        let scheduler = BGTaskScheduler.shared
        expired = onExpired
        fraction = 0
        // One registration per identifier for the app's life: a second one would crash.
        if !registered.contains(id) {
            registered.insert(id)
            _ = scheduler.register(forTaskWithIdentifier: id, using: .main) { [weak self] launched in
                guard let self, let launched = launched as? BGContinuedProcessingTask else {
                    launched.setTaskCompleted(success: false)
                    return
                }
                self.task = launched
                launched.progress.totalUnitCount = 1000
                launched.progress.completedUnitCount = Int64(self.fraction * 1000)
                // The user's cancel in the system's progress, or the system ending it: stop the render cleanly.
                launched.expirationHandler = { [weak self] in self?.expired?() }
            }
        }
        let request = BGContinuedProcessingTaskRequest(identifier: id, title: title, subtitle: subtitle)
        // Now or not at all: queued, it could start after the render has finished.
        request.strategy = .fail
        do {
            try scheduler.submit(request)
            return true
        } catch {
            return false
        }
    }

    func progress(fraction: Double, subtitle: String) {
        self.fraction = fraction
        guard let task else { return }
        task.progress.completedUnitCount = Int64(fraction * 1000)
        task.updateTitle(task.title, subtitle: subtitle)
    }

    func finish(success: Bool) {
        task?.setTaskCompleted(success: success)
        task = nil
        expired = nil
    }
}
