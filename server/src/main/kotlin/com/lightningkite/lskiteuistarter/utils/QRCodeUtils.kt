// by Claude - QR code generation and verification utilities
package com.lightningkite.lskiteuistarter.utils

import com.google.zxing.BarcodeFormat
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.qrcode.QRCodeWriter
import com.lightningkite.lightningserver.runtime.ServerRuntime
import com.lightningkite.lightningserver.definition.secretBasis
import com.lightningkite.lightningserver.encryption.signer
import com.lightningkite.lightningserver.encryption.sign
import com.lightningkite.lightningserver.encryption.verify
import com.lightningkite.lskiteuistarter.Purchase
import com.lightningkite.lskiteuistarter.Server
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class QRPayload(
    val purchaseId: Uuid,
    val timestamp: Instant,
    val signature: String
)

/**
 * Generates a signed QR code payload for a purchase.
 * The payload is signed with HMAC-SHA512 to prevent tampering.
 */
context(runtime: ServerRuntime)
suspend fun generateQRCode(purchase: Purchase): String {
    val payload = QRPayload(
        purchaseId = purchase._id,
        timestamp = kotlin.time.Clock.System.now(),
        signature = ""
    )

    val signer = secretBasis.signer("ticket-qr").await()
    val dataToSign = "${payload.purchaseId}|${payload.timestamp}"
    val signature = signer.sign(dataToSign)
    val signedPayload = payload.copy(signature = signature)

    return Json.encodeToString(signedPayload)
}

/**
 * Verifies a QR code signature and returns the payload if valid.
 * Returns null if the signature is invalid.
 */
context(runtime: ServerRuntime)
suspend fun verifyQRSignature(qrData: String): QRPayload? {
    return try {
        val payload = Json.decodeFromString<QRPayload>(qrData)
        val signer = secretBasis.signer("ticket-qr").await()
        val dataToVerify = "${payload.purchaseId}|${payload.timestamp}"

        if (signer.verify(dataToVerify, payload.signature)) {
            payload
        } else null
    } catch (e: Exception) {
        null
    }
}

/**
 * Generates a QR code image as PNG bytes from the given data string.
 */
fun generateQRImage(qrData: String): ByteArray {
    val writer = QRCodeWriter()
    val bitMatrix = writer.encode(qrData, BarcodeFormat.QR_CODE, 512, 512)
    val image = MatrixToImageWriter.toBufferedImage(bitMatrix)

    val baos = ByteArrayOutputStream()
    ImageIO.write(image, "PNG", baos)
    return baos.toByteArray()
}
