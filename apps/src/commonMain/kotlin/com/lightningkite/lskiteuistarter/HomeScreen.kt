package com.lightningkite.lskiteuistarter

import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.pageNavigator
import com.lightningkite.kiteui.reactive.Action
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.lskiteuistarter.sdk.currentSession
import com.lightningkite.lskiteuistarter.sdk.sessionToken
import com.lightningkite.reactive.context.await
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.Reactive

@Routable("/dashboard")
class HomePage : Page {
    override val title: Reactive<String> get() = Constant("Home")
    override fun ElementWriter.CanAddTheme.render() {

        reactive {
            if (currentSession() == null)
                pageNavigator.reset(LandingPage())
        }

        col {
            centered.h2("Welcome to your home page")

            // by Claude - Quick actions for Open Ticketer
            card.col {
                h3("Open Ticketer")
                text("Manage ticket sales and check-ins")
                separator()
                important.button {
                    centered.text("Organizations")
                    onClick { pageNavigator.navigate(OrganizationsPage()) }
                }
            }

            expanding.space()

            important.button {
                centered.text("Test Notifications")
                ::enabled { fcmToken() != null }
                action = Action("Test Notifications") {
                    val token = fcmToken.value ?: return@Action
                    currentSession.await()?.api?.fcmToken?.testInAppNotifications(token)
                }
            }

            button {
                centered.text("Logout")
                action = Action("Logout") {
                    // Best-effort server logout; local session is cleared regardless of connectivity.
                    try {
                        currentSession.await()?.api?.userAuth?.terminateSession()
                    } catch (_: Exception) {
                    }
                    sessionToken set null
                    pageNavigator.reset(LoginPage())
                }
            }
        }
    }
}