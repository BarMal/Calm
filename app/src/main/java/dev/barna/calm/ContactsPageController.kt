package dev.barna.calm

import android.graphics.Bitmap
import android.graphics.Outline
import android.graphics.Typeface
import android.os.Handler
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.Executor

class ContactsPageController(
    private val activity: MainActivity,
    private val contactsRepository: ContactsRepository,
    private val drawables: CalmDrawables,
    private val cardRenderer: CardRenderer,
    private val cardStackController: CardStackController,
    private val focusOverlay: FocusOverlayController,
    private val mainHandler: Handler,
    private val backgroundExecutor: Executor,
    private val activePreferences: () -> LauncherUiPreferences,
    private val barePagePanel: (Int) -> LinearLayout,
    private val label: (String, Int, Int, Int) -> TextView,
) {
    private var loadGeneration = 0

    fun buildPage(): LinearLayout {
        val page = barePagePanel(activity.dp(20))
        page.addView(
            label("People", 30, CalmTheme.INK, Typeface.NORMAL).apply {
                tag = CalmAnimationTags.CHROME
                setPadding(0, activity.dp(8), 0, activity.dp(20))
            },
        )
        val host = FrameLayout(activity).apply {
            clipChildren = false
            clipToPadding = false
        }
        page.addView(host, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        if (contactsRepository.hasContactsPermission()) {
            host.addView(note("Loading people…"))
            loadContacts(host)
        } else {
            host.addView(permissionPrompt())
        }
        return page
    }

    private fun loadContacts(host: FrameLayout) {
        val generation = ++loadGeneration
        backgroundExecutor.execute {
            val contacts = contactsRepository.loadFavouriteContacts()
            val photos = HashMap<Long, Bitmap?>()
            contacts.forEach { photos[it.contactId] = contactsRepository.photo(it) }
            mainHandler.post {
                if (generation != loadGeneration) return@post
                host.removeAllViews()
                if (contacts.isEmpty()) {
                    host.addView(note("No favourite contacts yet. Star a contact to see them here."))
                } else {
                    host.addView(
                        contactStack(contacts, photos),
                        FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                    )
                }
            }
        }
    }

    private fun contactStack(contacts: List<ContactEntry>, photos: Map<Long, Bitmap?>): View {
        val cards = contacts.map { contact -> contactCard(contact, photos[contact.contactId]) }
        return cardStackController.cardStack(
            cards,
            cardRenderer.cardHeight(),
            cardRenderer.cardStep(),
            activePreferences().cardStackTuning,
            STACK_KEY,
        )
    }

    private fun contactCard(contact: ContactEntry, photo: Bitmap?): TextView {
        return cardRenderer.stackCard(
            contact.displayName,
            contact.hueColor,
            true,
            photo,
            sideImageRenderKey = "contact:${contact.contactId}",
        ).apply {
            maxLines = 2
            setOnClickListener { contactsRepository.launchViewContact(contact) }
            setOnLongClickListener {
                focusOverlay.showExpandedCard(this, expandedContent(contact, photo), channelActions(contact))
                true
            }
        }
    }

    private fun channelActions(contact: ContactEntry): List<ContextAction> {
        val actions = ArrayList<ContextAction>()
        contact.primaryNumber?.let { number ->
            actions.add(ContextAction("Call", Runnable { contactsRepository.launchCall(number) }))
            actions.add(ContextAction("Message", Runnable { contactsRepository.launchSms(number) }))
        }
        contact.appChannels.forEach { channel ->
            actions.add(ContextAction(channel.kind.label, Runnable { contactsRepository.launchAppChannel(channel) }))
        }
        actions.add(ContextAction("Open contact", Runnable { contactsRepository.launchViewContact(contact) }))
        return actions
    }

    private fun expandedContent(contact: ContactEntry, photo: Bitmap?): View {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            if (photo != null) {
                addView(
                    roundedPhoto(photo),
                    LinearLayout.LayoutParams(activity.dp(72), activity.dp(72)).apply { rightMargin = activity.dp(18) },
                )
            }
            addView(
                LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(text(contact.displayName, 22, CalmTheme.INK, Typeface.BOLD))
                    val summary = channelSummary(contact)
                    if (summary.isNotBlank()) {
                        addView(text(summary, 13, CalmTheme.MUTED_INK, Typeface.NORMAL).apply {
                            setPadding(0, activity.dp(4), 0, 0)
                        })
                    }
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
        }
    }

    private fun channelSummary(contact: ContactEntry): String {
        val labels = ArrayList<String>()
        if (contact.primaryNumber != null) {
            labels.add("Call")
            labels.add("Message")
        }
        contact.appChannels.forEach { labels.add(it.kind.label) }
        return labels.joinToString("  ·  ")
    }

    private fun roundedPhoto(photo: Bitmap): ImageView {
        val radius = activity.dp(18).toFloat()
        return ImageView(activity).apply {
            setImageBitmap(photo)
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, radius)
                }
            }
        }
    }

    private fun permissionPrompt(): View {
        return note("Allow access to contacts to show your favourites here.").apply {
            isClickable = true
            setOnClickListener { contactsRepository.requestContactsAccess() }
        }
    }

    private fun note(message: String): TextView {
        return label(message, 15, CalmTheme.MUTED_INK, Typeface.NORMAL).apply {
            setPadding(activity.dp(16), activity.dp(14), activity.dp(16), activity.dp(14))
            background = drawables.glass(CalmTheme.QUIET_GLASS, activity.dp(16))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
    }

    private fun text(value: String, sp: Int, color: Int, style: Int): TextView {
        return TextView(activity).apply {
            text = value
            setTextColor(color)
            textSize = sp.toFloat()
            setTypeface(Typeface.DEFAULT, style)
        }
    }

    private companion object {
        const val STACK_KEY = "dev.barna.calm.contacts"
    }
}
