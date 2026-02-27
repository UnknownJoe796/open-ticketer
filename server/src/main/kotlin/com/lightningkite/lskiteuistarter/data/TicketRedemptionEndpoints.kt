// by Claude - Ticket redemption endpoints for scanners
package com.lightningkite.lskiteuistarter.data

import com.lightningkite.lightningserver.auth.id
import com.lightningkite.lightningserver.auth.require
import com.lightningkite.lightningserver.definition.builder.ServerBuilder
import com.lightningkite.lightningserver.http.post
import com.lightningkite.lightningserver.typed.ApiHttpHandler
import com.lightningkite.lightningserver.typed.ModelRestEndpoints
import com.lightningkite.lightningserver.typed.auth
import com.lightningkite.lightningserver.typed.modelInfo
import com.lightningkite.lskiteuistarter.*
import com.lightningkite.lskiteuistarter.UserAuth.RoleCache.userRole
import com.lightningkite.lskiteuistarter.utils.verifyQRSignature
import com.lightningkite.services.database.*
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlin.uuid.Uuid

object TicketRedemptionEndpoints : ServerBuilder() {

    val info = Server.database.modelInfo<User, TicketRedemption, Uuid>(
        auth = UserAuth.require(),
        permissions = {
            // System admins can do anything
            val isSystemAdmin = auth.userRole() >= UserRole.Admin
            val systemAdmin = if (isSystemAdmin) Condition.Always else Condition.Never

            // Scanners can create redemptions for their organizations
            ModelPermissions(
                create = Condition.Always, // Will check org membership in custom endpoint
                read = Condition.Always, // Will filter by org membership
                update = Condition.Never, // Redemptions are immutable
                delete = systemAdmin,
            )
        }
    )

    val rest = path include ModelRestEndpoints(info)

    // Custom endpoint to redeem a ticket with QR verification
    val redeem = path.path("redeem").post bind ApiHttpHandler(
        summary = "Redeem ticket",
        auth = UserAuth.require(),
        implementation = { input: RedeemTicketInput ->
            // Verify QR signature
            val qrPayload = verifyQRSignature(input.qrData)
                ?: return@ApiHttpHandler RedeemTicketResult(
                    success = false,
                    message = "Invalid QR code signature"
                )

            // Get the purchase
            val purchase = Server.database().collection<Purchase>()
                .get(qrPayload.purchaseId)
                ?: return@ApiHttpHandler RedeemTicketResult(
                    success = false,
                    message = "Purchase not found"
                )

            // Check if user is a member of the purchase's organization
            val isMember = Server.database().collection<OrganizationMembership>()
                .find(condition {
                    (it.organizationId eq purchase.organizationId) and (it.userId eq auth.id)
                }).firstOrNull() != null

            val isSystemAdmin = auth.userRole() >= UserRole.Admin

            if (!isMember && !isSystemAdmin) {
                return@ApiHttpHandler RedeemTicketResult(
                    success = false,
                    message = "Not authorized to redeem tickets for this organization"
                )
            }

            // Check how many have already been redeemed
            val existingRedemptions = Server.database().collection<TicketRedemption>()
                .find(condition { it.purchaseId eq purchase._id })
                .toList()

            val totalRedeemed = existingRedemptions.sumOf { it.quantityRedeemed }

            if (totalRedeemed + input.quantityToRedeem > purchase.quantity) {
                return@ApiHttpHandler RedeemTicketResult(
                    success = false,
                    message = "Cannot redeem ${input.quantityToRedeem} tickets. " +
                            "Total: ${purchase.quantity}, Already redeemed: $totalRedeemed, " +
                            "Remaining: ${purchase.quantity - totalRedeemed}"
                )
            }

            // Get scanner's name
            val scanner = Server.database().collection<User>().get(auth.id)
            val scannerName = scanner?.name ?: "Unknown"

            // Create redemption
            val redemption = TicketRedemption(
                purchaseId = purchase._id,
                quantityRedeemed = input.quantityToRedeem,
                scannedByUserId = auth.id,
                scannedByName = scannerName,
                notes = input.notes
            )

            Server.database().collection<TicketRedemption>().insertOne(redemption)

            RedeemTicketResult(
                success = true,
                message = "Successfully redeemed ${input.quantityToRedeem} ticket(s). " +
                        "Remaining: ${purchase.quantity - totalRedeemed - input.quantityToRedeem}",
                redemption = redemption,
                purchase = purchase,
                remainingQuantity = purchase.quantity - totalRedeemed - input.quantityToRedeem
            )
        }
    )
}
