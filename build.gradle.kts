import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.intellij.platform)
}

group   = property("pluginGroup") as String
version = property("pluginVersion") as String

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity(libs.versions.intellijPlatform.get())
        instrumentationTools()
        pluginVerifier()
        testFramework(TestFrameworkType.Platform)
    }

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

intellijPlatform {
    pluginConfiguration {
        version = project.version.toString()
        ideaVersion {
            sinceBuild = libs.versions.sinceBuild.get()
            untilBuild = provider { null }
        }
    }

    pluginVerification {
        ides { recommended() }
    }
}

// -----------------------------------------------------------------------------
// prepareNodeRuntimes — download + SHA-256-verify Node 22.x per target
// -----------------------------------------------------------------------------

val nodeRuntimesDir = layout.buildDirectory.dir("node-runtimes")

abstract class PrepareNodeRuntimesTask : org.gradle.api.DefaultTask() {
    @get:org.gradle.api.tasks.OutputDirectory
    abstract val outputDir: org.gradle.api.file.DirectoryProperty

    @org.gradle.api.tasks.TaskAction
    fun run() {
        val script = project.rootProject.file("scripts/prepare_node_runtimes.py")
        val proc = ProcessBuilder("python3", script.absolutePath, outputDir.get().asFile.absolutePath)
            .redirectErrorStream(true)
            .start()
        proc.inputStream.copyTo(System.out)
        val exit = proc.waitFor()
        if (exit != 0) throw org.gradle.api.GradleException("prepareNodeRuntimes failed (exit $exit)")
    }
}

val prepareNodeRuntimes by tasks.registering(PrepareNodeRuntimesTask::class) {
    outputDir = nodeRuntimesDir
}

// -----------------------------------------------------------------------------
// nativeVariants — 4 per-target ZIPs, each with EXACTLY that target's Node binary
// -----------------------------------------------------------------------------

val nativeTargets = listOf("windows-x64", "macos-x64", "macos-arm64", "linux-x64")

tasks.withType<PrepareSandboxTask>().configureEach {
    dependsOn(prepareNodeRuntimes)
}

nativeTargets.forEach { target ->
    tasks.register<Zip>("buildPlugin-$target") {
        dependsOn("buildPlugin", prepareNodeRuntimes)
        archiveBaseName = "rsm-jetbrains"
        archiveVersion  = project.version.toString()
        archiveClassifier = target
        destinationDirectory = layout.buildDirectory.dir("distributions")

        from(zipTree(tasks.named<Zip>("buildPlugin").flatMap { it.archiveFile })) {
            into(".")
        }
        from(nodeRuntimesDir.map { it.dir(target) }) {
            into("rsm-jetbrains/node-runtimes/$target")
        }
    }
}

tasks.register("buildAllNativeVariants") {
    dependsOn(nativeTargets.map { "buildPlugin-$it" })
}

// -----------------------------------------------------------------------------
// releaseManifest — signed manifest with per-archive SHA-256s
// -----------------------------------------------------------------------------

tasks.register<Exec>("releaseManifest") {
    dependsOn("buildAllNativeVariants")
    commandLine("python3", "scripts/build_release_manifest.py",
                layout.buildDirectory.get().asFile.absolutePath,
                project.version.toString(),
                libs.versions.intellijPlatformGradlePlugin.get(),
                libs.versions.sinceBuild.get())
}

tasks.test {
    useJUnitPlatform()
}
