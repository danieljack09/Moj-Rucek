package com.mojrucek.app.data.entity

import java.util.UUID

/**
 * Room v4 adds the same few columns to every table that holds a user's own data
 * (see docs/room-v4-ownership-and-sync-design.md):
 *
 *  - `ownerUid`  the Firebase user the row belongs to; "" = not claimed yet. Not written to backup files.
 *  - `uuid`      a permanent identity that is the same on every phone (sync). Written to backups.
 *  - `updatedAt` when the row last changed (ms since 1970).
 *  - `deletedAt` reserved for sync (a deleted row is kept as a marker); always empty for now.
 *  - `dirty`     "changed since the last sync"; not used until sync exists.
 *
 * In this step the columns exist but nothing reads or writes them yet, so the app behaves as before.
 */
fun newUuid(): String = UUID.randomUUID().toString()
