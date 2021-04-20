# cloudflare-ktor

A Ktor feature for restoring visitor's IP after being proxied through Cloudflare.

Based on
[cloudflare-express](https://github.com/Mewte/cloudflare-express),
[range_check](https://www.npmjs.com/package/range_check),
[ipaddr.js](https://www.npmjs.com/package/ipaddr.js) and
[ip6](https://www.npmjs.com/package/ip6)

## Installation

Make sure you have the [JitPack](https://jitpack.io/) repository added to your project and then add the dependency.

### Gradle (Groovy)

```groovy
repositories {
  // Existing entries
  maven { url 'https://jitpack.io' } // JitPack repository
}

dependencies {
  implementation 'com.github.zihadmahiuddin:cloudflare-ktor:master-SNAPSHOT'
}
```

### Gradle (Kotlin)

```kotlin
repositories {
  // Existing entries
  maven("https://jitpack.io/") // JitPack repository
}

dependencies {
  implementation("com.github.zihadmahiuddin:cloudflare-ktor:master-SNAPSHOT")
}
```

### Maven

```xml

<repositories>
  <!-- Existing entries -->
  <repository> <!-- JitPack repository -->
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>
```

```xml
<dependency>
  <groupId>com.github.zihadmahiuddin</groupId>
  <artifactId>cloudflare-ktor</artifactId>
  <version>master-SNAPSHOT</version>
</dependency>
```

## Usage

```kotlin
import dev.zihad.cloudflarektor.CloudflareFeature
import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main() {
  embeddedServer(
    Netty,
    port = 8080,
    host = "0.0.0.0"
  ) {
    install(CloudflareFeature) // <- install the CloudflareFeature
    routing {
      get("/") {
        val ip = call.attributes[CloudflareFeature.ipAttributeKey] // <- retrieve the "cloudflare aware" IP address
        call.respondText("Your IP address is $ip")
      }
    }
  }.start(wait = true)
}
```

Feel free to create an issue if you notice something wrong. Thanks.

You might face issues if you're using Ktor version below `1.5.3`
