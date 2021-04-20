package dev.zihad.cloudflarektor

sealed class IP {
  abstract fun match(other: IP, cidrRange: Int): Boolean
  abstract override fun toString(): String

  class IPv4(
    val octets: List<Int>
  ) : IP() {
    init {
      if (octets.size != 4) {
        throw IllegalArgumentException("IPv4 octet count should be 4")
      }

      for (octet in octets) {
        if (octet < 0 || octet > 255) {
          throw IllegalArgumentException("IPv4 octet should fit in 8 bits")
        }
      }
    }

    override fun match(other: IP, cidrRange: Int): Boolean {
      return other is IPv4 && IPUtils.matchCIDR(octets, other.octets, 8, cidrRange)
    }

    override fun toString(): String {
      return octets.joinToString(".")
    }

    companion object {
      private val ipV4Part = "(\\d+)".toRegex()
      private val fourOctetRegex =
        Regex("^$ipV4Part\\.$ipV4Part\\.$ipV4Part\\.$ipV4Part$", RegexOption.IGNORE_CASE)

      fun parse(ip: String): IPv4 {
        val fourOctetMatches = fourOctetRegex.matchEntire(ip)
        if (fourOctetMatches !== null) {
          return IPv4(fourOctetMatches.groupValues.drop(1).map { it.toInt() })
        } else {
          throw IllegalArgumentException("Invalid IPv4 address")
        }
      }
    }
  }

