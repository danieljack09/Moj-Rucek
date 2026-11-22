package com.mojrucek.app.domain.usecase

import com.mojrucek.app.data.entity.ShoppingListItem

/**
 * Shopping items are now removed the moment they are resolved, and there is no "Купено" section.
 * Data written before that (databases, old backup files) can still contain items flagged as
 * purchased. This decides what to do with them without losing anything the user still has to decide:
 *  - purchased AND handled  -> already resolved under the old flow: delete;
 *  - purchased, not handled -> still waiting for "add to inventory / keep": make it a normal active
 *    item again so the user can still resolve it.
 * Items that are not purchased are never touched. Running it twice changes nothing.
 */
object LegacyPurchasedItems {

    data class Plan(val deleteIds: List<Long>, val reopen: List<ShoppingListItem>) {
        val isEmpty: Boolean get() = deleteIds.isEmpty() && reopen.isEmpty()
    }

    fun plan(items: List<ShoppingListItem>): Plan {
        val purchased = items.filter { it.isPurchased }
        return Plan(
            deleteIds = purchased.filter { it.isHandled }.map { it.id },
            reopen = purchased.filter { !it.isHandled }.map { it.copy(isPurchased = false, isHandled = false) }
        )
    }
}
