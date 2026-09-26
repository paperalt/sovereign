package id.eclipsegate.transcribe.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Stable
class ScrollAnchorController(
    val listState: LazyListState,
    private val scope: CoroutineScope
) {
    // True if scroll position is anchored near the bottom
    val isAtBottom by derivedStateOf {
        val layoutInfo = listState.layoutInfo
        val totalItems = layoutInfo.totalItemsCount
        if (totalItems == 0) return@derivedStateOf true

        val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        lastVisibleItem >= totalItems - 2
    }

    var hasUnreadBelow by mutableStateOf(false)
        private set

    fun onNewItemAppended(totalItems: Int) {
        if (totalItems == 0) return

        if (isAtBottom) {
            hasUnreadBelow = false
            scope.launch {
                listState.animateScrollToItem(totalItems - 1)
            }
        } else {
            // Freeze viewport: prevent jumping while user is reading previous text
            hasUnreadBelow = true
        }
    }

    fun scrollToBottom(totalItems: Int) {
        hasUnreadBelow = false
        scope.launch {
            listState.animateScrollToItem(totalItems - 1)
        }
    }
}

@Composable
fun rememberScrollAnchorController(
    listState: LazyListState,
    scope: CoroutineScope = rememberCoroutineScope()
): ScrollAnchorController {
    return remember(listState) {
        ScrollAnchorController(listState, scope)
    }
}
