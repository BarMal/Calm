package dev.barna.calm

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
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
    private val agendaPageBuilder: AgendaPageBuilder,
    private val alarmsPageBuilder: AlarmsPageBuilder,
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
    private val configureClassicWidget: (ClassicGridItem) -> Unit,
    private val canConfigureClassicWidget: (ClassicGridItem) -> Boolean,
    private val removeClassicGridItem: (ClassicLauncherPageDefinition, ClassicGridItem) -> Unit,
    private val moveClassicGridItem: (ClassicLauncherPageDefinition, ClassicGridItem, ClassicLauncherPageDefinition) -> Unit,
    private val moveClassicGridItemWithinPage: (ClassicLauncherPageDefinition, ClassicGridItem, Int, Int) -> Unit,
    private val resizeClassicGridItem: (ClassicLauncherPageDefinition, ClassicGridItem, Int, Int) -> Unit,
    private val resetClassicGridItemSize: (ClassicLauncherPageDefinition, ClassicGridItem) -> Unit,
    private val pendingClassicPlacementItemId: () -> String?,
    private val finishClassicItemPlacement: (String) -> Unit,
    private val addClassicPage: () -> Unit,
    private val moveClassicPage: (ClassicLauncherPageDefinition, Int) -> Unit,
    private val isClassicPageEditing: (ClassicLauncherPageDefinition) -> Boolean,
    private val setClassicPageEditing: (ClassicLauncherPageDefinition, Boolean) -> Unit,
    private val renameClassicPage: (ClassicLauncherPageDefinition, String) -> Unit,
    private val setDefaultClassicPage: (ClassicLauncherPageDefinition) -> Unit,
    private val removeClassicPage: (ClassicLauncherPageDefinition) -> Unit,
    private val barePagePanel: (Int) -> LinearLayout,
    private val label: (String, Int, Int, Int) -> TextView,
) {
    private var classicMenuAnchor: Pair<Int, Int>? = null

    fun createPage(page: ChapterPage, state: LauncherRenderModel): View {
        return when {
            page.appScope != null -> createAppLibraryPage(page, state.appEntries)
            page.key == CalmTheme.PINNED_KEY -> createPinnedPage(state.pinnedApps)
            page.key == CalmTheme.CONTACTS_KEY -> contactsPageController.buildPage()
            page.key == CalmTheme.AGENDA_KEY -> agendaPageBuilder.buildPage(state)
            page.key == CalmTheme.ALARMS_KEY -> alarmsPageBuilder.buildPage()
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
        val editing = isClassicPageEditing(classicPage)
        val appEntries = state.appEntries
        val appsByKey = appEntries.associateBy { it.identityKey }
        val gridItems = classicPage.items.mapNotNull { item ->
            when (item.type) {
                ClassicGridItemType.APP -> appsByKey[item.target]?.let { app -> item to classicAppTile(classicPage, item, app, state, editing) }
                ClassicGridItemType.WIDGET -> item to classicWidgetTile(classicPage, item, state, editing)
                ClassicGridItemType.STATIC -> item to classicStaticTile(classicPage, item, state, editing)
            }
        }
        return barePagePanel(activity.dp(20)).apply {
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    classicMenuAnchor = event.rawX.toInt() to event.rawY.toInt()
                }
                false
            }
            setOnLongClickListener {
                showClassicPageActions(this, classicPage, state)
                true
            }
            addView(classicHeader(classicPage, state, editing))
            if (gridItems.isEmpty()) {
                addView(
                    View(activity),
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
                )
            } else {
                addView(
                    classicGrid(classicPage, state, gridItems),
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
                )
            }
        }
    }

    private fun classicHeader(
        classicPage: ClassicLauncherPageDefinition,
        state: LauncherRenderModel,
        editing: Boolean,
    ): View {
        return animatedChrome(
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, activity.dp(8), 0, activity.dp(if (editing) 18 else 24))
                setOnLongClickListener {
                    showClassicPageActions(this, classicPage, state)
                    true
                }
                addView(
                    LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        val header = this
                        addView(
                            label(classicPage.title, 30, CalmTheme.INK, Typeface.NORMAL).apply {
                                setOnLongClickListener {
                                    showClassicPageActions(header, classicPage, state)
                                    true
                                }
                            },
                            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                        )
                        if (editing) {
                            addView(
                                classicHeaderButton("Done", minWidthDp = 74) {
                                    setClassicPageEditing(classicPage, false)
                                },
                                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT),
                            )
                        }
                    },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
                )
            },
        )
    }

    private fun LinearLayout.addHeaderActionButton(text: String, action: () -> Unit) {
        if (childCount > 0) {
            addView(View(activity), LinearLayout.LayoutParams(activity.dp(8), 1))
        }
        addView(
            classicHeaderButton(text, minWidthDp = 0, action = action),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
    }

    private fun classicHeaderButton(text: String, minWidthDp: Int = 104, action: () -> Unit): TextView {
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
            minWidth = activity.dp(minWidthDp)
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
            ContextAction(
                if (isClassicPageEditing(classicPage)) "Done editing" else "Edit layout",
                Runnable { setClassicPageEditing(classicPage, !isClassicPageEditing(classicPage)) },
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
        showClassicPopupActions(source, actions)
    }

    private fun showRenameClassicPageDialog(classicPage: ClassicLauncherPageDefinition) {
        val input = EditText(activity).apply {
            setText(classicPage.title)
            setSingleLine(true)
            setSelection(0, text.length)
        }
        GoogleInteractionStyle.dialogBuilder(activity)
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
        GoogleInteractionStyle.dialogBuilder(activity)
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
        dialog = GoogleInteractionStyle.dialogBuilder(activity)
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
            background = GoogleInteractionStyle.rowBackground(activity, 18)
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
                            setTextColor(GoogleInteractionStyle.onSurface(activity))
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
                    setTextColor(GoogleInteractionStyle.onSurface(activity))
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
                        setTextColor(GoogleInteractionStyle.onSurfaceVariant(activity))
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

    private fun classicGrid(
        classicPage: ClassicLauncherPageDefinition,
        state: LauncherRenderModel,
        gridItems: List<Pair<ClassicGridItem, View>>,
    ): View {
        val gridConfig = state.classicGridConfig
        val screenWidth = activity.resources.displayMetrics.widthPixels
        val horizontalInsets = activity.dp(56)
        val cellWidth = ((screenWidth - horizontalInsets).coerceAtLeast(activity.dp(160)) / gridConfig.columns)
            .coerceIn(activity.dp(42), activity.dp(92))
        val cellHeight = activity.dp(92)
        val gap = activity.dp(2)
        val workspaceWidth = cellWidth * gridConfig.columns
        val workspaceHeight = cellHeight * gridConfig.rows
        val grid = FrameLayout(activity).apply {
            clipChildren = false
            clipToPadding = false
            gridItems.sortedWith(compareBy<Pair<ClassicGridItem, View>> { it.first.y }.thenBy { it.first.x })
                .forEach { (item, view) ->
                    val boundedWidth = item.width.coerceIn(1, gridConfig.columns)
                    val boundedHeight = item.height.coerceIn(1, gridConfig.rows)
                    val boundedX = item.x.coerceIn(0, (gridConfig.columns - boundedWidth).coerceAtLeast(0))
                    val boundedY = item.y.coerceIn(0, (gridConfig.rows - boundedHeight).coerceAtLeast(0))
                    addView(
                        classicGridItemFrame(
                            classicPage = classicPage,
                            item = item.copy(x = boundedX, y = boundedY, width = boundedWidth, height = boundedHeight),
                            content = view,
                            title = classicGridItemTitle(item, state),
                            state = state,
                            gridConfig = gridConfig,
                            cellWidth = cellWidth,
                            cellHeight = cellHeight,
                            gap = gap,
                        ),
                        FrameLayout.LayoutParams(
                            cellWidth * boundedWidth,
                            cellHeight * boundedHeight,
                        ).apply {
                            leftMargin = cellWidth * boundedX
                            topMargin = cellHeight * boundedY
                        },
                    )
                }
            layoutParams = FrameLayout.LayoutParams(workspaceWidth, workspaceHeight)
        }
        return FrameLayout(activity).apply {
            clipChildren = false
            clipToPadding = false
            addView(
                grid,
                FrameLayout.LayoutParams(workspaceWidth, workspaceHeight, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                    topMargin = activity.dp(4)
                    bottomMargin = activity.dp(12)
                },
            )
        }
    }

    private fun classicGridItemFrame(
        classicPage: ClassicLauncherPageDefinition,
        item: ClassicGridItem,
        content: View,
        title: String,
        state: LauncherRenderModel,
        gridConfig: ClassicGridConfig,
        cellWidth: Int,
        cellHeight: Int,
        gap: Int,
    ): View {
        return object : FrameLayout(activity) {
            private var downRawX = 0f
            private var downRawY = 0f
            private var dragging = false
            private var resizing = false
            private var selected = pendingClassicPlacementItemId() == item.id
            private var draftChanged = false
            private var draftX = item.x
            private var draftY = item.y
            private var draftWidth = item.width
            private var draftHeight = item.height
            private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
            private val longPressRunnable = Runnable {
                selected = true
                dragging = true
                classicMenuAnchor = downRawX.toInt() to downRawY.toInt()
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                showSelectionChrome()
                parent?.requestDisallowInterceptTouchEvent(true)
            }

            init {
                tag = CLASSIC_GRID_ITEM_FRAME_TAG
                clipChildren = false
                clipToPadding = false
                setPadding(gap, gap, gap, gap)
                addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                if (selected) {
                    post { showSelectionChrome() }
                }
            }

            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downRawX = event.rawX
                        downRawY = event.rawY
                        dragging = false
                        draftChanged = false
                        draftX = item.x
                        draftY = item.y
                        draftWidth = item.width
                        draftHeight = item.height
                        resizing = event.x >= width - activity.dp(28) && event.y >= height - activity.dp(28) && selected
                        removeCallbacks(longPressRunnable)
                        if (resizing) {
                            parent?.requestDisallowInterceptTouchEvent(true)
                            return true
                        }
                        if (selected) {
                            dragging = true
                            classicMenuAnchor = downRawX.toInt() to downRawY.toInt()
                            parent?.requestDisallowInterceptTouchEvent(true)
                            return true
                        }
                        postDelayed(longPressRunnable, longPressTimeout)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (resizing) {
                            updateResizeDraft(event.rawX, event.rawY, gridConfig, cellWidth, cellHeight)
                            return true
                        }
                        if (dragging) {
                            updateMoveDraft(event.rawX, event.rawY, gridConfig, cellWidth, cellHeight)
                            return true
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        removeCallbacks(longPressRunnable)
                        parent?.requestDisallowInterceptTouchEvent(false)
                        if (resizing) {
                            val valid = isDraftValid(gridConfig)
                            resizing = false
                            if (valid && draftChanged) {
                                commitResizeVisualState()
                                resizeClassicGridItem(classicPage, item, draftWidth, draftHeight)
                            } else {
                                resetVisualState()
                            }
                            return true
                        }
                        if (dragging) {
                            val valid = isDraftValid(gridConfig)
                            dragging = false
                            if (!draftChanged) {
                                resetVisualState()
                                showClassicItemActions(this, classicPage, item, title, state)
                                return true
                            }
                            if (valid) {
                                commitMoveVisualState()
                                moveClassicGridItemWithinPage(classicPage, item, draftX, draftY)
                                finishClassicItemPlacement(item.id)
                            } else {
                                resetVisualState()
                            }
                            return true
                        }
                        if (selected) {
                            showClassicItemActions(this, classicPage, item, title, state)
                            return true
                        }
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        removeCallbacks(longPressRunnable)
                        parent?.requestDisallowInterceptTouchEvent(false)
                        resetVisualState()
                        dragging = false
                        resizing = false
                    }
                }
                return super.dispatchTouchEvent(event)
            }

            private fun updateMoveDraft(rawX: Float, rawY: Float, gridConfig: ClassicGridConfig, cellWidth: Int, cellHeight: Int) {
                val dx = rawX - downRawX
                val dy = rawY - downRawY
                translationX = dx
                translationY = dy
                draftX = kotlin.math.round((item.x * cellWidth + dx) / cellWidth.toFloat()).toInt()
                draftY = kotlin.math.round((item.y * cellHeight + dy) / cellHeight.toFloat()).toInt()
                draftChanged = draftChanged || kotlin.math.abs(dx) > activity.dp(6) || kotlin.math.abs(dy) > activity.dp(6)
                setInvalidTint(!isDraftValid(gridConfig))
            }

            private fun updateResizeDraft(rawX: Float, rawY: Float, gridConfig: ClassicGridConfig, cellWidth: Int, cellHeight: Int) {
                val dx = rawX - downRawX
                val dy = rawY - downRawY
                draftWidth = kotlin.math.round((item.width * cellWidth + dx) / cellWidth.toFloat()).toInt().coerceAtLeast(1)
                draftHeight = kotlin.math.round((item.height * cellHeight + dy) / cellHeight.toFloat()).toInt().coerceAtLeast(1)
                draftChanged = draftChanged || draftWidth != item.width || draftHeight != item.height
                layoutParams = (layoutParams as FrameLayout.LayoutParams).apply {
                    width = cellWidth * draftWidth.coerceAtMost(gridConfig.columns)
                    height = cellHeight * draftHeight.coerceAtMost(gridConfig.rows)
                }
                requestLayout()
                setInvalidTint(!isDraftValid(gridConfig))
            }

            private fun isDraftValid(gridConfig: ClassicGridConfig): Boolean {
                return classicPage.canPlaceItem(item.id, draftX, draftY, draftWidth, draftHeight, gridConfig)
            }

            private fun resetVisualState() {
                translationX = 0f
                translationY = 0f
                foreground = null
                layoutParams = (layoutParams as FrameLayout.LayoutParams).apply {
                    width = cellWidth * item.width
                    height = cellHeight * item.height
                }
                requestLayout()
            }

            private fun commitMoveVisualState() {
                foreground = null
                translationX = 0f
                translationY = 0f
                layoutParams = (layoutParams as FrameLayout.LayoutParams).apply {
                    leftMargin = cellWidth * draftX
                    topMargin = cellHeight * draftY
                    width = cellWidth * item.width
                    height = cellHeight * item.height
                }
                requestLayout()
            }

            private fun commitResizeVisualState() {
                foreground = null
                translationX = 0f
                translationY = 0f
                layoutParams = (layoutParams as FrameLayout.LayoutParams).apply {
                    width = cellWidth * draftWidth.coerceAtMost(gridConfig.columns)
                    height = cellHeight * draftHeight.coerceAtMost(gridConfig.rows)
                }
                requestLayout()
            }

            private fun setInvalidTint(invalid: Boolean) {
                foreground = if (invalid) {
                    ColorDrawable(Color.argb(90, 244, 67, 54))
                } else {
                    null
                }
            }

            private fun showSelectionChrome() {
                if (findViewWithTag<View>(CLASSIC_SELECTION_TAG) != null) return
                addView(selectionChrome(), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
        }
    }

    private fun classicGridItemTitle(item: ClassicGridItem, state: LauncherRenderModel): String {
        return when (item.type) {
            ClassicGridItemType.APP -> state.appEntries.firstOrNull { app -> app.identityKey == item.target }?.label ?: "App"
            ClassicGridItemType.WIDGET -> "Widget"
            ClassicGridItemType.STATIC -> runCatching { ClassicStaticItem.valueOf(item.target) }.getOrNull()?.label ?: "Static item"
        }
    }

    private fun selectionChrome(): FrameLayout {
        return FrameLayout(activity).apply {
            tag = CLASSIC_SELECTION_TAG
            isClickable = false
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                setStroke(activity.dp(2), GoogleInteractionStyle.primary(activity))
                cornerRadius = activity.dp(14).toFloat()
            }
            addResizeHandle(Gravity.BOTTOM or Gravity.END)
        }
    }

    private fun FrameLayout.addResizeHandle(gravity: Int) {
        addView(
            View(activity).apply {
                background = GradientDrawable().apply {
                    setColor(GoogleInteractionStyle.primary(activity))
                    cornerRadius = activity.dp(999).toFloat()
                }
                isClickable = false
            },
            FrameLayout.LayoutParams(activity.dp(16), activity.dp(16), gravity).apply {
                setMargins(activity.dp(3), activity.dp(3), activity.dp(3), activity.dp(3))
            },
        )
    }

    private fun classicWidgetTile(
        classicPage: ClassicLauncherPageDefinition,
        item: ClassicGridItem,
        state: LauncherRenderModel,
        editing: Boolean,
    ): View {
        val tile = FrameLayout(activity).apply {
            clipChildren = false
            clipToPadding = false
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
                addView(
                    widgetView,
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                )
            }
        }
        return if (editing) {
            editableClassicTile(tile, "Widget") { source ->
                showClassicItemActions(source, classicPage, item, "Widget", state)
            }
        } else {
            tile
        }
    }

    private fun classicAppTile(
        classicPage: ClassicLauncherPageDefinition,
        item: ClassicGridItem,
        app: AppEntry,
        state: LauncherRenderModel,
        editing: Boolean,
    ): View {
        val tile = LinearLayout(activity).apply {
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
            if (!editing) {
                setOnClickListener { openAppEntry(app) }
            }
            setOnLongClickListener {
                showClassicItemActions(this, classicPage, item, app.label, state)
                true
            }
        }
        return if (editing) {
            editableClassicTile(tile, app.label) { source ->
                showClassicItemActions(source, classicPage, item, app.label, state)
            }
        } else {
            tile
        }
    }

    private fun classicStaticTile(
        classicPage: ClassicLauncherPageDefinition,
        item: ClassicGridItem,
        state: LauncherRenderModel,
        editing: Boolean,
    ): View {
        val staticItem = runCatching { ClassicStaticItem.valueOf(item.target) }.getOrNull()
        val title = staticItem?.label ?: "Static item"
        val tile = when (staticItem) {
            ClassicStaticItem.CLOCK -> classicClockTile()
            ClassicStaticItem.SEARCH -> classicSearchTile()
            null -> classicUnavailableStaticTile()
        }
        tile.setOnLongClickListener {
            showClassicItemActions(tile, classicPage, item, title, state)
            true
        }
        return if (editing) {
            editableClassicTile(tile, title) { source ->
                showClassicItemActions(source, classicPage, item, title, state)
            }
        } else {
            tile
        }
    }

    private fun classicClockTile(): View {
        return TextClock(activity).apply {
            format12Hour = "h:mm"
            format24Hour = "HH:mm"
            setTextColor(CalmTheme.INK)
            textSize = 28f
            typeface = Typeface.DEFAULT
            setTypeface(typeface, Typeface.NORMAL)
            gravity = Gravity.CENTER
            includeFontPadding = false
            contentDescription = "Clock"
        }
    }

    private fun classicSearchTile(): View {
        return TextView(activity).apply {
            text = "Search"
            setTextColor(CalmTheme.INK)
            textSize = 18f
            typeface = Typeface.DEFAULT
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            includeFontPadding = false
            background = drawables.glass(CalmTheme.QUIET_GLASS, activity.dp(999))
            setPadding(activity.dp(16), activity.dp(10), activity.dp(16), activity.dp(10))
            contentDescription = "Search"
        }
    }

    private fun classicUnavailableStaticTile(): View {
        return TextView(activity).apply {
            text = "Unavailable"
            setTextColor(CalmTheme.MUTED_INK)
            textSize = 14f
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
    }

    private fun editableClassicTile(
        content: View,
        title: String,
        showActions: (View) -> Unit,
    ): View {
        return FrameLayout(activity).apply {
            val editContainer = this
            clipChildren = false
            clipToPadding = false
            contentDescription = "Edit $title"
            addView(
                content,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
            )
            addView(
                TextView(activity).apply {
                    text = "Edit"
                    setTextColor(CalmTheme.INK)
                    textSize = 11f
                    typeface = Typeface.DEFAULT
                    setTypeface(typeface, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    background = drawables.glass(CalmTheme.QUIET_GLASS, activity.dp(999))
                    setPadding(activity.dp(8), activity.dp(5), activity.dp(8), activity.dp(5))
                },
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
                    setMargins(0, 0, activity.dp(2), 0)
                },
            )
            addView(
                View(activity).apply {
                    setBackgroundColor(Color.TRANSPARENT)
                    setOnClickListener { showActions(editContainer) }
                    setOnLongClickListener {
                        showActions(editContainer)
                        true
                    }
                },
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
            )
        }
    }

    private fun showClassicItemActions(
        source: View,
        classicPage: ClassicLauncherPageDefinition,
        item: ClassicGridItem,
        title: String,
        state: LauncherRenderModel,
    ) {
        val actions = mutableListOf<ContextAction>()
        val moveTargets = state.classicPages.filter { page -> page.id != classicPage.id }
        if (item.type == ClassicGridItemType.WIDGET && canConfigureClassicWidget(item)) {
            actions.add(
                ContextAction(
                    "Configure",
                    Runnable { configureClassicWidget(item) },
                    ContextActionCloseBehavior.REMOVE_CARD,
                ),
            )
        }
        actions.add(
            ContextAction(
                "Default size",
                Runnable { resetClassicGridItemSize(classicPage, item) },
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
                Runnable {
                    removeClassicItemFrameImmediately(source)
                    removeClassicGridItem(classicPage, item)
                },
                ContextActionCloseBehavior.REMOVE_CARD,
            ),
        )
        showClassicPopupActions(source, actions)
    }

    private fun removeClassicItemFrameImmediately(source: View) {
        val frame = source.closestClassicItemFrame() ?: source
        frame.animate()
            .alpha(0f)
            .scaleX(0.96f)
            .scaleY(0.96f)
            .setDuration(90L)
            .withEndAction { (frame.parent as? ViewGroup)?.removeView(frame) }
            .start()
    }

    private fun View.closestClassicItemFrame(): View? {
        var cursor: View? = this
        while (cursor != null) {
            if (cursor.tag == CLASSIC_GRID_ITEM_FRAME_TAG) return cursor
            cursor = cursor.parent as? View
        }
        return null
    }

    private fun showClassicPopupActions(source: View, actions: List<ContextAction>) {
        focusOverlay.dismiss(false)
        val anchor = classicMenuAnchor ?: source.screenCenter()
        classicMenuAnchor = null
        GoogleInteractionStyle.popupMenu(activity, source, anchor, actions)
    }

    private fun View.screenCenter(): Pair<Int, Int> {
        val location = IntArray(2)
        getLocationOnScreen(location)
        return location[0] + width / 2 to location[1] + height / 2
    }

    private fun showClassicMoveDialog(
        classicPage: ClassicLauncherPageDefinition,
        item: ClassicGridItem,
        moveTargets: List<ClassicLauncherPageDefinition>,
    ) {
        GoogleInteractionStyle.dialogBuilder(activity)
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
        val availablePositions = positions.toSet()
        val grid = GridLayout(activity).apply {
            columnCount = ClassicGridItem.GRID_COLUMNS
            rowCount = ClassicGridItem.DEFAULT_GRID_ROWS
            useDefaultMargins = false
            setPadding(activity.dp(16), activity.dp(16), activity.dp(16), activity.dp(16))
        }
        var dialog: AlertDialog? = null
        for (y in 0 until ClassicGridItem.DEFAULT_GRID_ROWS) {
            for (x in 0 until ClassicGridItem.GRID_COLUMNS) {
                val position = x to y
                val available = position in availablePositions
                val current = item.x == x && item.y == y
                grid.addView(
                    classicPositionCell(
                        label = if (current) "Here" else "${y + 1}.${x + 1}",
                        contentDescription = position.positionDescription(available, current),
                        available = available,
                        current = current,
                    ) {
                        dialog?.dismiss()
                        moveClassicGridItemWithinPage(classicPage, item, x, y)
                    },
                    GridLayout.LayoutParams(
                        GridLayout.spec(y, GridLayout.FILL),
                        GridLayout.spec(x, GridLayout.FILL),
                    ).apply {
                        width = 0
                        height = activity.dp(44)
                        columnSpec = GridLayout.spec(x, 1f)
                        setMargins(activity.dp(4), activity.dp(4), activity.dp(4), activity.dp(4))
                    },
                )
            }
        }
        val scroll = ScrollView(activity).apply {
            addView(grid, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        dialog = GoogleInteractionStyle.dialogBuilder(activity)
            .setTitle("Position")
            .setView(scroll)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()
    }

    private fun classicPositionCell(
        label: String,
        contentDescription: String,
        available: Boolean,
        current: Boolean,
        onClick: () -> Unit,
    ): TextView {
        return TextView(activity).apply {
            text = label
            this.contentDescription = contentDescription
            setTextColor(
                if (available) {
                    GoogleInteractionStyle.onSurface(activity)
                } else {
                    GoogleInteractionStyle.onSurfaceVariant(activity)
                },
            )
            textSize = if (current) 12f else 11f
            typeface = Typeface.DEFAULT
            setTypeface(typeface, if (current) Typeface.BOLD else Typeface.NORMAL)
            gravity = Gravity.CENTER
            includeFontPadding = false
            background = GoogleInteractionStyle.chipBackground(activity, selected = current, radiusDp = 12)
            alpha = if (available) 1f else 0.42f
            isEnabled = available
            if (available) {
                setOnClickListener { onClick() }
            }
        }
    }

    private fun Pair<Int, Int>.positionDescription(available: Boolean, current: Boolean): String {
        val state = when {
            current -> "current"
            available -> "available"
            else -> "unavailable"
        }
        return "Row ${second + 1}, column ${first + 1}, $state"
    }

    private fun showClassicResizeDialog(classicPage: ClassicLauncherPageDefinition, item: ClassicGridItem) {
        val options = CLASSIC_ITEM_SIZE_OPTIONS
        GoogleInteractionStyle.dialogBuilder(activity)
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

    private val ClassicStaticItem.label: String
        get() = when (this) {
            ClassicStaticItem.CLOCK -> "Clock"
            ClassicStaticItem.SEARCH -> "Search"
        }

    private companion object {
        private const val CLASSIC_PICKER_COLUMNS = 2
        private const val CLASSIC_SELECTION_TAG = "classic-selection"
        private const val CLASSIC_GRID_ITEM_FRAME_TAG = "classic-grid-item-frame"

        val CLASSIC_ITEM_SIZE_OPTIONS = listOf(
            ClassicItemSizeOption("Icon", 1, 1),
            ClassicItemSizeOption("Wide", 2, 1),
            ClassicItemSizeOption("Large", 2, 2),
            ClassicItemSizeOption("Full-width", ClassicGridItem.GRID_COLUMNS, 2),
            ClassicItemSizeOption("Tall full-width", ClassicGridItem.GRID_COLUMNS, 3),
        )
    }
}
