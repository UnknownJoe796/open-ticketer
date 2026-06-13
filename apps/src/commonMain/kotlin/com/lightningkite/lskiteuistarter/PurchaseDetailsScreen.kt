// Purchase details screen showing ticket info and redemption history
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

@Routable("/purchase/{purchaseId}")
class PurchaseDetailsPage(val purchaseId: Uuid) : Page {
    override val title: Reactive<String> get() = Constant("Purchase Details")

    override fun ElementWriter.CanAddTheme.render() {
        reactive {
            if (currentSession() == null)
                pageNavigator.reset(LandingPage())
        }

        val purchase = remember { currentSessionNotNull().purchases[purchaseId]() }
        val eventName = remember {
            val p = purchase() ?: return@remember ""
            currentSessionNotNull().eventWithTickets[p.eventId]()?.name ?: "Unknown Event"
        }
        val redemptions = remember {
            currentSessionNotNull().ticketRedemptions.list(
                Query(condition { it.purchaseId.eq(purchaseId) })
            )()
        }
        val sortedRedemptions = remember { redemptions().sortedByDescending { it.scannedAt } }
        val totalRedeemed = remember { redemptions().sumOf { it.quantityRedeemed } }
        val remaining = remember { (purchase()?.quantity ?: 0) - totalRedeemed() }

        col {
            row {
                button {
                    icon({ Icon.arrowBack }, "Back")
                    onClick { pageNavigator.goBack() }
                }
                centered.expanding.h2("Purchase Details")
                space()
            }

            expanding.shownWhen { purchase() != null }.scrolling.col {
                // Purchase info
                card.col {
                    h3 { ::content { eventName() } }

                    separator()

                    row {
                        bold.text("Customer:")
                        expanding.text { ::content { purchase()?.customerName ?: "N/A" } }
                    }
                    row {
                        bold.text("Email:")
                        expanding.text { ::content { purchase()?.customerEmail?.toString() ?: "" } }
                    }
                    row {
                        bold.text("Purchased:")
                        expanding.text { ::content { purchase()?.purchasedAt?.toString() ?: "" } }
                    }
                    row {
                        bold.text("Amount:")
                        expanding.text {
                            ::content {
                                purchase()?.let { "$${it.amountTotal / 100.0} ${it.currency.uppercase()}" } ?: ""
                            }
                        }
                    }

                    separator()

                    row {
                        bold.text("Quantity:")
                        expanding.text { ::content { purchase()?.quantity?.toString() ?: "" } }
                    }
                    row {
                        bold.text("Redeemed:")
                        expanding.text { ::content { totalRedeemed().toString() } }
                    }
                    row {
                        bold.text("Remaining:")
                        shownWhen { remaining() > 0 }.affirmative.text { ::content { remaining().toString() } }
                        shownWhen { remaining() <= 0 }.danger.text("0")
                    }
                }

                // Redemption history
                card.col {
                    h4("Check-in History")

                    centered.shownWhen { redemptions().isEmpty() }.text("No check-ins yet")

                    forEach(sortedRedemptions) { redemption ->
                        separator()
                        row {
                            col {
                                bold.text("${redemption.quantityRedeemed}x checked in")
                                text("by ${redemption.scannedByName}")
                                if (!redemption.notes.isNullOrBlank()) {
                                    text("Note: ${redemption.notes}")
                                }
                            }
                            expanding.space()
                            text(redemption.scannedAt.toString())
                        }
                    }
                }

                // Check-in button (only when tickets remain)
                shownWhen { remaining() > 0 }.col {
                    separator()
                    important.button {
                        centered.text("Check In")
                        onClick {
                            val p = purchase.await() ?: return@onClick
                            pageNavigator.navigate(
                                CheckInPage(
                                    purchaseId = p._id,
                                    organizationId = p.organizationId,
                                    eventId = p.eventId,
                                    quantity = 1
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Routable("/checkin/{purchaseId}/{organizationId}/{eventId}/{quantity}")
class CheckInPage(
    val purchaseId: Uuid,
    val organizationId: Uuid,
    val eventId: String,
    val quantity: Int = 1
) : Page {
    override val title: Reactive<String> get() = Constant("Check In")

    override fun ElementWriter.CanAddTheme.render() {
        val isSuccess = Signal(false)
        val notes = Signal("")

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
                centered.expanding.h2("Confirm Check-In")
                space()
            }

            centered.expanding.col {
                shownWhen { !isSuccess() }.col {
                    card.col {
                        centered.h3("Check in $quantity ticket(s)?")

                        separator()

                        field("Notes (optional)") {
                            textArea {
                                hint = "Add any notes..."
                                keyboardHints = KeyboardHints.paragraph
                                content bind notes
                            }
                        }
                    }

                    separator()

                    important.button {
                        centered.text("Confirm Check-In")
                        action = Action("Confirm Check-In") {
                            val session = currentSession.await() ?: throw PlainTextException("Not logged in")
                            val user = session.api.userAuth.getSelf()

                            session.ticketRedemptions.add(
                                TicketRedemption(
                                    eventId = eventId,
                                    purchaseId = purchaseId,
                                    quantityRedeemed = quantity,
                                    scannedByUserId = session.userId,
                                    scannedByName = user.name ?: "Unknown",
                                    notes = notes.value.takeIf { it.isNotBlank() }
                                )
                            )

                            isSuccess.value = true
                        }
                    }

                    button {
                        centered.text("Cancel")
                        onClick { pageNavigator.goBack() }
                    }
                }

                shownWhen { isSuccess() }.card.col {
                    centered.affirmative.h2("Check-In Complete!")
                    centered.text("$quantity ticket(s) checked in successfully")

                    separator()

                    button {
                        centered.text("Scan Another")
                        onClick { pageNavigator.navigate(ScannerPage(organizationId)) }
                    }

                    button {
                        centered.text("View Purchase")
                        onClick { pageNavigator.navigate(PurchaseDetailsPage(purchaseId)) }
                    }
                }
            }
        }
    }
}
