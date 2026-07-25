package com.yishenghuang.heartext.ui.reader.model

enum class ReaderEdgeTapAction {
    PREVIOUS_PAGE,
    NEXT_PAGE
}

enum class ReaderEdgeTapMode(
    val key: String,
    val leftAction: ReaderEdgeTapAction,
    val rightAction: ReaderEdgeTapAction
) {
    LEFT_PREVIOUS_RIGHT_NEXT(
        key = "left_previous_right_next",
        leftAction = ReaderEdgeTapAction.PREVIOUS_PAGE,
        rightAction = ReaderEdgeTapAction.NEXT_PAGE
    ),
    LEFT_NEXT_RIGHT_PREVIOUS(
        key = "left_next_right_previous",
        leftAction = ReaderEdgeTapAction.NEXT_PAGE,
        rightAction = ReaderEdgeTapAction.PREVIOUS_PAGE
    ),
    BOTH_PREVIOUS(
        key = "both_previous",
        leftAction = ReaderEdgeTapAction.PREVIOUS_PAGE,
        rightAction = ReaderEdgeTapAction.PREVIOUS_PAGE
    ),
    BOTH_NEXT(
        key = "both_next",
        leftAction = ReaderEdgeTapAction.NEXT_PAGE,
        rightAction = ReaderEdgeTapAction.NEXT_PAGE
    );

    companion object {
        fun fromKey(key: String?): ReaderEdgeTapMode =
            entries.firstOrNull { it.key == key } ?: LEFT_PREVIOUS_RIGHT_NEXT
    }
}
