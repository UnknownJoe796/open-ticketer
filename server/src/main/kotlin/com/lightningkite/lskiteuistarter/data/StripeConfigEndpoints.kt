// by Claude - Stripe configuration endpoints with encrypted API key storage
package com.lightningkite.lskiteuistarter.data

import com.lightningkite.lightningserver.auth.id
import com.lightningkite.lightningserver.auth.require
import com.lightningkite.lightningserver.definition.builder.ServerBuilder
import com.lightningkite.lightningserver.definition.secretBasis
import com.lightningkite.lightningserver.encryption.cipher
import com.lightningkite.lightningserver.http.get
import com.lightningkite.lightningserver.http.post
import dev.whyoleg.cryptography.operations.Cipher
import dev.whyoleg.cryptography.operations.Encryptor
import dev.whyoleg.cryptography.operations.Decryptor
import com.lightningkite.lightningserver.typed.ApiHttpHandler
import com.lightningkite.lightningserver.typed.ModelRestEndpoints
import com.lightningkite.lightningserver.typed.auth
import com.lightningkite.lightningserver.typed.modelInfo
import com.lightningkite.lskiteuistarter.*
import com.lightningkite.lskiteuistarter.UserAuth.RoleCache.userRole
import com.lightningkite.services.database.*
import kotlinx.coroutines.flow.firstOrNull
import kotlin.uuid.Uuid

object StripeConfigEndpoints : ServerBuilder() {

    val info = Server.database.modelInfo<User, StripeConfig, Uuid>(
        auth = UserAuth.require(),
        permissions = {
            // System admins can do anything
            val isSystemAdmin = auth.userRole() >= UserRole.Admin
            val systemAdmin = if (isSystemAdmin) Condition.Always else Condition.Never

            // Allow reading configs (will filter by org membership in handlers)
            ModelPermissions(
                create = systemAdmin,
                read = systemAdmin, // Only system admins can read directly
                update = systemAdmin,
                // Note: API key field is write-only - custom endpoints handle encryption
                delete = systemAdmin,
            )
        }
    )

    val rest = path include ModelRestEndpoints(info)

    // Write-only endpoint to set the API key (accepts plaintext, stores encrypted)
    val setKey = path.path("set-key").post bind ApiHttpHandler(
        summary = "Set Stripe API key",
        auth = UserAuth.require(),
        implementation = { input: SetStripeKeyInput ->
            // Check if user is admin of the organization
            val isOrgAdmin = Server.database().collection<OrganizationMembership>()
                .find(condition {
                    (it.organizationId eq input.organizationId) and
                    (it.userId eq auth.id) and
                    (it.role eq OrgRole.Admin)
                }).firstOrNull() != null

            val isSystemAdmin = auth.userRole() >= UserRole.Admin

            if (!isOrgAdmin && !isSystemAdmin) {
                throw IllegalAccessException("Only organization admins can configure Stripe")
            }

            // Encrypt the API key using secretBasis
            val cipher = secretBasis.cipher("stripe-keys").await()
            val encryptedBytes = cipher.encrypt(input.apiKey.encodeToByteArray())
            val encryptedKey = java.util.Base64.getEncoder().encodeToString(encryptedBytes)

            // Check if config already exists for this organization
            val existing = Server.database().collection<StripeConfig>()
                .find(condition { it.organizationId eq input.organizationId }).firstOrNull()

            if (existing != null) {
                // Update existing config
                Server.database().collection<StripeConfig>().replaceOne(
                    condition { it._id eq existing._id },
                    existing.copy(encryptedApiKey = encryptedKey, webhookSecret = input.webhookSecret)
                )
                StripeConfigPublic(
                    _id = existing._id,
                    organizationId = existing.organizationId,
                    webhookSecret = input.webhookSecret,
                    lastSyncedAt = existing.lastSyncedAt,
                    created = existing.created
                )
            } else {
                // Create new config
                val config = StripeConfig(
                    organizationId = input.organizationId,
                    encryptedApiKey = encryptedKey,
                    webhookSecret = input.webhookSecret
                )
                Server.database().collection<StripeConfig>().insertOne(config)
                StripeConfigPublic(
                    _id = config._id,
                    organizationId = config.organizationId,
                    webhookSecret = config.webhookSecret,
                    lastSyncedAt = config.lastSyncedAt,
                    created = config.created
                )
            }
        }
    )

    // Get config without the API key (read-only view)
    val getPublicConfig = path.path("public").get bind ApiHttpHandler(
        summary = "Get Stripe config (without API key)",
        auth = UserAuth.require(),
        implementation = { organizationId: Uuid ->
            // Check if user is member of the organization
            val isMember = Server.database().collection<OrganizationMembership>()
                .find(condition {
                    (it.organizationId eq organizationId) and (it.userId eq auth.id)
                }).firstOrNull() != null

            val isSystemAdmin = auth.userRole() >= UserRole.Admin

            if (!isMember && !isSystemAdmin) {
                throw IllegalAccessException("Only organization members can view config")
            }

            val config = Server.database().collection<StripeConfig>()
                .find(condition { it.organizationId eq organizationId })
                .firstOrNull() ?: throw NoSuchElementException("No Stripe config found for organization")

            StripeConfigPublic(
                _id = config._id,
                organizationId = config.organizationId,
                webhookSecret = config.webhookSecret,
                lastSyncedAt = config.lastSyncedAt,
                created = config.created
            )
        }
    )
}
