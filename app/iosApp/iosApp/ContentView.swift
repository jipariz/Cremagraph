import SwiftUI
import UIKit
import OnlyShotsApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        FoldWatchingController(content: MainViewControllerKt.MainViewController())
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
    }
}

/**
 * Holds the Compose screen and tells it about the fold of a foldable iPhone (iOS 27.1): the hinge's status from a
 * `UIHingeInteraction`, and where the fold crosses the window from the view's division region, which moves as the
 * window turns. Kotlin's UIKit bindings do not have these yet, so they are read here.
 */
final class FoldWatchingController: UIViewController {
    private let content: UIViewController
    private var partiallyOpen = false
    private var hasHinge = false

    init(content: UIViewController) {
        self.content = content
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) { fatalError("not from a storyboard") }

    override func viewDidLoad() {
        super.viewDidLoad()
        addChild(content)
        content.view.frame = view.bounds
        content.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(content.view)
        content.didMove(toParent: self)
        if #available(iOS 27.1, *) {
            view.addInteraction(UIHingeInteraction { [weak self] _, update in
                guard let self else { return }
                self.hasHinge = update.hinge != nil
                self.partiallyOpen = update.hinge?.status == .partiallyOpen
                self.report()
            })
        }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        report()
    }

    private func report() {
        guard #available(iOS 27.1, *), hasHinge,
              let region = view.reservedRegions(kind: .division).first
        else {
            MainViewControllerKt.foldGone()
            return
        }
        // The frame carries margins for touch targets around it; the fold itself is inside them.
        let fold = region.frame.inset(by: region.margins)
        MainViewControllerKt.foldChanged(
            partiallyOpen: partiallyOpen,
            left: fold.minX, top: fold.minY, right: fold.maxX, bottom: fold.maxY
        )
    }

    override var childForStatusBarStyle: UIViewController? { content }
    override var childForHomeIndicatorAutoHidden: UIViewController? { content }
}
