package `is`.xyz.mpv

/** Encode HTTP header lines for MPV's comma-separated string-list option. */
internal fun encodeMpvHttpHeaders(headers: String): String = headers
    .split("\r\n", "\n", "\r")
    .filter { it.isNotBlank() }
    .joinToString(",") { line -> line.replace("\\", "\\\\").replace(",", "\\,") }
