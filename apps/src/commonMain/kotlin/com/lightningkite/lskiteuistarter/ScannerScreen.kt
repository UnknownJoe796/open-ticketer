// Ticket scanner screen with camera QR code scanning
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
import com.lightningkite.reactive.context.await
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.*
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

@Routable("/scanner/{organizationId}")
class ScannerPage(val organizationId: Uuid) : Page {
    override val title: Reactive<String> get() = Constant("Scan Tickets")

    override fun ElementWriter.CanAddTheme.render() {
        val scanResult = Signal<VerifyQRResult?>(null)
        val eventName = Signal("") // looked-up event name
        val qrInput = Signal("")
        val errorMessage = Signal<String?>(null)
        val isLoading = Signal(false)
        val lastScannedValue = Signal<String?>(null)
        val manualMode = Signal(false)

        reactive {
            if (currentSession() == null)
                pageNavigator.reset(LandingPage())
        }

        // Shared verification logic. Invoked from the (non-suspending) camera callback and the
        // manual button, so it manages its own loading/error state rather than using an Action.
        fun verifyQR(qrData: String) {
            if (qrData.isBlank()) return
            if (isLoading.value) return

            isLoading.value = true
            errorMessage.value = null

            AppScope.launch {
                try {
                    val session = currentSession.await() ?: throw Exception("Not logged in")
                    val result = session.api.ticketScannerEndpoint
                        .verifyQRCode(VerifyQRInput(qrData = qrData))
                    scanResult.value = result
                    val purchase = result.purchase
                    if (purchase != null) {
                        eventName.value = try {
                            session.api.eventWithTickets.detail(purchase.eventId).name
                        } catch (_: Exception) {
                            "Unknown Event"
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
            row {
                button {
                    icon({ Icon.arrowBack }, "Back")
                    onClick { pageNavigator.goBack() }
                }
                centered.expanding.h2("Scan Ticket")
                space()
            }

            // Scanner section - camera or manual input
            shownWhen { scanResult() == null }.col {

                shownWhen { !manualMode() }.col {
                    lateinit var preview: com.lightningkite.kiteui.camera.CameraPreview
                    sizeConstraints(height = 20.rem).frame {
                        preview = cameraPreview {
                            onBarcode(setOf(BarcodeFormat.QR_CODE)) { results ->
                                val rawValue = results.firstOrNull()?.rawValue ?: return@onBarcode
                                if (isLoading.value) return@onBarcode
                                if (rawValue == lastScannedValue.value) return@onBarcode
                                lastScannedValue.value = rawValue
                                verifyQR(rawValue)
                            }
                        }
                    }

                    shownWhen { !preview.hasPermissions() }.card.col {
                        text("Camera access is needed to scan QR codes. Please allow camera permissions when prompted.")
                    }

                    button {
                        centered.text("Enter manually")
                        onClick { manualMode.value = true }
                    }
                }

                shownWhen { manualMode() }.col {
                    card.col {
                        h4("Enter QR Code Data")
                        text("Paste the QR code data below.")

                        separator()

                        field("QR Code Data") {
                            textArea {
                                hint = "Paste QR code content here..."
                                keyboardHints = KeyboardHints.id
                                content bind qrInput
                            }
                        }
                    }

                    shownWhen { !isLoading() }.important.button {
                        centered.text("Verify Ticket")
                        onClick { verifyQR(qrInput.value) }
                    }

                    button {
                        centered.text("Use camera")
                        onClick { manualMode.value = false }
                    }
                }

                shownWhen { errorMessage() != null }.card.danger.col {
                    text { ::content { errorMessage() ?: "" } }
                }

                centered.shownWhen { isLoading() }.activityIndicator()
            }

            // Scan result display
            expanding.shownWhen { scanResult() != null }.scrolling.col {
                val valid = remember {
                    val r = scanResult()
                    r != null && r.valid && r.purchase != null
                }
                val previousRedemptions = remember { scanResult()?.redemptions ?: emptyList() }

                shownWhen { valid() }.col {
                    card.col {
                        centered.affirmative.h3("Valid Ticket")

                        separator()

                        row {
                            bold.text("Event:")
                            expanding.text { ::content { eventName() } }
                        }
                        row {
                            bold.text("Customer:")
                            expanding.text {
                                ::content {
                                    scanResult()?.purchase?.let { it.customerName ?: it.customerEmail.toString() } ?: ""
                                }
                            }
                        }
                        row {
                            bold.text("Quantity:")
                            expanding.text { ::content { scanResult()?.purchase?.quantity?.toString() ?: "" } }
                        }
                        row {
                            bold.text("Remaining:")
                            shownWhen { (scanResult()?.remainingQuantity ?: 0) > 0 }
                                .affirmative.text { ::content { scanResult()?.remainingQuantity?.toString() ?: "" } }
                            shownWhen { (scanResult()?.remainingQuantity ?: 0) <= 0 }
                                .danger.text("0 - All redeemed")
                        }

                        shownWhen { previousRedemptions().isNotEmpty() }.col {
                            separator()
                            bold.text("Previous Check-ins:")
                            forEach(previousRedemptions) { redemption ->
                                row {
                                    text("${redemption.quantityRedeemed}x by ${redemption.scannedByName}")
                                    expanding.space()
                                    text(redemption.scannedAt.toString())
                                }
                            }
                        }
                    }

                    separator()

                    shownWhen { (scanResult()?.remainingQuantity ?: 0) > 0 }.important.button {
                        centered.text("Check In (1)")
                        onClick {
                            val purchase = scanResult.await()?.purchase ?: return@onClick
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

                    button {
                        centered.text("View Details")
                        onClick {
                            val purchase = scanResult.await()?.purchase ?: return@onClick
                            pageNavigator.navigate(PurchaseDetailsPage(purchase._id))
                        }
                    }
                }

                shownWhen { !valid() }.card.col {
                    centered.danger.h3("Invalid Ticket")
                    centered.text { ::content { scanResult()?.message ?: "" } }
                }

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
