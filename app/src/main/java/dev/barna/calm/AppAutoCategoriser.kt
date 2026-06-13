package dev.barna.calm

class AppAutoCategoriser {
    fun categorise(packageName: String): List<String> {
        return RULES.mapNotNull { (prefix, categoryId) ->
            categoryId.takeIf { packageName.startsWith(prefix) }
        }.distinct()
    }

    companion object {
        private val RULES: List<Pair<String, String>> = listOf(
            // Communications
            "com.whatsapp" to "communications",
            "org.telegram" to "communications",
            "com.facebook.orca" to "communications",
            "com.discord" to "communications",
            "com.snapchat" to "communications",
            "com.instagram" to "communications",
            "com.twitter" to "communications",
            "com.facebook.katana" to "communications",
            "com.linkedin" to "communications",
            "com.google.android.talk" to "communications",
            "com.google.android.apps.messaging" to "communications",
            "com.google.android.gm" to "communications",
            "com.microsoft.teams" to "communications",
            "com.slack" to "communications",
            "com.skype" to "communications",
            "com.viber" to "communications",
            "com.kakao" to "communications",
            "jp.naver.line" to "communications",
            "com.tencent.mm" to "communications",
            "com.tencent.mobileqq" to "communications",
            // Finance
            "com.paypal" to "finance",
            "com.revolut" to "finance",
            "com.monzo" to "finance",
            "com.starlingbank" to "finance",
            "com.n26" to "finance",
            "com.nubank" to "finance",
            "com.wise" to "finance",
            "com.coinbase" to "finance",
            "com.binance" to "finance",
            "com.kraken" to "finance",
            // Shopping
            "com.amazon" to "shopping",
            "com.ebay" to "shopping",
            "com.etsy" to "shopping",
            "com.shopify" to "shopping",
            "com.alibaba" to "shopping",
            // Media
            "com.spotify" to "media",
            "com.google.android.youtube" to "media",
            "com.netflix" to "media",
            "com.amazon.avod" to "media",
            "com.disney" to "media",
            "tv.twitch" to "media",
            "com.soundcloud" to "media",
            "com.apple.android.music" to "media",
            "com.tidal" to "media",
            "com.deezer" to "media",
            "com.vimeo" to "media",
            "com.plex" to "media",
            "com.emby" to "media",
            "com.kodi" to "media",
            // Productivity
            "com.google.android.apps.docs" to "productivity",
            "com.google.android.apps.sheets" to "productivity",
            "com.google.android.apps.slides" to "productivity",
            "com.google.android.calendar" to "productivity",
            "com.google.android.keep" to "productivity",
            "com.microsoft.office" to "productivity",
            "com.microsoft.word" to "productivity",
            "com.microsoft.excel" to "productivity",
            "com.microsoft.powerpoint" to "productivity",
            "com.microsoft.onenote" to "productivity",
            "com.microsoft.outlook" to "productivity",
            "com.notion" to "productivity",
            "com.todoist" to "productivity",
            "com.ticktick" to "productivity",
            "com.evernote" to "productivity",
            "org.mozilla.firefox" to "productivity",
            "com.android.chrome" to "productivity",
            "com.opera" to "productivity",
            "com.brave" to "productivity",
            "org.chromium" to "productivity",
            // Games
            "com.supercell" to "games",
            "com.king" to "games",
            "com.rovio" to "games",
            "com.miniclip" to "games",
            "com.ea.games" to "games",
            "com.activision" to "games",
            "com.roblox" to "games",
            "com.mojang" to "games",
            // System
            "com.android.settings" to "system",
            "com.google.android.gms" to "system",
            "com.google.android.gsf" to "system",
            "com.android.vending" to "system",
            // Customisation
            "dev.barna.calm" to "customisation",
        )
    }
}
