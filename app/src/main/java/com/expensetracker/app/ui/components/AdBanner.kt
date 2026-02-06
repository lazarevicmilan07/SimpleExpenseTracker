package com.expensetracker.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.expensetracker.app.BuildConfig
import com.expensetracker.app.data.preferences.PreferencesManager
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

@Composable
fun AdBanner(
    preferencesManager: PreferencesManager,
    modifier: Modifier = Modifier
) {
    val isPremium by preferencesManager.isPremium.collectAsState(initial = false)

    // TODO: Change back to `if (!isPremium)` before release
    if (true) {
        AndroidView(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            factory = { context ->
                AdView(context).apply {
                    setAdSize(AdSize.BANNER)
                    adUnitId = BuildConfig.ADMOB_BANNER_ID
                    loadAd(AdRequest.Builder().build())
                }
            }
        )
    }
}
