// by Claude - Background task to sync Stripe checkout sessions
package com.lightningkite.lskiteuistarter.tasks

import com.lightningkite.lightningserver.definition.ScheduledTask
import com.lightningkite.lightningserver.definition.builder.ServerBuilder
import com.lightningkite.lightningserver.definition.secretBasis
import com.lightningkite.lightningserver.encryption.cipher
import com.lightningkite.lskiteuistarter.*
import com.lightningkite.services.database.*
import com.lightningkite.toEmailAddress
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import com.stripe.Stripe
import com.stripe.model.checkout.Session
import com.stripe.param.checkout.SessionListParams
import kotlin.time.Duration.Companion.minutes

object StripeSyncTask : ServerBuilder() {

    val syncTask = path.path("stripe-sync") bind ScheduledTask(30.minutes) {
        println("Starting Stripe sync task...")

        val configs = Server.database().collection<StripeConfig>().find(Condition.Always).toList()
        println("Found ${configs.size} Stripe configs to sync")

        configs.forEach { config ->
            try {
                println("Syncing organization ${config.organizationId}")

                // by Claude - decrypt API key using secretBasis (same pattern as StripeConfigEndpoints)
                val cipher = secretBasis.cipher("stripe-keys").await()
                val decryptedBytes = cipher.decrypt(java.util.Base64.getDecoder().decode(config.encryptedApiKey))
                Stripe.apiKey = String(decryptedBytes)

                // Build query parameters
                val paramsBuilder = SessionListParams.builder()
                    .setLimit(100)

                // If we have a last sync time, only get sessions created after that
                val lastSynced = config.lastSyncedAt
                if (lastSynced != null) {
                    paramsBuilder.setCreated(
                        SessionListParams.Created.builder()
                            .setGte(lastSynced.epochSeconds)
                            .build()
                    )
                }

                val params = paramsBuilder.build()

                // Query Stripe for checkout sessions
                val sessions = Session.list(params)
                val sessionList = sessions.getData()

                println("Found ${sessionList.size} sessions for organization ${config.organizationId}")

                for (session in sessionList) {
                    if (session.getStatus() == "complete") {
                        val sessionId = session.getId()

                        // Check if purchase already exists
                        val existing = Server.database().collection<Purchase>()
                            .find(condition { it.stripeCheckoutSessionId eq sessionId }).firstOrNull()

                        if (existing == null) {
                            // by Claude - get product details from line items, sum quantities across all
                            val lineItems = session.listLineItems()?.getData()
                            val quantity = lineItems?.sumOf { it.getQuantity()?.toInt() ?: 0 } ?: 1
                            val eventId = lineItems?.firstOrNull()?.getPrice()?.getProduct() ?: "unknown"

                            // by Claude - auto-upsert EventWithTickets if missing
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

                            val customerEmailStr = session.getCustomerDetails()?.getEmail()
                            if (customerEmailStr == null) {
                                println("Skipping session $sessionId - no customer email")
                                continue
                            }

                            val purchase = Purchase(
                                stripeCheckoutSessionId = sessionId,
                                organizationId = config.organizationId,
                                eventId = eventId,
                                quantity = quantity,
                                customerEmail = customerEmailStr.toEmailAddress(),
                                customerName = session.getCustomerDetails()?.getName(),
                                amountTotal = session.getAmountTotal() ?: 0L,
                                currency = session.getCurrency() ?: "usd",
                                purchasedAt = kotlin.time.Instant.fromEpochSeconds(session.getCreated()),
                                emailSent = false
                            )

                            Server.database().collection<Purchase>().insertOne(purchase)
                            println("Created purchase ${purchase._id} from session $sessionId")
                        }
                    }
                }

                // Update last synced timestamp
                Server.database().collection<StripeConfig>().updateOne(
                    condition { it._id eq config._id },
                    modification {
                        it.lastSyncedAt assign kotlin.time.Clock.System.now()
                    }
                )

                println("Completed sync for organization ${config.organizationId}")

            } catch (e: Exception) {
                println("Error syncing organization ${config.organizationId}: ${e.message}")
                e.printStackTrace()
            }
        }

        println("Stripe sync task completed")
    }
}
