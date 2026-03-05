// by Claude - Stripe Checkout bridge endpoint: takes an event ID and quantity,
// fetches prices from Stripe, uses greedy bin-packing to optimize cost, and redirects to Stripe Checkout.
package com.lightningkite.lskiteuistarter.data

import com.lightningkite.lightningserver.definition.builder.ServerBuilder
import com.lightningkite.lightningserver.definition.generalSettings
import com.lightningkite.lightningserver.definition.secretBasis
import com.lightningkite.lightningserver.encryption.cipher
import com.lightningkite.lightningserver.http.*
import com.lightningkite.lightningserver.plainText
import com.lightningkite.lightningserver.html
import com.lightningkite.lightningserver.runtime.ServerRuntime
import com.lightningkite.lskiteuistarter.*
import com.lightningkite.services.database.*
import com.stripe.Stripe
import com.stripe.model.PaymentIntent
import com.stripe.model.Price
import com.stripe.model.checkout.Session
import com.stripe.param.PriceListParams
import com.stripe.param.checkout.SessionCreateParams
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.html.*

// by Claude - public-facing redirect endpoint (no auth)
object CheckoutBridgeEndpoint : ServerBuilder() {

    // by Claude - GET /buy?event={eventId}&quantity=5[&success=/path&cancel=/path]
    val buy = path.get bind HttpHandler { request ->
        // 1. Parse required parameters
        val eventId = request.queryParameters["event"]
            ?: return@HttpHandler HttpResponse.plainText("Missing 'event' query parameter", HttpStatus.BadRequest)
        val quantityStr = request.queryParameters["quantity"]
            ?: return@HttpHandler HttpResponse.plainText("Missing 'quantity' query parameter", HttpStatus.BadRequest)
        val quantity = quantityStr.toIntOrNull()
        if (quantity == null || quantity <= 0) {
            return@HttpHandler HttpResponse.plainText(
                "Invalid 'quantity': must be a positive integer",
                HttpStatus.BadRequest
            )
        }

        // 2. Look up EventWithTickets → org ID
        val event = Server.database().collection<EventWithTickets>().get(eventId)
            ?: return@HttpHandler HttpResponse.plainText("Event not found", HttpStatus.NotFound)

        val org = Server.database().collection<Organization>().get(event.organizationId)
            ?: return@HttpHandler HttpResponse.plainText("Organization not found", HttpStatus.NotFound)

        // 3. Get Stripe API key
        val config = Server.database().collection<StripeConfig>()
            .find(condition { it.organizationId eq event.organizationId }).firstOrNull()
            ?: return@HttpHandler HttpResponse.plainText(
                "No Stripe configuration found for this event's organization",
                HttpStatus.NotFound
            )
        val cipher = secretBasis.cipher("stripe-keys").await()
        val decryptedBytes = cipher.decrypt(java.util.Base64.getDecoder().decode(config.encryptedApiKey))
        Stripe.apiKey = String(decryptedBytes)

        // 4. Enforce ticket limit — sum existing purchases for this event
        val existingPurchases = Server.database().collection<Purchase>()
            .find(condition { it.eventId eq eventId }).toList()
        val totalSold = existingPurchases.sumOf { it.quantity }
        if (totalSold + quantity > event.ticketLimit) {
            val remaining = event.ticketLimit - totalSold
            return@HttpHandler HttpResponse.plainText(
                "Ticket limit exceeded. Only $remaining ticket(s) remaining for this event.",
                HttpStatus(409)
            )
        }

        // 5. Fetch active Stripe prices for this product
        val priceParams = PriceListParams.builder()
            .setProduct(eventId)
            .setActive(true)
            .setLimit(100)
            .build()
        val prices = Price.list(priceParams).data
        if (prices.isEmpty()) {
            return@HttpHandler HttpResponse.plainText(
                "No active prices found for this event in Stripe",
                HttpStatus.NotFound
            )
        }

        // 6. Build PriceOptions for bin-packing algorithm
        // by Claude - We always do our own bin-packing. For transform_quantity prices,
        // ticketCount = divide_by and we multiply the output quantity by divide_by
        // so Stripe's division cancels out (e.g., pack of 4: output qty 4 → Stripe 4/4 = 1 group).
        val priceOptions = prices.map { price ->
            val divideBy = price.transformQuantity?.divideBy?.toInt()
            if (divideBy != null && divideBy > 0) {
                PriceOption(
                    priceId = price.id,
                    ticketCount = divideBy,
                    unitAmountCents = price.unitAmount ?: 0,
                    stripeQuantityMultiplier = divideBy,
                )
            } else {
                val ticketCount = price.metadata?.get("ticket_count")?.toIntOrNull() ?: 1
                PriceOption(price.id, ticketCount, price.unitAmount ?: 0)
            }
        }

        // 7. Compute optimal line items via bin-packing
        val lineItems = computeCheckoutLineItems(priceOptions, quantity)

        if (lineItems.isEmpty()) {
            return@HttpHandler HttpResponse.plainText(
                "Could not determine pricing for $quantity ticket(s)",
                HttpStatus.InternalServerError
            )
        }

        // 8. Build redirect URLs
        val successParam = request.queryParameters["success"]
        val cancelParam = request.queryParameters["cancel"]

        if ((successParam != null || cancelParam != null) && org.domain == null) {
            return@HttpHandler HttpResponse.plainText(
                "Organization has no domain configured for redirects",
                HttpStatus.BadRequest
            )
        }
        if (successParam != null && !successParam.startsWith("/")) {
            return@HttpHandler HttpResponse.plainText(
                "Redirect 'success' must be a path starting with /",
                HttpStatus.BadRequest
            )
        }
        if (cancelParam != null && !cancelParam.startsWith("/")) {
            return@HttpHandler HttpResponse.plainText(
                "Redirect 'cancel' must be a path starting with /",
                HttpStatus.BadRequest
            )
        }

        val publicUrl = generalSettings().publicUrl
        val successUrl = if (successParam != null) {
            "https://${org.domain}$successParam" +
                (if ("?" in successParam) "&" else "?") +
                "session_id={CHECKOUT_SESSION_ID}"
        } else {
            "$publicUrl/buy/receipt?event=$eventId&session_id={CHECKOUT_SESSION_ID}"
        }
        val cancelUrl = if (cancelParam != null) {
            "https://${org.domain}$cancelParam"
        } else {
            "$publicUrl/buy?event=$eventId&quantity=$quantity"
        }

        // 9. Create Stripe Checkout Session
        try {
            val params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .setAllowPromotionCodes(true)
                .apply {
                    for (item in lineItems) {
                        addLineItem(
                            SessionCreateParams.LineItem.builder()
                                .setPrice(item.priceId)
                                .setQuantity(item.quantity)
                                .let {
                                    event.taxRateId?.let { rate ->
                                        it.addTaxRate(rate)
                                    } ?: it
                                }
                                .build()
                        )
                    }
                }
                .setPaymentIntentData(
                    SessionCreateParams.PaymentIntentData.builder()
                        .setDescription("Note: The tickets sold will be sent in a later email closer to the event.")
                        .build()
                )
                .build()
            val session = Session.create(params)

            // 10. 303 redirect to Stripe Checkout
            HttpResponse(
                status = HttpStatus.SeeOther,
                headers = HttpHeaders(HttpHeader.Location to session.url),
            )
        } catch (e: Exception) {
            HttpResponse.plainText(
                "Stripe error: ${e.message}",
                HttpStatus.InternalServerError
            )
        }
    }

