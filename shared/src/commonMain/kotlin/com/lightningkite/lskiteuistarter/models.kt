package com.lightningkite.lskiteuistarter

import com.lightningkite.EmailAddress
import com.lightningkite.services.data.*
import com.lightningkite.services.data.IndexUniqueness
import com.lightningkite.services.database.HasId
import kotlinx.datetime.*
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid


@Serializable
enum class AppPlatform {
    iOS,
    Android,
    Web,
    Desktop,
    ;

    companion object
}

@GenerateDataClassPaths
@Serializable
data class AppRelease(
    override val _id: Uuid = Uuid.random(),
    val version: String,
    val platform: AppPlatform,
    val releaseDate: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
    val requiredUpdate: Boolean,
) : HasId<Uuid>

@Serializable
@GenerateDataClassPaths
data class User(
    override val _id: Uuid = Uuid.random(),
    val email: EmailAddress,
    val name: String = "No Name Specified",
    val role: UserRole = UserRole.User,
) : HasId<Uuid>

@Serializable
enum class UserRole {
    NoOne,
    User,
    Admin,
    Developer,
    Root
}

@Serializable
@GenerateDataClassPaths
data class FcmToken(
    @MaxLength(160, 142) override val _id: String,
    @Index @References(User::class) val user: Uuid,
    val active: Boolean = true,
    val created: Instant = Clock.System.now(),
    val lastRegisteredAt: Instant = created,
    val userAgent: String? = null,
) : HasId<String>

// by Claude - Open Ticketer models

@Serializable
@GenerateDataClassPaths
data class Organization(
    override val _id: Uuid = Uuid.random(),
    val name: String,
    val domain: String? = null, // e.g. "example.com" — used to validate checkout redirect URLs // by Claude
    @Index val created: Instant = Clock.System.now(),
    val active: Boolean = true,
) : HasId<Uuid>

@Serializable
@GenerateDataClassPaths
data class OrganizationMembership(
    override val _id: Uuid = Uuid.random(),
    @Index @References(User::class) val userId: Uuid,
    @Index @References(Organization::class) val organizationId: Uuid,
    val role: OrgRole = OrgRole.Scanner,
    val created: Instant = Clock.System.now(),
) : HasId<Uuid>

@Serializable
enum class OrgRole {
    Scanner,  // Can scan tickets
    Admin,    // Can manage org, configure Stripe
}

@Serializable
@GenerateDataClassPaths
data class StripeConfig(
    override val _id: Uuid = Uuid.random(),
    @Index(unique = IndexUniqueness.Unique) @References(Organization::class) val organizationId: Uuid,
    @MaxLength(500) val encryptedApiKey: String,
    val webhookSecret: String,
    val lastSyncedAt: Instant? = null,
    val created: Instant = Clock.System.now(),
) : HasId<Uuid>

@Serializable
@GenerateDataClassPaths
data class EventWithTickets(
    @Description("The Stripe product ID of the event")
    override val _id: String,
    @Index @References(Organization::class) val organizationId: Uuid,
    val name: String,
    val ticketLimit: Int = Int.MAX_VALUE,
): HasId<String>

@Serializable
@GenerateDataClassPaths
data class Purchase(
    override val _id: Uuid = Uuid.random(),
    @Index @References(EventWithTickets::class) val eventId: String,
    @Index(unique = IndexUniqueness.Unique) val stripeCheckoutSessionId: String,
    @Index @References(Organization::class) val organizationId: Uuid,
    val quantity: Int,
    @Index val customerEmail: EmailAddress,
    val customerName: String?,
    val amountTotal: Long, // cents
    val currency: String,
    @Index val purchasedAt: Instant,
    val qrCodeData: String? = null,  // JSON payload
    val emailSent: Boolean = false,
    val created: Instant = Clock.System.now(),
) : HasId<Uuid>

@Serializable
@GenerateDataClassPaths
data class TicketRedemption(
    override val _id: Uuid = Uuid.random(),
    @Index @References(EventWithTickets::class) val eventId: String,
    @Index @References(Purchase::class) val purchaseId: Uuid,
    val quantityRedeemed: Int,
    @Index @References(User::class) val scannedByUserId: Uuid,
    val scannedByName: String,
    @Index val scannedAt: Instant = Clock.System.now(),
    val notes: String? = null,
) : HasId<Uuid>
