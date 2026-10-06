package com.sabalapps.arrowescape.game

/**
 * Where a level sits in the catalogue and what follows it. Everything here is
 * derived from [Levels.ALL], so adding a level to the catalogue is all it takes
 * to extend the game.
 */
object LevelProgression {

    val all: List<Level> get() = Levels.ALL

    val first: Level get() = all.first()

    val last: Level get() = all.last()

    val count: Int get() = all.size

    /** 0-based position of [current] in the catalogue, or -1 when unknown. */
    fun indexOf(current: Level): Int = all.indexOfFirst { it.id == current.id }

    /** The level after [current], or [current] again when it is the last one. */
    fun next(current: Level): Level {
        val index = indexOf(current)
        if (index == -1) return first
        return all.getOrNull(index + 1) ?: current
    }

    /** True once there is nothing new to advance to. */
    fun isLast(current: Level): Boolean = current.id == last.id

    /** The level with [id], falling back to the first one for unknown ids. */
    fun levelOrFirst(id: Int): Level = Levels.byId(id) ?: first
}
