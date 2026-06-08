package dev.barna.calm

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Handler
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import java.util.EnumMap

class AppSearchController(
    private val activity: MainActivity,
    private val mainHandler: Handler,
    private val drawables: CalmDrawables,
    private val appLibraryStore: AppLibraryRenderStore,
    private val appLibraryPageModelFactory: AppLibraryPageModelFactory,
    private val refreshAppStack: (FrameLayout, AppLibraryPageModel, MutableMap<String, TextView>) -> Unit,
) {
    private val pages = ArrayList<PageState>()
    private val queries = EnumMap<AppLibraryScope, String>(AppLibraryScope::class.java)

    private data class PageState(
        val key: String,
        val scope: AppLibraryScope,
        val pageModel: ChapterPage,
        val page: View,
        val header: View,
        val stackHost: FrameLayout,
        val search: EditText,
        val cardCache: MutableMap<String, TextView>,
    )

    fun registerPage(
        pageModel: ChapterPage,
        page: LinearLayout,
        header: LinearLayout,
        stackHost: FrameLayout,
    ): View {
        val scope = pageModel.appScope ?: AppLibraryScope.ALL
        val cardCache = LinkedHashMap<String, TextView>()
        val model = appLibraryPageModelFactory.create(
            page = pageModel,
            appEntries = appLibraryStore.state().apps,
            query = queryFor(scope),
            loading = appLibraryStore.state().loading,
        )
        val (searchRoot, searchEdit) = buildSearchBox(page, header, stackHost, pageModel, cardCache, scope)
        pages.add(PageState(pageModel.key, scope, pageModel, page, header, stackHost, searchEdit, cardCache))
        installKeyboardAnimator(page, header, searchEdit)
        refreshAppStack(stackHost, model, cardCache)
        return searchRoot
    }

    fun queryFor(scope: AppLibraryScope): String = queries[scope].orEmpty()

    fun refreshVisible(state: AppLibraryRenderState) {
        pages.forEach { pageState ->
            val model = appLibraryPageModelFactory.create(
                page = pageState.pageModel,
                appEntries = state.apps,
                query = queryFor(pageState.scope),
                loading = state.loading,
            )
            refreshAppStack(pageState.stackHost, model, pageState.cardCache)
        }
    }

    fun resetAll() = pages.forEach(::resetPage)

    fun resetInactiveExcept(activeKey: String) {
        pages.filter { it.key != activeKey }.forEach(::resetPage)
    }

    fun clear() {
        pages.clear()
        queries.clear()
    }

    private fun buildSearchBox(
        page: LinearLayout,
        header: LinearLayout,
        stackHost: FrameLayout,
        pageModel: ChapterPage,
        cardCache: MutableMap<String, TextView>,
        scope: AppLibraryScope,
    ): Pair<View, EditText> {
        val initialQuery = queryFor(scope)
        val root = FrameLayout(activity).apply {
            background = drawables.glass(CalmTheme.QUIET_GLASS, activity.dp(16))
            clipToPadding = false
            clipChildren = false
        }
        val clearButton = ImageButton(activity).apply {
            background = ColorDrawable(Color.TRANSPARENT)
            setImageResource(R.drawable.ic_clear_search)
            setColorFilter(CalmTheme.MUTED_INK)
            contentDescription = "Clear search"
            visibility = if (initialQuery.isBlank()) View.GONE else View.VISIBLE
        }
        val search = EditText(activity).apply {
            setText(initialQuery)
            hint = "Search apps"
            setSingleLine(true)
            setTextColor(CalmTheme.INK)
            setHintTextColor(CalmTheme.MUTED_INK)
            textSize = 16f
            typeface = Typeface.DEFAULT
            background = ColorDrawable(Color.TRANSPARENT)
            setPadding(activity.dp(16), activity.dp(12), activity.dp(50), activity.dp(12))
            setSelectAllOnFocus(false)
            setOnFocusChangeListener { view, hasFocus ->
                animateHeader(header, hasFocus)
                if (!hasFocus) animatePage(page, 0)
                if (hasFocus) {
                    view.post {
                        (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                            ?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
                    }
                }
            }
            addTextChangedListener(object : TextWatcher {
                var pendingRefresh: Runnable? = null

                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val query = s?.toString().orEmpty()
                    if (query == queryFor(scope)) return
                    setQuery(scope, query)
                    clearButton.visibility = if (query.isBlank()) View.GONE else View.VISIBLE
                    pendingRefresh?.let(mainHandler::removeCallbacks)
                    pendingRefresh = Runnable {
                        refreshAppStack(
                            stackHost,
                            appLibraryPageModelFactory.create(
                                page = pageModel,
                                appEntries = appLibraryStore.state().apps,
                                query = query,
                                loading = appLibraryStore.state().loading,
                            ),
                            cardCache,
                        )
                    }.also { mainHandler.postDelayed(it, APP_SEARCH_REFRESH_DELAY_MS) }
                }

                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        clearButton.setOnClickListener {
            search.setText("")
            search.clearFocus()
            hideKeyboard(search)
            animateHeader(header, false)
            animatePage(page, 0)
        }
        root.addView(search, FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(
            clearButton,
            FrameLayout.LayoutParams(activity.dp(44), activity.dp(44), android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL).apply {
                rightMargin = activity.dp(2)
            },
        )
        return root to search
    }

    private fun installKeyboardAnimator(page: LinearLayout, header: LinearLayout, search: EditText) {
        page.viewTreeObserver.addOnGlobalLayoutListener {
            if (!search.hasFocus()) return@addOnGlobalLayoutListener
            val kbHeight = keyboardHeight()
            val visible = kbHeight > activity.dp(120)
            animateHeader(header, visible)
            animatePage(page, if (visible) kbHeight else 0)
            if (!visible && search.text.isNullOrBlank()) search.clearFocus()
        }
    }

    private fun keyboardHeight(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.window.decorView.rootWindowInsets?.let { insets ->
                if (insets.isVisible(WindowInsets.Type.ime())) {
                    return insets.getInsets(WindowInsets.Type.ime()).bottom
                }
            }
        }
        val visibleFrame = Rect()
        val root = activity.window.decorView
        root.getWindowVisibleDisplayFrame(visibleFrame)
        return maxOf(0, root.height - visibleFrame.bottom)
    }

    private fun animateHeader(header: View, collapsed: Boolean) {
        header.animate()
            .alpha(if (collapsed) 0f else 1f)
            .translationY(if (collapsed) -activity.dp(18).toFloat() else 0f)
            .setDuration(180L)
            .start()
    }

    private fun animatePage(page: View, keyboardHeight: Int) {
        val target = if (keyboardHeight > 0) {
            -(keyboardHeight - activity.dp(34)).coerceAtLeast(0).toFloat()
        } else {
            0f
        }
        if (kotlin.math.abs(page.translationY - target) < 1f) return
        page.animate()
            .translationY(target)
            .setDuration(220L)
            .start()
    }

    private fun resetPage(state: PageState) {
        if (state.search.hasFocus()) {
            state.search.clearFocus()
            hideKeyboard(state.search)
        }
        if (state.search.text?.isNotBlank() == true) {
            state.search.setText("")
        } else {
            setQuery(state.scope, "")
        }
        state.header.animate().cancel()
        state.page.animate().cancel()
        state.header.alpha = 1f
        state.header.translationY = 0f
        state.page.translationY = 0f
    }

    private fun setQuery(scope: AppLibraryScope, query: String) {
        if (query.isBlank()) queries.remove(scope) else queries[scope] = query
    }

    private fun hideKeyboard(view: View) {
        (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private companion object {
        const val APP_SEARCH_REFRESH_DELAY_MS = 90L
    }
}
