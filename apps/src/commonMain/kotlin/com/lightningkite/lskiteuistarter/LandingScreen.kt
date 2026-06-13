package com.lightningkite.lskiteuistarter

import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.pageNavigator
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.lskiteuistarter.sdk.currentSession
import com.lightningkite.reactive.context.await
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.Reactive
import kotlinx.coroutines.launch

@Routable("/")
class LandingPage : Page, UseFullPage {
    override val title: Reactive<String> get() = Constant("Home")
    override fun ElementWriter.CanAddTheme.render() {
        launch {
            if (currentSession.await() != null) {
                pageNavigator.reset(HomePage())
            } else {
                pageNavigator.reset(LoginPage())
            }
        }
        // KUI 8: alignment modifiers aren't available at the page root (CanAddTheme); wrap in a frame.
        frame { centered.activityIndicator() }
    }
}