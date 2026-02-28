// by Claude - unit tests for checkout bin-packing algorithm
package com.lightningkite.lskiteuistarter

import com.lightningkite.lskiteuistarter.data.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BinPackingTest {

    // Helper: compute total tickets covered by line items
    // For transform_quantity prices (multiplier > 1), Stripe quantity = packs × multiplier,
    // so actual packs = stripeQty / multiplier, tickets = packs × ticketCount.
    // Since ticketCount == multiplier for transform prices, tickets = stripeQty.
    // For regular prices, multiplier=1, so tickets = stripeQty × ticketCount.
    private fun totalTickets(items: List<CheckoutLineItem>, prices: List<PriceOption>): Int {
        val priceMap = prices.associateBy { it.priceId }
        return items.sumOf { item ->
            val option = priceMap[item.priceId]!!
            val packs = item.quantity.toInt() / option.stripeQuantityMultiplier
            packs * option.ticketCount
        }
    }

    // Helper: compute total cost in cents (what Stripe will charge)
    // For transform_quantity: Stripe charges unitAmount per group. Groups = stripeQty / divideBy.
    // For regular: Stripe charges unitAmount × stripeQty.
    private fun totalCost(items: List<CheckoutLineItem>, prices: List<PriceOption>): Long {
        val priceMap = prices.associateBy { it.priceId }
        return items.sumOf { item ->
            val option = priceMap[item.priceId]!!
            if (option.stripeQuantityMultiplier > 1) {
                // transform_quantity: groups = qty / divideBy, cost = groups × unitAmount
                val groups = item.quantity / option.stripeQuantityMultiplier
                groups * option.unitAmountCents
            } else {
                option.unitAmountCents * item.quantity
            }
        }
    }

    @Test
    fun singlePriceSingleTicket() {
        val prices = listOf(PriceOption("price_single", 1, 5000))  // $50 each
        val result = computeCheckoutLineItems(prices, 3)
        assertEquals(1, result.size)
        assertEquals("price_single", result[0].priceId)
        assertEquals(3L, result[0].quantity)
        assertEquals(3, totalTickets(result, prices))
    }

    @Test
    fun fourPackAndSingle_fiveTickets() {
        // 4-pack at $165 (metadata, no transform_quantity) + $50 single
        val prices = listOf(
            PriceOption("price_4pack", 4, 16500),
            PriceOption("price_single", 1, 5000),
        )
        val result = computeCheckoutLineItems(prices, 5)

        assertEquals(5, totalTickets(result, prices))
        val resultMap = result.associate { it.priceId to it.quantity }
        assertEquals(1L, resultMap["price_4pack"], "Should use one 4-pack")
        assertEquals(1L, resultMap["price_single"], "Should use one single")
    }

    @Test
    fun fourPackOnly_fiveTickets() {
        val prices = listOf(PriceOption("price_4pack", 4, 16500))
        val result = computeCheckoutLineItems(prices, 5)
        val tickets = totalTickets(result, prices)
        assertTrue(tickets >= 5, "Should cover at least 5 tickets, got $tickets")
    }

    @Test
    fun exactMultiple() {
        val prices = listOf(
            PriceOption("price_4pack", 4, 16500),
            PriceOption("price_single", 1, 5000),
        )
        val result = computeCheckoutLineItems(prices, 8)
        assertEquals(8, totalTickets(result, prices))
        val resultMap = result.associate { it.priceId to it.quantity }
        assertEquals(2L, resultMap["price_4pack"])
        assertEquals(null, resultMap["price_single"])
    }

    @Test
    fun prefersCheaperPerTicket() {
        val prices = listOf(
            PriceOption("price_2pack", 2, 8000),   // $40/ticket
            PriceOption("price_single", 1, 5000),    // $50/ticket
        )
        val result = computeCheckoutLineItems(prices, 3)
        assertEquals(3, totalTickets(result, prices))
        val resultMap = result.associate { it.priceId to it.quantity }
        assertEquals(1L, resultMap["price_2pack"])
        assertEquals(1L, resultMap["price_single"])
    }

    @Test
    fun emptyPrices() {
        assertTrue(computeCheckoutLineItems(emptyList(), 5).isEmpty())
    }

    @Test
    fun zeroQuantity() {
        assertTrue(computeCheckoutLineItems(listOf(PriceOption("p", 1, 5000)), 0).isEmpty())
    }

    @Test
    fun threeTiers() {
        val prices = listOf(
            PriceOption("price_vip", 10, 40000),
            PriceOption("price_4pack", 4, 16500),
            PriceOption("price_single", 1, 5000),
        )
        val result = computeCheckoutLineItems(prices, 15)
        assertEquals(15, totalTickets(result, prices))
        val resultMap = result.associate { it.priceId to it.quantity }
        assertEquals(1L, resultMap["price_vip"])
        assertEquals(1L, resultMap["price_4pack"])
        assertEquals(1L, resultMap["price_single"])
    }

    // --- transform_quantity tests ---
    // These simulate Stripe prices with transform_quantity.divide_by.
    // ticketCount = divideBy, stripeQuantityMultiplier = divideBy.
    // The algorithm outputs Stripe quantities (packs × multiplier).

    @Test
    fun transformQuantity_groupOf4_plus_single_6tickets() {
        // The user's actual scenario: $165 per group of 4 (transform_quantity) + $50 single
        val prices = listOf(
            PriceOption("price_group4", ticketCount = 4, unitAmountCents = 16500, stripeQuantityMultiplier = 4),
            PriceOption("price_single", ticketCount = 1, unitAmountCents = 5000),
        )
        val result = computeCheckoutLineItems(prices, 6)

        val resultMap = result.associate { it.priceId to it.quantity }
        // Algorithm: 6/4 = 1 pack (4 tickets), remainder 2 → 2 singles
        // Stripe qty for group: 1 pack × 4 multiplier = 4
        assertEquals(4L, resultMap["price_group4"], "Should pass qty=4 to Stripe (1 pack × 4)")
        assertEquals(2L, resultMap["price_single"], "Should use 2 singles for remainder")

        assertEquals(6, totalTickets(result, prices))
        // Cost: 1 group × $165 + 2 × $50 = $265
        assertEquals(26500L, totalCost(result, prices))
    }

    @Test
    fun transformQuantity_groupOf4_12tickets() {
        val prices = listOf(
            PriceOption("price_group4", ticketCount = 4, unitAmountCents = 16500, stripeQuantityMultiplier = 4),
            PriceOption("price_single", ticketCount = 1, unitAmountCents = 5000),
        )
        val result = computeCheckoutLineItems(prices, 12)

        val resultMap = result.associate { it.priceId to it.quantity }
        // 12/4 = 3 packs, no remainder. Stripe qty = 3 × 4 = 12
        assertEquals(12L, resultMap["price_group4"], "Should pass qty=12 to Stripe (3 packs × 4)")
        assertEquals(null, resultMap["price_single"])

        assertEquals(12, totalTickets(result, prices))
        // Cost: 3 groups × $165 = $495
        assertEquals(49500L, totalCost(result, prices))
    }

    @Test
    fun transformQuantity_groupOf4_only_5tickets() {
        // Only 4-pack available, 5 tickets → 2 packs (covers 8, overshoots by 3)
        val prices = listOf(
            PriceOption("price_group4", ticketCount = 4, unitAmountCents = 16500, stripeQuantityMultiplier = 4),
        )
        val result = computeCheckoutLineItems(prices, 5)

        val resultMap = result.associate { it.priceId to it.quantity }
        // 5/4 = 1 pack + remainder 1 → needs another pack. 2 packs × 4 = 8
        assertEquals(8L, resultMap["price_group4"], "Should pass qty=8 to Stripe (2 packs × 4)")
        assertTrue(totalTickets(result, prices) >= 5)
    }

    @Test
    fun transformQuantity_groupOf4_exactMultiple() {
        val prices = listOf(
            PriceOption("price_group4", ticketCount = 4, unitAmountCents = 16500, stripeQuantityMultiplier = 4),
        )
        val result = computeCheckoutLineItems(prices, 8)

        assertEquals(8L, result.single().quantity, "8 tickets = 2 packs × 4 multiplier")
        assertEquals(8, totalTickets(result, prices))
    }
}
