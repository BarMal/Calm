package dev.barna.calm

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
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
    private val moveClassicGridItemWithinPage: (ClassicLauncherPageDefinition, ClassicGridItem, Int, Int) -> Unit,
    private val resizeClassicGridItem: (ClassicLauncherPageDefinition, ClassicGridItem, Int, Int) -> Unit,
    private val addClassicPage: () -> Unit,
    private val moveClassicPage: (ClassicLauncherPageDefinition, Int) -> Unit,
    private val renameClassicPage: (ClassicLauncherPageDefinition, String) -> Unit,
    private val setDefaultClassicPage: (ClassicLauncherPageDefinition) -> Unit,
    private val removeClassicPage: (ClassicLauncherPageDefinition) -> Unit,
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
            setOnLongClickListener {
                showClassicPageActions(this, classicPage, state)
                true
            }
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
                setOnLongClickListener {
                    showClassicPageActions(this, classicPage, state)
                    true
                }
                addView(
                    label(classicPage.title, 30, CalmTheme.INK, Typeface.NORMAL).apply {
                        setOnLongClickListener {
                            showClassicPageActions(this, classicPage, state)
                            true
                        }
                    },
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

    private fun showClassicPageActions(
        source: View,
        classicPage: ClassicLauncherPageDefinition,
        state: LauncherRenderModel,
    ) {
        val pageIndex = state.classicPages.indexOfFirst { page -> page.id == classicPage.id }
        val actions = mutableListOf(
            ContextAction(
                "Add app",
                Runnable { showClassicAppPicker(classicPage, state) },
                ContextActionCloseBehavior.REMOVE_CARD,
            ),
            ContextAction(
                "Add widget",
                Runnable { addWidgetToClassicPage(classicPage) },
                ContextActionCloseBehavior.REMOVE_CARD,
            ),
            ContextAction(
                "New page",
                Runnable { addClassicPage() },
                ContextActionCloseBehavior.REMOVE_CARD,
            ),
        )
        if (pageIndex > 0) {
            actions += ContextAction(
                "Move left",
                Runnable { moveClassicPage(classicPage, pageIndex - 1) },
                ContextActionCloseBehavior.REMOVE_CARD,
            )
        }
        if (pageIndex != -1 && pageIndex < state.classicPages.lastIndex) {
            actions += ContextAction(
                "Move right",
                Runnable { moveClassicPage(classicPage, pageIndex + 1) },
                ContextActionCloseBehavior.REMOVE_CARD,
            )
        }
        actions += listOf(
            ContextAction(
                "Rename",
                Runnable { showRenameClassicPageDialog(classicPage) },
                ContextActionCloseBehavior.REMOVE_CARD,
            ),
            ContextAction(
                "Set home",
                Runnable { setDefaultClassicPage(classicPage) },
                ContextActionCloseBehavior.REMOVE_CARD,
            ),
            ContextAction(
                "Remove",
                Runnable { confirmRemoveClassicPage(classicPage) },
                ContextActionCloseBehavior.REMOVE_CARD,
            ),
        )
        val content = TextView(activity).apply {
            text = classicPage.title
            setTextColor(CalmTheme.INK)
            textSize = 22f
            typeface = Typeface.DEFAULT
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        focusOverlay.showExpandedCard(
            source,
            content,
            actions,
        )
    }

    private fun showRenameClassicPageDialog(classicPage: ClassicLauncherPageDefinition) {
        val input = EditText(activity).apply {
            setText(classicPage.title)
            setSingleLine(true)
            setSelection(0, text.length)
        }
        AlertDialog.Builder(activity)
            .setTitle("Rename Classic page")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ -> renameClassicPage(classicPage, input.text.toString()) }
            .show()
    }

    private fun confirmRemoveClassicPage(classicPage: ClassicLauncherPageDefinition) {
        val itemCount = classicPage.items.size
        val message = if (itemCount == 0) {
            "Remove ${classicPage.title}?"
        } else {
            "Remove ${classicPage.title} and its $itemCount ${if (itemCount == 1) "item" else "items"}?"
        }
        AlertDialog.Builder(activity)
            .setTitle("Remove Classic page")
            .setMessage(message)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Remove") { _, _ -> removeClassicPage(classicPage) }
            .show()
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
        val grid = GridLayout(activity).apply {
            columnCount = CLASSIC_PICKER_COLUMNS
            useDefaultMargins = false
            setPadding(activity.dp(14), activity.dp(14), activity.dp(14), activity.dp(14))
        }
        var dialog: AlertDialog? = null
        apps.forEachIndexed { index, app ->
            grid.addView(
                classicAppPickerCard(app) {
                    dialog?.dismiss()
                    addAppToClassicPage(classicPage, app)
                },
                GridLayout.LayoutParams(
                    GridLayout.spec(index / CLASSIC_PICKER_COLUMNS),
                    GridLayout.spec(index % CLASSIC_PICKER_COLUMNS, GridLayout.FILL),
                ).apply {
                    width = 0
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(index % CLASSIC_PICKER_COLUMNS, 1f)
                    setMargins(activity.dp(6), activity.dp(6), activity.dp(6), activity.dp(6))
                },
            )
        }
        val scroll = ScrollView(activity).apply {
            addView(grid, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        dialog = AlertDialog.Builder(activity)
            .setTitle("Add app to ${classicPage.title}")
            .setView(scroll)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()
    }

    private fun classicAppPickerLabel(app: AppEntry): String {
        val profile = app.profileLabel.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
        return "${app.label}$profile"
    }

    private fun classicAppPickerCard(app: AppEntry, onClick: () -> Unit): View {
        val pickerLabel = classicAppPickerLabel(app)
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = drawables.glass(CalmTheme.QUIET_GLASS, activity.dp(16))
            contentDescription = pickerLabel
            setPadding(activity.dp(10), activity.dp(12), activity.dp(10), activity.dp(12))
            addView(
                FrameLayout(activity).apply {
                    resolveIcon(app)?.let { icon ->
                        addView(
                            ImageView(activity).apply {
                                scaleType = ImageView.ScaleType.CENTER_CROP
                                setImageDrawable(RoundedBitmapDrawable(icon, activity.dp(16).toFloat()))
                            },
                            FrameLayout.LayoutParams(activity.dp(56), activity.dp(56), Gravity.CENTER),
                        )
                    } ?: addView(
                        TextView(activity).apply {
                            text = app.label.firstOrNull()?.uppercaseChar()?.toString().orEmpty()
                            setTextColor(CalmTheme.INK)
                            textSize = 28f
                            typeface = Typeface.DEFAULT
                            setTypeface(typeface, Typeface.BOLD)
                            gravity = Gravity.CENTER
                            includeFontPadding = false
                        },
                        FrameLayout.LayoutParams(activity.dp(56), activity.dp(56), Gravity.CENTER),
                    )
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(62)),
            )
            addView(
                TextView(activity).apply {
                    text = app.label
                    setTextColor(CalmTheme.INK)
                    textSize = 13f
                    typeface = Typeface.DEFAULT
                    setTypeface(typeface, Typeface.BOLD)
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    setPadding(0, activity.dp(8), 0, 0)
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
            app.profileLabel.takeIf { it.isNotBlank() }?.let { profile ->
                addView(
                    TextView(activity).apply {
                        text = profile
                        setTextColor(CalmTheme.MUTED_INK)
                        textSize = 11f
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                        gravity = Gravity.CENTER
                        includeFontPadding = false
                        setPadding(0, activity.dp(5), 0, 0)
                    },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
                )
            }
            minimumHeight = activity.dp(150)
            setOnClickListener { onClick() }
        }
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
        actions.add(
            ContextAction(
                "Position",
                Runnable { showClassicPositionDialog(classicPage, item) },
                ContextActionCloseBehavior.REMOVE_CARD,
            ),
        )
        actions.add(
            ContextAction(
                "Resize",
                Runnable { showClassicResizeDialog(classicPage, item) },
                ContextActionCloseBehavior.REMOVE_CARD,
            ),
        )
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

    private fun showClassicPositionDialog(classicPage: ClassicLauncherPageDefinition, item: ClassicGridItem) {
        val positions = classicPage.availablePositionsForItem(item.id)
        if (positions.isEmpty()) {
            Toast.makeText(activity, "No positions available", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(activity)
            .setTitle("Position")
            .setItems(positions.map { position -> position.label(item) }.toTypedArray()) { _, which ->
                val (x, y) = positions[which]
                moveClassicGridItemWithinPage(classicPage, item, x, y)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showClassicResizeDialog(classicPage: ClassicLauncherPageDefinition, item: ClassicGridItem) {
        val options = CLASSIC_ITEM_SIZE_OPTIONS
        AlertDialog.Builder(activity)
            .setTitle("Resize")
            .setItems(options.map { option -> option.label(item) }.toTypedArray()) { _, which ->
                val option = options[which]
                resizeClassicGridItem(classicPage, item, option.width, option.height)
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

    private data class ClassicItemSizeOption(
        val title: String,
        val width: Int,
        val height: Int,
    ) {
        fun label(item: ClassicGridItem): String {
            val current = if (item.width == width && item.height == height) " (current)" else ""
            return "$title - ${width}x$height$current"
        }
    }

    private fun Pair<Int, Int>.label(item: ClassicGridItem): String {
        val current = if (first == item.x && second == item.y) " (current)" else ""
        return "Row ${second + 1}, column ${first + 1}$current"
    }

    private companion object {
        private const val CLASSIC_PICKER_COLUMNS = 2

        val CLASSIC_ITEM_SIZE_OPTIONS = listOf(
            ClassicItemSizeOption("Icon", 1, 1),
            ClassicItemSizeOption("Wide", 2, 1),
            ClassicItemSizeOption("Large", 2, 2),
            ClassicItemSizeOption("Full-width", ClassicGridItem.GRID_COLUMNS, 2),
            ClassicItemSizeOption("Tall full-width", ClassicGridItem.GRID_COLUMNS, 3),
        )
    }
}
