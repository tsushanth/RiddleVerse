package com.kreativekoala.riddleverse

/**
 * Tutorial manager for Discount Price Ordering puzzles
 */
class DiscountPriceTutorialManager {

    fun getTutorialSteps(): List<TutorialStep> = listOf(
        TutorialStep(
            id = "welcome",
            title = "Welcome to Price Ordering!",
            description = "Learn to order items from least to most expensive, considering discounts. Let's practice!",
            targetComponent = "none"
        ),
        TutorialStep(
            id = "instructions",
            title = "Read the Instructions",
            description = "Your goal is to tap items in order from least expensive to most expensive after discounts are applied.",
            targetComponent = "instructions"
        ),
        TutorialStep(
            id = "understand_items",
            title = "Understanding Items",
            description = "Each item shows its original price and any discount. Look carefully at both the price and discount percentage!",
            targetComponent = "items"
        ),
        TutorialStep(
            id = "calculate_prices",
            title = "Calculate Final Prices",
            description = "Items with discounts cost less than their original price. A 50% discount means you pay half the original price!",
            targetComponent = "items"
        ),
        TutorialStep(
            id = "select_cheapest",
            title = "Start with Cheapest",
            description = "Find the item that costs the least after discounts and tap it first. Look for the best deals!",
            targetComponent = "items",
            interactionRequired = true,
            expectedAction = "select_item"
        ),
        TutorialStep(
            id = "selection_order",
            title = "Selection Order",
            description = "See the number on selected items? This shows your selection order. Continue selecting from cheapest to most expensive!",
            targetComponent = "items"
        )
    )
}
