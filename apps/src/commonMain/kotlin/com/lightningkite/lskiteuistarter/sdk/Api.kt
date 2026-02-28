package com.lightningkite.lskiteuistarter.sdk



interface Api {
	fun withHeaderCalculator(calculator: suspend () -> List<Pair<String, String>>): Api
	/**
	 * Example Endpoint
	 * 
	 * **Auth Requirements:** No Requirements
	 * */
	suspend fun exampleEndpoint(): kotlin.Int
	/**
	 * Example Endpoint
	 * 
	 * **Auth Requirements:** User with root access
	 * */
	suspend fun exampleEndpoint(input: kotlin.Int): kotlin.Int

	val uploadEarlyEndpoint: com.lightningkite.lightningserver.files.ClientUploadEarlyEndpoints

	val appRelease: com.lightningkite.lightningserver.typed.ClientModelRestEndpoints<com.lightningkite.lskiteuistarter.AppRelease, kotlin.uuid.Uuid>

	val user: com.lightningkite.lightningserver.typed.ClientModelRestEndpoints<com.lightningkite.lskiteuistarter.User, kotlin.uuid.Uuid>

	interface UserAuthApi : com.lightningkite.lightningserver.typed.ClientModelRestEndpoints<com.lightningkite.lightningserver.sessions.Session<com.lightningkite.lskiteuistarter.User, kotlin.uuid.Uuid>, kotlin.uuid.Uuid>, com.lightningkite.lightningserver.sessions.proofs.AuthClientEndpoints<com.lightningkite.lskiteuistarter.User, kotlin.uuid.Uuid> {

		interface EmailApi : com.lightningkite.lightningserver.sessions.proofs.ProofClientEndpoints.Email {
			/**
			 * Verify New Email
			 * 
			 * Sends a verification passcode to a new email.
			 * 
			 * **Auth Requirements:** User with root access
			 * */
			suspend fun verifyNewEmail(input: com.lightningkite.EmailAddress): kotlin.String
		}
		val email: EmailApi

		interface TimeBasedOTPProof : com.lightningkite.lightningserver.typed.ClientModelRestEndpoints<com.lightningkite.lightningserver.sessions.TotpSecret, kotlin.uuid.Uuid>, com.lightningkite.lightningserver.sessions.proofs.ProofClientEndpoints.TimeBasedOTP {
		}
		val totp: TimeBasedOTPProof

		interface PasswordProof : com.lightningkite.lightningserver.typed.ClientModelRestEndpoints<com.lightningkite.lightningserver.sessions.PasswordSecret, kotlin.uuid.Uuid>, com.lightningkite.lightningserver.sessions.proofs.ProofClientEndpoints.Password {
		}
		val password: PasswordProof

		val backupCode: com.lightningkite.lightningserver.sessions.proofs.ProofClientEndpoints.BackupCode
	}
	val userAuth: UserAuthApi

	interface FcmTokenApi : com.lightningkite.lightningserver.typed.ClientModelRestEndpoints<com.lightningkite.lskiteuistarter.FcmToken, kotlin.String> {
		/**
		 * Register Token
		 * 
		 * **Auth Requirements:** User with root access
		 * */
		suspend fun registerToken(input: kotlin.String): com.lightningkite.services.database.EntryChange<com.lightningkite.lskiteuistarter.FcmToken>
		/**
		 * Test In-App Notifications
		 * 
		 * **Auth Requirements:** User with root access
		 * */
		suspend fun testInAppNotifications(id: kotlin.String): kotlin.String
		/**
		 * Clear Token
		 * 
		 * **Auth Requirements:** No Requirements
		 * */
		suspend fun clearToken(id: kotlin.String): kotlin.Boolean
	}
	val fcmToken: FcmTokenApi

	val organization: com.lightningkite.lightningserver.typed.ClientModelRestEndpoints<com.lightningkite.lskiteuistarter.Organization, kotlin.uuid.Uuid>

	interface OrganizationMembershipApi : com.lightningkite.lightningserver.typed.ClientModelRestEndpoints<com.lightningkite.lskiteuistarter.OrganizationMembership, kotlin.uuid.Uuid> {
		/**
		 * Add member to organization
		 * 
		 * **Auth Requirements:** User with root access
		 * */
		suspend fun addMemberToOrganization(input: com.lightningkite.lskiteuistarter.data.AddMemberInput): com.lightningkite.lskiteuistarter.OrganizationMembership
	}
	val organizationMembership: OrganizationMembershipApi

	interface StripeConfigApi : com.lightningkite.lightningserver.typed.ClientModelRestEndpoints<com.lightningkite.lskiteuistarter.StripeConfig, kotlin.uuid.Uuid> {
		/**
		 * Get Stripe config (without API key)
		 * 
		 * **Auth Requirements:** User with root access
		 * */
		suspend fun getStripeConfigWithoutAPIKey(input: kotlin.uuid.Uuid): com.lightningkite.lskiteuistarter.data.StripeConfigPublic
		/**
		 * Set Stripe API key
		 * 
		 * **Auth Requirements:** User with root access
		 * */
		suspend fun setStripeAPIKey(input: com.lightningkite.lskiteuistarter.data.SetStripeKeyInput): com.lightningkite.lskiteuistarter.data.StripeConfigPublic
	}
	val stripeConfig: StripeConfigApi

	val eventWithTickets: com.lightningkite.lightningserver.typed.ClientModelRestEndpoints<com.lightningkite.lskiteuistarter.EventWithTickets, kotlin.String>

	val purchase: com.lightningkite.lightningserver.typed.ClientModelRestEndpoints<com.lightningkite.lskiteuistarter.Purchase, kotlin.uuid.Uuid>

	interface TicketRedemptionApi : com.lightningkite.lightningserver.typed.ClientModelRestEndpoints<com.lightningkite.lskiteuistarter.TicketRedemption, kotlin.uuid.Uuid> {
		/**
		 * Redeem ticket
		 * 
		 * **Auth Requirements:** User with root access
		 * */
		suspend fun redeemTicket(input: com.lightningkite.lskiteuistarter.data.RedeemTicketInput): com.lightningkite.lskiteuistarter.data.RedeemTicketResult
	}
	val ticketRedemption: TicketRedemptionApi

	interface StripeWebhookEndpointApi {
	}
	val stripeWebhookEndpoint: StripeWebhookEndpointApi

	interface CheckoutBridgeEndpointApi {
	}
	val checkoutBridgeEndpoint: CheckoutBridgeEndpointApi

	interface TicketScannerEndpointApi {
		/**
		 * Verify QR code
		 * 
		 * **Auth Requirements:** User with root access
		 * */
		suspend fun verifyQRCode(input: com.lightningkite.lskiteuistarter.data.VerifyQRInput): com.lightningkite.lskiteuistarter.data.VerifyQRResult
	}
	val ticketScannerEndpoint: TicketScannerEndpointApi

	interface StripeSyncTaskApi {
	}
	val stripeSyncTask: StripeSyncTaskApi

	interface MetaApi {
		/**
		 * Get Server Health
		 * 
		 * Gets the current status of the server
		 * 
		 * **Auth Requirements:** No Requirements
		 * */
		suspend fun getServerHealth(): com.lightningkite.lightningserver.typed.ServerHealth
		/**
		 * Bulk Request
		 * 
		 * Performs multiple requests at once, returning the results in the same order.
		 * 
		 * **Auth Requirements:** No Requirements
		 * */
		suspend fun bulkRequest(input: Map<String, com.lightningkite.lightningserver.typed.BulkRequest>): Map<String, com.lightningkite.lightningserver.typed.BulkResponse>
	}
	val meta: MetaApi
}
