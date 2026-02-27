// by Claude - Organization membership management endpoints
package com.lightningkite.lskiteuistarter.data

import com.lightningkite.lightningserver.auth.id
import com.lightningkite.lightningserver.auth.require
import com.lightningkite.lightningserver.definition.builder.ServerBuilder
import com.lightningkite.lightningserver.http.post
import com.lightningkite.lightningserver.typed.ApiHttpHandler
import com.lightningkite.lightningserver.typed.ModelRestEndpoints
import com.lightningkite.lightningserver.typed.auth
import com.lightningkite.lightningserver.typed.modelInfo
import com.lightningkite.EmailAddress
import com.lightningkite.lskiteuistarter.*
import com.lightningkite.lskiteuistarter.UserAuth.RoleCache.userRole
import com.lightningkite.services.database.*
import kotlinx.coroutines.flow.firstOrNull
import kotlin.uuid.Uuid

object OrganizationMembershipEndpoints : ServerBuilder() {

    val info = Server.database.modelInfo<User, OrganizationMembership, Uuid>(
        auth = UserAuth.require(),
        permissions = {
            // System admins can do anything
            val isSystemAdmin = auth.userRole() >= UserRole.Admin
            val systemAdmin = if (isSystemAdmin) Condition.Always else Condition.Never

            // Allow reading all memberships (will filter in handlers)
            // Only system admins can create/update/delete directly
            ModelPermissions(
                create = systemAdmin,
                read = Condition.Always, // Will filter by organization access
                update = systemAdmin,
                delete = systemAdmin,
            )
        }
    )

    val rest = path include ModelRestEndpoints(info)

    // Custom endpoint to add a member to an organization
    val addMember = path.path("add-member").post bind ApiHttpHandler(
        summary = "Add member to organization",
        auth = UserAuth.require(),
        implementation = { input: AddMemberInput ->
            // Check if user is admin of the organization
            val isOrgAdmin = Server.database().collection<OrganizationMembership>()
                .find(condition {
                    (it.organizationId eq input.organizationId) and
                    (it.userId eq auth.id) and
                    (it.role eq OrgRole.Admin)
                }).firstOrNull() != null

            val isSystemAdmin = auth.userRole() >= UserRole.Admin

            if (!isOrgAdmin && !isSystemAdmin) {
                throw IllegalAccessException("Only organization admins can add members")
            }

            // Find user by email
            val targetUser = Server.database().collection<User>()
                .find(condition { it.email eq EmailAddress(input.userEmail) })
                .firstOrNull()
                ?: throw IllegalArgumentException("User with email '${input.userEmail}' not found")

            // Check if membership already exists
            val existing = Server.database().collection<OrganizationMembership>()
                .find(condition {
                    (it.organizationId eq input.organizationId) and (it.userId eq targetUser._id)
                }).firstOrNull()

            if (existing != null) {
                throw IllegalArgumentException("User is already a member of this organization")
            }

            // Create membership
            val membership = OrganizationMembership(
                organizationId = input.organizationId,
                userId = targetUser._id,
                role = input.role
            )

            Server.database().collection<OrganizationMembership>().insertOne(membership)
            membership
        }
    )
}
