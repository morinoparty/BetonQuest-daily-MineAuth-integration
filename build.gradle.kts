import org.gradle.api.provider.Provider
import org.gradle.api.artifacts.ExternalModuleDependencyBundle
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    java
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.paper)
    alias(libs.plugins.resource.factory)
}

group = "party.morino"
version = project.version.toString()

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://repo.papermc.io/repository/maven-public/")
    // JitPak: MineAuthリリースタグ確定後に有効化
    // maven("https://jitpack.io")
    maven("https://repo.betonquest.org/betonquest/")
    maven("https://repo.minebench.de/")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
}

dependencies {
    compileOnly(libs.mineauth.api)
    compileOnly(libs.paper.api)
    compileOnly(libs.betonquest)

    implementation(libs.bundles.commands)

    // Paperのlibraries機能またはMineAuthコアから提供されるライブラリ（compileOnly）
    compileOnly(libs.kotlinx.serialization.json)
    compileOnly(libs.bundles.coroutines)
    compileOnly(kotlin("stdlib-jdk8"))
}

kotlin {
    jvmToolchain(21)
}

tasks {
    build {
        dependsOn("shadowJar")
    }
    compileKotlin {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
        compilerOptions.javaParameters = true
    }
    shadowJar {
        dependencies {
            exclude(dependency("org.jetbrains.kotlin:.*:.*"))
            exclude(dependency("org.jetbrains.kotlinx:kotlinx-coroutines-.*:.*"))
            exclude(dependency("org.jetbrains.kotlinx:kotlinx-serialization-.*:.*"))
            exclude(dependency("io.arrow-kt:.*:.*"))
        }
    }
    runServer {
        minecraftVersion("1.21.4")
        val plugins = runPaper.downloadPluginsSpec {
            url("https://repo.betonquest.org/betonquest/org/betonquest/betonquest/3.0.0-SNAPSHOT/betonquest-3.0.0-20250818.215240-364-shaded.jar")
        }
        downloadPlugins {
            downloadPlugins.from(plugins)
        }
    }
}

sourceSets.main {
    resourceFactory {
        bukkitPluginYaml {
            name = "BetonQuest-DailyQuest-MineAuth"
            version = project.version.toString()
            website = "https://github.com/morinoparty/BetonQuest-daily-MineAuth-integration"
            main = "$group.betonquest.daily.mineauth.DailyQuestMineAuthPlugin"
            apiVersion = "1.20"
            libraries = libs.bundles.coroutines.asString()
            depend = listOf("MineAuth", "BetonQuest")
        }
    }
}

fun Provider<MinimalExternalModuleDependency>.asString(): String {
    val dependency = this.get()
    return dependency.module.toString() + ":" + dependency.versionConstraint.toString()
}

fun Provider<ExternalModuleDependencyBundle>.asString(): List<String> {
    return this.get().map { dependency ->
        "${dependency.group}:${dependency.name}:${dependency.version}"
    }
}
