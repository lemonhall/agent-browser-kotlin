import org.gradle.api.tasks.Copy
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

group = "com.lsl"
version = "0.1.0"

kotlin {
    jvmToolchain(8)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}

val generatedResourcesDir = layout.buildDirectory.dir("generated-resources/main")
val syncAgentBrowserJs by tasks.registering(Copy::class) {
    from(rootProject.file("agent-browser-js/agent-browser.js"))
    into(generatedResourcesDir)
}

val sourceSets = the<SourceSetContainer>()
sourceSets.named("main") {
    resources.srcDir(generatedResourcesDir)
}

tasks.named("processResources") {
    dependsOn(syncAgentBrowserJs)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit)
}

