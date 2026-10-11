package com.jpcottin.composespaceinvaders.engine

import kotlin.system.exitProcess

/**
 * The Auto Play bench: plays seeded, headless rounds with the autopilot and
 * reports how often it clears each level, how it dies, how long a clear
 * takes and how it spends its lasers. Every Auto Play change is measured
 * here before it ships (the numbers in the commit messages come from this
 * program).
 *
 *   ./gradlew :engine:run --args="[--runs N] [--width W --height H] [--levels] [--campaigns]
 *                                 [--min-campaign-win PCT] [--seed S] [--level L] [--trace]"
 *
 * --levels    plays each level 1..10 on its own, N seeds each (the default
 *             when nothing is selected).
 * --campaigns plays N full campaigns from level 1.
 * --min-campaign-win PCT exits 1 when fewer than PCT% of campaigns are won:
 *             the CI regression guard.
 * --seed S    plays seed S alone instead of seeds 1..N: the way to replay
 *             the one run that died in a full pass.
 * --level L   plays level L alone (with --levels).
 * --trace     prints the scene (ship, bombs, autopilot intent) at every
 *             lost life and at a timeout.
 */
private const val DT = 1f / 60f
private const val LEVEL_TIMEOUT = 300f     // seconds before a level counts as stuck
private var trace = false

private class Stats {
    var runs = 0; var cleared = 0; var timeouts = 0
    var livesLost = 0
    var byBomb = 0; var byCollision = 0; var byInvasion = 0
    var seconds = 0.0            // summed over cleared runs
    var score = 0L
    var shots = 0L; var lost = 0L; var bombsShot = 0L; var kills = 0L
}

private fun dumpScene(g: Game, t: Float) {
    println("  L%d t=%.1f lives=%d ship x=%.3f aiTarget=%.3f move=%b fire=%b aliens=%d bombs=%d lasers=%d".format(
        g.level, t, g.lives, g.shipX, g.aiTarget, g.aiHasTarget, g.aiFiring, g.aliveAliens, g.bombs.size, g.lasers.size))
    for (b in g.bombs) println("    bomb x=%.3f y=%.3f v=(%.2f, %.2f)".format(b.x, b.y, b.vx, b.vy))
}

/** Plays from the current PLAYING state until the level ends or the game
 *  does. Returns the seconds played and whether the level was cleared. */
private fun playLevel(g: Game, s: Stats): Pair<Float, Boolean> {
    var seconds = 0f
    var lives = g.lives
    val kills0 = g.aliveAliens
    val shots0 = g.shotsFired; val lost0 = g.lasersLost; val bombs0 = g.bombsShot
    while (g.state == Game.State.PLAYING) {
        g.update(DT)
        seconds += DT
        if (g.lives < lives) {
            s.livesLost += lives - g.lives
            lives = g.lives
            if (trace) dumpScene(g, seconds)
        }
        if (seconds > LEVEL_TIMEOUT) {
            s.timeouts++
            if (trace) { println("  timeout:"); dumpScene(g, seconds) }
            break
        }
    }
    s.kills += kills0 - g.aliveAliens
    s.shots += g.shotsFired - shots0
    s.lost += g.lasersLost - lost0
    s.bombsShot += g.bombsShot - bombs0
    if (g.state == Game.State.GAME_OVER) when (g.deathCause) {
        Game.DeathCause.BOMB -> s.byBomb++
        Game.DeathCause.COLLISION -> s.byCollision++
        Game.DeathCause.INVASION -> s.byInvasion++
        Game.DeathCause.NONE -> Unit
    }
    return seconds to (g.state == Game.State.LEVEL_CLEAR || g.state == Game.State.WIN)
}

private fun printStats(label: String, s: Stats) {
    val avg = if (s.cleared > 0) s.seconds / s.cleared else 0.0
    println("%-10s cleared %5.1f%%  lives lost/run %.2f  died bomb %d coll %d inv %d  timeouts %2d  avg %6.1f s  lasers/kill %.2f  lost %4.1f%%  avg score %7d".format(
        label, 100.0 * s.cleared / s.runs, s.livesLost.toDouble() / s.runs, s.byBomb, s.byCollision, s.byInvasion,
        s.timeouts, avg, if (s.kills > 0) s.shots.toDouble() / s.kills else 0.0,
        if (s.shots > 0) 100.0 * s.lost / s.shots else 0.0, if (s.runs > 0) s.score / s.runs else 0L))
}

