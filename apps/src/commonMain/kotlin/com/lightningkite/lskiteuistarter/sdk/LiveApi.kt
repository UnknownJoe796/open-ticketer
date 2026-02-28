package com.lightningkite.lskiteuistarter.sdk

import com.lightningkite.lightningserver.HttpMethod
import com.lightningkite.lightningserver.typed.Fetcher
import kotlinx.serialization.ContextualSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable

class LiveApi(val fetcher: Fetcher) : Api {
	override fun withHeaderCalculator(calculator: suspend () -> List<Pair<String, String>>): LiveApi = 
		LiveApi(fetcher.withHeaderCalculator(calculator))
	override suspend fun exampleEndpoint(): kotlin.Int =
		fetcher("example-endpoint", HttpMethod.GET, kotlin.Unit.serializer(), kotlin.Unit, kotlin.Int.serializer())
	override suspend fun exampleEndpoint(input: kotlin.Int): kotlin.Int =
		fetcher("example-endpoint", HttpMethod.POST, kotlin.Int.serializer(), input, kotlin.Int.serializer())

	override val uploadEarlyEndpoint = com.lightningkite.lightningserver.files.LiveClientUploadEarlyEndpoints(fetcher, "upload-early", )

	override val appRelease = com.lightningkite.lightningserver.typed.LiveClientModelRestEndpoints(fetcher, "app-releases", com.lightningkite.lskiteuistarter.AppRelease.serializer(), kotlin.uuid.Uuid.serializer())

	override val user = com.lightningkite.lightningserver.typed.LiveClientModelRestEndpoints(fetcher, "users", com.lightningkite.lskiteuistarter.User.serializer(), kotlin.uuid.Uuid.serializer())

	inner class LiveUserAuthApi : Api.UserAuthApi, com.lightningkite.lightningserver.typed.ClientModelRestEndpoints<com.lightningkite.lightningserver.sessions.Session<com.lightningkite.lskiteuistarter.User, kotlin.uuid.Uuid>, kotlin.uuid.Uuid> by com.lightningkite.lightningserver.typed.LiveClientModelRestEndpoints(fetcher, "auth/session/sessions", com.lightningkite.lightningserver.sessions.Session.serializer(com.lightningkite.lskiteuistarter.User.serializer(), kotlin.uuid.Uuid.serializer()), kotlin.uuid.Uuid.serializer()), com.lightningkite.lightningserver.sessions.proofs.AuthClientEndpoints<com.lightningkite.lskiteuistarter.User, kotlin.uuid.Uuid> by com.lightningkite.lightningserver.sessions.proofs.LiveAuthClientEndpoints(fetcher, "auth/session", com.lightningkite.lskiteuistarter.User.serializer(), kotlin.uuid.Uuid.serializer()) {

		inner class LiveEmailApi : Api.UserAuthApi.EmailApi, com.lightningkite.lightningserver.sessions.proofs.ProofClientEndpoints.Email by com.lightningkite.lightningserver.sessions.proofs.LiveProofClientEndpoints.Email(fetcher, "auth/proof/email", ) {
			override suspend fun verifyNewEmail(input: com.lightningkite.EmailAddress): kotlin.String =
				fetcher("auth/proof/email/verify-new-email", HttpMethod.POST, com.lightningkite.EmailAddress.serializer(), input, kotlin.String.serializer())
		}
		override val email = LiveEmailApi()

		inner class LiveTimeBasedOTPProof : Api.UserAuthApi.TimeBasedOTPProof, com.lightningkite.lightningserver.typed.ClientModelRestEndpoints<com.lightningkite.lightningserver.sessions.TotpSecret, kotlin.uuid.Uuid> by com.lightningkite.lightningserver.typed.LiveClientModelRestEndpoints(fetcher, "auth/proof/totp/secrets", com.lightningkite.lightningserver.sessions.TotpSecret.serializer(), kotlin.uuid.Uuid.serializer()), com.lightningkite.lightningserver.sessions.proofs.ProofClientEndpoints.TimeBasedOTP by com.lightningkite.lightningserver.sessions.proofs.LiveProofClientEndpoints.TimeBasedOTP(fetcher, "auth/proof/totp", ) {
		}
		override val totp = LiveTimeBasedOTPProof()

		inner class LivePasswordProof : Api.UserAuthApi.PasswordProof, com.lightningkite.lightningserver.typed.ClientModelRestEndpoints<com.lightningkite.lightningserver.sessions.PasswordSecret, kotlin.uuid.Uuid> by com.lightningkite.lightningserver.typed.LiveClientModelRestEndpoints(fetcher, "auth/proof/password/secrets", com.lightningkite.lightningserver.sessions.PasswordSecret.serializer(), kotlin.uuid.Uuid.serializer()), com.lightningkite.lightningserver.sessions.proofs.ProofClientEndpoints.Password by com.lightningkite.lightningserver.sessions.proofs.LiveProofClientEndpoints.Password(fetcher, "auth/proof/password", ) {
		}
		override val password = LivePasswordProof()

		override val backupCode = com.lightningkite.lightningserver.sessions.proofs.LiveProofClientEndpoints.BackupCode(fetcher, "auth/proof/backup-codes", )
	}
	override val userAuth = LiveUserAuthApi()

