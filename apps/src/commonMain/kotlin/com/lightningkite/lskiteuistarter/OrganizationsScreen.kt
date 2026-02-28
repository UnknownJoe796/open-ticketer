// by Claude - Organization management screens
package com.lightningkite.lskiteuistarter

import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.pageNavigator
import com.lightningkite.kiteui.reactive.*
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.l2.field
import com.lightningkite.lskiteuistarter.data.AddMemberInput
import com.lightningkite.lskiteuistarter.data.SetStripeKeyInput
import com.lightningkite.lskiteuistarter.sdk.currentSession
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.context.reactiveSuspending
import com.lightningkite.reactive.core.*
import com.lightningkite.services.database.*
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

@Routable("/organizations")
class OrganizationsPage : Page {
    override val title: Reactive<String> get() = Constant("Organizations")

    override fun ViewWriter.render() {
        val organizations = Signal<List<Organization>>(emptyList())
        val memberships = Signal<List<OrganizationMembership>>(emptyList())
        val isLoading = Signal(true)

        reactive {
            if (currentSession() == null)
                pageNavigator.reset(LandingPage())
        }

        // Load organizations - use reactiveSuspending to access reactive values
        reactiveSuspending {
            val session = currentSession() ?: return@reactiveSuspending
            val api = session.api
            val userId = session.userId

            try {
                // Get user's memberships
                memberships.value = api.organizationMembership.query(
                    Query(condition { it.userId.eq(userId) })
                )

                // Get all organizations
                organizations.value = api.organization.query(Query())
            } catch (e: Exception) {
                // Handle error
            } finally {
                isLoading.value = false
            }
        }

        col {
            // Header
            row {
                button {
                    icon({ Icon.arrowBack }, "Back")
                    onClick { pageNavigator.navigate(HomePage()) }
                }
                expanding.centered.h2("Organizations")
                button {
                    icon({ Icon.add }, "Create")
                    onClick { pageNavigator.navigate(CreateOrganizationPage()) }
                }
            }

            // Loading
            shownWhen { isLoading() }.centered.activityIndicator()

            // Organization list
            shownWhen { !isLoading() }.expanding.scrolling.col {
                reactive {
                    val orgs = organizations()
                    val mems = memberships()

                    if (orgs.isEmpty()) {
                        centered.col {
                            text("No organizations yet")
                            button {
                                text("Create Organization")
                                onClick { pageNavigator.navigate(CreateOrganizationPage()) }
                            }
                        }
                    } else {
                        orgs.forEach { org ->
                            val membership = mems.find { it.organizationId == org._id }
                            val isAdmin = membership?.role == OrgRole.Admin

                            card.col {
                                row {
                                    expanding.col {
                                        bold.text(org.name)
                                        if (membership != null) {
                                            text("Role: ${membership.role.name}")
                                        }
                                    }
                                    if (!org.active) {
                                        danger.text("Inactive")
                                    }
                                }

                                separator()

                                // Action buttons
                                row {
                                    if (membership != null) {
                                        important.buttonTheme.button {
                                            text("Scan Tickets")
                                            onClick { pageNavigator.navigate(ScannerPage(org._id)) }
                                        }
                                    }

                                    if (isAdmin) {
                                        button {
                                            text("Manage")
                                            onClick { pageNavigator.navigate(OrganizationDetailsPage(org._id)) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Routable("/organization/{organizationId}")
class OrganizationDetailsPage(val organizationId: Uuid) : Page {
    override val title: Reactive<String> get() = Constant("Organization Details")

    override fun ViewWriter.render() {
        val organization = Signal<Organization?>(null)
        val members = Signal<List<OrganizationMembership>>(emptyList())
        val users = Signal<Map<Uuid, User>>(emptyMap())
        val isLoading = Signal(true)

        reactive {
            if (currentSession() == null)
                pageNavigator.reset(LandingPage())
        }

        // Load data
        reactiveSuspending {
            val session = currentSession() ?: return@reactiveSuspending
            val api = session.api

            try {
                organization.value = api.organization.detail(organizationId)

                val mems = api.organizationMembership.query(
                    Query(condition { it.organizationId.eq(organizationId) })
                )
                members.value = mems

                // Load user info for members
                val userMap = mutableMapOf<Uuid, User>()
                mems.forEach { m ->
                    try {
                        userMap[m.userId] = api.user.detail(m.userId)
                    } catch (_: Exception) {}
                }
                users.value = userMap
            } catch (e: Exception) {
                // Handle error
            } finally {
                isLoading.value = false
            }
        }

        col {
            // Header
            row {
                button {
                    icon({ Icon.arrowBack }, "Back")
                    onClick { pageNavigator.goBack() }
                }
                expanding.centered.h2("Organization Details")
                space()
            }

            shownWhen { isLoading() }.centered.activityIndicator()

            shownWhen { !isLoading() }.expanding.scrolling.col {
                // Quick actions
                card.col {
                    h4("Quick Actions")
                    row {
                        important.buttonTheme.button {
                            text("Scan Tickets")
                            onClick { pageNavigator.navigate(ScannerPage(organizationId)) }
                        }
                        button {
                            text("Manage Events")
                            onClick { pageNavigator.navigate(EventsListPage(organizationId)) }
                        }
                        button {
                            text("Configure Stripe")
                            onClick { pageNavigator.navigate(StripeConfigPage(organizationId)) }
                        }
                    }
                }

                // Members section - by Claude
                card.col {
                    row {
                        expanding.h4("Members")
                        button {
                            icon({ Icon.add }, "Add Member")
                            onClick { pageNavigator.navigate(AddMemberPage(organizationId)) }
                        }
                    }

                    // by Claude - Members list with swapView for empty state
                    swapView {
                        swapping(current = { if (members().isEmpty()) "empty" else "list" }) { mode ->
                            when (mode) {
                                "empty" -> text("No members")
                                "list" -> col {
                                    reactive {
                                        clearChildren()
                                        val memberList = members()
                                        val userMap = users()
                                        memberList.forEach { member ->
                                            separator()
                                            row {
                                                expanding.col {
                                                    text(userMap[member.userId]?.name ?: userMap[member.userId]?.email?.toString() ?: "Unknown")
                                                    text("Role: ${member.role.name}")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Routable("/organization/create")
class CreateOrganizationPage : Page {
    override val title: Reactive<String> get() = Constant("Create Organization")

    override fun ViewWriter.render() {
        val name = Signal("")
        val isLoading = Signal(false)
        val errorMessage = Signal<String?>(null)

        reactive {
            if (currentSession() == null)
                pageNavigator.reset(LandingPage())
        }

        col {
            row {
                button {
                    icon({ Icon.arrowBack }, "Back")
                    onClick { pageNavigator.goBack() }
                }
                expanding.centered.h2("Create Organization")
                space()
            }

            expanding.centered.sizedBox(SizeConstraints(maxWidth = 30.rem)).col {
                card.col {
                    field("Organization Name") {
                        textInput {
                            hint = "Enter organization name"
                            content bind name
                        }
                    }
                }

                shownWhen { errorMessage() != null }.danger.col {
                    reactive { text(errorMessage() ?: "") }
                }

                shownWhen { isLoading() }.centered.activityIndicator()

                shownWhen { !isLoading() }.important.buttonTheme.button {
                    centered.text("Create Organization")
                    onClick {
                        val currentName = name.value
                        if (currentName.isBlank()) return@onClick

                        isLoading.value = true
                        errorMessage.value = null

                        AppScope.launch {
                            try {
                                val session = currentSession() ?: throw Exception("Not logged in")
                                val api = session.api
                                val userId = session.userId

                                // Create organization
                                val org = api.organization.insert(
                                    Organization(name = currentName)
                                )

                                // Add creator as admin
                                api.organizationMembership.insert(
                                    OrganizationMembership(
                                        organizationId = org._id,
                                        userId = userId,
                                        role = OrgRole.Admin
                                    )
                                )

                                pageNavigator.navigate(OrganizationDetailsPage(org._id))
                            } catch (e: Exception) {
                                errorMessage.value = e.message ?: "Failed to create organization"
                            } finally {
                                isLoading.value = false
                            }
                        }
                    }
                }
            }
        }
    }
}

@Routable("/organization/{organizationId}/add-member")
class AddMemberPage(val organizationId: Uuid) : Page {
    override val title: Reactive<String> get() = Constant("Add Member")

    override fun ViewWriter.render() {
        val email = Signal("")
        val role = Signal(OrgRole.Scanner)
        val isLoading = Signal(false)
        val errorMessage = Signal<String?>(null)

        reactive {
            if (currentSession() == null)
                pageNavigator.reset(LandingPage())
        }

        col {
            row {
                button {
                    icon({ Icon.arrowBack }, "Back")
                    onClick { pageNavigator.goBack() }
                }
                expanding.centered.h2("Add Member")
                space()
            }

            expanding.centered.sizedBox(SizeConstraints(maxWidth = 30.rem)).col {
                card.col {
                    field("User Email") {
                        textInput {
                            hint = "user@example.com"
                            content bind email
                        }
                    }

                    field("Role") {
                        select {
                            bind(role, OrgRole.entries.toList().let(::Constant)) { it.name }
                        }
                    }
                }

                shownWhen { errorMessage() != null }.danger.col {
                    reactive { text(errorMessage() ?: "") }
                }

                shownWhen { isLoading() }.centered.activityIndicator()

                shownWhen { !isLoading() }.important.buttonTheme.button {
                    centered.text("Add Member")
                    onClick {
                        val currentEmail = email.value
                        val currentRole = role.value
                        if (currentEmail.isBlank()) return@onClick

                        isLoading.value = true
                        errorMessage.value = null

                        AppScope.launch {
                            try {
                                val session = currentSession() ?: throw Exception("Not logged in")
                                val api = session.api

                                api.organizationMembership.addMemberToOrganization(
                                    AddMemberInput(
                                        organizationId = organizationId,
                                        userEmail = currentEmail,
                                        role = currentRole
                                    )
                                )

                                pageNavigator.goBack()
                            } catch (e: Exception) {
                                errorMessage.value = e.message ?: "Failed to add member"
                            } finally {
                                isLoading.value = false
                            }
                        }
                    }
                }
            }
        }
    }
}

@Routable("/organization/{organizationId}/stripe")
class StripeConfigPage(val organizationId: Uuid) : Page {
    override val title: Reactive<String> get() = Constant("Stripe Configuration")

    override fun ViewWriter.render() {
        val apiKey = Signal("")
        val webhookSecret = Signal("")
        val isLoading = Signal(false)
        val showSuccess = Signal(false)
        val errorMessage = Signal<String?>(null)

        reactive {
            if (currentSession() == null)
                pageNavigator.reset(LandingPage())
        }

        col {
            row {
                button {
                    icon({ Icon.arrowBack }, "Back")
                    onClick { pageNavigator.goBack() }
                }
                expanding.centered.h2("Stripe Configuration")
                space()
            }

            expanding.centered.sizedBox(SizeConstraints(maxWidth = 30.rem)).scrolling.col {
                card.col {
                    h4("API Credentials")
                    text("Enter your Stripe API credentials. The API key will be encrypted before storage.")

                    separator()

                    field("Stripe API Key") {
                        textInput {
                            hint = "sk_live_..."
                            content bind apiKey
                        }
                    }

                    field("Webhook Secret") {
                        textInput {
                            hint = "whsec_..."
                            content bind webhookSecret
                        }
                    }
                }

                shownWhen { errorMessage() != null }.danger.col {
                    reactive { text(errorMessage() ?: "") }
                }
                shownWhen { showSuccess() }.affirmative.text("Configuration saved successfully!")

                shownWhen { isLoading() }.centered.activityIndicator()

                shownWhen { !isLoading() }.important.buttonTheme.button {
                    centered.text("Save Configuration")
                    onClick {
                        val currentApiKey = apiKey.value
                        val currentWebhookSecret = webhookSecret.value
                        if (currentApiKey.isBlank() || currentWebhookSecret.isBlank()) return@onClick

                        isLoading.value = true
                        errorMessage.value = null
                        showSuccess.value = false

                        AppScope.launch {
                            try {
                                val session = currentSession() ?: throw Exception("Not logged in")
                                val api = session.api

                                api.stripeConfig.setStripeAPIKey(
                                    SetStripeKeyInput(
                                        organizationId = organizationId,
                                        apiKey = currentApiKey,
                                        webhookSecret = currentWebhookSecret
                                    )
                                )

                                showSuccess.value = true
                                apiKey.value = "" // Clear sensitive data
                            } catch (e: Exception) {
                                errorMessage.value = e.message ?: "Failed to save configuration"
                            } finally {
                                isLoading.value = false
                            }
                        }
                    }
                }

                // Help text
                card.col {
                    h4("Setup Instructions")
                    text("1. Log in to your Stripe dashboard")
                    text("2. Go to Developers > API keys")
                    text("3. Copy your Secret key (starts with sk_)")
                    text("4. Go to Developers > Webhooks")
                    text("5. Create an endpoint pointing to your server")
                    text("6. Copy the webhook signing secret (starts with whsec_)")
                }
            }
        }
    }
}
