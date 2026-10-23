package app.onlyshots.core.model

/**
 * The machine's name as the screens and the graph's title write it. Machines go by their own names; a scale is a word,
 * which the caller brings in the user's language ([scale]).
 */
fun machineName(kind: SourceKind, scale: String): String =
    when (kind) {
        SourceKind.GAGGIUINO -> "Gaggiuino"
        SourceKind.GAGGIMATE -> "GaggiMate"
        SourceKind.DECENT -> "Decent DE1"
        SourceKind.SCALE -> scale
        SourceKind.VISUALIZER -> "Visualizer"
    }

/**
 * The graph's title: the user's own when they wrote one, else "profile · machine". The preview and every exporter use
 * this one rule, so they never disagree.
 */
fun overlayTitle(shot: Shot, overlay: OverlaySettings, scale: String): String =
    overlay.title?.takeIf { it.isNotBlank() } ?: "${shot.profileName} · ${machineName(shot.source, scale)}"
