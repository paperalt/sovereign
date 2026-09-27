package org.sovereign.app.ui

import androidx.compose.runtime.Composable
import org.sovereign.app.auth.TokenStorage
import org.sovereign.app.data.MeetingRepository
import org.sovereign.app.ui.engine.AIEngineConfigDialog as EngineConfigDialogImpl

/**
 * Top-level alias for backward compatibility.
 * Delegates to the modular engine package under org.sovereign.app.ui.engine.
 */
@Composable
fun AIEngineConfigDialog(
    tokenStorage: TokenStorage,
    meetingRepository: MeetingRepository,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit
) {
    EngineConfigDialogImpl(
        tokenStorage = tokenStorage,
        meetingRepository = meetingRepository,
        onDismiss = onDismiss,
        onSaved = onSaved
    )
}
