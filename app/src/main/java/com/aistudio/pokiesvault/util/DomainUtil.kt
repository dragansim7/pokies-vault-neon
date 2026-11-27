package com.aistudio.pokiesvault.util

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

object DomainUtil {
    private val schemeRegex = Regex("^https?://", RegexOption.IGNORE_CASE)
    private val publicDomainRegex =
        Regex("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)+$")
    private val ipv4Regex = Regex("^\\d+\\.\\d+\\.\\d+\\.\\d+$")
    private val tokenSplitRegex = Regex("[\\s,;]+")

    fun normalizeDomain(value: String): String {
        var domain = value.trim().lowercase(Locale.US)
        domain = domain.replace(schemeRegex, "")
        domain = domain.substringBefore("/").substringBefore("?").substringBefore("#").substringBefore(":")
        domain = domain.removePrefix("www.").trimEnd('.')
        return domain
    }

    fun isPublicDomain(domain: String): Boolean {
        if (domain.isBlank() || domain.length > 253) return false
        if (!publicDomainRegex.matches(domain)) return false
        if (ipv4Regex.matches(domain)) return false
        if (domain == "localhost" ||
            domain.endsWith(".local") ||
            domain.endsWith(".internal") ||
            domain.endsWith(".localhost")
        ) {
            return false
        }
        return true
    }

    private val searchStripRegex = Regex("[^a-z0-9]")
    private val searchSplitRegex = Regex("[^a-z0-9]+")

    fun matchesSearch(domain: String, query: String): Boolean {
        val raw = query.trim().lowercase(Locale.US)
        if (raw.isEmpty()) return true
        val cleaned = raw.replace(schemeRegex, "").removePrefix("www.")
        val normQuery = cleaned.substringBefore("/").substringBefore("?").substringBefore("#")
            .replace(searchStripRegex, "")
        if (normQuery.isEmpty()) return true
        val normDomain = domain.lowercase(Locale.US).replace(searchStripRegex, "")
        if (normDomain.contains(normQuery)) return true
        val tokens = cleaned.split(searchSplitRegex).filter { it.isNotEmpty() }
        return tokens.isEmpty() || tokens.all { normDomain.contains(it) }
    }

    fun extractDomainsFromText(rawText: String): List<String> {
        if (rawText.isBlank()) return emptyList()
        val seen = LinkedHashSet<String>()
        rawText.split(tokenSplitRegex).forEach { token ->
            val cleaned = normalizeDomain(token.trim('"', '\'', '(', ')', '[', ']'))
            if (isPublicDomain(cleaned)) {
                seen.add(cleaned)
            }
        }
        return seen.toList()
    }
}

/**
 * Thread-safe currency, numeric, and timestamp formatting utilities.
 * Never holds mutable Java Format instances across threads.
 */
object FormatUtil {
    private val melbourneZone: ZoneId = ZoneId.of("Australia/Melbourne")
    private val addedFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd/MM/yyyy · h:mm a", Locale.US).withZone(melbourneZone)
    private val shortTimeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd MMM, h:mm a", Locale.US).withZone(melbourneZone)
    private val nonAlphaNumRegex = Regex("[^a-zA-Z0-9]")

    fun money(amount: Double?): String {
        val value = amount ?: 0.0
        val sign = if (value < -0.004) "-" else ""
        return String.format(Locale.US, "%s$%,.2f", sign, abs(value))
    }

    fun moneyCompact(amount: Double): String {
        val roundedCents = (amount * 100.0).roundToLong()
        val rounded = roundedCents / 100.0
        return if (roundedCents % 100L == 0L) {
            String.format(Locale.US, "$%,d", roundedCents / 100L)
        } else {
            String.format(Locale.US, "$%,.2f", rounded)
        }
    }

    fun parseIsoEpochMs(isoString: String?): Long {
        if (isoString.isNullOrBlank()) return 0L
        if (isoString.equals("Now", ignoreCase = true)) return System.currentTimeMillis()
        return try {
            OffsetDateTime.parse(isoString).toInstant().toEpochMilli()
        } catch (_: Exception) {
            try {
                Instant.parse(isoString).toEpochMilli()
            } catch (_: Exception) {
                0L
            }
        }
    }

    fun timeAgo(isoString: String?): String {
        if (isoString.isNullOrBlank()) return ""
        if (isoString.equals("Now", ignoreCase = true)) return "Just now"
        val ts = parseIsoEpochMs(isoString)
        if (ts <= 0L) return ""
        val diffSec = ((System.currentTimeMillis() - ts) / 1000L).coerceAtLeast(0L)
        val mins = diffSec / 60L
        val hours = mins / 60L
        val days = hours / 24L
        return when {
            mins < 1L -> "Just now"
            mins < 60L -> "${mins}m ago"
            hours < 24L -> "${hours}h ago"
            days < 60L -> "${days}d ago"
            else -> "${days / 30L}mo ago"
        }
    }

    fun formatAdded(isoString: String): String {
        if (isoString.isBlank()) return ""
        if (isoString.equals("Now", ignoreCase = true)) return "Just added"
        return try {
            val odt = OffsetDateTime.parse(isoString)
            addedFormatter.format(odt)
        } catch (_: Exception) {
            try {
                val instant = Instant.parse(isoString)
                addedFormatter.format(instant)
            } catch (_: Exception) {
                ""
            }
        }
    }

    fun formatTimestamp(epochMillis: Long): String {
        if (epochMillis <= 0L) return "Never"
        return try {
            shortTimeFormatter.format(Instant.ofEpochMilli(epochMillis))
        } catch (_: Exception) {
            ""
        }
    }

    fun formatLatency(ms: Long): String {
        if (ms <= 0L) return ""
        return if (ms >= 1000L) {
            String.format(Locale.US, "%.1fs", ms / 1000.0)
        } else {
            "${ms}ms"
        }
    }

    fun initials(domain: String): String {
        val letters = domain.replace(nonAlphaNumRegex, "")
        val sub = when {
            letters.length >= 2 -> letters.substring(0, 2)
            letters.length == 1 -> "${letters}V"
            else -> "PV"
        }
        return sub.uppercase(Locale.US)
    }
}
