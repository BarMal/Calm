package dev.barna.calm

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.Collator

/**
 * Builds the per-page content views for the launcher pager. Overview and notification-chapter pages
 * delegate to their dedicated builders; pinned and app-library pages are assembled here from the
 * shared page primitives (panel/label/chrome) owned by the runner.
 */
class LauncherPageFactory(
    private val activity: MainActivity,
    private val drawables: CalmDrawables,
    private val focusOverlay: FocusOverlayController,
    private val overviewPageBuilder: OverviewPageBuilder,
    private val chapterPageBuilder: ChapterPageBuilder,
    private val appLibraryController: LauncherAppLibraryController,
    private val appSearchController: AppSearchController,
    private val appLibraryPageModelFactory: AppLibraryPageModelFactory,
    private val appLibraryStore: AppLibraryRenderStore,
    private val contactsPageController: ContactsPageController,
    private val resolveIcon: (AppEntry) -> Bitmap?,
    private val openAppEntry: (AppEntry) -> Unit,
    private val createWidgetView: (ClassicGridItem) -> View?,
    private val addAppToClassicPage: (ClassicLauncherPageDefinition, AppEntry) -> Unit,
    private val addWidgetToClassicPage: (ClassicLauncherPageDefinition) -> Unit,
    private val removeClassicGridItem: (ClassicLauncherPageDefinition, ClassicGridItem) -> Unit,
    private val moveClassicGridItem: (ClassicLauncherPageDefinition, ClassicGridItem, ClassicLauncherPageDefinition) -> Unit,
    private val barePagePanel: (Int) -> LinearLayout,
    private val label: (String, Int, Int, Int) -> TextView,
) {
    fun createPage(page: ChapterPage, state: LauncherRenderModel): View {
        return when {
            page.appScope != null -> createAppLibraryPage(page, state.appEntries)
            page.key == CalmTheme.PINNED_KEY -> createPinnedPage(state.pinnedApps)
            page.key == CalmTheme.CONTACTS_KEY -> contactsPageController.buildPage()
            page.key == CalmTheme.WORK_OVERVIEW_KEY -> overviewPageBuilder.buildPage(state, workProfile = true)
            page.classicPage != null -> createClassicPage(page.classicPage, state)
            page.chapter == null -> overviewPageBuilder.buildPage(state)
            else -> chapterPageBuilder.buildPage(page.chapter)
        }
    }

    private fun createClassicPage(
        classicPage: ClassicLauncherPageDefinition,
        state: LauncherRenderModel,
    ): LinearLayout {
        val appEntries = state.appEntries
        val appsByKey = appEntries.associateBy { it.identityKey }
        val gridItems = classicPage.items.mapNotNull { item ->
            when (item.type) {
                ClassicGridItemType.APP -> appsByKey[item.target]?.let { app -> item to classicAppTile(classicPage, item, app, state) }
                ClassicGridItemType.WIDGET -> item to classicWidgetTile(classicPage, item, state)
            }
        }
        return barePagePanel(activity.dp(20)).apply {
            addView(classicHeader(classicPage, state))
            if (gridItems.isEmpty()) {
                addView(
                    LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER
                        addView(label("No items yet", 22, CalmTheme.INK, Typeface.NORMAL).apply {
                            gravity = Gravity.CENTER
                        })
                    },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
                )
            } else {
                addView(
                    classicGrid(gridItems),
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
                )
            }
        }
    }

    private fun classicHeader(classicPage: ClassicLauncherPageDefinition, state: LauncherRenderModel): View {
        return animatedChrome(
            LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, activity.dp(8), 0, activity.dp(24))
                addView(
                    label(classicPage.title, 30, CalmTheme.INK, Typeface.NORMAL),
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                )
                addView(classicHeaderButton("Add app") { showClassicAppPicker(classicPage, state) }.apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        marginEnd = activity.dp(8)
                    }
                })
                addView(classicHeaderButton("Add widget") { addWidgetToClassicPage(classicPage) })
            },
        )
    }

    private fun classicHeaderButton(text: String, action: () -> Unit): TextView {
        return TextView(activity).apply {
            this.text = text
            setTextColor(CalmTheme.INK)
            textSize = 13f
            typeface = Typeface.DEFAULT
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            includeFontPadding = false
            background = drawables.glass(CalmTheme.QUIET_GLASS, activity.dp(999))
            setPadding(activity.dp(14), activity.dp(9), activity.dp(14), activity.dp(9))
            minWidth = activity.dp(104)
            setOnClickListener { action() }
        }
    }

    private fun showClassicAppPicker(classicPage: ClassicLauncherPageDefinition, state: LauncherRenderModel) {
        val placedAppKeys = state.classicPages
            .flatMap { page -> page.items }
            .filter { item -> item.type == ClassicGridItemType.APP }
            .map { item -> item.target }
            .toSet()
        val apps = state.appEntries
            .filterNot { app -> app.identityKey in placedAppKeys }
            .sortedWith { left, right -> Collator.getInstance().compare(classicAppPickerLabel(left), classicAppPickerLabel(right)) }
        if (apps.isEmpty()) {
            Toast.makeText(activity, "No apps available to add", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = apps.map(::classicAppPickerLabel).toTypedArray()
        AlertDialog.Builder(activity)
            .setTitle("Add app to ${classicPage.title}")
            .setItems(labels) { _, which ->
                addAppToClassicPage(classicPage, apps[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun classicAppPickerLabel(app: AppEntry): String {
        val profile = app.profileLabel.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
        return "${app.label}$profile"
    }

    private fun classicGrid(gridItems: List<Pair<ClassicGridItem, View>>): View {
        val cellWidth = activity.dp(78)
        val cellHeight = activity.dp(92)
        val grid = GridLayout(activity).apply {
            columnCount = ClassicGridItem.GRID_COLUMNS
            rowCount = ClassicGridItem.DEFAULT_GRID_ROWS
            alignmentMode = GridLayout.ALIGN_BOUNDS
            useDefaultMargins = false
            clipChildren = false
            clipToPadding = false
            setPadding(0, activity.dp(4), 0, activity.dp(12))
            gridItems.sortedWith(compareBy<Pair<ClassicGridItem, View>> { it.first.y }.thenBy { it.first.x })
                .forEach { (item, view) ->
                    val columnSpec = GridLayout.spec(item.x, item.width, GridLayout.FILL)
                    val rowSpec = GridLayout.spec(item.y, item.height, GridLayout.FILL)
                    addView(
                        view,
                        GridLayout.LayoutParams(rowSpec, columnSpec).apply {
                            width = cellWidth * item.width
                            height = cellHeight * item.height
                            setMargins(activity.dp(2), activity.dp(2), activity.dp(2), activity.dp(2))
                        },
                    )
                }
        }
        return FrameLayout(activity).apply {
            clipChildren = false
            clipToPadding = false
            addView(
                grid,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL),
            )
        }
    }

    private fun classicWidgetTile(
        classicPage: ClassicLauncherPageDefinition,
        item: ClassicGridItem,
        state: LauncherRenderModel,
    ): View {
        return FrameLayout(activity).apply {
            clipChildren = false
            clipToPadding = false
            val showActions = View.OnLongClickListener {
                showClassicItemActions(this, classicPage, item, "Widget", state)
                true
            }
            val widgetView = createWidgetView(item)
            if (widgetView == null) {
                addView(
                    TextView(activity).apply {
                        text = "Widget unavailable"
                        setTextColor(CalmTheme.MUTED_INK)
                        textSize = 14f
                        gravity = Gravity.CENTER
                        includeFontPadding = false
                    },
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                )
            } else {
                widgetView.setOnLongClickListener(showActions)
                addView(
                    widgetView,
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                )
            }
            setOnLongClickListener(showActions)
        }
    }

    private fun classicAppTile(
        classicPage: ClassicLauncherPageDefinition,
        item: ClassicGridItem,
        app: AppEntry,
        state: LauncherRenderModel,
    ): View {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            clipChildren = false
            clipToPadding = false
            contentDescription = app.label
            tooltipText = app.label
            resolveIcon(app)?.let { icon ->
                addView(
                    ImageView(activity).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        setImageDrawable(RoundedBitmapDrawable(icon, activity.dp(15).toFloat()))
                    },
                    LinearLayout.LayoutParams(activity.dp(52), activity.dp(52)),
                )
            }
            addView(
                TextView(activity).apply {
                    text = app.label
                    setTextColor(CalmTheme.INK)
                    textSize = 12f
                    typeface = Typeface.DEFAULT
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    setPadding(activity.dp(2), activity.dp(6), activity.dp(2), 0)
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
            setOnClickListener { openAppEntry(app) }
            setOnLongClickListener {
                showClassicItemActions(this, classicPage, item, app.label, state)
                true
            }
        }
    }

    private fun showClassicItemActions(
        source: View,
        classicPage: ClassicLauncherPageDefinition,
        item: ClassicGridItem,
        title: String,
        state: LauncherRenderModel,
    ) {
        val content = TextView(activity).apply {
            text = title
            setTextColor(CalmTheme.INK)
            textSize = 22f
            typeface = Typeface.DEFAULT
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        val actions = mutableListOf<ContextAction>()
        val moveTargets = state.classicPages.filter { page -> page.id != classicPage.id }
        if (moveTargets.isNotEmpty()) {
            actions.add(
                ContextAction(
                    "Move",
                    Runnable { showClassicMoveDialog(classicPage, item, moveTargets) },
                    ContextActionCloseBehavior.REMOVE_CARD,
                ),
            )
        }
        actions.add(
            ContextAction(
                "Remove",
                Runnable { removeClassicGridItem(classicPage, item) },
                ContextActionCloseBehavior.REMOVE_CARD,
            ),
        )
        focusOverlay.showExpandedCard(
            source,
            content,
            actions,
        )
    }

    private fun showClassicMoveDialog(
        classicPage: ClassicLauncherPageDefinition,
        item: ClassicGridItem,
        moveTargets: List<ClassicLauncherPageDefinition>,
    ) {
        AlertDialog.Builder(activity)
            .setTitle("Move to")
            .setItems(moveTargets.map { page -> page.title }.toTypedArray()) { _, which ->
                moveClassicGridItem(classicPage, item, moveTargets[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createPinnedPage(pinnedApps: List<AppEntry>): LinearLayout {
        return barePagePanel(activity.dp(20)).apply {
            addView(animatedChrome(label("Pinned", 30, CalmTheme.INK, Typeface.NORMAL).apply {
                setPadding(0, activity.dp(8), 0, activity.dp(24))
            }))
            addView(
                appLibraryController.appStack(pinnedApps, stackKey = CardStackStateKey.appEntries("pinned", pinnedApps)),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
            )
        }
    }

    private fun createAppLibraryPage(pageModel: ChapterPage, appEntries: List<AppEntry>): LinearLayout {
        val scope = pageModel.appScope ?: AppLibraryScope.ALL
        val model = appLibraryPageModelFactory.create(
            page = pageModel,
            appEntries = appEntries,
            query = appSearchController.queryFor(scope),
            loading = appLibraryStore.state().loading,
        )
        val page = barePagePanel(activity.dp(20))
        val header = LinearLayout(activity).apply {
            tag = CalmAnimationTags.CHROME
            orientation = LinearLayout.VERTICAL
            clipToPadding = false
            clipChildren = false
            addView(label(model.title, 30, CalmTheme.INK, Typeface.NORMAL).apply {
                setPadding(0, activity.dp(8), 0, 0)
            })
            model.subtitle?.let { subtitle ->
                addView(label(subtitle, 15, CalmTheme.MUTED_INK, Typeface.NORMAL).apply {
                    setPadding(0, activity.dp(6), 0, activity.dp(18))
                })
            }
        }
        page.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val stackHost = FrameLayout(activity).apply {
            clipChildren = false
            clipToPadding = false
        }
        page.addView(stackHost, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val searchBox = appSearchController.registerPage(pageModel, page, header, stackHost, model)
        page.addView(animatedChrome(searchBox), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = activity.dp(12)
        })
        return page
    }

    private fun animatedChrome(view: View): View {
        return view.apply { tag = CalmAnimationTags.CHROME }
    }
}
