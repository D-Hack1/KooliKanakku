package com.koolikanaku.ai

import android.content.Context

object LabelLoader {

    const val LABELS_ASSET = "labels.txt"

    fun load(context: Context, asset: String = LABELS_ASSET): List<String> =
        context.assets.open(asset).bufferedReader().use { parse(it.readText()) }

    /** One label per line, in class-index order. Blank trailing lines are dropped. */
    fun parse(text: String): List<String> =
        text.lines().map { it.trim() }.dropLastWhile { it.isEmpty() }
}
