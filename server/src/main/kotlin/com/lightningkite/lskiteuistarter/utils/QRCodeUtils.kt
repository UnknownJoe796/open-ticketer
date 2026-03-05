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
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

// by Claude - JWT-style compact QR format: base64url(uuid_bytes).base64url(signature)
data class QRPayload(
    val purchaseId: Uuid,
)

private val b64Encoder = Base64.getUrlEncoder().withoutPadding()
private val b64Decoder = Base64.getUrlDecoder()

private fun Uuid.toBytes(): ByteArray {
    val java = this.toJavaUuid()
    return ByteBuffer.allocate(16)
        .putLong(java.mostSignificantBits)
        .putLong(java.leastSignificantBits)
        .array()
}

private fun ByteArray.toUuid(): Uuid {
    val buf = ByteBuffer.wrap(this)
    return java.util.UUID(buf.getLong(), buf.getLong()).toKotlinUuid()
}

/**
 * Generates a compact signed QR code string for a purchase.
 * Format: base64url(uuid_bytes).base64url(hmac_signature)
 */
context(runtime: ServerRuntime)
suspend fun generateQRData(purchase: Purchase): String = generateQRData(purchase._id)
/**
 * Generates a compact signed QR code string for a purchase.
 * Format: base64url(uuid_bytes).base64url(hmac_signature)
 */
context(runtime: ServerRuntime)
suspend fun generateQRData(id: Uuid): String {
    val idBytes = id.toBytes()
    val idPart = b64Encoder.encodeToString(idBytes)
    val signer = secretBasis.signer("ticket-qr").await()
    val signature = signer.sign(idBytes)
    val sigPart = b64Encoder.encodeToString(signature)
    return "$idPart.$sigPart"
}

/**
 * Verifies a compact QR code signature and returns the payload if valid.
 * Returns null if the format is wrong or the signature is invalid.
 */
context(runtime: ServerRuntime)
suspend fun verifyQRSignature(qrData: String): QRPayload? {
    return try {
        val parts = qrData.split('.', limit = 2)
        if (parts.size != 2) return null
        val idBytes = b64Decoder.decode(parts[0])
        if (idBytes.size != 16) return null
        val signature = b64Decoder.decode(parts[1])
        val signer = secretBasis.signer("ticket-qr").await()
        if (signer.verify(idBytes, signature)) {
            QRPayload(purchaseId = idBytes.toUuid())
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
