package com.lightningkite.lskiteuistarter.utils

import com.lightningkite.MediaType
import com.lightningkite.lightningserver.files.fileObject
import com.lightningkite.lightningserver.runtime.ServerRuntime
import com.lightningkite.lskiteuistarter.*
import com.lightningkite.services.data.TypedData
import com.lightningkite.services.database.*
import com.lightningkite.services.email.Email
import com.lightningkite.services.email.EmailAddressWithName
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlinx.datetime.format.optional
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import org.joda.time.format.DateTimeFormat

private const val qrFilename = "ticket-qr.png"
private const val eventFilename = "event.png"

/**
 * Generates a signed QR code for a purchase and sends it via email.
 * Updates the purchase record to mark the email as sent and store the QR data.
 */
context(runtime: ServerRuntime)
suspend fun generateAndSendTicket(purchase: Purchase) {
    if (purchase.emailSent) return

    val event = Server.database().collection<EventWithTickets>().get(purchase.eventId)
    val eventName = event?.name ?: "Unknown Event"

    // Generate QR code
    val qrData = generateQRData(purchase)
    val qrImage = generateQRImage(qrData)

    Server.email().send(Email(
        subject = "Your Ticket - $eventName",
        to = listOf(EmailAddressWithName(purchase.customerEmail, purchase.customerName)),
        html = {
            ticketEmailHtml(event, purchase)
        },
        attachments = listOfNotNull(
            Email.Attachment(
                inline = true,
                filename = qrFilename,
                typedData = TypedData.bytes(qrImage, MediaType.Image.PNG)
            ),
            event?.image?.fileObject?.get()?.let {
                Email.Attachment(
                    inline = true,
                    filename = eventFilename,
                    typedData = it
                )
            }
        )
    ))

    // Mark as sent and store QR data
    Server.database().collection<Purchase>().replaceOne(
        condition { it._id eq purchase._id },
        purchase.copy(emailSent = true, qrCodeData = qrData)
    )
}

fun HTML.ticketEmailHtml(
    event: EventWithTickets?,
    purchase: Purchase
) {
    emailBase {
        // Personalized greeting
        greeting(
            purchase.customerName?.ifBlank { null } ?: "Guest",
            "You've got tickets!"
        )

        // Hero event image
        event?.image?.let {
            heroImage("cid:$eventFilename", event.name)
        }

        // Event name
        header(event?.name ?: "Your Event")

        // Event description
        event?.description?.let { paragraph(it) }

        divider()

        // Structured event details
        subheader("Event Details")
        event?.dateTime?.let { detailRow("Date", it.format(customFormat)) }
        event?.location?.let { detailRow("Location", it.toString()) }
        detailRow("Quantity", "${purchase.quantity}")
        detailRow("Total", "$${purchase.amountTotal / 100.0} ${purchase.currency.uppercase()}")

        divider()

        // QR code section
        subheader("Your Ticket")
        note("Present this QR code at the door for entry.")
        inlineImage("cid:$qrFilename", "Ticket QR Code", "250px", "250px")
    }
}


val customFormat = LocalDateTime.Format {
    date(LocalDate.Format {
        monthName(MonthNames.ENGLISH_FULL)
        char(' ')
        day(Padding.NONE)
        chars(", ")
        year(Padding.NONE)
    })
    chars(" at ")
    amPmHour(Padding.NONE)
    char(':')
    minute()
    char(' ')
    amPmMarker("AM", "PM")
}
