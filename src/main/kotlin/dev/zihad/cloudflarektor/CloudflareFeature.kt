package dev.zihad.cloudflarektor

import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.features.*
import io.ktor.request.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.runBlocking

class CloudflareFeature(
  private val ipV4Addresses: List<String>,
  private val ipV6Addresses: List<String>
) {
  private fun intercept(content: PipelineContext<Unit, ApplicationCall>) {
    var finalIp = content.context.request.origin.remoteHost
    val ip = IPUtils.storeIp(content.context.request.origin.remoteHost)
    if (ip !== null) {
      val parsedIp = ip.let { IP.parse(it) }
      if (parsedIp !== null) {
        val addresses = if (parsedIp is IP.IPv6) ipV6Addresses else ipV4Addresses
        val cloudflareConnectingIP = content.context.request.header("CF-Connecting-IP")
        if (cloudflareConnectingIP !== null && IPUtils.isInRange(parsedIp, *addresses.toTypedArray())) {
          finalIp = cloudflareConnectingIP
        }
      } else {
        finalIp = ip
      }
    }
    content.context.attributes.put(ipAttributeKey, finalIp)
  }

  companion object : ApplicationFeature<ApplicationCallPipeline, Nothing, CloudflareFeature> {
    val ipAttributeKey = AttributeKey<String>("dev.zihad.cloudflarektor.cloudflareIp")

    private val client = HttpClient()

    override val key = AttributeKey<CloudflareFeature>("dev.zihad.cloudflarektor.CloudflareFeature")

    override fun install(pipeline: ApplicationCallPipeline, configure: Nothing.() -> Unit): CloudflareFeature {
      val ipV4Addresses = mutableListOf<String>()
      val ipV6Addresses = mutableListOf<String>()
      runBlocking {
        val ipV4Lines: String = client.get("https://www.cloudflare.com/ips-v4")
        ipV4Lines.split("\n").forEach {
          if (it.isNotEmpty()) ipV4Addresses.add(it)
        }
        val ipV6Lines: String = client.get("https://www.cloudflare.com/ips-v6")
        ipV6Lines.split("\n").forEach {
          if (it.isNotEmpty()) ipV6Addresses.add(it)
        }
      }

      val feature = CloudflareFeature(
        ipV4Addresses,
        ipV4Addresses
      )

      pipeline.intercept(ApplicationCallPipeline.Call) {
        feature.intercept(this)
      }

      return feature
    }
  }
}
