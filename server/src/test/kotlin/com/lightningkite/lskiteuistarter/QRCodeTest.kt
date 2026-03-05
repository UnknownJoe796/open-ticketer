// by Claude - Unit tests for QR code generation and verification
package com.lightningkite.lskiteuistarter

import com.lightningkite.lightningserver.runtime.test.TestRunner
import com.lightningkite.lightningserver.runtime.test.test
import com.lightningkite.lightningserver.settings.set
import com.lightningkite.lskiteuistarter.utils.*
import com.lightningkite.services.database.Database
import com.lightningkite.services.database.jsonfile.JsonFileDatabase
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

class QRCodeTest {

    init {
        JsonFileDatabase
    }

    private inline fun testServer(crossinline action: suspend context(TestRunner<Server>) Server.() -> Unit) {
        Server.test(settings = { Server.database set Database.Settings("ram") }) {
            val server = this
            runBlocking { with(server) { action() } }
        }
    }

    private fun makePurchase(id: Uuid = Uuid.random(), sessionId: String = "cs_test") = Purchase(
        _id = id,
        eventId = "test-event",
        stripeCheckoutSessionId = sessionId,
        organizationId = Uuid.random(),
        quantity = 2,
        customerEmail = com.lightningkite.EmailAddress("test@example.com"),
        customerName = "Test User",
        amountTotal = 5000,
        currency = "usd",
        purchasedAt = Clock.System.now(),
    )

    @Test
    fun generateAndVerifyRoundTrip() = testServer {
        val purchase = makePurchase()
        val qrData = generateQRCode(purchase)
        val verified = verifyQRSignature(qrData)

        assertNotNull(verified, "Valid QR code should verify successfully")
        assertEquals(purchase._id, verified.purchaseId)
    }

    @Test
    fun compactFormat() = testServer {
        val purchase = makePurchase()
        val qrData = generateQRCode(purchase)

        // Should be two base64url parts separated by a dot
        val parts = qrData.split('.')
        assertEquals(2, parts.size, "QR data should have exactly 2 parts separated by '.'")
        // UUID part: 16 bytes -> 22 base64url chars (no padding)
        assertEquals(22, parts[0].length, "UUID part should be 22 chars (16 bytes base64url)")
        assertTrue(parts[1].isNotEmpty(), "Signature part should not be empty")
    }

    @Test
    fun tamperedPurchaseIdRejected() = testServer {
        val purchase = makePurchase()
        val qrData = generateQRCode(purchase)
        val parts = qrData.split('.')

        // Replace UUID part with a different UUID's bytes
        val differentId = Uuid.random()
        val buf = java.nio.ByteBuffer.allocate(16)
        val javaUuid = differentId.toJavaUuid()
        buf.putLong(javaUuid.mostSignificantBits)
        buf.putLong(javaUuid.leastSignificantBits)
        val fakeIdPart = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(buf.array())

        val tampered = "$fakeIdPart.${parts[1]}"
        assertNull(verifyQRSignature(tampered), "Tampered purchase ID should fail verification")
    }

    @Test
    fun tamperedSignatureRejected() = testServer {
        val purchase = makePurchase()
        val qrData = generateQRCode(purchase)
        val parts = qrData.split('.')

        val tampered = "${parts[0]}.AAAAAAAAAAAAAAAA"
        assertNull(verifyQRSignature(tampered), "Tampered signature should fail verification")
    }

    @Test
    fun garbageInputRejected() = testServer {
        assertNull(verifyQRSignature("not valid at all"), "Garbage input should return null")
        assertNull(verifyQRSignature(""), "Empty string should return null")
        assertNull(verifyQRSignature("onlyonepart"), "Single part should return null")
        assertNull(verifyQRSignature("short.sig"), "Short ID part should return null")
    }

    @Test
    fun differentPurchasesGetDifferentQRCodes() = testServer {
        val qr1 = generateQRCode(makePurchase(sessionId = "cs_a"))
        val qr2 = generateQRCode(makePurchase(sessionId = "cs_b"))

        assertTrue(qr1 != qr2, "Different purchases should produce different QR codes")
    }

    @Test
    fun qrImageIsValidPng() {
        val imageBytes = generateQRImage("test data")
        // PNG magic bytes: 0x89 0x50 0x4E 0x47
        assertTrue(imageBytes.size > 4, "Image should not be empty")
        assertEquals(0x89.toByte(), imageBytes[0], "PNG magic byte 0")
        assertEquals(0x50.toByte(), imageBytes[1], "PNG magic byte 1 (P)")
        assertEquals(0x4E.toByte(), imageBytes[2], "PNG magic byte 2 (N)")
        assertEquals(0x47.toByte(), imageBytes[3], "PNG magic byte 3 (G)")
    }
}