    // by Claude - GET /buy/receipt?event={eventId}&session_id=cs_xxx
    val receipt = path.path("receipt").get bind HttpHandler { request ->
        val eventId = request.queryParameters["event"]
            ?: return@HttpHandler HttpResponse.plainText("Missing 'event' parameter", HttpStatus.BadRequest)
        val sessionId = request.queryParameters["session_id"]
            ?: return@HttpHandler HttpResponse.plainText("Missing 'session_id' parameter", HttpStatus.BadRequest)

        // Look up org via EventWithTickets
        val event = Server.database().collection<EventWithTickets>().get(eventId)
            ?: return@HttpHandler HttpResponse.plainText("Event not found", HttpStatus.NotFound)

        val config = Server.database().collection<StripeConfig>()
            .find(condition { it.organizationId eq event.organizationId }).firstOrNull()
            ?: return@HttpHandler HttpResponse.plainText(
                "No Stripe configuration found",
                HttpStatus.NotFound
            )
        val cipher = secretBasis.cipher("stripe-keys").await()
        val decryptedBytes = cipher.decrypt(java.util.Base64.getDecoder().decode(config.encryptedApiKey))
        Stripe.apiKey = String(decryptedBytes)

        try {
            // Retrieve session -> payment intent -> charge -> receipt URL
            val session = Session.retrieve(sessionId)
            val paymentIntentId = session.paymentIntent
            if (paymentIntentId != null) {
                val paymentIntent = PaymentIntent.retrieve(paymentIntentId)
                val receiptUrl = paymentIntent.latestCharge?.let { chargeId ->
                    com.stripe.model.Charge.retrieve(chargeId).receiptUrl
                }
                if (receiptUrl != null) {
                    return@HttpHandler HttpResponse(
                        status = HttpStatus.SeeOther,
                        headers = HttpHeaders(HttpHeader.Location to receiptUrl),
                    )
                }
            }

            // Fallback: simple HTML thank-you page
            HttpResponse.html {
                head {
                    title { +"Purchase Complete" }
                    style {
                        unsafe {
                            +"""
                            body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
                                   display: flex; justify-content: center; align-items: center;
                                   min-height: 100vh; margin: 0; background: #f5f5f5; }
                            .card { background: white; padding: 3rem; border-radius: 12px;
                                    box-shadow: 0 2px 8px rgba(0,0,0,0.1); text-align: center; max-width: 480px; }
                            h1 { color: #333; margin-bottom: 1rem; }
                            p { color: #666; line-height: 1.6; }
                            """.trimIndent()
                        }
                    }
                }
                body {
                    div("card") {
                        h1 { +"Thank You!" }
                        p { +"Your purchase is complete. Your receipt will be emailed to you shortly. You will receive a QR code for tickets closer to the event." }
                    }
                }
            }
        } catch (e: Exception) {
            HttpResponse.plainText(
                "Error retrieving receipt: ${e.message}",
                HttpStatus.InternalServerError
            )
        }
    }
}