	inner class LiveFcmTokenApi : Api.FcmTokenApi, com.lightningkite.lightningserver.typed.ClientModelRestEndpoints<com.lightningkite.lskiteuistarter.FcmToken, kotlin.String> by com.lightningkite.lightningserver.typed.LiveClientModelRestEndpoints(fetcher, "fcmTokens", com.lightningkite.lskiteuistarter.FcmToken.serializer(), kotlin.String.serializer()) {
		override suspend fun registerToken(input: kotlin.String): com.lightningkite.services.database.EntryChange<com.lightningkite.lskiteuistarter.FcmToken> =
			fetcher("fcmTokens/register", HttpMethod.POST, kotlin.String.serializer(), input, com.lightningkite.services.database.EntryChange.serializer(com.lightningkite.lskiteuistarter.FcmToken.serializer()))
		override suspend fun testInAppNotifications(id: kotlin.String): kotlin.String =
			fetcher("fcmTokens/${fetcher.url(id, kotlin.String.serializer())}/test", HttpMethod.POST, kotlin.Unit.serializer(), kotlin.Unit, kotlin.String.serializer())
		override suspend fun clearToken(id: kotlin.String): kotlin.Boolean =
			fetcher("fcmTokens/${fetcher.url(id, kotlin.String.serializer())}/clear", HttpMethod.POST, kotlin.Unit.serializer(), kotlin.Unit, kotlin.Boolean.serializer())
	}
	override val fcmToken = LiveFcmTokenApi()

	override val organization = com.lightningkite.lightningserver.typed.LiveClientModelRestEndpoints(fetcher, "organizations", com.lightningkite.lskiteuistarter.Organization.serializer(), kotlin.uuid.Uuid.serializer())

	inner class LiveOrganizationMembershipApi : Api.OrganizationMembershipApi, com.lightningkite.lightningserver.typed.ClientModelRestEndpoints<com.lightningkite.lskiteuistarter.OrganizationMembership, kotlin.uuid.Uuid> by com.lightningkite.lightningserver.typed.LiveClientModelRestEndpoints(fetcher, "memberships", com.lightningkite.lskiteuistarter.OrganizationMembership.serializer(), kotlin.uuid.Uuid.serializer()) {
		override suspend fun addMemberToOrganization(input: com.lightningkite.lskiteuistarter.data.AddMemberInput): com.lightningkite.lskiteuistarter.OrganizationMembership =
			fetcher("memberships/add-member", HttpMethod.POST, com.lightningkite.lskiteuistarter.data.AddMemberInput.serializer(), input, com.lightningkite.lskiteuistarter.OrganizationMembership.serializer())
	}
	override val organizationMembership = LiveOrganizationMembershipApi()

