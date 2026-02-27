// by Claude - Stripe Checkout bridge endpoint: accepts a shareable URL with price-quantity pairs,
// creates a Stripe Checkout Session, and redirects to Stripe's hosted checkout page.
// This replaces Stripe Payment Links which don't support tiered/package pricing.
package com.lightningkite.lskiteuistarter.data

import com.lightningkite.lightningserver.definition.builder.ServerBuilder
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
import com.stripe.model.checkout.Session
import com.stripe.param.checkout.SessionCreateParams
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.html.*
import kotlin.uuid.Uuid

// by Claude - uses raw HttpHandler (no auth) because this is a public-facing redirect endpoint
object CheckoutBridgeEndpoint : ServerBuilder() {

    // by Claude - GET /buy?org={orgId}&price_abc123=4&success=/thanks&cancel=/shop&field_name=text:Name
    val buy = path.get bind HttpHandler { request ->
        // 1. Parse org parameter
        val orgIdStr = request.queryParameters["org"]
            ?: return@HttpHandler HttpResponse.plainText("Missing 'org' query parameter", HttpStatus.BadRequest)
        val orgId = try {
            Uuid.parse(orgIdStr)
        } catch (e: IllegalArgumentException) {
            return@HttpHandler HttpResponse.plainText("Invalid 'org' parameter: not a valid UUID", HttpStatus.BadRequest)
        }

        // 2. Parse price_* parameters into line items
        val lineItems = request.queryParameters
            .filter { (key, _) -> key.startsWith("price_") }
            .map { (key, value) ->
                val quantity = value.toLongOrNull()
                if (quantity == null || quantity <= 0) {
                    return@HttpHandler HttpResponse.plainText(
                        "Invalid quantity for $key: must be a positive integer",
                        HttpStatus.BadRequest
                    )
                }
                key to quantity
            }
        if (lineItems.isEmpty()) {
            return@HttpHandler HttpResponse.plainText(
                "No price_* query parameters found. Include at least one, e.g. ?price_abc123=2",
                HttpStatus.BadRequest
            )
        }

        // 3. Look up Organization (needed for domain-based redirects) and StripeConfig
        val org = Server.database().collection<Organization>()
            .get(orgId)
            ?: return@HttpHandler HttpResponse.plainText("Organization not found", HttpStatus.NotFound)

        val config = Server.database().collection<StripeConfig>()
            .find(condition { it.organizationId eq orgId }).firstOrNull()
            ?: return@HttpHandler HttpResponse.plainText(
                "No Stripe configuration found for organization $orgId",
                HttpStatus.NotFound
            )
        val cipher = secretBasis.cipher("stripe-keys").await()
        val decryptedBytes = cipher.decrypt(java.util.Base64.getDecoder().decode(config.encryptedApiKey))
        Stripe.apiKey = String(decryptedBytes)

        // 4. Parse and validate redirect paths
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

        val publicUrl = Server.webUrl()
        val successUrl = if (successParam != null) {
            "https://${org.domain}$successParam" +
                (if ("?" in successParam) "&" else "?") +
                "session_id={CHECKOUT_SESSION_ID}"
        } else {
            "$publicUrl/buy/receipt?org=$orgId&session_id={CHECKOUT_SESSION_ID}"
        }
        val cancelUrl = if (cancelParam != null) {
            "https://${org.domain}$cancelParam"
        } else {
            // Reconstruct the current URL for cancel (return to same link to retry)
            buildString {
                append(publicUrl)
                append("/buy?org=")
                append(orgIdStr)
                for ((priceId, qty) in lineItems) {
                    append("&$priceId=$qty")
                }
            }
        }

        // 5. Parse custom field_* parameters (max 3)
        val fieldParams = request.queryParameters
            .filter { (key, _) -> key.startsWith("field_") }
        if (fieldParams.size > 3) {
            return@HttpHandler HttpResponse.plainText("Maximum 3 custom fields allowed", HttpStatus.BadRequest)
        }
        val customFields = fieldParams.map { (key, value) ->
            val fieldKey = key.removePrefix("field_")
            parseCustomField(fieldKey, value)
                ?: return@HttpHandler HttpResponse.plainText(
                    "Invalid field format for '$key'. Expected TYPE[?]:LABEL[:OPT1,OPT2,...] " +
                        "where TYPE is text, numeric, or dropdown",
                    HttpStatus.BadRequest
                )
        }

        // 6. Build Checkout Session
        try {
            val params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .apply {
                    for ((priceId, quantity) in lineItems) {
                        addLineItem(
                            SessionCreateParams.LineItem.builder()
                                .setPrice(priceId)
                                .setQuantity(quantity)
                                .build()
                        )
                    }
                    for (field in customFields) {
                        addCustomField(field)
                    }
                }
                .build()
            val session = Session.create(params)

            // 7. 303 redirect to Stripe Checkout
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

    // by Claude - Parses "TYPE[?]:LABEL[:OPT1,OPT2,...]" into a Stripe CustomField.
    // Returns null if the format is invalid.
    private fun parseCustomField(key: String, value: String): SessionCreateParams.CustomField? {
        val parts = value.split(":", limit = 3)
        if (parts.size < 2) return null

        val typeRaw = parts[0]
        val optional = typeRaw.endsWith("?")
        val typeName = typeRaw.removeSuffix("?").lowercase()
        val label = parts[1].ifBlank { return null }
        val options = if (parts.size == 3) parts[2].split(",").filter { it.isNotBlank() } else emptyList()

        val type = when (typeName) {
            "text" -> SessionCreateParams.CustomField.Type.TEXT
            "numeric" -> SessionCreateParams.CustomField.Type.NUMERIC
            "dropdown" -> SessionCreateParams.CustomField.Type.DROPDOWN
            else -> return null
        }

        if (type == SessionCreateParams.CustomField.Type.DROPDOWN && options.isEmpty()) return null

        return SessionCreateParams.CustomField.builder()
            .setKey(key)
            .setType(type)
            .setLabel(
                SessionCreateParams.CustomField.Label.builder()
                    .setType(SessionCreateParams.CustomField.Label.Type.CUSTOM)
                    .setCustom(label)
                    .build()
            )
            .setOptional(optional)
            .apply {
                when (type) {
                    SessionCreateParams.CustomField.Type.DROPDOWN -> setDropdown(
                        SessionCreateParams.CustomField.Dropdown.builder()
                            .apply {
                                for (opt in options) {
                                    addOption(
                                        SessionCreateParams.CustomField.Dropdown.Option.builder()
                                            .setLabel(opt)
                                            .setValue(opt.lowercase().replace(" ", "_"))
                                            .build()
                                    )
                                }
                            }
                            .build()
                    )
                    SessionCreateParams.CustomField.Type.TEXT -> setText(
                        SessionCreateParams.CustomField.Text.builder().build()
                    )
                    SessionCreateParams.CustomField.Type.NUMERIC -> setNumeric(
                        SessionCreateParams.CustomField.Numeric.builder().build()
                    )
                }
            }
            .build()
    }

    // GET /buy/receipt?org={orgId}&session_id=cs_xxx
    val receipt = path.path("receipt").get bind HttpHandler { request ->
        val orgIdStr = request.queryParameters["org"]
            ?: return@HttpHandler HttpResponse.plainText("Missing 'org' parameter", HttpStatus.BadRequest)
        val orgId = try {
            Uuid.parse(orgIdStr)
        } catch (e: IllegalArgumentException) {
            return@HttpHandler HttpResponse.plainText("Invalid 'org' parameter", HttpStatus.BadRequest)
        }
        val sessionId = request.queryParameters["session_id"]
            ?: return@HttpHandler HttpResponse.plainText("Missing 'session_id' parameter", HttpStatus.BadRequest)

        // Decrypt API key
        val config = Server.database().collection<StripeConfig>()
            .find(condition { it.organizationId eq orgId }).firstOrNull()
            ?: return@HttpHandler HttpResponse.plainText(
                "No Stripe configuration found for organization $orgId",
                HttpStatus.NotFound
            )
        val cipher = secretBasis.cipher("stripe-keys").await()
        val decryptedBytes = cipher.decrypt(java.util.Base64.getDecoder().decode(config.encryptedApiKey))
        Stripe.apiKey = String(decryptedBytes)

        try {
            // Retrieve session → payment intent → charge → receipt URL
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
                        p { +"Your purchase is complete. Your tickets will be emailed to you shortly." }
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
