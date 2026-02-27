// by Claude - Scanner endpoints for verifying and checking in tickets
package com.lightningkite.lskiteuistarter.data

import com.lightningkite.lightningserver.auth.id
import com.lightningkite.lightningserver.auth.require
import com.lightningkite.lightningserver.definition.builder.ServerBuilder
import com.lightningkite.lightningserver.http.post
import com.lightningkite.lightningserver.typed.ApiHttpHandler
import com.lightningkite.lightningserver.typed.auth
import com.lightningkite.lskiteuistarter.*
import com.lightningkite.lskiteuistarter.utils.verifyQRSignature
import com.lightningkite.services.database.*
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlin.uuid.Uuid

object TicketScannerEndpoint : ServerBuilder() {

    // Verify QR code without creating a redemption
    val verify = path.path("scanner").path("verify").post bind ApiHttpHandler(
        summary = "Verify QR code",
        auth = UserAuth.require(),
        implementation = { input: VerifyQRInput ->
            // Verify QR signature
            val qrPayload = verifyQRSignature(input.qrData)
                ?: return@ApiHttpHandler VerifyQRResult(
                    valid = false,
                    message = "Invalid QR code signature"
                )

            // Get the purchase
            val purchase = Server.database().collection<Purchase>()
                .get(qrPayload.purchaseId)
                ?: return@ApiHttpHandler VerifyQRResult(
                    valid = false,
                    message = "Purchase not found"
                )

            // Check if user is a member of the purchase's organization
            val isMember = Server.database().collection<OrganizationMembership>()
                .find(condition {
                    (it.organizationId eq purchase.organizationId) and (it.userId eq auth.id)
                }).firstOrNull() != null

            if (!isMember) {
                return@ApiHttpHandler VerifyQRResult(
                    valid = false,
                    message = "Not authorized to scan tickets for this organization"
                )
            }

            // Get all redemptions for this purchase
            val redemptions = Server.database().collection<TicketRedemption>()
                .find(condition { it.purchaseId eq purchase._id })
                .toList()

            val totalRedeemed = redemptions.sumOf { it.quantityRedeemed }
            val remaining = purchase.quantity - totalRedeemed

            VerifyQRResult(
                valid = true,
                message = if (remaining > 0) {
                    "Valid ticket. $remaining of ${purchase.quantity} remaining."
                } else {
                    "All tickets have been redeemed."
                },
                purchase = purchase,
                redemptions = redemptions,
                remainingQuantity = remaining
            )
        }
    )
}