	inner class LiveStripeConfigApi : Api.StripeConfigApi, com.lightningkite.lightningserver.typed.ClientModelRestEndpoints<com.lightningkite.lskiteuistarter.StripeConfig, kotlin.uuid.Uuid> by com.lightningkite.lightningserver.typed.LiveClientModelRestEndpoints(fetcher, "stripe-config", com.lightningkite.lskiteuistarter.StripeConfig.serializer(), kotlin.uuid.Uuid.serializer()) {
		override suspend fun getStripeConfigWithoutAPIKey(input: kotlin.uuid.Uuid): com.lightningkite.lskiteuistarter.data.StripeConfigPublic =
			fetcher("stripe-config/public", HttpMethod.GET, kotlin.uuid.Uuid.serializer(), input, com.lightningkite.lskiteuistarter.data.StripeConfigPublic.serializer())
		override suspend fun setStripeAPIKey(input: com.lightningkite.lskiteuistarter.data.SetStripeKeyInput): com.lightningkite.lskiteuistarter.data.StripeConfigPublic =
			fetcher("stripe-config/set-key", HttpMethod.POST, com.lightningkite.lskiteuistarter.data.SetStripeKeyInput.serializer(), input, com.lightningkite.lskiteuistarter.data.StripeConfigPublic.serializer())
	}
	override val stripeConfig = LiveStripeConfigApi()

	override val eventWithTickets = com.lightningkite.lightningserver.typed.LiveClientModelRestEndpoints(fetcher, "events", com.lightningkite.lskiteuistarter.EventWithTickets.serializer(), kotlin.String.serializer())

	override val purchase = com.lightningkite.lightningserver.typed.LiveClientModelRestEndpoints(fetcher, "purchases", com.lightningkite.lskiteuistarter.Purchase.serializer(), kotlin.uuid.Uuid.serializer())

	inner class LiveTicketRedemptionApi : Api.TicketRedemptionApi, com.lightningkite.lightningserver.typed.ClientModelRestEndpoints<com.lightningkite.lskiteuistarter.TicketRedemption, kotlin.uuid.Uuid> by com.lightningkite.lightningserver.typed.LiveClientModelRestEndpoints(fetcher, "redemptions", com.lightningkite.lskiteuistarter.TicketRedemption.serializer(), kotlin.uuid.Uuid.serializer()) {
		override suspend fun redeemTicket(input: com.lightningkite.lskiteuistarter.data.RedeemTicketInput): com.lightningkite.lskiteuistarter.data.RedeemTicketResult =
			fetcher("redemptions/redeem", HttpMethod.POST, com.lightningkite.lskiteuistarter.data.RedeemTicketInput.serializer(), input, com.lightningkite.lskiteuistarter.data.RedeemTicketResult.serializer())
	}
	override val ticketRedemption = LiveTicketRedemptionApi()

	inner class LiveStripeWebhookEndpointApi : Api.StripeWebhookEndpointApi {
	}
	override val stripeWebhookEndpoint = LiveStripeWebhookEndpointApi()

	inner class LiveCheckoutBridgeEndpointApi : Api.CheckoutBridgeEndpointApi {
	}
	override val checkoutBridgeEndpoint = LiveCheckoutBridgeEndpointApi()

	inner class LiveTicketScannerEndpointApi : Api.TicketScannerEndpointApi {
		override suspend fun verifyQRCode(input: com.lightningkite.lskiteuistarter.data.VerifyQRInput): com.lightningkite.lskiteuistarter.data.VerifyQRResult =
			fetcher("scanner/verify", HttpMethod.POST, com.lightningkite.lskiteuistarter.data.VerifyQRInput.serializer(), input, com.lightningkite.lskiteuistarter.data.VerifyQRResult.serializer())
	}
	override val ticketScannerEndpoint = LiveTicketScannerEndpointApi()

	inner class LiveStripeSyncTaskApi : Api.StripeSyncTaskApi {
	}
	override val stripeSyncTask = LiveStripeSyncTaskApi()

	inner class LiveMetaApi : Api.MetaApi {
		override suspend fun getServerHealth(): com.lightningkite.lightningserver.typed.ServerHealth =
			fetcher("meta/health", HttpMethod.GET, kotlin.Unit.serializer(), kotlin.Unit, com.lightningkite.lightningserver.typed.ServerHealth.serializer())
		override suspend fun bulkRequest(input: Map<String, com.lightningkite.lightningserver.typed.BulkRequest>): Map<String, com.lightningkite.lightningserver.typed.BulkResponse> =
			fetcher("meta/bulk", HttpMethod.POST, MapSerializer(String.serializer(), com.lightningkite.lightningserver.typed.BulkRequest.serializer()), input, MapSerializer(String.serializer(), com.lightningkite.lightningserver.typed.BulkResponse.serializer()))
	}
	override val meta = LiveMetaApi()
}
