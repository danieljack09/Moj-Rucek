package com.mojrucek.app.repository

import com.mojrucek.app.data.entity.Recipe

/**
 * How a recipe is named in the favourites table. A recipe from the server is the same on every phone and
 * for every account, so it is named by its server id ("s:42"); a user's own recipe by its permanent uuid ("u:...").
 * (A local database id would not do: it differs between phones and changes when a backup is restored.)
 */
object FavoriteRefs {
    fun of(serverId: Long?, uuid: String): String = if (serverId != null) "s:$serverId" else "u:$uuid"
    fun of(recipe: Recipe): String = of(recipe.serverId, recipe.uuid)
}