private fun seededGame(seed: Int, w: Int, h: Int): Game = Game().apply {
    setAspect(w.toFloat() / h)
    seed((seed.toLong() * 2654435761L).toInt())
    debug.startGame()
    autoPlayActive = true
}

fun main(args: Array<String>) {
    var runs = 100; var w = 1080; var h = 2400
    var levels = false; var campaigns = false
    var minCampaignWin = -1.0
    var onlySeed = 0; var onlyLevel = 0
    var i = 0
    while (i < args.size) {
        val next = { if (i + 1 < args.size) args[++i].toIntOrNull() ?: 0 else 0 }
        when (args[i]) {
            "--runs" -> runs = next()
            "--width" -> w = next()
            "--height" -> h = next()
            "--levels" -> levels = true
            "--campaigns" -> campaigns = true
            "--trace" -> trace = true
            "--seed" -> onlySeed = next()
            "--level" -> onlyLevel = next()
            "--min-campaign-win" -> minCampaignWin = if (i + 1 < args.size) args[++i].toDoubleOrNull() ?: -1.0 else -1.0
            else -> { System.err.println("unknown argument ${args[i]}"); exitProcess(2) }
        }
        i++
    }
    if (minCampaignWin >= 0.0) campaigns = true
    if (!levels && !campaigns) levels = true
    if (runs <= 0 || w <= 0 || h <= 0) { System.err.println("bad size or run count"); exitProcess(2) }
    if (onlyLevel != 0 && onlyLevel !in 1..Levels.COUNT) { System.err.println("no level $onlyLevel"); exitProcess(2) }
    val seeds = if (onlySeed != 0) listOf(onlySeed) else (1..runs).toList()
    val levelRange = if (onlyLevel != 0) onlyLevel..onlyLevel else 1..Levels.COUNT
    println("Auto Play bench: ${w}x$h, " + (if (onlySeed != 0) "seed $onlySeed" else "$runs seeded runs") +
        (if (onlyLevel != 0) ", level $onlyLevel" else ""))

    if (levels) {
        val total = Stats()
        for (level in levelRange) {
            val s = Stats()
            for (seed in seeds) {
                val g = seededGame(seed, w, h)
                if (level > 1) g.debug.startLevel(level)
                s.runs++
                val (sec, ok) = playLevel(g, s)
                if (ok) { s.cleared++; s.seconds += sec }
                s.score += g.score
            }
            printStats("level %2d".format(level), s)
            total.runs += s.runs; total.cleared += s.cleared; total.timeouts += s.timeouts
            total.livesLost += s.livesLost; total.seconds += s.seconds; total.score += s.score
            total.byBomb += s.byBomb; total.byCollision += s.byCollision; total.byInvasion += s.byInvasion
            total.shots += s.shots; total.lost += s.lost; total.bombsShot += s.bombsShot; total.kills += s.kills
        }
        printStats("all", total)
    }

    if (campaigns) {
        val s = Stats()
        var won = 0
        var wonSeconds = 0.0
        val reached = IntArray(Levels.COUNT + 1)
        for (seed in seeds) {
            val g = seededGame(seed, w, h)
            var total = 0f
            s.runs++
            while (true) {
                reached[g.level]++
                val (sec, ok) = playLevel(g, s)
                total += sec
                if (!ok || g.state == Game.State.WIN) break
                // LEVEL_CLEAR: let the interlude run into the next level.
                while (g.state == Game.State.LEVEL_CLEAR) g.update(DT)
            }
            if (g.state == Game.State.WIN) { won++; wonSeconds += total }
            s.score += g.score
        }
        println("campaigns  won %5.1f%%  lives lost/run %.2f  died bomb %d coll %d inv %d  timeouts %2d  avg won %6.1f s  lasers/kill %.2f  lost %4.1f%%  avg score %7d".format(
            100.0 * won / s.runs, s.livesLost.toDouble() / s.runs, s.byBomb, s.byCollision, s.byInvasion, s.timeouts,
            if (won > 0) wonSeconds / won else 0.0, if (s.kills > 0) s.shots.toDouble() / s.kills else 0.0,
            if (s.shots > 0) 100.0 * s.lost / s.shots else 0.0, s.score / s.runs))
        println("reached    " + (1..Levels.COUNT).joinToString("  ") { "L$it %3d".format(reached[it]) })
        if (minCampaignWin >= 0.0 && 100.0 * won / s.runs < minCampaignWin) {
            println("FAIL: campaign win rate below %.1f%%".format(minCampaignWin))
            exitProcess(1)
        }
    }
}
