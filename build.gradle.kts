plugins {
  maven
  kotlin("jvm") version "1.4.32"
}

group = "dev.zihad"
version = "1.0.0"

val ktorVersion = "1.5.3"
val slf4jVersion = "1.7.30"

repositories {
  jcenter()
  mavenCentral()
}

dependencies {
  implementation("org.slf4j:slf4j-api:$slf4jVersion")

  implementation("io.ktor:ktor-client-core:$ktorVersion")
  implementation("io.ktor:ktor-client-java:$ktorVersion")
  implementation("io.ktor:ktor-server-core:$ktorVersion")
}

tasks {
  compileKotlin {
    kotlinOptions {
      jvmTarget = "1.8"
    }
  }

  compileTestKotlin {
    kotlinOptions {
      jvmTarget = "1.8"
    }
  }
}
