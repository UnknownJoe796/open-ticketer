// by Claude - Stripe webhook endpoint to handle checkout session completion
package com.lightningkite.lskiteuistarter.data

import com.lightningkite.lightningserver.definition.builder.ServerBuilder
import com.lightningkite.lightningserver.http.*
import com.lightningkite.lightningserver.plainText
import com.lightningkite.lightningserver.runtime.ServerRuntime
import com.lightningkite.lskiteuistarter.*
import com.lightningkite.services.database.*
import com.lightningkite.toEmailAddress
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import com.stripe.model.Event
import com.stripe.model.checkout.Session
import com.stripe.net.Webhook

// by Claude - uses raw HttpHandler because Stripe webhooks need raw body for HMAC signature verification
object StripeWebhookEndpoint : ServerBuilder() {

    val webhook = path.post bind HttpHandler { request ->
        val bodyText = request.body?.text()
            ?: return@HttpHandler HttpResponse.plainText("Missing body", HttpStatus.BadRequest)
        val signature = request.headers["Stripe-Signature"]?.toHttpString()
            ?: return@HttpHandler HttpResponse.plainText("Missing Stripe-Signature header", HttpStatus.BadRequest)

        try {
            // Try all configured webhook secrets to find the matching one
            val configs = Server.database().collection<StripeConfig>().find(Condition.Always).toList()

            var event: Event? = null
            var matchedConfig: StripeConfig? = null

            for (config in configs) {
                try {
                    event = Webhook.constructEvent(bodyText, signature, config.webhookSecret)
                    matchedConfig = config
                    break
                } catch (e: Exception) {
                    continue
                }
            }

            if (event == null || matchedConfig == null) {
                return@HttpHandler HttpResponse.plainText(
                    "Invalid signature for all configured webhook secrets",
                    HttpStatus.Unauthorized
                )
            }

            when (event.type) {
                "checkout.session.completed" -> handleCheckoutSessionCompleted(event, matchedConfig)
                else -> println("Unhandled event type: ${event.type}")
            }

            HttpResponse.plainText("Webhook received")

        } catch (e: Exception) {
            println("Webhook error: ${e.message}")
            e.printStackTrace()
            HttpResponse.plainText(
                "Webhook processing failed: ${e.message}",
                HttpStatus.InternalServerError
            )
        }
    }

    context(_: ServerRuntime)
    private suspend fun handleCheckoutSessionCompleted(event: Event, config: StripeConfig) {
        val session = event.dataObjectDeserializer.`object`.get() as? Session
            ?: throw IllegalArgumentException("Invalid session object")

        val sessionId = session.getId()

        val existing = Server.database().collection<Purchase>()
            .find(condition { it.stripeCheckoutSessionId eq sessionId }).firstOrNull()

        if (existing != null) {
            println("Purchase already exists for session $sessionId")
            return
        }

        val lineItems = session.listLineItems()?.getData()
        // by Claude - sum quantity across ALL line items (bin-packed orders may have multiple)
        val quantity = lineItems?.sumOf { it.getQuantity()?.toInt() ?: 0 } ?: 1
        val eventId = lineItems?.firstOrNull()?.getPrice()?.getProduct() ?: "unknown"

        // by Claude - auto-upsert EventWithTickets if one doesn't exist for this product
        val eventName = lineItems?.firstOrNull()?.getDescription() ?: "Unknown Event"
        val existingEvent = Server.database().collection<EventWithTickets>().get(eventId)
        if (existingEvent == null) {
            Server.database().collection<EventWithTickets>().insertOne(
                EventWithTickets(
                    _id = eventId,
                    organizationId = config.organizationId,
                    name = eventName,
                )
            )
        }

        // Create purchase record (triggers postCreate signal to send email)
        val purchase = Purchase(
            stripeCheckoutSessionId = sessionId,
            organizationId = config.organizationId,
            eventId = eventId,
            quantity = quantity,
            customerEmail = session.getCustomerDetails()?.getEmail()?.toEmailAddress()
                ?: throw IllegalArgumentException("No customer email in session"),
            customerName = session.getCustomerDetails()?.getName(),
            amountTotal = session.getAmountTotal() ?: 0L,
            currency = session.getCurrency() ?: "usd",
            purchasedAt = kotlin.time.Instant.fromEpochSeconds(session.getCreated()),
            emailSent = false
        )

        Server.database().collection<Purchase>().insertOne(purchase)
        println("Created purchase ${purchase._id} for session $sessionId")
    }
}
