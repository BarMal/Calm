package dev.barna.calm

/** A favourite (starred) contact and the ways they can be reached. */
data class ContactEntry(
    val contactId: Long,
    val lookupKey: String?,
    val displayName: String,
    val primaryNumber: String?,
    val appChannels: List<ContactAppChannel>,
    val hueColor: Int,
)

/** A detected third-party messaging channel for a contact, with the data row needed to launch it. */
data class ContactAppChannel(
    val kind: ContactAppChannelKind,
    val dataId: Long,
    val mimeType: String,
)
