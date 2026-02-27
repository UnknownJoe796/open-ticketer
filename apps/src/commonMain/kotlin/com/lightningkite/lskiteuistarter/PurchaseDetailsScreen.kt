// by Claude - Purchase details screen showing ticket info and redemption history
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

@Routable("/purchase/{purchaseId}")
class PurchaseDetailsPage(val purchaseId: Uuid) : Page {
    override val title: Reactive<String> get() = Constant("Purchase Details")

    override fun ViewWriter.render() {
        val purchase = Signal<Purchase?>(null)
        val redemptions = Signal<List<TicketRedemption>>(emptyList())
        val isLoading = Signal(true)
        val errorMessage = Signal<String?>(null)

        reactive {
            if (currentSession() == null)
                pageNavigator.reset(LandingPage())
        }

        // Load purchase and redemptions
        reactiveSuspending {
            val session = currentSession() ?: return@reactiveSuspending
            val api = session.api

            try {
                // Get purchase
                purchase.value = api.purchase.detail(purchaseId)

                // Get redemptions
                redemptions.value = api.ticketRedemption.query(
                    Query(condition { it.purchaseId.eq(purchaseId) })
                )
            } catch (e: Exception) {
                errorMessage.value = e.message ?: "Failed to load purchase"
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
                expanding.centered.h2("Purchase Details")
                space()
            }

            // Loading
            shownWhen { isLoading() }.centered.activityIndicator()

            // Error
            shownWhen { errorMessage() != null }.card.danger.col {
                reactive { text(errorMessage() ?: "") }
            }

            // Content
            shownWhen { !isLoading() && purchase() != null }.expanding.scrolling.col {
                reactive {
                    val p = purchase() ?: return@reactive
                    val r = redemptions()
                    val totalRedeemed = r.sumOf { it.quantityRedeemed }
                    val remaining = p.quantity - totalRedeemed

                    // Purchase info card
                    card.col {
                        h3(p.productName)

                        separator()

                        row {
                            bold.text("Customer:")
                            expanding.text(p.customerName ?: "N/A")
                        }
                        row {
                            bold.text("Email:")
                            expanding.text(p.customerEmail.toString())
                        }
                        row {
                            bold.text("Purchased:")
                            expanding.text(p.purchasedAt.toString())
                        }
                        row {
                            bold.text("Amount:")
                            expanding.text("$${p.amountTotal / 100.0} ${p.currency.uppercase()}")
                        }

                        separator()

                        row {
                            bold.text("Quantity:")
                            expanding.text(p.quantity.toString())
                        }
                        row {
                            bold.text("Redeemed:")
                            expanding.text(totalRedeemed.toString())
                        }
                        row {
                            bold.text("Remaining:")
                            if (remaining > 0) {
                                affirmative.text(remaining.toString())
                            } else {
                                danger.text("0")
                            }
                        }
                    }

                    // Redemption history
                    card.col {
                        h4("Check-in History")

                        if (r.isEmpty()) {
                            centered.text("No check-ins yet")
                        } else {
                            r.sortedByDescending { it.scannedAt }.forEach { redemption ->
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
                    }

                    // Check-in button (if remaining)
                    if (remaining > 0) {
                        separator()
                        important.buttonTheme.button {
                            centered.text("Check In")
                            onClick {
                                pageNavigator.navigate(
                                    CheckInPage(
                                        purchaseId = p._id,
                                        organizationId = p.organizationId,
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
}

@Routable("/checkin/{purchaseId}/{organizationId}/{quantity}")
class CheckInPage(
    val purchaseId: Uuid,
    val organizationId: Uuid,
    val quantity: Int = 1
) : Page {
    override val title: Reactive<String> get() = Constant("Check In")

    override fun ViewWriter.render() {
        val isLoading = Signal(false)
        val isSuccess = Signal(false)
        val errorMessage = Signal<String?>(null)
        val notes = Signal("")

        reactive {
            if (currentSession() == null)
                pageNavigator.reset(LandingPage())
        }

        col {
            // Header
            row {
                button {
                    icon({ Icon.arrowBack }, "Back")
                    onClick { pageNavigator.goBack() }
                }
                expanding.centered.h2("Confirm Check-In")
                space()
            }

            expanding.centered.col {
                shownWhen { !isSuccess() }.col {
                    card.col {
                        centered.h3("Check in $quantity ticket(s)?")

                        separator()

                        field("Notes (optional)") {
                            textArea {
                                hint = "Add any notes..."
                                content bind notes
                            }
                        }
                    }

                    separator()

                    shownWhen { errorMessage() != null }.card.danger.col {
                        reactive { text(errorMessage() ?: "") }
                    }

                    shownWhen { isLoading() }.centered.activityIndicator()

                    shownWhen { !isLoading() }.col {
                        important.buttonTheme.button {
                            centered.text("Confirm Check-In")
                            onClick {
                                val currentNotes = notes.value.takeIf { it.isNotBlank() }

                                isLoading.value = true
                                errorMessage.value = null

                                AppScope.launch {
                                    try {
                                        val session = currentSession() ?: throw Exception("Not logged in")
                                        val api = session.api
                                        val user = api.userAuth.getSelf()

                                        // Create redemption
                                        api.ticketRedemption.insert(
                                            TicketRedemption(
                                                purchaseId = purchaseId,
                                                quantityRedeemed = quantity,
                                                scannedByUserId = session.userId,
                                                scannedByName = user.name ?: "Unknown",
                                                notes = currentNotes
                                            )
                                        )

                                        isSuccess.value = true
                                    } catch (e: Exception) {
                                        errorMessage.value = e.message ?: "Failed to check in"
                                    } finally {
                                        isLoading.value = false
                                    }
                                }
                            }
                        }

                        button {
                            centered.text("Cancel")
                            onClick { pageNavigator.goBack() }
                        }
                    }
                }

                // Success state
                shownWhen { isSuccess() }.card.col {
                    affirmative.centered.h2("Check-In Complete!")
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
