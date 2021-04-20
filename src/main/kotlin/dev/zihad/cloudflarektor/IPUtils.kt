package dev.zihad.cloudflarektor

object IPUtils {
  fun subnetMatch(address: IP.IPv6, rangeList: List<IP.IPv6.Range>): IP.IPv6.Range? {
    for (range in rangeList) {
      if (address.match(range.ip, range.cidrBits)) {
        return range
      }
    }
    return null
  }

  fun matchCIDR(firstParts: List<Int>, secondParts: List<Int>, partSize: Int, cidrBits: Int): Boolean {
    if (firstParts.size != secondParts.size) {
      throw IllegalArgumentException("Cannot match CIDR for objects with different lengths")
    }

    var mutableCidrBits = cidrBits
    var part = 0
    var shift: Int

    while (mutableCidrBits > 0) {
      shift = partSize - mutableCidrBits
      if (shift < 0) shift = 0

      if (firstParts[part] shr shift != secondParts[part] shr shift) {
        return false
      }

      mutableCidrBits -= partSize
      part += 1
    }

    return true
  }

  fun isInRange(address: IP, vararg ranges: String): Boolean {
    for (range in ranges) {
      if (range.contains("/")) {
        val rangeData = range.split("/")
        val parsedRange = IP.parse(rangeData[0])
        if (parsedRange !== null && address.match(parsedRange, rangeData[1].toInt())) return true
      } else {
        val parsedRange = IP.parse(range)
        if (address == parsedRange) return true
      }
    }
    return false
  }

  fun storeIp(ip: String): String? {
    return try {
      when (val ipAddress = IP.parse(ip)) {
        is IP.IPv4 -> ipAddress.toString()
        is IP.IPv6 -> {
          if (ipAddress.isIPv4MappedAddress) ipAddress.toIpV4Address().toString()
          else IP.IPv6.abbreviate(ip)
        }
        else -> null
      }
    } catch (e: Exception) {
      null
    }
  }
}
