// by Claude - Ticket scanner screen with camera QR code scanning
package com.lightningkite.lskiteuistarter

import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.camera.BarcodeFormat
import com.lightningkite.kiteui.camera.cameraPreview
import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.pageNavigator
import com.lightningkite.kiteui.reactive.*
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.l2.field
import com.lightningkite.lskiteuistarter.data.VerifyQRInput
import com.lightningkite.lskiteuistarter.data.VerifyQRResult
import com.lightningkite.lskiteuistarter.sdk.currentSession
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.*
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

@Routable("/scanner/{organizationId}")
class ScannerPage(val organizationId: Uuid) : Page {
    override val title: Reactive<String> get() = Constant("Scan Tickets")

    override fun ViewWriter.render() {
        val scanResult = Signal<VerifyQRResult?>(null)
        val eventName = Signal("") // by Claude - looked-up event name
        val qrInput = Signal("")
        val errorMessage = Signal<String?>(null)
        val isLoading = Signal(false)
        val lastScannedValue = Signal<String?>(null)
        val manualMode = Signal(false)
        val hasPermission = Signal(false)

        reactive {
            if (currentSession() == null)
                pageNavigator.reset(LandingPage())
        }

        // by Claude - shared verification logic
        fun verifyQR(qrData: String) {
            if (qrData.isBlank()) return
            if (isLoading.value) return

            isLoading.value = true
            errorMessage.value = null

            AppScope.launch {
                try {
                    val session = currentSession() ?: throw Exception("Not logged in")
                    val result = session.api.ticketScannerEndpoint
                        .verifyQRCode(VerifyQRInput(qrData = qrData))
                    scanResult.value = result
                    // by Claude - look up event name for display
                    val purchase = result.purchase
                    if (purchase != null) {
                        try {
                            eventName.value = session.api.eventWithTickets.detail(purchase.eventId).name
                        } catch (_: Exception) {
                            eventName.value = "Unknown Event"
                        }
                    }
                } catch (e: Exception) {
                    errorMessage.value = e.message ?: "Failed to verify ticket"
                } finally {
                    isLoading.value = false
                }
            }
        }

        col {
            // Header
            row {
                button {
                    icon({ Icon.arrowBack }, "Back")
                    onClick { pageNavigator.goBack() }
                }
                expanding.centered.h2("Scan Ticket")
                space()
            }

            // Scanner section - camera or manual input
            shownWhen { scanResult() == null }.col {

                // Camera mode - by Claude
                shownWhen { !manualMode() }.col {
                    sizeConstraints(height = 20.rem).frame {
                        val preview = cameraPreview {
                            onBarcode(setOf(BarcodeFormat.QR_CODE)) { results ->
                                val firstResult = results.firstOrNull() ?: return@onBarcode
                                val rawValue = firstResult.rawValue
                                if (isLoading.value) return@onBarcode
                                if (rawValue == lastScannedValue.value) return@onBarcode
                                lastScannedValue.value = rawValue
                                verifyQR(rawValue)
                            }
                        }
                        reactive {
                            hasPermission.value = preview.hasPermissions()
                        }
                    }

                    // Permission prompt
                    shownWhen { !hasPermission() }.card.col {
                        text("Camera access is needed to scan QR codes. Please allow camera permissions when prompted.")
                    }

                    // Manual fallback toggle
                    button {
                        centered.text("Enter manually")
                        onClick { manualMode.value = true }
                    }
                }

                // Manual mode - by Claude
                shownWhen { manualMode() }.col {
                    card.col {
                        h4("Enter QR Code Data")
                        text("Paste the QR code data below.")

                        separator()

                        field("QR Code Data") {
                            textArea {
                                hint = "Paste QR code content here..."
                                content bind qrInput
                            }
                        }
                    }

                    shownWhen { !isLoading() }.important.buttonTheme.button {
                        centered.text("Verify Ticket")
                        onClick { verifyQR(qrInput.value) }
                    }

                    button {
                        centered.text("Use camera")
                        onClick { manualMode.value = false }
                    }
                }

                // Error message - by Claude
                shownWhen { errorMessage() != null }.card.danger.col {
                    reactive { text(errorMessage() ?: "") }
                }

                // Loading indicator
                shownWhen { isLoading() }.centered.activityIndicator()
            }

            // Scan result display
            shownWhen { scanResult() != null }.expanding.scrolling.col {
                reactive {
                    val result = scanResult() ?: return@reactive
                    val purchase = result.purchase

                    if (result.valid && purchase != null) {
                        // Valid ticket
                        card.col {
                            affirmative.centered.h3("Valid Ticket")

                            separator()

                            // by Claude - use looked-up event name
                            row {
                                bold.text("Event:")
                                expanding.text(eventName())
                            }
                            row {
                                bold.text("Customer:")
                                expanding.text(purchase.customerName ?: purchase.customerEmail.toString())
                            }
                            row {
                                bold.text("Quantity:")
                                expanding.text(purchase.quantity.toString())
                            }
                            row {
                                bold.text("Remaining:")
                                if (result.remainingQuantity > 0) {
                                    affirmative.text(result.remainingQuantity.toString())
                                } else {
                                    danger.text("0 - All redeemed")
                                }
                            }

                            if (result.redemptions.isNotEmpty()) {
                                separator()
                                bold.text("Previous Check-ins:")
                                result.redemptions.forEach { redemption ->
                                    row {
                                        text("${redemption.quantityRedeemed}x by ${redemption.scannedByName}")
                                        expanding.space()
                                        text(redemption.scannedAt.toString())
                                    }
                                }
                            }
                        }

                        // Action buttons
                        separator()

                        // by Claude - pass eventId to CheckInPage
                        if (result.remainingQuantity > 0) {
                            important.buttonTheme.button {
                                centered.text("Check In (1)")
                                onClick {
                                    pageNavigator.navigate(
                                        CheckInPage(
                                            purchaseId = purchase._id,
                                            organizationId = organizationId,
                                            eventId = purchase.eventId,
                                            quantity = 1
                                        )
                                    )
                                }
                            }
                        }

                        button {
                            centered.text("View Details")
                            onClick {
                                pageNavigator.navigate(PurchaseDetailsPage(purchase._id))
                            }
                        }
                    } else {
                        // Invalid ticket
                        card.col {
                            danger.centered.h3("Invalid Ticket")
                            centered.text(result.message)
                        }
                    }

                    // Scan another button - by Claude
                    separator()
                    button {
                        centered.text("Scan Another")
                        onClick {
                            scanResult.value = null
                            qrInput.value = ""
                            lastScannedValue.value = null
                            errorMessage.value = null
                        }
                    }
                }
            }
        }
    }
}
