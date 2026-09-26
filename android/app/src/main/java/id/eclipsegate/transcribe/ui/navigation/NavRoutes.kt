package id.eclipsegate.transcribe.ui.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Dashboard : Screen("dashboard")
    object LiveTranscription : Screen("live_transcription/{meetingId}/{title}") {
        fun createRoute(meetingId: String, title: String): String {
            return "live_transcription/$meetingId/${android.net.Uri.encode(title)}"
        }
    }
    object MeetingDetail : Screen("meeting_detail/{meetingId}") {
        fun createRoute(meetingId: String): String {
            return "meeting_detail/$meetingId"
        }
    }
}
