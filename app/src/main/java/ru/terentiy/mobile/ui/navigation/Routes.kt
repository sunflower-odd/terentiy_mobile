package ru.terentiy.mobile.ui.navigation

/** Маршруты приложения. */
object Routes {
    const val SPLASH = "splash"
    const val ACTIVATION = "activation"
    const val PIN = "pin"

    const val HOME = "home"
    const val EVENTS = "events"
    const val PROFILE = "profile"

    const val EVENT_DETAIL = "event/{eventId}"
    const val CLIP = "clip/{mediaId}"

    const val ZONES = "zones"

    fun eventDetail(eventId: String): String = "event/$eventId"

    fun clip(mediaId: String): String = "clip/$mediaId"

    /** Маршруты, на которых виден нижний таб-бар. */
    val TAB_ROUTES = setOf(HOME, EVENTS, PROFILE)

    /** Маршруты входа: на них потеря сессии не должна никуда переводить. */
    val AUTH_ROUTES = setOf(SPLASH, ACTIVATION, PIN)
}
