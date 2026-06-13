// Organization management screens
package com.lightningkite.lskiteuistarter

import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.exceptions.PlainTextException
import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.pageNavigator
import com.lightningkite.kiteui.reactive.*
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.l2.field
import com.lightningkite.kiteui.views.l2.toast
import com.lightningkite.lskiteuistarter.data.AddMemberInput
import com.lightningkite.lskiteuistarter.data.SetStripeKeyInput
import com.lightningkite.lskiteuistarter.sdk.currentSession
import com.lightningkite.lskiteuistarter.sdk.currentSessionNotNull
import com.lightningkite.reactive.context.await
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.*
import com.lightningkite.services.database.*
import kotlin.uuid.Uuid

@Routable("/organizations")
class OrganizationsPage : Page {
    override val title: Reactive<String> get() = Constant("Organizations")

    override fun ElementWriter.CanAddTheme.render() {
        reactive {
            if (currentSession() == null)
                pageNavigator.reset(LandingPage())
        }

        val memberships = remember {
            val s = currentSessionNotNull()
            s.organizationMemberships.list(Query(condition { it.userId.eq(s.userId) }))()
        }
        // Server-side permissions restrict this to organizations the user may read.
        val organizations = remember {
            currentSessionNotNull().organizations.list(Query())()
        }
        // Pair each organization with the user's membership (if any) in one reactive pass.
        val orgRows = remember {
            val mems = memberships()
            organizations().map { org -> org to mems.find { it.organizationId == org._id } }
        }

        col {
            row {
                button {
                    icon({ Icon.arrowBack }, "Back")
                    onClick { pageNavigator.navigate(HomePage()) }
                }
                centered.expanding.h2("Organizations")
                button {
                    icon({ Icon.add }, "Create")
                    onClick { pageNavigator.navigate(CreateOrganizationPage()) }
                }
            }

            expanding.scrolling.col {
                centered.shownWhen { organizations().isEmpty() }.col {
                    text("No organizations yet")
                    button {
                        text("Create Organization")
                        onClick { pageNavigator.navigate(CreateOrganizationPage()) }
                    }
                }

                forEach(orgRows) { (org, membership) ->
                    card.col {
                        row {
                            expanding.col {
                                bold.text(org.name)
                                if (membership != null) text("Role: ${membership.role.name}")
                            }
                            if (!org.active) danger.text("Inactive")
                        }

                        separator()

                        row {
                            if (membership != null) {
                                important.button {
                                    text("Scan Tickets")
                                    onClick { pageNavigator.navigate(ScannerPage(org._id)) }
                                }
                            }
                            if (membership?.role == OrgRole.Admin) {
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

@Routable("/organization/{organizationId}")
class OrganizationDetailsPage(val organizationId: Uuid) : Page {
    override val title: Reactive<String> get() = Constant("Organization Details")

    override fun ElementWriter.CanAddTheme.render() {
        reactive {
            if (currentSession() == null)
                pageNavigator.reset(LandingPage())
        }

        val members = remember {
            currentSessionNotNull().organizationMemberships.list(
                Query(condition { it.organizationId.eq(organizationId) })
            )()
        }
        // Look up each member's user via the cache (requests are batched into one query).
        val memberRows = remember {
            val s = currentSessionNotNull()
            members().map { m -> m to s.users[m.userId]() }
        }

        col {
            row {
                button {
                    icon({ Icon.arrowBack }, "Back")
                    onClick { pageNavigator.goBack() }
                }
                centered.expanding.h2("Organization Details")
                space()
            }

            expanding.scrolling.col {
                card.col {
                    h4("Quick Actions")
                    row {
                        important.button {
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

                card.col {
                    row {
                        expanding.h4("Members")
                        button {
                            icon({ Icon.add }, "Add Member")
                            onClick { pageNavigator.navigate(AddMemberPage(organizationId)) }
                        }
                    }

                    shownWhen { members().isEmpty() }.text("No members")

                    forEach(memberRows) { (member, user) ->
                        separator()
                        row {
                            expanding.col {
                                text(user?.name ?: user?.email?.toString() ?: "Unknown")
                                text("Role: ${member.role.name}")
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

    override fun ElementWriter.CanAddTheme.render() {
        val name = Signal("")

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
                centered.expanding.h2("Create Organization")
                space()
            }

            centered.expanding.sizedBox(SizeConstraints(maxWidth = 30.rem)).col {
                card.col {
                    field("Organization Name") {
                        textInput {
                            hint = "Enter organization name"
                            keyboardHints = KeyboardHints.title
                            content bind name
                        }
                    }
                }

                important.button {
                    centered.text("Create Organization")
                    action = Action("Create Organization") {
                        val orgName = name.value.trim()
                        if (orgName.isBlank()) throw PlainTextException("Please enter an organization name.")

                        val session = currentSession.await() ?: throw PlainTextException("Not logged in")

                        val org = session.organizations.add(Organization(name = orgName))
                        session.organizationMemberships.add(
                            OrganizationMembership(
                                organizationId = org._id,
                                userId = session.userId,
                                role = OrgRole.Admin
                            )
                        )

                        pageNavigator.navigate(OrganizationDetailsPage(org._id))
                    }
                }
            }
        }
    }
}

@Routable("/organization/{organizationId}/add-member")
class AddMemberPage(val organizationId: Uuid) : Page {
    override val title: Reactive<String> get() = Constant("Add Member")

    override fun ElementWriter.CanAddTheme.render() {
        val email = Signal("")
        val role = Signal(OrgRole.Scanner)

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
                centered.expanding.h2("Add Member")
                space()
            }

            centered.expanding.sizedBox(SizeConstraints(maxWidth = 30.rem)).col {
                card.col {
                    field("User Email") {
                        textInput {
                            hint = "user@example.com"
                            keyboardHints = KeyboardHints.email
                            content bind email
                        }
                    }

                    field("Role") {
                        select {
                            bind(role, Constant(OrgRole.entries.toList())) { it.name }
                        }
                    }
                }

                important.button {
                    centered.text("Add Member")
                    action = Action("Add Member") {
                        val userEmail = email.value.trim()
                        if (userEmail.isBlank()) throw PlainTextException("Please enter a user email.")

                        val session = currentSession.await() ?: throw PlainTextException("Not logged in")
                        // Custom endpoint (not CRUD), so call the API directly.
                        session.api.organizationMembership.addMemberToOrganization(
                            AddMemberInput(
                                organizationId = organizationId,
                                userEmail = userEmail,
                                role = role.value
                            )
                        )
                        pageNavigator.goBack()
                    }
                }
            }
        }
    }
}

@Routable("/organization/{organizationId}/stripe")
class StripeConfigPage(val organizationId: Uuid) : Page {
    override val title: Reactive<String> get() = Constant("Stripe Configuration")

    override fun ElementWriter.CanAddTheme.render() {
        val apiKey = Signal("")
        val webhookSecret = Signal("")

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
                centered.expanding.h2("Stripe Configuration")
                space()
            }

            centered.expanding.sizedBox(SizeConstraints(maxWidth = 30.rem)).scrolling.col {
                card.col {
                    h4("API Credentials")
                    text("Enter your Stripe API credentials. The API key will be encrypted before storage.")

                    separator()

                    field("Stripe API Key") {
                        textInput {
                            hint = "sk_live_..."
                            keyboardHints = KeyboardHints.password
                            content bind apiKey
                        }
                    }

                    field("Webhook Secret") {
                        textInput {
                            hint = "whsec_..."
                            keyboardHints = KeyboardHints.password
                            content bind webhookSecret
                        }
                    }
                }

                important.button {
                    centered.text("Save Configuration")
                    action = Action("Save Configuration") {
                        val key = apiKey.value.trim()
                        val secret = webhookSecret.value.trim()
                        if (key.isBlank() || secret.isBlank())
                            throw PlainTextException("Please enter both the API key and webhook secret.")

                        val session = currentSession.await() ?: throw PlainTextException("Not logged in")
                        session.api.stripeConfig.setStripeAPIKey(
                            SetStripeKeyInput(
                                organizationId = organizationId,
                                apiKey = key,
                                webhookSecret = secret
                            )
                        )

                        apiKey.value = "" // Clear sensitive data
                        webhookSecret.value = ""
                        toast("Configuration saved successfully")
                    }
                }

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
