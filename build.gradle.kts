// ClickHouse Console — build script.
//
// A plugin that queries a ClickHouse server through its OFFICIAL HTTP
// interface and shows the result as a table, inside any JetBrains IDE.
//
// ---------------------------------------------------------------------------
// Why is there no JSON library here?
// ---------------------------------------------------------------------------
// Measured on the jars of IntelliJ IDEA Community 2025.3 (build 253):
//
//   * com.google.gson.Gson                    : 0 class
//   * com.fasterxml.jackson.databind.*       : 0 class
//   * kotlinx.serialization.json.*            : 0 class
//
// The platform ships none of them, so a third-party plugin either bundles one
// or parses JSON itself. We parse it ourselves: the ClickHouse `JSON` format
// is small and regular, the parser is pure Kotlin (no IDE class), and it is
// covered by its own unit tests. Nothing is bundled, nothing can conflict
// with the IDE.
//
// The HTTP client is `java.net.http.HttpClient` (JDK 11+): the platform
// runs on a JBR 21, so it is always present, and no HTTP dependency has to
// be shipped either.

import org.gradle.api.tasks.bundling.AbstractArchiveTask

plugins {
    // Kotlin 2.2.0, and NOT the 2.0.21 that was in the local cache:
    // IntelliJ IDEA Community 2025.3 is compiled with Kotlin 2.2, and the
    // compiler refuses a library whose metadata is newer:
    //   "Module was compiled with an incompatible version of Kotlin. The
    //    binary version of its metadata is 2.2.0, expected version is 2.0.0."
    // (measured on 175 modules of ideaIC-2025.3, 13/11/2026).
    kotlin("jvm") version "2.2.0"
    id("org.jetbrains.intellij.platform") version "2.7.2"
}

group = "com.tgglabs.clickhouseconsole"
version = (project.findProperty("pluginVersion") as String?) ?: "0.0.1"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // The IDE to compile against. `create("IC", "2025.3")` resolves
        // `com.jetbrains.intellij.idea:ideaIC:2025.3` from the JetBrains
        // repository; the ZIP is already in the local Gradle cache of this
        // container, so the build downloads nothing of the IDE itself.
        //
        // `-PplatformLocalPath=/path/to/ide` compiles against a local IDE
        // installation instead (used for a fully offline build).
        val localIdePath = (project.findProperty("platformLocalPath") as String?)?.trim()
        if (!localIdePath.isNullOrEmpty()) {
            local(file(localIdePath))
        } else {
            create("IC", (project.findProperty("platformVersion") as String?) ?: "2025.3") {
                // MEASURED: in IntelliJ Platform Gradle Plugin 2.7.2 the
                // property `useInstaller` defaults to TRUE (source:
                // IntelliJPlatformDependencyConfiguration.kt, `convention(true)`).
                // With the installer, the plugin asks for the coordinates
                // `idea:ideaIC:2025.3` (the tar.gz of download.jetbrains.com),
                // which do not exist on the JetBrains repository:
                //   "Could not find idea:ideaIC:2025.3."
                // With `useInstaller = false`, the coordinates are the ones of
                // the Maven artifact `com.jetbrains.intellij.idea:ideaIC`,
                // which IS in the local cache of this container.
                useInstaller = false
            }
        }
    }

    // The Kotlin standard library is NOT bundled: the IntelliJ Platform ships
    // its own (Kotlin 2.1.x in 2025.3) and JetBrains forbids bundling a
    // second copy of a library the platform already provides.
    compileOnly(kotlin("stdlib"))

    // Test-only dependencies. `testImplementation` is NEVER bundled in the
    // distribution ZIP.
    testImplementation(kotlin("stdlib"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // JUnit 4, for the TEST FRAMEWORK OF THE PLATFORM only.
    //
    // The IntelliJ Platform puts its own test framework on the test classpath,
    // and its JUnit 5 launcher (`JUnit5TestEnvironmentInitializer`) calls
    // `initializeTestEnvironment`, which needs the JUnit 4 classes
    // (`org.junit.rules.TestRule`). Without them the executor dies before the
    // first test:
    //   "java.lang.NoClassDefFoundError: org/junit/rules/TestRule"
    // It is `testRuntimeOnly`: nothing of it reaches the plugin ZIP.
    testRuntimeOnly("junit:junit:4.13.2")
}

intellijPlatform {
    // JetBrains bytecode instrumentation: DISABLED, and this is a measured
    // decision (same as the reference plugin): the `:instrumentCode` task
    // resolves an Ant task from Maven, which is not needed for the build to
    // be green and only adds a download.
    instrumentCode = false

    // The plugin has NO settings page: the index of the searchable options
    // would be empty. The task is disabled with the documented property
    // (measured: with the task on, the build prints "No searchable options
    // found" and the ZIP holds an empty `-searchableOptions.jar`).
    buildSearchableOptions = false

    pluginConfiguration {
        id = "com.tgglabs.clickhouseconsole"
        name = (project.findProperty("pluginName") as String?) ?: "ClickHouse Console"
        version = (project.findProperty("pluginMarketplaceVersion") as String?)
            ?: project.version.toString()
        ideaVersion {
            sinceBuild = (project.findProperty("pluginSinceBuild") as String?) ?: "253"
            val configuredUntilBuild = (project.findProperty("pluginUntilBuild") as String?)?.trim()
            if (!configuredUntilBuild.isNullOrEmpty()) {
                untilBuild = configuredUntilBuild
            }
        }
        vendor {
            name = (project.findProperty("pluginVendorName") as String?) ?: "Tgg Labs"
            email = (project.findProperty("pluginVendorEmail") as String?) ?: "tgg.apps.support@gmail.com"
            url = (project.findProperty("pluginVendorUrl") as String?) ?: "https://plugins.jetbrains.com/vendor/tgg-labs"
        }
    }

    // No publication from this build: the token is not even read here.
    // `publishPlugin` / `uploadPlugin` must NEVER be run without the explicit
    // agreement of the repository owner.
    publishing {
        token = System.getenv("JETBRAINS_MARKETPLACE_TOKEN")
    }
}

// ---------------------------------------------------------------------------
// Java toolchain
// ---------------------------------------------------------------------------
// IntelliJ IDEA Community 2025.3 is compiled for Java 21 (class file 65), so
// the plugin is compiled and shipped as Java 21 bytecode, with the JDK 21
// that lives in /work/tools.
kotlin {
    jvmToolchain((project.findProperty("javaToolchainVersion") as String?)?.toInt() ?: 21)
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = "21"
    targetCompatibility = "21"
    options.encoding = "UTF-8"
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

// The tests are pure unit tests (parser, client against a local stub server):
// they need no class of the platform.
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    systemProperty("java.awt.headless", "true")
    maxHeapSize = "512m"
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
    }
}

// ---------------------------------------------------------------------------
// REPRODUCIBLE ZIP
// ---------------------------------------------------------------------------
// By default Gradle writes the TIMESTAMP of every entry of the ZIP: the hash
// then changes at every build, and the documented hash becomes stale after
// the first rebuild. These two options remove the timestamps and sort the
// entries: two builds of the same sources produce the SAME ZIP.
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
