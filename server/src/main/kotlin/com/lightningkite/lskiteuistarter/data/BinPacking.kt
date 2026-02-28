// by Claude - extracted bin-packing algorithm for Stripe checkout line items
package com.lightningkite.lskiteuistarter.data

/**
 * Represents a Stripe price option with its ticket coverage.
 * @param priceId the Stripe price ID
 * @param ticketCount how many tickets one unit of this price covers
 * @param unitAmountCents the price in cents for one unit (used for cost optimization)
 * @param stripeQuantityMultiplier when building Stripe line items, multiply the pack count by this.
 *   For transform_quantity prices this equals divide_by (so Stripe's division cancels out).
 *   For regular prices this is 1.
 */
data class PriceOption(
    val priceId: String,
    val ticketCount: Int,
    val unitAmountCents: Long,
    val stripeQuantityMultiplier: Int = 1,
)

/**
 * Represents a line item for Stripe checkout.
 * @param priceId the Stripe price ID
 * @param quantity the quantity to pass to Stripe (already adjusted for transform_quantity)
 */
data class CheckoutLineItem(val priceId: String, val quantity: Long)

/**
 * Given a list of available price options and a desired ticket quantity,
 * compute the cheapest set of line items that covers at least the requested quantity.
 *
 * Uses cost-per-ticket sorting with greedy bin-packing, falling back to
 * the smallest available price to cover any remainder.
 *
 * The returned quantities are already multiplied by stripeQuantityMultiplier,
 * so they can be passed directly to Stripe.
 *
 * @return list of line items, or empty if no prices available
 */
// by Claude
fun computeCheckoutLineItems(prices: List<PriceOption>, quantity: Int): List<CheckoutLineItem> {
    if (prices.isEmpty() || quantity <= 0) return emptyList()

    // Sort by cost-per-ticket ascending (best value first)
    val sorted = prices
        .filter { it.ticketCount > 0 }
        .sortedBy { it.unitAmountCents.toDouble() / it.ticketCount }

    if (sorted.isEmpty()) return emptyList()

    val lineItems = mutableMapOf<String, Int>() // priceId -> pack count
    var remaining = quantity

    // Greedy: use best-value prices first, but only if they don't overshoot
    for (option in sorted) {
        if (remaining <= 0) break
        val count = remaining / option.ticketCount
        if (count > 0) {
            lineItems[option.priceId] = (lineItems[option.priceId] ?: 0) + count
            remaining -= count * option.ticketCount
        }
    }

    // Handle remainder: find the cheapest way to cover the leftover
    if (remaining > 0) {
        // Find the cheapest price that can cover the remainder in one unit
        val bestCover = sorted
            .filter { it.ticketCount >= remaining }
            .minByOrNull { it.unitAmountCents }

        if (bestCover != null) {
            lineItems[bestCover.priceId] = (lineItems[bestCover.priceId] ?: 0) + 1
        } else {
            // No single price covers the remainder; use the largest available
            val largest = sorted.maxByOrNull { it.ticketCount }!!
            lineItems[largest.priceId] = (lineItems[largest.priceId] ?: 0) + 1
            remaining -= largest.ticketCount
            while (remaining > 0) {
                lineItems[largest.priceId] = (lineItems[largest.priceId] ?: 0) + 1
                remaining -= largest.ticketCount
            }
        }
    }

    // Convert pack counts to Stripe quantities using the multiplier
    val priceMap = prices.associateBy { it.priceId }
    return lineItems.map { (priceId, packCount) ->
        val multiplier = priceMap[priceId]?.stripeQuantityMultiplier ?: 1
        CheckoutLineItem(priceId, (packCount * multiplier).toLong())
    }
}
