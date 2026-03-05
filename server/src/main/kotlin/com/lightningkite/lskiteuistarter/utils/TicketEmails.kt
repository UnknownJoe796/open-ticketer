// by Claude - Email utilities for sending tickets with QR codes
package com.lightningkite.lskiteuistarter.utils

import com.lightningkite.lightningserver.runtime.ServerRuntime
import com.lightningkite.lskiteuistarter.*
import com.lightningkite.services.database.*
import com.lightningkite.services.email.Email
import com.lightningkite.services.email.EmailAddressWithName
import kotlinx.html.*
import java.util.Base64

/**
 * Generates a signed QR code for a purchase and sends it via email.
 * Updates the purchase record to mark the email as sent and store the QR data.
 */
context(runtime: ServerRuntime)
suspend fun generateAndSendTicket(purchase: Purchase) {
    if (purchase.emailSent) return

    // by Claude - look up event name from EventWithTickets
    val event = Server.database().collection<EventWithTickets>().get(purchase.eventId)
    val eventName = event?.name ?: "Unknown Event"

    // Generate QR code
    val qrData = generateQRData(purchase)
    val qrImage = generateQRImage(qrData)
    val base64QR = Base64.getEncoder().encodeToString(qrImage)

    // Send email
    Server.email().send(Email(
        subject = "Your Ticket - $eventName",
        to = listOf(EmailAddressWithName(purchase.customerEmail, purchase.customerName)),
        html = kotlinx.html.stream.createHTML(true).html {
            emailBase {
                this.header("Your Ticket")
                this.paragraph("Thank you for your purchase!")
                this.paragraph("Event: $eventName")
                this.paragraph("Quantity: ${purchase.quantity}")
                this.qrImage(base64QR)
                this.paragraph("Present this QR code at the event.")
                this.paragraph("Order total: $${purchase.amountTotal / 100.0} ${purchase.currency.uppercase()}")
            }
        }
    ))

    // Mark as sent and store QR data
    Server.database().collection<Purchase>().replaceOne(
        condition { it._id eq purchase._id },
        purchase.copy(emailSent = true, qrCodeData = qrData)
    )
}
