// by Claude - Organization management endpoints
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
import kotlin.uuid.Uuid

object OrganizationEndpoints : ServerBuilder() {

    // by Claude - filter read permission by membership
    val info = Server.database.modelInfo<User, Organization, Uuid>(
        auth = UserAuth.require(),
        permissions = {
            val isSystemAdmin = auth.userRole() >= UserRole.Admin
            val systemAdmin = if (isSystemAdmin) Condition.Always else Condition.Never

            // System admins see all orgs; others see only orgs they belong to
            val readCondition = if (isSystemAdmin) {
                Condition.Always
            } else {
                val memberOrgIds = Server.database().collection<OrganizationMembership>()
                    .find(condition { it.userId eq auth.id })
                    .toList()
                    .map { it.organizationId }
                condition<Organization> { it._id inside memberOrgIds }
            }

            ModelPermissions(
                create = Condition.Always,
                read = readCondition,
                update = systemAdmin,
                delete = systemAdmin,
            )
        }
    )

    val rest = path include ModelRestEndpoints(info)
}
