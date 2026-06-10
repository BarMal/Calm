package dev.barna.calm

/**
 * Third-party messaging channels that register custom contact-data rows. Each app advertises a
 * well-known MIME type on a contact's `ContactsContract.Data` row; launching that row (ACTION_VIEW)
 * opens the app's contact action (message/call) for that person.
 */
enum class ContactAppChannelKind(val label: String) {
    WHATSAPP("WhatsApp"),
    SIGNAL("Signal"),
    TELEGRAM("Telegram"),
}

object ContactChannels {
    const val MIME_WHATSAPP = "vnd.android.cursor.item/vnd.com.whatsapp.profile"
    const val MIME_SIGNAL = "vnd.android.cursor.item/vnd.org.thoughtcrime.securesms.contact"
    const val MIME_TELEGRAM = "vnd.android.cursor.item/vnd.org.telegram.messenger.android.profile"

    /** MIME types worth querying for on a contact's data rows. */
    val appChannelMimeTypes: List<String> = listOf(MIME_WHATSAPP, MIME_SIGNAL, MIME_TELEGRAM)

    fun appChannelFor(mimeType: String?): ContactAppChannelKind? = when (mimeType) {
        MIME_WHATSAPP -> ContactAppChannelKind.WHATSAPP
        MIME_SIGNAL -> ContactAppChannelKind.SIGNAL
        MIME_TELEGRAM -> ContactAppChannelKind.TELEGRAM
        else -> null
    }
}
