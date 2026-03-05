// by Claude - Admin endpoint to send sample emails for style verification
package com.lightningkite.lskiteuistarter.data

import com.lightningkite.lightningserver.auth.fetch
import com.lightningkite.lightningserver.auth.require
import com.lightningkite.lightningserver.definition.builder.ServerBuilder
import com.lightningkite.lightningserver.http.post
import com.lightningkite.lightningserver.typed.ApiHttpHandler
import com.lightningkite.lightningserver.typed.auth
import com.lightningkite.lskiteuistarter.*
import com.lightningkite.lskiteuistarter.UserAuth.RoleCache.userRole
import com.lightningkite.lskiteuistarter.utils.generateQRData
import com.lightningkite.lskiteuistarter.utils.generateQRImage
import com.lightningkite.services.email.Email
import com.lightningkite.services.email.EmailAddressWithName
import kotlinx.html.html
import kotlinx.html.stream.createHTML
import kotlinx.serialization.Serializable
import java.util.Base64
import kotlin.uuid.Uuid

@Serializable
data class SampleEmailResult(val message: String)

// by Claude
object SampleEmailEndpoints : ServerBuilder() {

    val sendSampleTicketEmail = path.path("ticket").post bind ApiHttpHandler(
        summary = "Send Sample Ticket Email",
        description = "Sends a sample ticket email to the authenticated admin's email address for style verification.",
        auth = UserAuth.require(),
        implementation = { _: Unit ->
            if (auth.userRole() < UserRole.Admin) {
                throw IllegalAccessException("Only admins can send sample emails")
            }
            val user = auth.fetch()

            // Generate a dummy QR code for preview purposes
            val dummyQrData = generateQRData(Uuid.random())
            val qrImage = generateQRImage(dummyQrData)
            val base64QR = Base64.getEncoder().encodeToString(qrImage)

            Server.email().send(Email(
                subject = "Your Ticket - Sample Event",
                to = listOf(EmailAddressWithName(user.email, user.name)),
                html = createHTML(true).html {
                    emailBase {
                        header("Your Ticket")
                        paragraph("Thank you for your purchase!")
                        paragraph("Event: Sample Event")
                        paragraph("Quantity: 2")
                        qrImage(base64QR)
                        paragraph("Present this QR code at the event.")
                        paragraph("Order total: ${'$'}25.00 USD")
                    }
                }
            ))

            SampleEmailResult("Sample ticket email sent to ${user.email}")
        }
    )

    val sendSampleLoginEmail = path.path("login").post bind ApiHttpHandler(
        summary = "Send Sample Login Email",
        description = "Sends a sample login PIN email to the authenticated admin's email address for style verification.",
        auth = UserAuth.require(),
        implementation = { _: Unit ->
            if (auth.userRole() < UserRole.Admin) {
                throw IllegalAccessException("Only admins can send sample emails")
            }
            val user = auth.fetch()

            Server.email().send(Email(
                subject = "Log In Code",
                to = listOf(EmailAddressWithName(user.email, user.name)),
                html = createHTML(true).html {
                    emailBase {
                        header("Log In Code")
                        paragraph(
                            buildString {
                                appendLine("Hi ${user.name},")
                                append("Your log in code is:")
                            }
                        )
                        code("123456")
                        paragraph("If you did not request this code, you can safely ignore this email.")
                    }
                }
            ))

            SampleEmailResult("Sample login email sent to ${user.email}")
        }
    )
}
