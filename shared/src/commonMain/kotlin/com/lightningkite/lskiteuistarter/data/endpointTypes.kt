// by Claude - Shared endpoint request/response types for the SDK
package com.lightningkite.lskiteuistarter.data

import com.lightningkite.lskiteuistarter.*
import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

// Organization Membership types
@Serializable
data class AddMemberInput(
    val organizationId: Uuid,
    val userEmail: String,
    val role: OrgRole = OrgRole.Scanner
)

// Stripe Config types
@Serializable
data class SetStripeKeyInput(
    val organizationId: Uuid,
    val apiKey: String,
    val webhookSecret: String
)

@Serializable
data class StripeConfigPublic(
    val _id: Uuid,
    val organizationId: Uuid,
    val webhookSecret: String,
    val lastSyncedAt: Instant? = null,
    val created: Instant
)

// Ticket Scanner types
@Serializable
data class VerifyQRInput(
    val qrData: String
)

@Serializable
data class VerifyQRResult(
    val valid: Boolean,
    val message: String,
    val purchase: Purchase? = null,
    val redemptions: List<TicketRedemption> = emptyList(),
    val remainingQuantity: Int = 0
)

// Ticket Redemption types
@Serializable
data class RedeemTicketInput(
    val qrData: String,
    val quantityToRedeem: Int = 1,
    val notes: String? = null
)

@Serializable
data class RedeemTicketResult(
    val success: Boolean,
    val message: String,
    val redemption: TicketRedemption? = null,
    val purchase: Purchase? = null,
    val remainingQuantity: Int = 0
)
