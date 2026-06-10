package dev.barna.calm

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.provider.ContactsContract

class ContactsRepository(
    private val activity: MainActivity,
    private val requestContactsPermission: () -> Unit,
) {
    fun hasContactsPermission(): Boolean {
        return activity.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }

    fun requestContactsAccess() {
        requestContactsPermission()
    }

    fun loadFavouriteContacts(): List<ContactEntry> {
        if (!hasContactsPermission()) return emptyList()
        val resolver = activity.contentResolver
        val contacts = ArrayList<ContactEntry>()
        runCatching {
            resolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.LOOKUP_KEY,
                    ContactsContract.Contacts.DISPLAY_NAME,
                    ContactsContract.Contacts.HAS_PHONE_NUMBER,
                ),
                "${ContactsContract.Contacts.STARRED}=1",
                null,
                "${ContactsContract.Contacts.DISPLAY_NAME} COLLATE NOCASE ASC",
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
                val lookupIdx = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY)
                val nameIdx = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME)
                val hasPhoneIdx = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.HAS_PHONE_NUMBER)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIdx)?.takeIf { it.isNotBlank() } ?: continue
                    val id = cursor.getLong(idIdx)
                    val number = if (cursor.getInt(hasPhoneIdx) > 0) primaryNumber(resolver, id) else null
                    contacts.add(
                        ContactEntry(
                            contactId = id,
                            lookupKey = cursor.getString(lookupIdx),
                            displayName = name,
                            primaryNumber = number,
                            appChannels = appChannels(resolver, id),
                            hueColor = hueForName(name),
                        ),
                    )
                }
            }
        }
        return contacts
    }

    fun photo(contact: ContactEntry): Bitmap? {
        return runCatching {
            val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contact.contactId)
            ContactsContract.Contacts.openContactPhotoInputStream(activity.contentResolver, uri, true)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }.getOrNull()
    }

    fun launchCall(number: String) = launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))))

    fun launchSms(number: String) = launch(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number))))

    fun launchAppChannel(channel: ContactAppChannel) {
        val dataUri = ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, channel.dataId)
        launch(Intent(Intent.ACTION_VIEW).setDataAndType(dataUri, channel.mimeType))
    }

    fun launchViewContact(contact: ContactEntry) {
        val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contact.contactId)
        launch(Intent(Intent.ACTION_VIEW, uri))
    }

    private fun launch(intent: Intent) {
        runCatching { activity.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun primaryNumber(resolver: ContentResolver, contactId: Long): String? {
        return runCatching {
            resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID}=?",
                arrayOf(contactId.toString()),
                "${ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY} DESC",
            )?.use { c ->
                val numIdx = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (c.moveToFirst()) c.getString(numIdx) else null
            }
        }.getOrNull()
    }

    private fun appChannels(resolver: ContentResolver, contactId: Long): List<ContactAppChannel> {
        val mimes = ContactChannels.appChannelMimeTypes
        val placeholders = mimes.joinToString(",") { "?" }
        val selection = "${ContactsContract.Data.CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE} IN ($placeholders)"
        val args = (listOf(contactId.toString()) + mimes).toTypedArray()
        val result = LinkedHashMap<ContactAppChannelKind, ContactAppChannel>()
        runCatching {
            resolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data._ID, ContactsContract.Data.MIMETYPE),
                selection,
                args,
                null,
            )?.use { c ->
                val idIdx = c.getColumnIndexOrThrow(ContactsContract.Data._ID)
                val mimeIdx = c.getColumnIndexOrThrow(ContactsContract.Data.MIMETYPE)
                while (c.moveToNext()) {
                    val kind = ContactChannels.appChannelFor(c.getString(mimeIdx)) ?: continue
                    if (!result.containsKey(kind)) {
                        result[kind] = ContactAppChannel(kind, c.getLong(idIdx), c.getString(mimeIdx))
                    }
                }
            }
        }
        return result.values.toList()
    }

    private fun hueForName(name: String): Int {
        val hue = (name.hashCode() and 0x7fffffff) % 360
        return Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.45f, 0.92f))
    }
}
