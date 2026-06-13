// Event management screens for listing and creating events
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
import com.lightningkite.lskiteuistarter.sdk.currentSession
import com.lightningkite.lskiteuistarter.sdk.currentSessionNotNull
import com.lightningkite.reactive.context.await
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.*
import com.lightningkite.services.database.*
import kotlin.uuid.Uuid

// List events for an organization
@Routable("/organization/{organizationId}/events")
class EventsListPage(val organizationId: Uuid) : Page {
    override val title: Reactive<String> get() = Constant("Events")

    override fun ElementWriter.CanAddTheme.render() {
        reactive {
            if (currentSession() == null)
                pageNavigator.reset(LandingPage())
        }

        // ModelCache query: cached, reactive, and shows loading/error states automatically.
        val events = remember {
            currentSessionNotNull().eventWithTickets.list(
                Query(condition { it.organizationId.eq(organizationId) })
            )()
        }

        col {
            row {
                button {
                    icon({ Icon.arrowBack }, "Back")
                    onClick { pageNavigator.goBack() }
                }
                centered.expanding.h2("Events")
                button {
                    icon({ Icon.add }, "Create")
                    onClick { pageNavigator.navigate(CreateEventPage(organizationId)) }
                }
            }

            expanding.scrolling.col {
                centered.shownWhen { events().isEmpty() }.col {
                    text("No events yet")
                    button {
                        text("Create Event")
                        onClick { pageNavigator.navigate(CreateEventPage(organizationId)) }
                    }
                }

                forEach(events) { event ->
                    card.col {
                        bold.text(event.name)
                        subtext("Product ID: ${event._id}")
                        separator()
                        text("Ticket limit: ${if (event.ticketLimit == Int.MAX_VALUE) "Unlimited" else event.ticketLimit.toString()}")
                    }
                }
            }
        }
    }
}

// Create a new event linked to a Stripe product
@Routable("/organization/{organizationId}/events/create")
class CreateEventPage(val organizationId: Uuid) : Page {
    override val title: Reactive<String> get() = Constant("Create Event")

    override fun ElementWriter.CanAddTheme.render() {
        val stripeProductId = Signal("")
        val name = Signal("")
        val ticketLimit = Signal("")

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
                centered.expanding.h2("Create Event")
                space()
            }

            centered.expanding.sizedBox(SizeConstraints(maxWidth = 30.rem)).scrolling.col {
                card.col {
                    field("Stripe Product ID") {
                        textInput {
                            hint = "prod_..."
                            keyboardHints = KeyboardHints.id
                            content bind stripeProductId
                        }
                    }

                    field("Event Name") {
                        textInput {
                            hint = "My Event"
                            keyboardHints = KeyboardHints.title
                            content bind name
                        }
                    }

                    field("Ticket Limit (leave blank for unlimited)") {
                        textInput {
                            hint = "e.g. 500"
                            keyboardHints = KeyboardHints.integer
                            content bind ticketLimit
                        }
                    }
                }

                important.button {
                    centered.text("Create Event")
                    // Action handles loading state and surfaces errors automatically.
                    action = Action("Create Event") {
                        val productId = stripeProductId.value.trim()
                        val eventName = name.value.trim()
                        if (productId.isBlank() || eventName.isBlank())
                            throw PlainTextException("Please enter both a Stripe product ID and an event name.")

                        val limit = ticketLimit.value.trim().toIntOrNull() ?: Int.MAX_VALUE
                        val session = currentSession.await() ?: throw PlainTextException("Not logged in")

                        session.eventWithTickets.add(
                            EventWithTickets(
                                _id = productId,
                                organizationId = organizationId,
                                name = eventName,
                                ticketLimit = limit,
                            )
                        )
                        pageNavigator.goBack()
                    }
                }

                // Help text
                card.col {
                    h4("How it works")
                    text("1. Create a product in your Stripe dashboard")
                    text("2. Add prices to the product (set metadata key 'ticket_count' for bundle prices)")
                    text("3. Copy the product ID (starts with prod_) and paste it above")
                    text("4. Share the checkout URL: /buy?event={productId}&quantity=N")
                }
            }
        }
    }
}
