package app.iqra.engine

import app.iqra.data.Haraka
import app.iqra.data.Letter
import app.iqra.data.Rule

/**
 * Names of the things a question practises. Progress keeps a review schedule per skill, and
 * practice sessions are built from the skills that are due or often answered wrongly.
 */
object Skills {
    fun letter(l: Letter) = "letter:${l.key}"
    fun vowel(h: Haraka) = "vowel:${h.sound}"
    fun rule(r: Rule) = "rule:${r.key}"
}
