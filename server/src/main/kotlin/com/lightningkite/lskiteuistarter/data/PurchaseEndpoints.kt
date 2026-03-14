// by Claude - Purchase endpoints with email triggering via signals
package com.lightningkite.lskiteuistarter.data

import com.lightningkite.lightningserver.NotFoundException
import com.lightningkite.lightningserver.auth.id
import com.lightningkite.lightningserver.auth.require
import com.lightningkite.lightningserver.definition.builder.ServerBuilder
import com.lightningkite.lightningserver.deprecations.path1
import com.lightningkite.lightningserver.html
import com.lightningkite.lightningserver.http.HttpHandler
import com.lightningkite.lightningserver.http.HttpResponse
import com.lightningkite.lightningserver.http.HttpStatus
import com.lightningkite.lightningserver.http.get
import com.lightningkite.lightningserver.http.post
import com.lightningkite.lightningserver.pathing.arg1
import com.lightningkite.lightningserver.typed.ApiHttpHandler
import com.lightningkite.lightningserver.typed.AuthAccess
import com.lightningkite.lightningserver.typed.ModelRestEndpoints
import com.lightningkite.lightningserver.typed.access
import com.lightningkite.lightningserver.typed.auth
import com.lightningkite.lightningserver.typed.modelInfo
import com.lightningkite.lightningserver.typed.route
import com.lightningkite.lskiteuistarter.*
import com.lightningkite.lskiteuistarter.UserAuth.RoleCache.userRole
import com.lightningkite.lskiteuistarter.utils.generateAndSendTicket
import com.lightningkite.lskiteuistarter.utils.ticketEmailHtml
import com.lightningkite.services.data.TypedData
import com.lightningkite.services.database.*
import kotlin.uuid.Uuid

object PurchaseEndpoints : ServerBuilder() {

    val info = Server.database.modelInfo<User, Purchase, Uuid>(
        auth = UserAuth.require(),
        permissions = {
            // System admins can do anything
            val isSystemAdmin = auth.userRole() >= UserRole.Admin
            val systemAdmin = if (isSystemAdmin) Condition.Always else Condition.Never

            // Purchases are read-only for org members (created by webhook/sync only)
            // Allow reading all purchases (will filter by org membership in handlers)
            ModelPermissions(
                create = systemAdmin, // Only system can create via webhook/sync
                read = Condition.Always, // Will filter by org membership
                update = systemAdmin,
                delete = systemAdmin,
            )
        },
        // Critical: Use signals to trigger email sending when purchase is created
        signals = {
            it.postCreate { purchase ->
                // Send ticket email automatically when purchase is created
                try {
                    generateAndSendTicket(purchase)
                } catch (e: Exception) {
                    // Log error but don't fail the purchase creation
                    println("Failed to send ticket email for purchase ${purchase._id}: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    )

    val rest = path include ModelRestEndpoints(info)

    val resendTicket = rest.detailPath.path("resend-ticket").post bind ApiHttpHandler(
        summary = "Resend Tickets",
        auth = UserAuth.require(),
        implementation = { _: Unit ->
            val purchase = info.table(this).get(route.arg1) ?: throw NotFoundException()
            generateAndSendTicket(purchase)
            Unit
        }
    )

    val viewTicketEmail = path.arg<Uuid>("id").path("view-ticket").get bind HttpHandler {
        val purchase = info.table(it.access(UserAuth.require())).get(it.path.arg1) ?: throw NotFoundException()
        val event = Server.events.info.table().get(purchase.eventId)
        HttpResponse.html {
            ticketEmailHtml(event, purchase)
        }
    }
}
