package com.lightningkite.lskiteuistarter.sdk

import com.lightningkite.lightningserver.db.*
import kotlinx.serialization.builtins.*

open class CachedApi(val uncached: Api) {
	open val appReleases = ModelCache(uncached.appRelease, com.lightningkite.lskiteuistarter.AppRelease.serializer())
	open val users = ModelCache(uncached.user, com.lightningkite.lskiteuistarter.User.serializer())
	open val sessions = ModelCache(uncached.userAuth, com.lightningkite.lightningserver.sessions.Session.serializer(com.lightningkite.lskiteuistarter.User.serializer(), kotlin.uuid.Uuid.serializer()))
	open val totpSecrets = ModelCache(uncached.userAuth.totp, com.lightningkite.lightningserver.sessions.TotpSecret.serializer())
	open val passwordSecrets = ModelCache(uncached.userAuth.password, com.lightningkite.lightningserver.sessions.PasswordSecret.serializer())
	open val fcmTokens = ModelCache(uncached.fcmToken, com.lightningkite.lskiteuistarter.FcmToken.serializer())
	open val organizations = ModelCache(uncached.organization, com.lightningkite.lskiteuistarter.Organization.serializer())
	open val organizationMemberships = ModelCache(uncached.organizationMembership, com.lightningkite.lskiteuistarter.OrganizationMembership.serializer())
	open val stripeConfigs = ModelCache(uncached.stripeConfig, com.lightningkite.lskiteuistarter.StripeConfig.serializer())
	open val eventWithTickets = ModelCache(uncached.eventWithTickets, com.lightningkite.lskiteuistarter.EventWithTickets.serializer())
	open val purchases = ModelCache(uncached.purchase, com.lightningkite.lskiteuistarter.Purchase.serializer())
	open val ticketRedemptions = ModelCache(uncached.ticketRedemption, com.lightningkite.lskiteuistarter.TicketRedemption.serializer())
}
