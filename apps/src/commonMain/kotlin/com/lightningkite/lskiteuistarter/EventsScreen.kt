// by Claude - Event management screens for listing and creating events
package com.lightningkite.lskiteuistarter

import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.pageNavigator
import com.lightningkite.kiteui.reactive.*
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.l2.field
import com.lightningkite.lskiteuistarter.sdk.currentSession
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.context.reactiveSuspending
import com.lightningkite.reactive.core.*
import com.lightningkite.services.database.*
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

// by Claude - list events for an organization
@Routable("/organization/{organizationId}/events")
class EventsListPage(val organizationId: Uuid) : Page {
    override val title: Reactive<String> get() = Constant("Events")

    override fun ViewWriter.render() {
        val events = Signal<List<EventWithTickets>>(emptyList())
        val isLoading = Signal(true)
        val errorMessage = Signal<String?>(null)

        reactive {
            if (currentSession() == null)
                pageNavigator.reset(LandingPage())
        }

        reactiveSuspending {
            val session = currentSession() ?: return@reactiveSuspending
            try {
                events.value = session.api.eventWithTickets.query(
                    Query(condition { it.organizationId.eq(organizationId) })
                )
            } catch (e: Exception) {
                errorMessage.value = e.message ?: "Failed to load events"
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
                expanding.centered.h2("Events")
                button {
                    icon({ Icon.add }, "Create")
                    onClick { pageNavigator.navigate(CreateEventPage(organizationId)) }
                }
            }

            shownWhen { isLoading() }.centered.activityIndicator()

            shownWhen { errorMessage() != null }.card.danger.col {
                reactive { text(errorMessage() ?: "") }
            }

            shownWhen { !isLoading() }.expanding.scrolling.col {
                reactive {
                    val eventList = events()
                    if (eventList.isEmpty()) {
                        centered.col {
                            text("No events yet")
                            button {
                                text("Create Event")
                                onClick { pageNavigator.navigate(CreateEventPage(organizationId)) }
                            }
                        }
                    } else {
                        eventList.forEach { event ->
                            card.col {
                                row {
                                    expanding.col {
                                        bold.text(event.name)
                                        text("Product ID: ${event._id}")
                                    }
                                }
                                separator()
                                row {
                                    text("Ticket limit: ${if (event.ticketLimit == Int.MAX_VALUE) "Unlimited" else event.ticketLimit.toString()}")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// by Claude - create a new event linked to a Stripe product
@Routable("/organization/{organizationId}/events/create")
class CreateEventPage(val organizationId: Uuid) : Page {
    override val title: Reactive<String> get() = Constant("Create Event")

    override fun ViewWriter.render() {
        val stripeProductId = Signal("")
        val name = Signal("")
        val ticketLimit = Signal("")
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
                expanding.centered.h2("Create Event")
                space()
            }

            expanding.centered.sizedBox(SizeConstraints(maxWidth = 30.rem)).scrolling.col {
                card.col {
                    field("Stripe Product ID") {
                        textInput {
                            hint = "prod_..."
                            content bind stripeProductId
                        }
                    }

                    field("Event Name") {
                        textInput {
                            hint = "My Event"
                            content bind name
                        }
                    }

                    field("Ticket Limit (leave blank for unlimited)") {
                        textInput {
                            hint = "e.g. 500"
                            content bind ticketLimit
                        }
                    }
                }

                shownWhen { errorMessage() != null }.card.danger.col {
                    reactive { text(errorMessage() ?: "") }
                }

                shownWhen { isLoading() }.centered.activityIndicator()

                shownWhen { !isLoading() }.important.buttonTheme.button {
                    centered.text("Create Event")
                    onClick {
                        val currentProductId = stripeProductId.value.trim()
                        val currentName = name.value.trim()
                        if (currentProductId.isBlank() || currentName.isBlank()) return@onClick

                        val limit = ticketLimit.value.trim().toIntOrNull() ?: Int.MAX_VALUE

                        isLoading.value = true
                        errorMessage.value = null

                        AppScope.launch {
                            try {
                                val session = currentSession() ?: throw Exception("Not logged in")
                                session.api.eventWithTickets.insert(
                                    EventWithTickets(
                                        _id = currentProductId,
                                        organizationId = organizationId,
                                        name = currentName,
                                        ticketLimit = limit,
                                    )
                                )
                                pageNavigator.goBack()
                            } catch (e: Exception) {
                                errorMessage.value = e.message ?: "Failed to create event"
                            } finally {
                                isLoading.value = false
                            }
                        }
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
