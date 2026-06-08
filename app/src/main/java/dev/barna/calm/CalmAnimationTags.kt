package dev.barna.calm

object CalmAnimationTags {
    const val PAGE_PANEL = "calm_page_panel"
    const val CARD_STACK = "calm_card_stack"
    const val CARD = "calm_card"
    const val CHROME = "calm_chrome"

    // Integer key for View.setTag(key, value) — stores the translationY that style() last
    // assigned to this card. Used by the entry animator to recover the true styled position
    // even when styledY >= exitOffset (where the sign-based heuristic breaks down).
    const val STYLED_TRANSLATION_Y_TAG_KEY = 0x7B4D3A9E
}
