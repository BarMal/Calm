package dev.barna.calm;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.provider.CalendarContract;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.TextClock;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.CompositePageTransformer;
import androidx.viewpager2.widget.ViewPager2;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {
    private static final String OVERVIEW_KEY = "dev.barna.calm.OVERVIEW";
    private static final String SETTINGS_KEY = "dev.barna.calm.SETTINGS";
    private static final String PREFS_NAME = "calm_preferences";
    private static final String PREF_EXCLUDED_PACKAGES = "excluded_notification_packages";
    private static final String PREF_EXCLUDED_LABEL_PREFIX = "excluded_label_";
    private static final String PREF_TINT_NOTIFICATION_CARDS = "tint_notification_cards";
    private static final String PREF_CARD_HAPTICS_ENABLED = "card_haptics_enabled";
    private static final String PREF_CARD_HAPTICS_STRENGTH = "card_haptics_strength";
    private static final int REQUEST_CALENDAR = 1001;

    private static final int INK = Color.rgb(236, 232, 222);
    private static final int MUTED_INK = Color.rgb(166, 161, 151);
    private static final int GLASS = Color.argb(82, 15, 15, 20);
    private static final int QUIET_GLASS = Color.argb(40, 31, 30, 38);
    private static final int STROKE = Color.argb(30, 255, 246, 226);
    private static final int GLOSS = Color.argb(18, 255, 252, 240);
    private static final int SHADOW = Color.argb(12, 0, 0, 0);
    private static final int SHADE_TOP = Color.argb(118, 5, 5, 10);
    private static final int SHADE_MID = Color.argb(16, 190, 168, 128);
    private static final int SHADE_BOTTOM = Color.argb(140, 2, 2, 6);
    private static final int ACCENT = Color.rgb(198, 181, 151);
    private static final int REFRACTION_BLUE = Color.argb(14, 116, 145, 210);
    private static final int REFRACTION_LILAC = Color.argb(12, 220, 198, 172);

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable notificationRefresh = () -> mainHandler.post(this::render);
    private String selectedPackageName = OVERVIEW_KEY;
    private HorizontalScrollView chapterCarousel;
    private LinearLayout chapterCarouselRow;
    private ViewPager2 currentPager;
    private View currentScreen;
    private FrameLayout focusedCardOverlay;
    private final List<FocusDisplacement> displacedFocusViews = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        CalmNotificationListenerService.addListener(notificationRefresh);
        render();
    }

    @Override
    protected void onPause() {
        CalmNotificationListenerService.removeListener(notificationRefresh);
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CALENDAR) {
            render();
        }
    }

    private void configureWindow() {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        window.getDecorView().setBackgroundColor(Color.TRANSPARENT);
        window.getDecorView().setSystemUiVisibility(0);
    }

    private void render() {
        dismissFocusedCard(false);
        List<AppChapter> notificationChapters = buildNotificationChapters();
        List<ChapterPage> pages = buildPages(notificationChapters);
        int initialPage = resolveInitialPage(pages);

        FrameLayout screen = new FrameLayout(this);
        currentScreen = screen;
        screen.setBackgroundColor(Color.TRANSPARENT);
        screen.addView(createWallpaperShade(), matchParentParams());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10), statusBarHeight() + dp(28), dp(10), dp(34));
        screen.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        root.addView(createHeader());

        ViewPager2 pager = new ViewPager2(this);
        currentPager = pager;
        pager.setAdapter(new ChapterPagerAdapter(pages));
        pager.setClipToPadding(false);
        pager.setClipChildren(false);
        pager.setOffscreenPageLimit(1);
        pager.setPadding(0, 0, 0, 0);
        CompositePageTransformer transformer = new CompositePageTransformer();
        transformer.addTransformer((page, position) -> {
            float distance = Math.min(1f, Math.abs(position));
            page.setAlpha(1f - (0.08f * distance));
            page.setScaleX(1f);
            page.setScaleY(1f);
            page.setTranslationX(0);
            page.setTranslationY(0);
            page.setTranslationZ(0);
        });
        pager.setPageTransformer(transformer);
        if (pager.getChildAt(0) instanceof RecyclerView) {
            RecyclerView recyclerView = (RecyclerView) pager.getChildAt(0);
            recyclerView.setClipToPadding(false);
            recyclerView.setClipChildren(false);
            recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        }
        pager.setCurrentItem(initialPage, false);
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                selectedPackageName = pages.get(position).key;
                updateChapterCarousel(pages, position);
            }
        });

        root.addView(createChapterCarousel(pages, initialPage));

        LinearLayout.LayoutParams pagerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        );
        root.addView(pager, pagerParams);
        updateChapterCarousel(pages, initialPage);

        setContentView(screen);
    }

    private List<ChapterPage> buildPages(List<AppChapter> notificationChapters) {
        List<ChapterPage> pages = new ArrayList<>();
        pages.add(ChapterPage.overview());
        int chapterNumber = 2;
        for (AppChapter chapter : notificationChapters) {
            pages.add(ChapterPage.notifications(chapter, roman(chapterNumber)));
            chapterNumber++;
        }
        pages.add(ChapterPage.settings(roman(chapterNumber)));
        return pages;
    }

    private int resolveInitialPage(List<ChapterPage> pages) {
        for (int index = 0; index < pages.size(); index++) {
            if (pages.get(index).key.equals(selectedPackageName)) {
                return index;
            }
        }
        selectedPackageName = OVERVIEW_KEY;
        return 0;
    }

    private List<AppChapter> buildNotificationChapters() {
        PackageManager packageManager = getPackageManager();
        List<ResolveInfo> apps = loadLaunchableApps();
        Map<String, ResolveInfo> launchableByPackage = new LinkedHashMap<>();
        for (ResolveInfo app : apps) {
            launchableByPackage.put(app.activityInfo.packageName, app);
        }

        Map<String, List<CalmNotificationListenerService.CalmNotification>> notificationsByPackage =
                new LinkedHashMap<>();
        Set<String> excludedPackages = excludedPackages();
        for (CalmNotificationListenerService.CalmNotification notification :
                CalmNotificationListenerService.snapshot()) {
            if (excludedPackages.contains(notification.packageName)) {
                continue;
            }
            List<CalmNotificationListenerService.CalmNotification> group =
                    notificationsByPackage.get(notification.packageName);
            if (group == null) {
                group = new ArrayList<>();
                notificationsByPackage.put(notification.packageName, group);
            }
            group.add(notification);
        }

        List<AppChapter> chapters = new ArrayList<>();
        for (Map.Entry<String, List<CalmNotificationListenerService.CalmNotification>> entry :
                notificationsByPackage.entrySet()) {
            String packageName = entry.getKey();
            List<CalmNotificationListenerService.CalmNotification> notifications = entry.getValue();
            if (notifications.isEmpty()) {
                continue;
            }
            chapters.add(new AppChapter(
                    packageName,
                    resolveAppLabel(packageManager, packageName, launchableByPackage.get(packageName)),
                    notifications,
                    launchableByPackage.containsKey(packageName),
                    resolveAppHue(packageManager, packageName)
            ));
        }

        Collator collator = Collator.getInstance();
        chapters.sort((left, right) -> {
            int notificationCompare = Integer.compare(right.notifications.size(), left.notifications.size());
            if (notificationCompare != 0) {
                return notificationCompare;
            }
            return collator.compare(left.label, right.label);
        });
        return chapters;
    }

    private View createHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(20), dp(2), dp(20), dp(2));
        header.setBackgroundColor(Color.TRANSPARENT);
        header.setElevation(0);

        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("HH:mm");
        clock.setTextColor(INK);
        clock.setTextSize(38);
        clock.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        clock.setIncludeFontPadding(false);
        header.addView(clock);

        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEE, MMM d");
        date.setFormat24Hour("EEE, MMM d");
        date.setTextColor(MUTED_INK);
        date.setTextSize(13);
        date.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        date.setIncludeFontPadding(false);
        date.setPadding(0, dp(3), 0, 0);
        header.addView(date);

        return header;
    }

    private View createChapterCarousel(List<ChapterPage> pages, int selectedPosition) {
        LinearLayout spine = new LinearLayout(this);
        spine.setOrientation(LinearLayout.VERTICAL);
        spine.setClipToPadding(false);

        LinearLayout.LayoutParams spineParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        spineParams.topMargin = dp(12);
        spineParams.bottomMargin = dp(18);
        spine.setLayoutParams(spineParams);

        spine.addView(spineLine());

        chapterCarousel = new HorizontalScrollView(this);
        chapterCarousel.setHorizontalScrollBarEnabled(false);
        chapterCarousel.setOverScrollMode(View.OVER_SCROLL_NEVER);
        chapterCarousel.setClipToPadding(false);
        chapterCarousel.setPadding(dp(78), dp(3), dp(78), dp(3));
        chapterCarousel.setBackgroundColor(Color.TRANSPARENT);

        chapterCarouselRow = new LinearLayout(this);
        chapterCarouselRow.setOrientation(LinearLayout.HORIZONTAL);
        chapterCarousel.addView(chapterCarouselRow, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        spine.addView(chapterCarousel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        spine.addView(spineLine());

        renderChapterCarouselItems(pages, selectedPosition);
        return spine;
    }

    private View spineLine() {
        View line = new View(this);
        line.setBackgroundColor(Color.argb(52, Color.red(ACCENT), Color.green(ACCENT), Color.blue(ACCENT)));
        line.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(1, dp(1))
        ));
        return line;
    }

    private View createOverviewPage(List<AppChapter> notificationChapters) {
        LinearLayout page = createBarePagePanel(dp(20));

        TextView chapterMarker = label("CHAPTER / OVERVIEW", 12, ACCENT, Typeface.BOLD);
        chapterMarker.setPadding(0, 0, 0, dp(18));
        page.addView(chapterMarker);

        page.addView(sectionTitle("Next alarm"));
        page.addView(alarmCard());

        page.addView(sectionTitle("Upcoming calendar"));
        if (hasCalendarPermission()) {
            List<CalendarEvent> events = loadUpcomingEvents();
            if (events.isEmpty()) {
                page.addView(emptyNote("No upcoming calendar events found."));
            } else {
                page.addView(calendarStack(events), new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(224)
                ));
            }
        } else {
            page.addView(emptyNote("Calendar access is needed before Calm can index upcoming events. Manage it in Settings."));
        }

        page.addView(sectionTitle("Active notification chapters"));
        if (notificationChapters.isEmpty()) {
            page.addView(emptyNote("No active notification chapters right now."));
        } else {
            for (AppChapter chapter : notificationChapters) {
                page.addView(infoCard(chapter.label + "\n" + notificationSummary(chapter)));
            }
        }

        page.addView(sectionTitle("Launcher settings"));
        page.addView(fullWidthAction("Open settings", this::openSettingsChapter));

        return page;
    }

    private View createSettingsPage() {
        LinearLayout page = createPagePanel(null, 0);

        TextView chapterMarker = label("CHAPTER / SETTINGS", 12, ACCENT, Typeface.BOLD);
        page.addView(chapterMarker);

        TextView title = label("Launcher settings", 30, INK, Typeface.NORMAL);
        title.setPadding(0, dp(8), 0, 0);
        page.addView(title);

        TextView note = label("Wallpaper, access, and hidden notification sources live here.", 15, MUTED_INK, Typeface.NORMAL);
        note.setPadding(0, dp(6), 0, dp(18));
        page.addView(note);

        page.addView(sectionTitle("Appearance"));
        page.addView(fullWidthAction(
                useTintedNotificationCards()
                        ? "Notification surface\nTinted cards"
                        : "Notification surface\nChapter panel",
                this::toggleNotificationSurface
        ));
        page.addView(fullWidthAction(
                cardHapticsEnabled()
                        ? "Card haptics\nOn"
                        : "Card haptics\nOff",
                this::toggleCardHaptics
        ));
        page.addView(hapticStrengthControl());

        page.addView(sectionTitle("Access"));
        page.addView(fullWidthAction("Set wallpaper", this::openWallpaperPicker));
        page.addView(fullWidthAction(
                isNotificationAccessEnabled() ? "Notification access enabled" : "Enable notification access",
                this::openNotificationAccess
        ));
        page.addView(fullWidthAction(
                hasCalendarPermission() ? "Calendar access enabled" : "Allow calendar access",
                this::requestCalendarAccess
        ));

        page.addView(sectionTitle("Excluded"));
        List<ExcludedSource> excluded = excludedSources();
        if (excluded.isEmpty()) {
            page.addView(emptyNote("No notification sources are excluded."));
        } else {
            for (ExcludedSource source : excluded) {
                page.addView(fullWidthAction(
                        "Restore " + source.label + "\n" + source.packageName,
                        () -> restoreNotificationSource(source.packageName)
                ));
            }
        }

        return page;
    }

    private View createChapterPage(AppChapter chapter) {
        boolean tintCards = useTintedNotificationCards();
        LinearLayout page = tintCards
                ? createBarePagePanel()
                : createPagePanel(resolveChapterBackground(chapter), chapter.hueColor);

        TextView chapterMarker = label("CHAPTER / " + chapter.packageName, 12, ACCENT, Typeface.BOLD);
        chapterMarker.setSingleLine(true);
        chapterMarker.setEllipsize(TextUtils.TruncateAt.END);
        page.addView(chapterMarker);

        TextView appName = label(chapter.label, 30, INK, Typeface.NORMAL);
        appName.setPadding(0, dp(8), 0, 0);
        page.addView(appName);

        TextView count = label(notificationSummary(chapter), 15, MUTED_INK, Typeface.NORMAL);
        count.setPadding(0, dp(6), 0, dp(30));
        page.addView(count);

        page.addView(sectionTitle("Notifications"));
        page.addView(notificationStack(chapter, tintCards), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                2.25f
        ));

        return page;
    }

    private View notificationStack(AppChapter chapter, boolean tintCards) {
        List<TextView> cards = new ArrayList<>();
        for (CalmNotificationListenerService.CalmNotification notification : chapter.notifications) {
            cards.add(notificationCard(notification, chapter, tintCards));
        }
        return cardStack(cards, dp(184), dp(58));
    }

    private View calendarStack(List<CalendarEvent> events) {
        List<TextView> cards = new ArrayList<>();
        for (CalendarEvent event : events) {
            cards.add(calendarCard(event));
        }
        return cardStack(cards, dp(150), dp(48));
    }

    private View cardStack(List<TextView> cards, int cardHeight, int cardStep) {
        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(false);
        scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroller.setBackgroundColor(Color.TRANSPARENT);
        scroller.setPadding(0, 0, 0, 0);
        scroller.setClipToPadding(false);
        scroller.setClipChildren(false);

        LinearLayout stack = new LinearLayout(this);
        stack.setOrientation(LinearLayout.VERTICAL);
        stack.setClipToPadding(false);
        stack.setClipChildren(false);
        int stackTopPadding = dp(6);
        int minimumBottomPadding = dp(32);
        stack.setPadding(0, stackTopPadding, 0, minimumBottomPadding);
        scroller.addView(stack, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        int stackOverlap = cardHeight - cardStep;
        int index = 0;
        for (TextView card : cards) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    cardHeight
            );
            params.topMargin = notificationCardTopMargin(index, stackOverlap);
            stack.addView(card, params);
            index++;
        }
        Runnable[] magneticSnap = new Runnable[1];
        int[] lastHapticIndex = {-1};
        magneticSnap[0] = () -> magnetizeNotificationStack(scroller, cards, cardStep);
        scroller.setOnScrollChangeListener((view, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            styleNotificationStack(scroller, cards, cardStep, true, lastHapticIndex);
            mainHandler.removeCallbacks(magneticSnap[0]);
            mainHandler.postDelayed(magneticSnap[0], 90);
        });
        scroller.post(() -> {
            int trailingPadding = Math.max(
                    minimumBottomPadding,
                    scroller.getHeight() - cardHeight + dp(36)
            );
            stack.setPadding(0, stackTopPadding, 0, trailingPadding);
            styleNotificationStack(scroller, cards, cardStep, false, lastHapticIndex);
        });
        return scroller;
    }

    private int notificationCardTopMargin(int index, int stackOverlap) {
        if (index == 0) {
            return 0;
        }
        return -stackOverlap;
    }

    private void styleNotificationStack(
            ScrollView scroller,
            List<TextView> cards,
            int cardStep,
            boolean allowHaptic,
            int[] lastHapticIndex
    ) {
        if (cards.isEmpty()) {
            return;
        }

        int readingAnchor = cards.get(0).getTop();
        int scrollY = clampedNotificationScroll(scroller, cards, readingAnchor);
        int activeIndex = 0;
        int threshold = scrollY + readingAnchor;
        for (int index = 0; index < cards.size(); index++) {
            TextView card = cards.get(index);
            if (card.getTop() <= threshold) {
                activeIndex = index;
            }
        }

        for (int index = 0; index < cards.size(); index++) {
            TextView card = cards.get(index);
            float visualDepth = (card.getTop() - threshold) / (float) cardStep;
            float scale = notificationCardScale(visualDepth);
            card.setPivotY(0f);
            card.setTranslationZ(visualDepth < 0f ? 0f : 100f - visualDepth);
            card.setScaleX(scale);
            card.setScaleY(scale);
            card.setAlpha(notificationCardAlpha(visualDepth));
            card.setTextColor(notificationCardTextColor(visualDepth));
            card.setEnabled(visualDepth >= -0.05f && visualDepth <= 2.05f);
        }

        if (lastHapticIndex[0] == -1) {
            lastHapticIndex[0] = activeIndex;
        } else if (allowHaptic && lastHapticIndex[0] != activeIndex) {
            lastHapticIndex[0] = activeIndex;
            performCardScrollHaptic(scroller);
        }
    }

    private int clampedNotificationScroll(ScrollView scroller, List<TextView> cards, int readingAnchor) {
        int scrollY = scroller.getScrollY();
        int maxScroll = Math.max(0, cards.get(cards.size() - 1).getTop() - readingAnchor);
        int clamped = Math.max(0, Math.min(scrollY, maxScroll));
        if (clamped != scrollY) {
            scroller.scrollTo(0, clamped);
        }
        return clamped;
    }

    private void magnetizeNotificationStack(ScrollView scroller, List<TextView> cards, int cardStep) {
        if (cards.isEmpty() || cardStep <= 0) {
            return;
        }

        int readingAnchor = cards.get(0).getTop();
        int scrollY = clampedNotificationScroll(scroller, cards, readingAnchor);
        int maxScroll = Math.max(0, cards.get(cards.size() - 1).getTop() - readingAnchor);
        int nearestStep = Math.round(scrollY / (float) cardStep);
        int target = Math.max(0, Math.min(nearestStep * cardStep, maxScroll));
        int distance = Math.abs(target - scrollY);
        if (distance > dp(42) || distance < dp(1)) {
            return;
        }

        scroller.smoothScrollTo(0, target);
    }

    private float notificationCardScale(float visualDepth) {
        if (visualDepth < 0f) {
            float outgoing = clamp01(-visualDepth);
            return lerp(1.02f, 0.96f, outgoing);
        }
        if (visualDepth <= 1f) {
            return lerp(1.02f, 0.96f, visualDepth);
        }
        if (visualDepth <= 2f) {
            return lerp(0.96f, 0.90f, visualDepth - 1f);
        }
        return 0.88f;
    }

    private float notificationCardAlpha(float visualDepth) {
        if (visualDepth < 0f) {
            return clamp01(1f + visualDepth);
        }
        if (visualDepth <= 2f) {
            return lerp(1f, 0.56f, visualDepth / 2f);
        }
        if (visualDepth <= 2.35f) {
            return lerp(0.56f, 0f, (visualDepth - 2f) / 0.35f);
        }
        return 0f;
    }

    private int notificationCardTextColor(float visualDepth) {
        float depth = clamp01(Math.max(0f, visualDepth) / 2f);
        return blendColor(INK, Color.rgb(128, 124, 116), depth);
    }

    private void showFocusedCard(TextView sourceCard, List<ContextAction> actions) {
        dismissFocusedCard(false);
        FrameLayout content = findViewById(android.R.id.content);
        if (content == null) {
            return;
        }

        animatePageElementsAway(sourceCard, true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && currentScreen != null) {
            currentScreen.setRenderEffect(RenderEffect.createBlurEffect(dp(7), dp(7), Shader.TileMode.CLAMP));
        }
        if (currentScreen != null) {
            currentScreen.animate()
                    .alpha(0.72f)
                    .setDuration(180)
                    .start();
        }

        focusedCardOverlay = new FrameLayout(this);
        focusedCardOverlay.setAlpha(0f);
        focusedCardOverlay.setBackgroundColor(Color.argb(104, 0, 0, 0));
        focusedCardOverlay.setOnClickListener(view -> dismissFocusedCard(true));
        content.addView(focusedCardOverlay, matchParentParams());

        LinearLayout focusColumn = new LinearLayout(this);
        focusColumn.setOrientation(LinearLayout.VERTICAL);
        focusColumn.setGravity(Gravity.CENTER);
        focusColumn.setPadding(dp(18), 0, dp(18), 0);
        focusColumn.setTranslationY(dp(26));
        focusColumn.setOnClickListener(view -> {
            // Consume taps inside the focused card/menu area.
        });

        focusedCardOverlay.addView(focusColumn, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        ));

        TextView focusedCard = label(sourceCard.getText().toString(), 17, INK, Typeface.NORMAL);
        focusedCard.setLineSpacing(dp(3), 1.0f);
        focusedCard.setPadding(dp(20), dp(18), dp(20), dp(18));
        focusedCard.setMaxLines(8);
        focusedCard.setEllipsize(TextUtils.TruncateAt.END);
        Drawable background = sourceCard.getBackground();
        Drawable.ConstantState state = background == null ? null : background.getConstantState();
        focusedCard.setBackground(state == null ? glassDrawable(GLASS, dp(20)) : state.newDrawable().mutate());
        focusedCard.setElevation(dp(10));
        focusColumn.addView(focusedCard, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        GridLayout menu = new GridLayout(this);
        menu.setColumnCount(2);
        menu.setUseDefaultMargins(false);
        LinearLayout.LayoutParams menuParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        menuParams.topMargin = dp(14);
        focusColumn.addView(menu, menuParams);

        for (ContextAction action : actions) {
            menu.addView(contextActionButton(action));
        }

        focusedCardOverlay.animate().alpha(1f).setDuration(170).start();
        focusColumn.animate().translationY(0).setDuration(220).start();
    }

    private void dismissFocusedCard(boolean animate) {
        if (focusedCardOverlay == null) {
            resetFocusBackdrop(animate);
            return;
        }

        FrameLayout overlay = focusedCardOverlay;
        focusedCardOverlay = null;
        Runnable cleanup = () -> {
            ViewGroup parent = (ViewGroup) overlay.getParent();
            if (parent != null) {
                parent.removeView(overlay);
            }
            resetFocusBackdrop(animate);
        };

        if (animate) {
            overlay.animate()
                    .alpha(0f)
                    .setDuration(150)
                    .withEndAction(cleanup)
                    .start();
        } else {
            cleanup.run();
        }
    }

    private void resetFocusBackdrop(boolean animate) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && currentScreen != null) {
            currentScreen.setRenderEffect(null);
        }
        if (currentScreen != null) {
            if (animate) {
                currentScreen.animate().alpha(1f).setDuration(150).start();
            } else {
                currentScreen.setAlpha(1f);
            }
        }
        animatePageElementsAway(null, false);
    }

    private void animatePageElementsAway(View sourceCard, boolean away) {
        if (!away) {
            for (FocusDisplacement displacement : displacedFocusViews) {
                displacement.view.animate()
                        .translationY(displacement.translationY)
                        .alpha(displacement.alpha)
                        .setDuration(170)
                        .start();
            }
            displacedFocusViews.clear();
            return;
        }

        LinearLayout page = findPageContainer(sourceCard);
        if (page == null) {
            return;
        }

        int[] sourceLocation = new int[2];
        sourceCard.getLocationOnScreen(sourceLocation);
        int sourceCenter = sourceLocation[1] + (sourceCard.getHeight() / 2);
        displacedFocusViews.clear();
        animateStackCardsAway(sourceCard, sourceCenter);

        for (int index = 0; index < page.getChildCount(); index++) {
            View child = page.getChildAt(index);
            if (containsView(child, sourceCard)) {
                continue;
            }
            int[] childLocation = new int[2];
            child.getLocationOnScreen(childLocation);
            int childCenter = childLocation[1] + (child.getHeight() / 2);
            int direction = childCenter < sourceCenter ? -1 : 1;
            displacedFocusViews.add(new FocusDisplacement(child));
            child.animate()
                    .translationY(direction * dp(34))
                    .alpha(0.48f)
                    .setDuration(210)
                    .start();
        }
    }

    private void animateStackCardsAway(View sourceCard, int sourceCenter) {
        LinearLayout stack = findCardStackContent(sourceCard);
        if (stack == null) {
            displacedFocusViews.add(new FocusDisplacement(sourceCard));
            sourceCard.animate()
                    .alpha(0f)
                    .setDuration(120)
                    .start();
            return;
        }

        for (int index = 0; index < stack.getChildCount(); index++) {
            View card = stack.getChildAt(index);
            displacedFocusViews.add(new FocusDisplacement(card));
            if (card == sourceCard) {
                card.animate()
                        .alpha(0f)
                        .setDuration(120)
                        .start();
                continue;
            }

            int[] cardLocation = new int[2];
            card.getLocationOnScreen(cardLocation);
            int cardCenter = cardLocation[1] + (card.getHeight() / 2);
            int direction = cardCenter < sourceCenter ? -1 : 1;
            card.animate()
                    .translationY(card.getTranslationY() + (direction * dp(72)))
                    .alpha(Math.min(card.getAlpha(), 0.18f))
                    .setDuration(190)
                    .start();
        }
    }

    private LinearLayout findPageContainer(View source) {
        View cursor = source;
        while (cursor != null) {
            ViewParent parent = cursor.getParent();
            if (cursor instanceof LinearLayout && parent instanceof FrameLayout) {
                return (LinearLayout) cursor;
            }
            cursor = parent instanceof View ? (View) parent : null;
        }
        return null;
    }

    private boolean containsView(View container, View target) {
        if (container == target) {
            return true;
        }
        if (!(container instanceof ViewGroup)) {
            return false;
        }
        ViewGroup group = (ViewGroup) container;
        for (int index = 0; index < group.getChildCount(); index++) {
            if (containsView(group.getChildAt(index), target)) {
                return true;
            }
        }
        return false;
    }

    private LinearLayout findCardStackContent(View source) {
        View cursor = source;
        while (cursor != null) {
            ViewParent parent = cursor.getParent();
            if (cursor instanceof LinearLayout && parent instanceof ScrollView) {
                return (LinearLayout) cursor;
            }
            cursor = parent instanceof View ? (View) parent : null;
        }
        return null;
    }

    private TextView contextActionButton(ContextAction action) {
        TextView button = label(action.label, 14, INK, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setPadding(dp(10), dp(12), dp(10), dp(12));
        button.setBackground(glassDrawable(QUIET_GLASS, dp(999)));
        button.setOnClickListener(view -> {
            dismissFocusedCard(true);
            mainHandler.postDelayed(action.action, 170);
        });

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(dp(4), dp(4), dp(4), dp(4));
        button.setLayoutParams(params);
        return button;
    }

    private float lerp(float start, float end, float amount) {
        return start + ((end - start) * clamp01(amount));
    }

    private int blendColor(int start, int end, float amount) {
        float clampedAmount = clamp01(amount);
        return Color.rgb(
                (int) lerp(Color.red(start), Color.red(end), clampedAmount),
                (int) lerp(Color.green(start), Color.green(end), clampedAmount),
                (int) lerp(Color.blue(start), Color.blue(end), clampedAmount)
        );
    }

    private float clamp01(float value) {
        if (value < 0f) {
            return 0f;
        }
        if (value > 1f) {
            return 1f;
        }
        return value;
    }

    private LinearLayout createPagePanel(Bitmap backgroundImage, int hueColor) {
        LinearLayout page = glassPanel(GLASS);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setClipChildren(false);
        page.setClipToPadding(false);
        page.setPadding(dp(20), dp(28), dp(20), dp(30));
        page.setElevation(dp(1));
        page.setTranslationZ(0);
        if (backgroundImage != null) {
            page.setBackground(glassDrawableWithImage(GLASS, dp(22), backgroundImage, hueColor));
        } else if (hueColor != 0) {
            page.setBackground(glassDrawableWithHue(GLASS, dp(22), hueColor));
        }

        LinearLayout.LayoutParams pageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        pageParams.topMargin = dp(20);
        pageParams.bottomMargin = dp(18);
        page.setLayoutParams(pageParams);
        return page;
    }

    private LinearLayout createBarePagePanel() {
        return createBarePagePanel(dp(4));
    }

    private LinearLayout createBarePagePanel(int horizontalPadding) {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setClipChildren(false);
        page.setClipToPadding(false);
        page.setPadding(horizontalPadding, dp(28), horizontalPadding, dp(30));
        page.setBackgroundColor(Color.TRANSPARENT);
        page.setElevation(0);
        page.setTranslationZ(0);

        LinearLayout.LayoutParams pageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        pageParams.topMargin = dp(20);
        pageParams.bottomMargin = dp(18);
        page.setLayoutParams(pageParams);
        return page;
    }

    private void updateChapterCarousel(List<ChapterPage> pages, int position) {
        if (chapterCarousel == null || chapterCarouselRow == null || pages.isEmpty()) {
            return;
        }

        renderChapterCarouselItems(pages, position);
        chapterCarousel.post(() -> centerCarouselItem(position));
    }

    private void renderChapterCarouselItems(List<ChapterPage> pages, int selectedPosition) {
        chapterCarouselRow.removeAllViews();
        for (int index = 0; index < pages.size(); index++) {
            ChapterPage page = pages.get(index);
            boolean selected = index == selectedPosition;
            TextView item = label(
                    page.marker + "  " + page.title,
                    selected ? 18 : 14,
                    selected ? INK : MUTED_INK,
                    selected ? Typeface.BOLD : Typeface.NORMAL
            );
            item.setGravity(Gravity.CENTER);
            item.setSingleLine(true);
            item.setEllipsize(TextUtils.TruncateAt.END);
            item.setPadding(dp(selected ? 12 : 8), dp(8), dp(selected ? 12 : 8), dp(8));
            item.setAlpha(selected ? 1f : 0.5f);
            item.setBackground(null);
            item.setMaxWidth(selected ? dp(176) : dp(126));
            item.setMinWidth(selected ? dp(118) : dp(74));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.leftMargin = dp(1);
            params.rightMargin = dp(1);
            chapterCarouselRow.addView(item, params);

            final int target = index;
            item.setOnClickListener(view -> {
                if (currentPager != null) {
                    currentPager.setCurrentItem(target, true);
                }
            });
        }
    }

    private void centerCarouselItem(int position) {
        if (chapterCarouselRow.getChildCount() <= position) {
            return;
        }

        View child = chapterCarouselRow.getChildAt(position);
        int target = child.getLeft() - ((chapterCarousel.getWidth() - child.getWidth()) / 2);
        chapterCarousel.smoothScrollTo(Math.max(0, target), 0);
    }

    private TextView alarmCard() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        AlarmManager.AlarmClockInfo nextAlarm = alarmManager == null ? null : alarmManager.getNextAlarmClock();
        if (nextAlarm == null || nextAlarm.getTriggerTime() <= System.currentTimeMillis()) {
            return emptyNote("No upcoming alarm is scheduled.");
        }

        String alarmTime = DateFormat.getTimeFormat(this).format(new Date(nextAlarm.getTriggerTime()));
        TextView card = infoCard("Next alarm\n" + alarmTime);
        card.setOnClickListener(view -> openNextAlarm(nextAlarm));
        return card;
    }

    private void openNextAlarm(AlarmManager.AlarmClockInfo nextAlarm) {
        if (nextAlarm.getShowIntent() != null) {
            try {
                nextAlarm.getShowIntent().send();
                return;
            } catch (Exception ignored) {
                // Fall through to the generic alarm settings screen.
            }
        }
        startActivity(new Intent(Settings.ACTION_SETTINGS));
    }

    private List<CalendarEvent> loadUpcomingEvents() {
        List<CalendarEvent> events = new ArrayList<>();
        long now = System.currentTimeMillis();
        long horizon = now + 7L * 24L * 60L * 60L * 1000L;

        Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(builder, now);
        ContentUris.appendId(builder, horizon);

        String[] projection = {
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.EVENT_LOCATION,
                CalendarContract.Instances.ALL_DAY
        };

        ContentResolver resolver = getContentResolver();
        try (Cursor cursor = resolver.query(
                builder.build(),
                projection,
                null,
                null,
                CalendarContract.Instances.BEGIN + " ASC"
        )) {
            if (cursor == null) {
                return events;
            }

            while (cursor.moveToNext() && events.size() < 5) {
                events.add(new CalendarEvent(
                        cursor.getString(0),
                        cursor.getLong(1),
                        cursor.getLong(2),
                        cursor.getString(3),
                        cursor.getInt(4) == 1
                ));
            }
        } catch (SecurityException ignored) {
            return events;
        }
        return events;
    }

    private TextView calendarCard(CalendarEvent event) {
        String title = event.title == null || event.title.trim().isEmpty()
                ? "Untitled event"
                : event.title.trim();
        String when = formatEventTime(event);
        String location = event.location == null || event.location.trim().isEmpty()
                ? ""
                : "\n" + event.location.trim();
        boolean today = isToday(event.begin);
        TextView card = label(
                (today ? "TODAY" : "UPCOMING") + "\n" + title + "\n" + when + location,
                15,
                INK,
                Typeface.NORMAL
        );
        card.setLineSpacing(dp(2), 1.0f);
        card.setPadding(dp(18), dp(15), dp(18), dp(15));
        card.setMaxLines(5);
        card.setEllipsize(TextUtils.TruncateAt.END);
        card.setBackground(notificationCardDrawable(
                dp(18),
                today ? ACCENT : Color.rgb(122, 146, 178),
                true
        ));
        card.setElevation(dp(2));
        card.setOnClickListener(view -> openCalendarEvent(event));
        card.setOnLongClickListener(view -> {
            showFocusedCard(card, calendarContextActions(event));
            return true;
        });
        return card;
    }

    private boolean isToday(long timeMillis) {
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(timeMillis);
        Calendar now = Calendar.getInstance();
        return then.get(Calendar.YEAR) == now.get(Calendar.YEAR)
                && then.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR);
    }

    private String formatEventTime(CalendarEvent event) {
        if (event.allDay) {
            return DateFormat.getDateFormat(this).format(new Date(event.begin));
        }

        String startDate = DateFormat.getDateFormat(this).format(new Date(event.begin));
        String startTime = DateFormat.getTimeFormat(this).format(new Date(event.begin));
        String endTime = DateFormat.getTimeFormat(this).format(new Date(event.end));
        return startDate + " / " + startTime + " - " + endTime;
    }

    private TextView notificationCard(
            CalmNotificationListenerService.CalmNotification notification,
            AppChapter chapter,
            boolean tintCards
    ) {
        String title = notification.title.isEmpty() ? "Untitled notification" : notification.title;
        String body = notification.text.isEmpty() ? notification.subText : notification.text;
        String time = DateFormat.getTimeFormat(this).format(new Date(notification.postTime));
        TextView card = label(title + "\n" + body + "\n" + time, 15, INK, Typeface.NORMAL);
        card.setLineSpacing(dp(2), 1.0f);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setMaxLines(4);
        card.setEllipsize(TextUtils.TruncateAt.END);
        card.setBackground(notificationCardDrawable(dp(18), chapter.hueColor, tintCards));
        card.setElevation(dp(2));
        card.setOnClickListener(view -> openNotification(notification));
        card.setOnLongClickListener(view -> {
            showFocusedCard(card, notificationContextActions(notification, chapter));
            return true;
        });
        return card;
    }

    private TextView infoCard(String text) {
        TextView card = label(text, 15, INK, Typeface.NORMAL);
        card.setLineSpacing(dp(2), 1.0f);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(glassDrawable(QUIET_GLASS, dp(16)));
        card.setElevation(0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = dp(12);
        card.setLayoutParams(params);
        return card;
    }

    private TextView emptyNote(String text) {
        TextView note = label(text, 15, MUTED_INK, Typeface.NORMAL);
        note.setPadding(dp(14), dp(12), dp(14), dp(12));
        note.setBackground(glassDrawable(QUIET_GLASS, dp(16)));
        note.setElevation(0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = dp(12);
        note.setLayoutParams(params);
        return note;
    }

    private TextView sectionTitle(String text) {
        TextView title = label(text.toUpperCase(Locale.getDefault()), 12, ACCENT, Typeface.BOLD);
        title.setPadding(0, dp(14), 0, dp(8));
        return title;
    }

    private TextView fullWidthAction(String text, Runnable action) {
        TextView button = label(text, 16, INK, Typeface.BOLD);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setPadding(dp(14), dp(14), dp(14), dp(14));
        button.setBackground(glassDrawable(QUIET_GLASS, dp(16)));
        button.setElevation(0);
        button.setOnClickListener(view -> action.run());

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = dp(10);
        button.setLayoutParams(params);
        return button;
    }

    private View hapticStrengthControl() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(glassDrawable(QUIET_GLASS, dp(16)));

        TextView title = label("Haptic strength", 15, INK, Typeface.BOLD);
        card.addView(title);

        TextView value = label(hapticStrengthLabel(cardHapticStrength()), 13, MUTED_INK, Typeface.NORMAL);
        value.setPadding(0, dp(4), 0, dp(4));
        card.addView(value);

        SeekBar slider = new SeekBar(this);
        slider.setMax(4);
        slider.setProgress(cardHapticStrength() - 1);
        slider.setEnabled(cardHapticsEnabled());
        slider.setAlpha(cardHapticsEnabled() ? 1f : 0.42f);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int strength = progress + 1;
                value.setText(hapticStrengthLabel(strength));
                if (fromUser) {
                    preferences().edit()
                            .putInt(PREF_CARD_HAPTICS_STRENGTH, strength)
                            .apply();
                    performCardScrollHaptic(card);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // No-op.
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // No-op.
            }
        });
        card.addView(slider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = dp(10);
        card.setLayoutParams(params);
        return card;
    }

    private String hapticStrengthLabel(int strength) {
        return "Very light / " + strength + " of 5";
    }

    private TextView actionPill(String text, Runnable action) {
        TextView button = label(text, 12, INK, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setPadding(dp(6), dp(9), dp(6), dp(9));
        button.setBackground(glassDrawable(QUIET_GLASS, dp(999)));
        button.setOnClickListener(view -> action.run());

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
        button.setLayoutParams(params);
        return button;
    }

    private LinearLayout glassPanel(int color) {
        LinearLayout panel = new LinearLayout(this);
        panel.setBackground(glassDrawable(color, dp(22)));
        return panel;
    }

    private Bitmap resolveChapterBackground(AppChapter chapter) {
        for (CalmNotificationListenerService.CalmNotification notification : chapter.notifications) {
            if (notification.backgroundImage != null) {
                return notification.backgroundImage;
            }
        }

        try {
            Drawable icon = getPackageManager().getApplicationIcon(chapter.packageName);
            return drawableToBitmap(icon);
        } catch (PackageManager.NameNotFoundException ignored) {
            return null;
        }
    }

    private int resolveAppHue(PackageManager packageManager, String packageName) {
        try {
            Drawable icon = packageManager.getApplicationIcon(packageName);
            return dominantColor(drawableToBitmap(icon));
        } catch (PackageManager.NameNotFoundException ignored) {
            return 0;
        }
    }

    private int dominantColor(Bitmap bitmap) {
        long red = 0;
        long green = 0;
        long blue = 0;
        long weight = 0;
        int stepX = Math.max(1, bitmap.getWidth() / 24);
        int stepY = Math.max(1, bitmap.getHeight() / 24);

        for (int y = 0; y < bitmap.getHeight(); y += stepY) {
            for (int x = 0; x < bitmap.getWidth(); x += stepX) {
                int pixel = bitmap.getPixel(x, y);
                int alpha = Color.alpha(pixel);
                if (alpha < 96) {
                    continue;
                }

                int r = Color.red(pixel);
                int g = Color.green(pixel);
                int b = Color.blue(pixel);
                int max = Math.max(r, Math.max(g, b));
                int min = Math.min(r, Math.min(g, b));
                int saturation = max - min;
                int brightness = (r + g + b) / 3;
                if (saturation < 22 || brightness < 28 || brightness > 236) {
                    continue;
                }

                int pixelWeight = Math.max(1, saturation);
                red += (long) r * pixelWeight;
                green += (long) g * pixelWeight;
                blue += (long) b * pixelWeight;
                weight += pixelWeight;
            }
        }

        if (weight == 0) {
            return ACCENT;
        }
        return Color.rgb((int) (red / weight), (int) (green / weight), (int) (blue / weight));
    }

    private Bitmap drawableToBitmap(Drawable drawable) {
        int width = Math.max(1, drawable.getIntrinsicWidth());
        int height = Math.max(1, drawable.getIntrinsicHeight());
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    private Drawable glassDrawable(int color, int radius) {
        GradientDrawable shadow = new GradientDrawable();
        shadow.setColor(SHADOW);
        shadow.setCornerRadius(radius + dp(2));

        GradientDrawable base = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.argb(58, 34, 31, 38), color, Color.argb(66, 4, 4, 9)}
        );
        base.setCornerRadius(radius);
        base.setStroke(dp(1), STROKE);

        GradientDrawable gloss = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{GLOSS, Color.argb(10, 255, 248, 234), Color.TRANSPARENT}
        );
        gloss.setCornerRadius(radius);

        GradientDrawable refraction = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{REFRACTION_BLUE, Color.TRANSPARENT, REFRACTION_LILAC}
        );
        refraction.setCornerRadius(radius);

        LayerDrawable layers = new LayerDrawable(new Drawable[]{shadow, base, gloss, refraction});
        layers.setLayerInset(0, 0, dp(1), 0, 0);
        layers.setLayerInset(1, 0, 0, 0, 0);
        layers.setLayerInset(2, dp(1), dp(1), dp(1), 0);
        layers.setLayerInset(3, dp(1), dp(1), dp(1), 0);
        return layers;
    }

    private Drawable notificationCardDrawable(int radius, int hueColor, boolean tintCards) {
        GradientDrawable base = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.argb(214, 24, 22, 28),
                        Color.argb(196, 10, 10, 15),
                        Color.argb(212, 4, 4, 8)
                }
        );
        base.setCornerRadius(radius);
        base.setStroke(dp(1), Color.argb(tintCards ? 72 : 66, 255, 246, 226));

        GradientDrawable frost = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        Color.argb(58, 255, 252, 240),
                        Color.argb(18, 255, 252, 240),
                        Color.argb(6, 255, 252, 240)
                }
        );
        frost.setCornerRadius(radius);

        GradientDrawable gloss = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(44, 255, 249, 235), Color.argb(10, 255, 249, 235), Color.TRANSPARENT}
        );
        gloss.setCornerRadius(radius);

        if (!tintCards || hueColor == 0) {
            LayerDrawable layers = new LayerDrawable(new Drawable[]{base, frost, gloss});
            layers.setLayerInset(0, 0, 0, 0, 0);
            layers.setLayerInset(1, dp(1), dp(1), dp(1), 0);
            layers.setLayerInset(2, dp(1), dp(1), dp(1), 0);
            return layers;
        }

        GradientDrawable hue = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.argb(84, Color.red(hueColor), Color.green(hueColor), Color.blue(hueColor)),
                        Color.argb(20, Color.red(hueColor), Color.green(hueColor), Color.blue(hueColor)),
                        Color.argb(58, Color.red(hueColor), Color.green(hueColor), Color.blue(hueColor))
                }
        );
        hue.setCornerRadius(radius);

        LayerDrawable layers = new LayerDrawable(new Drawable[]{base, hue, frost, gloss});
        layers.setLayerInset(0, 0, 0, 0, 0);
        layers.setLayerInset(1, 0, 0, 0, 0);
        layers.setLayerInset(2, dp(1), dp(1), dp(1), 0);
        layers.setLayerInset(3, dp(1), dp(1), dp(1), 0);
        return layers;
    }

    private Drawable calendarCardDrawable(int radius, boolean today) {
        int accentColor = today ? ACCENT : Color.rgb(122, 146, 178);
        int strokeAlpha = today ? 92 : 54;
        int hueAlpha = today ? 64 : 34;

        GradientDrawable base = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.argb(today ? 218 : 194, 24, 22, 28),
                        Color.argb(today ? 204 : 178, 10, 10, 15),
                        Color.argb(today ? 214 : 188, 4, 4, 8)
                }
        );
        base.setCornerRadius(radius);
        base.setStroke(dp(1), Color.argb(
                strokeAlpha,
                Color.red(accentColor),
                Color.green(accentColor),
                Color.blue(accentColor)
        ));

        GradientDrawable discriminator = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{
                        Color.argb(hueAlpha, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)),
                        Color.argb(12, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)),
                        Color.TRANSPARENT
                }
        );
        discriminator.setCornerRadius(radius);

        GradientDrawable frost = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        Color.argb(today ? 56 : 42, 255, 252, 240),
                        Color.argb(today ? 16 : 10, 255, 252, 240),
                        Color.TRANSPARENT
                }
        );
        frost.setCornerRadius(radius);

        LayerDrawable layers = new LayerDrawable(new Drawable[]{base, discriminator, frost});
        layers.setLayerInset(0, 0, 0, 0, 0);
        layers.setLayerInset(1, 0, 0, 0, 0);
        layers.setLayerInset(2, dp(1), dp(1), dp(1), 0);
        return layers;
    }

    private Drawable glassDrawableWithHue(int color, int radius, int hueColor) {
        GradientDrawable hue = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.argb(38, Color.red(hueColor), Color.green(hueColor), Color.blue(hueColor)),
                        Color.TRANSPARENT,
                        Color.argb(24, Color.red(hueColor), Color.green(hueColor), Color.blue(hueColor))
                }
        );
        hue.setCornerRadius(radius);

        LayerDrawable layers = new LayerDrawable(new Drawable[]{hue, glassDrawable(color, radius)});
        layers.setLayerInset(0, 0, 0, 0, 0);
        layers.setLayerInset(1, 0, 0, 0, 0);
        return layers;
    }

    private Drawable glassDrawableWithImage(int color, int radius, Bitmap image, int hueColor) {
        BitmapDrawable imageDrawable = new BitmapDrawable(getResources(), image);
        imageDrawable.setGravity(Gravity.END | Gravity.TOP);
        imageDrawable.setAlpha(34);
        imageDrawable.setDither(true);

        GradientDrawable hue = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.argb(42, Color.red(hueColor), Color.green(hueColor), Color.blue(hueColor)),
                        Color.TRANSPARENT,
                        Color.argb(26, Color.red(hueColor), Color.green(hueColor), Color.blue(hueColor))
                }
        );
        hue.setCornerRadius(radius);

        GradientDrawable veil = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        Color.argb(88, 5, 5, 9),
                        Color.argb(48, 18, 16, 20),
                        Color.argb(116, 3, 3, 7)
                }
        );
        veil.setCornerRadius(radius);

        LayerDrawable layers = new LayerDrawable(new Drawable[]{
                imageDrawable,
                hue,
                veil,
                glassDrawable(color, radius)
        });
        layers.setLayerInset(0, dp(26), dp(14), dp(8), dp(120));
        layers.setLayerInset(1, 0, 0, 0, 0);
        layers.setLayerInset(2, 0, 0, 0, 0);
        layers.setLayerInset(3, 0, 0, 0, 0);
        return layers;
    }

    private TextView label(String text, int sp, int color, int style) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(color);
        label.setTextSize(sp);
        label.setTypeface(Typeface.DEFAULT, style);
        label.setIncludeFontPadding(true);
        return label;
    }

    private View createWallpaperShade() {
        View shade = new View(this);
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{SHADE_TOP, SHADE_MID, SHADE_BOTTOM}
        );
        shade.setBackground(drawable);
        return shade;
    }

    private List<ResolveInfo> loadLaunchableApps() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        PackageManager packageManager = getPackageManager();
        List<ResolveInfo> apps = new ArrayList<>(packageManager.queryIntentActivities(intent, 0));
        Collator collator = Collator.getInstance();
        apps.sort((left, right) -> collator.compare(
                left.loadLabel(packageManager).toString(),
                right.loadLabel(packageManager).toString()
        ));
        return apps;
    }

    private String resolveAppLabel(PackageManager packageManager, String packageName, ResolveInfo app) {
        if (app != null) {
            return app.loadLabel(packageManager).toString();
        }

        try {
            ApplicationInfo info = packageManager.getApplicationInfo(packageName, 0);
            return packageManager.getApplicationLabel(info).toString();
        } catch (PackageManager.NameNotFoundException ignored) {
            return packageName;
        }
    }

    private String notificationSummary(AppChapter chapter) {
        int count = chapter.notifications.size();
        if (count == 1) {
            return "1 active note";
        }
        return count + " active notes";
    }

    private boolean isNotificationAccessEnabled() {
        String enabledListeners = Settings.Secure.getString(
                getContentResolver(),
                "enabled_notification_listeners"
        );
        ComponentName componentName = new ComponentName(this, CalmNotificationListenerService.class);
        return enabledListeners != null
                && enabledListeners.toLowerCase(Locale.ROOT).contains(
                componentName.flattenToString().toLowerCase(Locale.ROOT)
        );
    }

    private boolean hasCalendarPermission() {
        return checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCalendarAccess() {
        requestPermissions(new String[]{Manifest.permission.READ_CALENDAR}, REQUEST_CALENDAR);
    }

    private void openSettingsChapter() {
        selectedPackageName = SETTINGS_KEY;
        if (currentPager != null) {
            int settingsPage = currentPager.getAdapter() == null ? -1 : currentPager.getAdapter().getItemCount() - 1;
            if (settingsPage >= 0) {
                currentPager.setCurrentItem(settingsPage, true);
                return;
            }
        }
        render();
    }

    private void openNotificationAccess() {
        startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
    }

    private void openWallpaperPicker() {
        Intent intent = new Intent(Intent.ACTION_SET_WALLPAPER);
        startActivity(Intent.createChooser(intent, "Set wallpaper"));
    }

    private List<ContextAction> notificationContextActions(
            CalmNotificationListenerService.CalmNotification notification,
            AppChapter chapter
    ) {
        List<ContextAction> actions = new ArrayList<>();
        actions.add(new ContextAction("Open", () -> openNotification(notification)));
        actions.add(new ContextAction("App", () -> openPackage(chapter)));
        actions.add(new ContextAction("Info", () -> openAppInfo(chapter.packageName)));
        actions.add(new ContextAction("Clear", () -> clearChapter(chapter)));
        actions.add(new ContextAction("Hide", () -> excludeNotificationSource(chapter)));
        actions.add(new ContextAction("Settings", this::openSettingsChapter));
        return actions;
    }

    private List<ContextAction> calendarContextActions(CalendarEvent event) {
        List<ContextAction> actions = new ArrayList<>();
        actions.add(new ContextAction("Open calendar", () -> openCalendarEvent(event)));
        actions.add(new ContextAction(
                hasCalendarPermission() ? "Calendar access" : "Allow calendar",
                this::requestCalendarAccess
        ));
        actions.add(new ContextAction("Settings", this::openSettingsChapter));
        return actions;
    }

    private void openPackage(AppChapter chapter) {
        if (!chapter.launchable) {
            Toast.makeText(this, "This notification source has no launcher entry", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = getPackageManager().getLaunchIntentForPackage(chapter.packageName);
        if (intent == null) {
            Toast.makeText(this, "This app cannot be opened directly", Toast.LENGTH_SHORT).show();
            return;
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void openCalendarEvent(CalendarEvent event) {
        Uri uri = CalendarContract.CONTENT_URI.buildUpon()
                .appendPath("time")
                .appendPath(Long.toString(event.begin))
                .build();
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        try {
            startActivity(intent);
        } catch (Exception ignored) {
            Toast.makeText(this, "Calendar cannot be opened", Toast.LENGTH_SHORT).show();
        }
    }

    private void openAppInfo(String packageName) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + packageName));
        startActivity(intent);
    }

    private void clearChapter(AppChapter chapter) {
        CalmNotificationListenerService.clearPackage(chapter.packageName);
        Toast.makeText(this, "Cleared " + chapter.label, Toast.LENGTH_SHORT).show();
    }

    private void openNotification(CalmNotificationListenerService.CalmNotification notification) {
        PendingIntent intent = notification.contentIntent;
        if (intent != null) {
            try {
                intent.send();
                return;
            } catch (PendingIntent.CanceledException ignored) {
                // Fall back to the source app below.
            }
        }

        Intent appIntent = getPackageManager().getLaunchIntentForPackage(notification.packageName);
        if (appIntent == null) {
            Toast.makeText(this, "This notification cannot be opened", Toast.LENGTH_SHORT).show();
            return;
        }

        appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(appIntent);
    }

    private Set<String> excludedPackages() {
        return new HashSet<>(preferences().getStringSet(PREF_EXCLUDED_PACKAGES, new HashSet<>()));
    }

    private List<ExcludedSource> excludedSources() {
        PackageManager packageManager = getPackageManager();
        List<ExcludedSource> sources = new ArrayList<>();
        for (String packageName : excludedPackages()) {
            sources.add(new ExcludedSource(packageName, resolveExcludedLabel(packageManager, packageName)));
        }

        Collator collator = Collator.getInstance();
        sources.sort((left, right) -> collator.compare(left.label, right.label));
        return sources;
    }

    private String resolveExcludedLabel(PackageManager packageManager, String packageName) {
        String savedLabel = preferences().getString(PREF_EXCLUDED_LABEL_PREFIX + packageName, null);
        if (savedLabel != null && !savedLabel.trim().isEmpty()) {
            return savedLabel;
        }

        try {
            ApplicationInfo info = packageManager.getApplicationInfo(packageName, 0);
            return packageManager.getApplicationLabel(info).toString();
        } catch (PackageManager.NameNotFoundException ignored) {
            return packageName;
        }
    }

    private void excludeNotificationSource(AppChapter chapter) {
        Set<String> packages = excludedPackages();
        packages.add(chapter.packageName);
        preferences().edit()
                .putStringSet(PREF_EXCLUDED_PACKAGES, packages)
                .putString(PREF_EXCLUDED_LABEL_PREFIX + chapter.packageName, chapter.label)
                .apply();
        selectedPackageName = OVERVIEW_KEY;
        Toast.makeText(this, "Excluded " + chapter.label, Toast.LENGTH_SHORT).show();
        render();
    }

    private void restoreNotificationSource(String packageName) {
        Set<String> packages = excludedPackages();
        packages.remove(packageName);
        preferences().edit()
                .putStringSet(PREF_EXCLUDED_PACKAGES, packages)
                .remove(PREF_EXCLUDED_LABEL_PREFIX + packageName)
                .apply();
        Toast.makeText(this, "Restored notification source", Toast.LENGTH_SHORT).show();
        render();
    }

    private boolean useTintedNotificationCards() {
        return preferences().getBoolean(PREF_TINT_NOTIFICATION_CARDS, false);
    }

    private void toggleNotificationSurface() {
        boolean nextValue = !useTintedNotificationCards();
        preferences().edit()
                .putBoolean(PREF_TINT_NOTIFICATION_CARDS, nextValue)
                .apply();
        Toast.makeText(
                this,
                nextValue ? "Notification cards are tinted" : "Chapter panels are tinted",
                Toast.LENGTH_SHORT
        ).show();
        render();
    }

    private boolean cardHapticsEnabled() {
        return preferences().getBoolean(PREF_CARD_HAPTICS_ENABLED, true);
    }

    private int cardHapticStrength() {
        return Math.max(1, Math.min(5, preferences().getInt(PREF_CARD_HAPTICS_STRENGTH, 2)));
    }

    private void toggleCardHaptics() {
        boolean nextValue = !cardHapticsEnabled();
        preferences().edit()
                .putBoolean(PREF_CARD_HAPTICS_ENABLED, nextValue)
                .apply();
        if (nextValue) {
            performCardScrollHaptic(getWindow().getDecorView());
        }
        Toast.makeText(
                this,
                nextValue ? "Card haptics on" : "Card haptics off",
                Toast.LENGTH_SHORT
        ).show();
        render();
    }

    private void performCardScrollHaptic(View source) {
        if (!cardHapticsEnabled()) {
            return;
        }

        int strength = cardHapticStrength();
        int amplitude = 12 + (strength * 8);
        long durationMs = 6L + strength;
        Vibrator vibrator = vibrator();
        if (vibrator == null || !vibrator.hasVibrator()) {
            source.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            return;
        }
        vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude));
    }

    private Vibrator vibrator() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager manager = (VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE);
            return manager == null ? null : manager.getDefaultVibrator();
        }
        return (Vibrator) getSystemService(VIBRATOR_SERVICE);
    }

    private SharedPreferences preferences() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
    }

    private FrameLayout.LayoutParams matchParentParams() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int statusBarHeight() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsets insets = getWindow().getDecorView().getRootWindowInsets();
            if (insets != null) {
                return insets.getInsets(WindowInsets.Type.statusBars()).top;
            }
        }
        return dp(28);
    }

    private String roman(int value) {
        String[] numerals = {
                "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X",
                "XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX"
        };
        if (value > 0 && value <= numerals.length) {
            return numerals[value - 1];
        }
        return String.valueOf(value);
    }

    private static final class AppChapter {
        final String packageName;
        final String label;
        final List<CalmNotificationListenerService.CalmNotification> notifications;
        final boolean launchable;
        final int hueColor;

        AppChapter(
                String packageName,
                String label,
                List<CalmNotificationListenerService.CalmNotification> notifications,
                boolean launchable,
                int hueColor
        ) {
            this.packageName = packageName;
            this.label = label;
            this.notifications = notifications;
            this.launchable = launchable;
            this.hueColor = hueColor;
        }
    }

    private static final class CalendarEvent {
        final String title;
        final long begin;
        final long end;
        final String location;
        final boolean allDay;

        CalendarEvent(String title, long begin, long end, String location, boolean allDay) {
            this.title = title;
            this.begin = begin;
            this.end = end;
            this.location = location;
            this.allDay = allDay;
        }
    }

    private static final class ExcludedSource {
        final String packageName;
        final String label;

        ExcludedSource(String packageName, String label) {
            this.packageName = packageName;
            this.label = label;
        }
    }

    private static final class ContextAction {
        final String label;
        final Runnable action;

        ContextAction(String label, Runnable action) {
            this.label = label;
            this.action = action;
        }
    }

    private static final class FocusDisplacement {
        final View view;
        final float alpha;
        final float translationY;

        FocusDisplacement(View view) {
            this.view = view;
            this.alpha = view.getAlpha();
            this.translationY = view.getTranslationY();
        }
    }

    private static final class ChapterPage {
        final String key;
        final String marker;
        final String title;
        final AppChapter chapter;

        private ChapterPage(String key, String marker, String title, AppChapter chapter) {
            this.key = key;
            this.marker = marker;
            this.title = title;
            this.chapter = chapter;
        }

        static ChapterPage overview() {
            return new ChapterPage(OVERVIEW_KEY, "I", "Overview", null);
        }

        static ChapterPage notifications(AppChapter chapter, String marker) {
            return new ChapterPage(chapter.packageName, marker, chapter.label, chapter);
        }

        static ChapterPage settings(String marker) {
            return new ChapterPage(SETTINGS_KEY, marker, "Settings", null);
        }
    }

    private final class ChapterPagerAdapter extends RecyclerView.Adapter<PageHolder> {
        private final List<ChapterPage> pages;

        ChapterPagerAdapter(List<ChapterPage> pages) {
            this.pages = pages;
        }

        @Override
        public PageHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            FrameLayout container = new FrameLayout(parent.getContext());
            container.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            return new PageHolder(container);
        }

        @Override
        public void onBindViewHolder(PageHolder holder, int position) {
            ChapterPage page = pages.get(position);
            holder.container.removeAllViews();
            View pageView;
            if (SETTINGS_KEY.equals(page.key)) {
                pageView = createSettingsPage();
            } else if (page.chapter == null) {
                pageView = createOverviewPage(buildNotificationChapters());
            } else {
                pageView = createChapterPage(page.chapter);
            }
            holder.container.addView(pageView, matchParentParams());
        }

        @Override
        public int getItemCount() {
            return pages.size();
        }
    }

    private static final class PageHolder extends RecyclerView.ViewHolder {
        final FrameLayout container;

        PageHolder(FrameLayout container) {
            super(container);
            this.container = container;
        }
    }
}
