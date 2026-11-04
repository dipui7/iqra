package app.iqra.engine

/**
 * What the reader plays: ayat [from]..[to] (1-based), each [perAyah] times, the whole range
 * [loops] times (0 = until stopped). This is how many people memorise: one ayah over and over,
 * then a few ayat together.
 */
data class RepeatPlan(val from: Int, val to: Int, val perAyah: Int = 1, val loops: Int = 1) {
    init {
        require(from in 1..to) { "bad range $from..$to" }
        require(perAyah >= 1 && loops >= 0)
    }

    /** Where playback is: the ayah, which repetition of it (0-based), which pass over the range. */
    data class Position(val ayah: Int, val rep: Int = 0, val loop: Int = 0)

    fun start(at: Int = from) = Position(at.coerceIn(from, to))

    /** The next thing to play, or null when the plan is done. */
    fun next(p: Position): Position? = when {
        p.rep + 1 < perAyah -> p.copy(rep = p.rep + 1)
        p.ayah < to -> Position(p.ayah + 1, 0, p.loop)
        loops == 0 || p.loop + 1 < loops -> Position(from, 0, p.loop + 1)
        else -> null
    }
}
