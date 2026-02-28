// by Claude - Updated to use authComponent() DSL (AuthComponent2)
package com.lightningkite.lskiteuistarter

import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.auth.authComponent
import com.lightningkite.kiteui.models.SizeConstraints
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.pageNavigator
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.lightningserver.auth.AuthEndpoints
import com.lightningkite.lskiteuistarter.sdk.*
import com.lightningkite.reactive.core.*

@Routable("/login")
class LoginPage : Page, UseFullPage {
    override val title: Reactive<String> get() = Constant("Login")

    override fun ViewWriter.render() {
        val api = selectedApi.value.api

        frame {
            centered.sizedBox(SizeConstraints(maxWidth = 40.rem)).scrolling.col {
                centered.h4("Open Ticketer")
                centered.text("Sign in to get started")

                authComponent(
                    endpoints = AuthEndpoints(
                        subjects = mapOf("User" to api.userAuth),
                        emailProof = api.userAuth.email,
                        oneTimePasswordProof = api.userAuth.totp,
                        backupCodeProof = api.userAuth.backupCode,
                        passwordProof = api.userAuth.password,
                    ),
                ) { token ->
                    sessionToken set token
                    pageNavigator.reset(HomePage())
                }
            }
        }
    }
}