  class IPv6(
    var parts: List<Int>
  ) : IP() {
    private val range
      get() = IPUtils.subnetMatch(this, SpecialRange.values().map(SpecialRange::range))

    val isIPv4MappedAddress
      get() = range == SpecialRange.IPV4Mapped.range

    init {
      when {
        parts.size == 16 -> {
          val parts = mutableListOf<Int>()
          var i = 0
          while (i <= 14) {
            parts.add((this.parts[i] shl 8) or this.parts[i + 1])
            i += 2
          }
          this.parts = parts
        }
        parts.size != 8 -> {
          throw IllegalArgumentException("IPv6 part count should be 8 or 16")
        }
      }

      for (part in this.parts) {
        if (part < 0 || part > 0xffff) {
          throw IllegalArgumentException("IPv6 parts should fit in 16 bits")
        }
      }
    }

    override fun match(other: IP, cidrRange: Int): Boolean {
      return other is IPv6 && IPUtils.matchCIDR(parts, other.parts, 16, cidrRange)
    }

    override fun toString(): String {
      return abbreviate(parts.joinToString(":") { it.toString(16) })
    }

    fun toIpV4Address(): IPv4 {
      if (!isIPv4MappedAddress) {
        throw IllegalStateException("Trying to convert a generic ipv6 address to ipv4")
      }

      val ref = this.parts.takeLast(2)
      val high = ref[0]
      val low = ref[1]

      return IPv4(listOf(high shr 8, high and 0xff, low shr 8, low and 0xff))
    }

    data class Range(
      val ip: IPv6,
      val cidrBits: Int
    )

    enum class SpecialRange(val range: Range) {
      Unspecified(Range(IPv6(listOf(0, 0, 0, 0, 0, 0, 0, 0)), 128)),
      LinkLocal(Range(IPv6(listOf(0xfe80, 0, 0, 0, 0, 0, 0, 0)), 10)),
      Multicast(Range(IPv6(listOf(0xff00, 0, 0, 0, 0, 0, 0, 0)), 8)),
      Loopback(Range(IPv6(listOf(0, 0, 0, 0, 0, 0, 0, 1)), 128)),
      UniqueLocal(Range(IPv6(listOf(0xfc00, 0, 0, 0, 0, 0, 0, 0)), 7)),
      IPV4Mapped(Range(IPv6(listOf(0, 0, 0, 0, 0, 0xffff, 0, 0)), 96)),
      RFC6145(Range(IPv6(listOf(0, 0, 0, 0, 0, 0xffff, 0, 0)), 96)),
      RFC6052(Range(IPv6(listOf(0, 0, 0, 0, 0, 0xffff, 0, 0)), 96)),
      V6ToV4(Range(IPv6(listOf(0, 0, 0, 0, 0, 0xffff, 0, 0)), 16)),
      Teredo(Range(IPv6(listOf(0x2001, 0, 0, 0, 0, 0, 0, 0)), 32)),
      Reserved(Range(IPv6(listOf(0x2001, 0xdb8, 0, 0, 0, 0, 0, 0)), 32));
    }

    companion object {
      private fun validate(ip: String) {
        val ns = mutableListOf<String>()
        val nh = ip.split("::")
        if (nh.size > 2) {
          throw IllegalArgumentException("Invalid address: $ip")
        } else if (nh.size == 2) {
          if (
            nh[0].startsWith(":") ||
            nh[0].endsWith(":") ||
            nh[1].startsWith(":") ||
            nh[1].endsWith(":")
          ) {
            throw IllegalArgumentException("Invalid address: $ip")
          }

          ns.addAll(nh[0].split(":"))
          ns.addAll(nh[0].split(":"))
          ns.removeAll { it.isEmpty() }

          if (ns.size > 7) {
            throw IllegalArgumentException("Invalid address: $ip")
          }
        } else if (nh.size == 1) {
          ns.addAll(nh[0].split(":").filter { it.isNotEmpty() })
          if (ns.size != 8) {
            throw IllegalArgumentException("Invalid address: $ip")
          }
        }

        val regex = "^[a-f0-9]{1,4}\$".toRegex(RegexOption.IGNORE_CASE)
        for (n in ns) {
          val match = regex.matchEntire(n)
          if (match == null || match.groupValues[0] != n) throw IllegalArgumentException("Invalid address: $ip")
        }
      }

      private fun normalize(ip: String): String {
        validate(ip)

        val ipAddress = ip.toLowerCase()
        val nh = ipAddress.split("::")

        var sections: MutableList<String?> = MutableList(8) {
          null
        }

        if (nh.size == 1) {
          sections = ipAddress.split(":").toMutableList()
          if (sections.size != 8) {
            throw IllegalArgumentException("Invalid address: $ip")
          }
        } else if (nh.size == 2) {
          val n = nh[0]
          val h = nh[1]
          val ns = n.split(":")
          val hs = h.split(":")

          for (i in ns.indices) {
            sections[i] = ns[i]
          }

          var i = hs.size
          while (i > 0) {
            sections[7 - (hs.size - i)] = hs[i - 1]
            i--
          }
        } else throw IllegalArgumentException("Invalid address: $ip")

        for (i in sections.indices) {
          if (sections[i] == null) {
            sections[i] = "0000"
          }
          sections[i] = sections[i]!!.padStart(4, '0')
        }
        return sections.joinToString(":")
      }

      fun abbreviate(ip: String): String {
        val normalized = normalize(ip)
          .replace("0000", "g")
          .replace(":000", ":")
          .replace(":00", ":")
          .replace(":0", ":")
          .replace("g", "0")

        val sections = normalized.split(":".toRegex()).toMutableList()
        var zPreviousFlag = false
        var zeroStartIndex = -1
        var zeroLength = 0
        var zStartIndex = -1
        var zLength = 0

        for (i in 0 until 8) {
          val section = sections[i]
          val zFlag = section == "0"
          if (zFlag && !zPreviousFlag) {
            zStartIndex = i
          }
          if (!zFlag && zPreviousFlag) {
            zLength = i - zStartIndex
          }
          if (zLength > 1 && zLength > zeroLength) {
            zeroStartIndex = zStartIndex
            zeroLength = zLength
          }
          zPreviousFlag = zFlag
        }

        if (zPreviousFlag) {
          zLength = 8 - zStartIndex
        }

        if (zLength > 1 && zLength > zeroLength) {
          zeroStartIndex = zStartIndex
          zeroLength = zLength
        }

        if (zeroStartIndex >= 0 && zeroLength > 1) {
          repeat(zeroLength) {
            sections.removeAt(zeroStartIndex)
          }
          sections.add(zeroStartIndex, "g")
        }

        return sections.joinToString(":")
          .replace(":g:", "::")
          .replace("g:", "::")
          .replace(":g", "::")
          .replace("g", "::")
      }

      fun parse(ip: String): IPv6 {
        validate(ip)

        val normalized = normalize(ip)
        return IPv6(normalized.split(":").map { it.toInt(16) })
      }
    }
  }

  companion object {
    fun parse(ip: String): IP? {
      var parsedIp: IP? = null
      try {
        parsedIp = IPv4.parse(ip)
      } catch (e: IllegalArgumentException) {
      }
      try {
        parsedIp = IPv6.parse(ip)
      } catch (e: IllegalArgumentException) {
      }
      return parsedIp
    }
  }
}
