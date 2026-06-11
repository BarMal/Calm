package dev.barna.calm

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

/**
 * Builds the per-page content views for the launcher pager. Overview and notification-chapter pages
 * delegate to their dedicated builders; pinned and app-library pages are assembled here from the
 * shared page primitives (panel/label/chrome) owned by the runner.
 */
class LauncherPageFactory(
    private val activity: MainActivity,
    private val overviewPageBuilder: OverviewPageBuilder,
    private val chapterPageBuilder: ChapterPageBuilder,
    private val appLibraryController: LauncherAppLibraryController,
    private val appSearchController: AppSearchController,
    private val appLibraryPageModelFactory: AppLibraryPageModelFactory,
    private val appLibraryStore: AppLibraryRenderStore,
    private val contactsPageController: ContactsPageController,
    private val resolveIcon: (AppEntry) -> Bitmap?,
    private val openAppEntry: (AppEntry) -> Unit,
    private val barePagePanel: (Int) -> LinearLayout,
    private val label: (String, Int, Int, Int) -> TextView,
) {
    fun createPage(page: ChapterPage, state: LauncherRenderModel): View {
        return when {
            page.appScope != null -> createAppLibraryPage(page, state.appEntries)
            page.key == CalmTheme.PINNED_KEY -> createPinnedPage(state.pinnedApps)
            page.key == CalmTheme.CONTACTS_KEY -> contactsPageController.buildPage()
            page.key == CalmTheme.WORK_OVERVIEW_KEY -> overviewPageBuilder.buildPage(state, workProfile = true)
            page.classicPage != null -> createClassicPage(page.classicPage, state.appEntries)
            page.chapter == null -> overviewPageBuilder.buildPage(state)
            else -> chapterPageBuilder.buildPage(page.chapter)
        }
    }

    private fun createClassicPage(
        classicPage: ClassicLauncherPageDefinition,
        appEntries: List<AppEntry>,
    ): LinearLayout {
        val appsByKey = appEntries.associateBy { it.identityKey }
        val appItems = classicPage.items
            .filter { item -> item.type == ClassicGridItemType.APP }
            .mapNotNull { item -> appsByKey[item.target]?.let { app -> item to app } }
        return barePagePanel(activity.dp(20)).apply {
            addView(animatedChrome(label(classicPage.title, 30, CalmTheme.INK, Typeface.NORMAL).apply {
                setPadding(0, activity.dp(8), 0, activity.dp(24))
            }))
            if (appItems.isEmpty()) {
                addView(
                    LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER
                        addView(label("No icons yet", 22, CalmTheme.INK, Typeface.NORMAL).apply {
                            gravity = Gravity.CENTER
                        })
                    },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
                )
            } else {
                addView(
                    classicGrid(appItems),
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
                )
            }
        }
    }

    private fun classicGrid(appItems: List<Pair<ClassicGridItem, AppEntry>>): View {
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
            appItems.sortedWith(compareBy<Pair<ClassicGridItem, AppEntry>> { it.first.y }.thenBy { it.first.x })
                .forEach { (item, app) ->
                    val columnSpec = GridLayout.spec(item.x, item.width)
                    val rowSpec = GridLayout.spec(item.y, item.height)
                    addView(
                        classicAppTile(app),
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

    private fun classicAppTile(app: AppEntry): View {
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
        }
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
