plugins {
    // Auto-provisions JDK toolchains (e.g. JDK 25) on developer machines and CI runners
    // that don't have the required JDK installed.
    // https://github.com/gradle/foojay-toolchains
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

rootProject.name = "sky-starter"

include("domain")
include("service")
include("infrastructure")
include("app")
