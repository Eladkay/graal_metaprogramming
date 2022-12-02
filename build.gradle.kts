plugins {
    java
    kotlin("jvm") version "1.7.20"
}

group = "il.ac.technion.cs"
version = "1.0-SNAPSHOT"

allprojects {
    repositories {
        mavenCentral()
    }
}

val graalVersion = "22.3.0"
val jgraphtVersion = "1.5.1"
val junitVersion = "5.9.0"
// Staying at 17 for now because Gradle doesn't support Java 19
val javaVersion = JavaVersion.VERSION_17

val apronLocation = "/Users/kinsbruner/IdeaProjects/graal_metaprogramming/apron-0.9.13/"
val elinaLocation = "/Users/kinsbruner/repos/ELINA"

dependencies {
    implementation(kotlin("reflect"))

    // ReactiveX
    implementation("io.reactivex.rxjava3:rxjava:3.1.5")
    implementation("io.reactivex.rxjava3:rxkotlin:3.0.1")

    // Arrow (should use sparingly?)
    implementation("io.arrow-kt:arrow-core:1.1.2")

    // Graal
    implementation("org.graalvm.sdk:graal-sdk:$graalVersion")
    implementation("org.graalvm.compiler:compiler:$graalVersion")
    implementation("org.graalvm.tools:insight:$graalVersion")

    // jgrapht
    implementation("org.jgrapht:jgrapht-core:$jgraphtVersion")
    implementation("org.jgrapht:jgrapht-io:$jgraphtVersion")

    // spotbugs annotations (for @NonNull in Java)
    implementation("com.github.spotbugs:spotbugs-annotations:4.7.3")

    // better-parse parser library
    implementation("com.github.h0tk3y.betterParse:better-parse:0.4.4")

    // JSON parser/builder
    implementation("com.beust:klaxon:5.6")

    if (File(apronLocation).isDirectory) {
        implementation(files("$apronLocation/lib/gmp.jar", "$apronLocation/lib/apron.jar"))
    }

    if (File(elinaLocation).isDirectory) {
        implementation(files("$elinaLocation/java_interface/elina.jar"))
    }

    implementation(files("elina.jar"))

    testImplementation("org.assertj:assertj-core:3.23.1")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
    testImplementation("org.amshove.kluent:kluent:1.72")
    testImplementation("io.mockk:mockk:1.13.2")
}

val moduleArgs = listOf(
    "--add-modules", "jdk.internal.vm.ci,jdk.internal.vm.compiler",
    "--add-exports", "jdk.internal.vm.ci/jdk.vm.ci.meta=ALL-UNNAMED", // diff
    "--add-exports", "jdk.internal.vm.ci/jdk.vm.ci.services=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.ci/jdk.vm.ci.runtime=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.ci/jdk.vm.ci.code=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.api.runtime=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.nodes=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.debug=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.options=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.runtime=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.core.target=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.nodes.cfg=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.nodes.virtual=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.nodeinfo=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.phases.tiers=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.phases=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.code=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.core=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.core.phases=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.nodes.graphbuilderconf=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.java=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.graph=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.graph=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.nodes.memory=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.nodes.memory.address=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.phases.common=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.nodes.java=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.graph.iterators=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.nodes.calc=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.nodes.spi=ALL-UNNAMED",
    "--add-exports", "jdk.internal.vm.compiler/org.graalvm.compiler.nodes.extended=ALL-UNNAMED"
)


java {
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
}

tasks {
    compileJava {
        options.apply {
            compilerArgs = moduleArgs + "-Xlint:deprecation"
        }
    }
    compileTestJava {
        options.apply {
            compilerArgs = moduleArgs + "-Xlint:deprecation"
        }
    }

    compileKotlin {
        kotlinOptions.jvmTarget = javaVersion.toString()
        kotlinOptions.freeCompilerArgs = listOf("-Xextended-compiler-checks")
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = javaVersion.toString()
        kotlinOptions.freeCompilerArgs = listOf("-Xextended-compiler-checks")
    }

    test {
        useJUnitPlatform()
        jvmArgs = moduleArgs + listOf(
            "-Ddebug.graal.TrackNodeCreationPosition=true",
            "--add-opens",
            "jdk.internal.vm.compiler/org.graalvm.compiler.graph=ALL-UNNAMED",
        )
        maxHeapSize = "8g"
        var libraryPath = "/usr/local/lib"
        if (File(apronLocation).exists())
            libraryPath += ":$apronLocation/lib"
        if (File(elinaLocation).exists())
            libraryPath += ":$elinaLocation/lib"
        systemProperty("java.library.path", libraryPath)
        environment("LD_LIBRARY_PATH", libraryPath)
    }
}