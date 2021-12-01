import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.6.0"
}

group = "sschr15.tools"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://maven.kotlindiscord.com/repository/maven-public/")
    maven("https://oss.sonatype.org/content/repositories/snapshots")
}

dependencies {
    // kotlin
    listOf(
        "stdlib",
        "stdlib-common",
        "reflect",
        "stdlib-jdk7",
        "stdlib-jdk8",
    ).forEach {
        implementation(kotlin(it))
    }

    // kord (and kord-extensions)
    implementation("dev.kord:kord-core:0.8.x-SNAPSHOT")
    implementation("com.kotlindiscord.kord.extensions:kord-extensions:1.5.1-SNAPSHOT")

    // ASM
    listOf(
        "asm",
        "asm-commons",
        "asm-tree",
        "asm-analysis",
        "asm-util"
    ).forEach {
        implementation("org.ow2.asm:$it:9.2")
    }

    // KorIO
    implementation("com.soywiz.korlibs.korio:korio-jvm:2.4.8")

    // SLF4J
    implementation("org.slf4j:slf4j-api:1.7.32")
    implementation("org.slf4j:slf4j-simple:1.7.32")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "16"
}