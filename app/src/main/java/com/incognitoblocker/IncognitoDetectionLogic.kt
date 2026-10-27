package com.incognitoblocker

object IncognitoDetectionLogic {

    const val CHROME_STABLE_PACKAGE = "com.android.chrome"
    const val MAX_TREE_DEPTH = 16

    const val INCOGNITO_LANDING_TITLE_ID =
        "com.android.chrome:id/new_tab_incognito_title"

    val EXPLICIT_INCOGNITO_INDICATORS = listOf(
        "you've gone incognito",
        "you have gone incognito"
    )

    enum class DetectionState {
        INCONCLUSIVE,
        NOT_INCOGNITO,
        INCOGNITO
    }

    data class Transition(
        val previous: DetectionState,
        val current: DetectionState,
        val changed: Boolean
    )

    fun normalizeText(raw: String?): String {
        if (raw.isNullOrBlank()) return ""

        return raw
            .lowercase()
            .replace("’", "'")
            .replace("‘", "'")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun isExplicitIncognitoIndicator(raw: String?): Boolean {
        val normalized = normalizeText(raw)
        if (normalized.isBlank()) return false

        return EXPLICIT_INCOGNITO_INDICATORS.any { indicator ->
            normalized.contains(indicator)
        }
    }

    /**
     * Only the actual Chrome Incognito landing-page title is reliable.
     * A search query, autocomplete suggestion, or webpage can contain the
     * same phrase without Chrome being on its Incognito landing page.
     */
    fun isIncognitoLandingTitle(
        viewId: String?,
        text: String?
    ): Boolean {
        if (viewId != INCOGNITO_LANDING_TITLE_ID) return false
        return isExplicitIncognitoIndicator(text)
    }

    fun isGoogleSearchUrl(raw: String?): Boolean {
        val normalized = normalizeText(raw)
        if (normalized.isBlank()) return false

        return normalized.contains("google.com/search") ||
                normalized.contains("google.co.in/search") ||
                normalized.contains("google.com/url?") ||
                (
                        normalized.contains("q=") &&
                                normalized.contains("google.")
                        )
    }

    /**
     * null means the current screen does not provide enough evidence
     * to classify the browser state.
     */
    fun evaluateTransition(
        previous: DetectionState,
        isIncognito: Boolean?
    ): Transition {
        val current = when (isIncognito) {
            true -> DetectionState.INCOGNITO
            false -> DetectionState.NOT_INCOGNITO
            null -> DetectionState.INCONCLUSIVE
        }

        return Transition(
            previous = previous,
            current = current,
            changed = previous != current
        )
    }
}