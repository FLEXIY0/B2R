package com.example.compose.jetchat.b2r

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** b2r: short clock label for feed/chat items, e.g. `14:32`. */
fun formatTime(epochMillis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMillis))

/** Fallback handle when a peer published no display mask. */
fun shortKeyLabel(key: String): String = "b2r_pub" + key.take(8) + "…"
