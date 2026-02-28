// by Claude - EventWithTickets CRUD endpoints
package com.lightningkite.lskiteuistarter.data

import com.lightningkite.lightningserver.auth.id
import com.lightningkite.lightningserver.auth.require
import com.lightningkite.lightningserver.definition.builder.ServerBuilder
import com.lightningkite.lightningserver.typed.ModelRestEndpoints
import com.lightningkite.lightningserver.typed.auth
import com.lightningkite.lightningserver.typed.modelInfo
import com.lightningkite.lskiteuistarter.*
import com.lightningkite.lskiteuistarter.UserAuth.RoleCache.userRole
import com.lightningkite.services.database.*
import kotlinx.coroutines.flow.toList

// by Claude - ID is the Stripe product ID (String), not a Uuid
object EventWithTicketsEndpoints : ServerBuilder() {

    val info = Server.database.modelInfo<User, EventWithTickets, String>(
        auth = UserAuth.require(),
        permissions = {
            val isSystemAdmin = auth.userRole() >= UserRole.Admin
            val systemAdmin = if (isSystemAdmin) Condition.Always else Condition.Never

            // Org admins can manage events for their orgs; system admins see all
            val readCondition = if (isSystemAdmin) {
                Condition.Always
            } else {
                val adminOrgIds = Server.database().collection<OrganizationMembership>()
                    .find(condition { (it.userId eq auth.id) and (it.role eq OrgRole.Admin) })
                    .toList()
                    .map { it.organizationId }
                condition<EventWithTickets> { it.organizationId inside adminOrgIds }
            }

            ModelPermissions(
                create = readCondition,
                read = readCondition,
                update = readCondition,
                delete = systemAdmin,
            )
        }
    )

    val rest = path include ModelRestEndpoints(info)
}
