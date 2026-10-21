plugins {
    id("java")
    kotlin("jvm") version "1.9.24"
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "com.plugin.webpreview"
version = "1.1.1"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

intellij {
    version.set("2024.1.4")
    type.set("IC")
    plugins.set(listOf())
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            jvmTarget = "17"
            freeCompilerArgs = listOf("-Xjsr305=strict")
        }
    }

    patchPluginXml {
        sinceBuild.set("241")
        untilBuild.set(provider { null })
    }

    buildSearchableOptions {
        enabled = false
    }

    buildPlugin {
        doLast {
            val releaseDir = rootDir.parentFile.resolve("release")
            releaseDir.mkdirs()
            archiveFile.orNull?.asFile?.let { zipFile ->
                val targetFile = releaseDir.resolve(zipFile.name)
                zipFile.copyTo(targetFile, overwrite = true)
                logger.lifecycle("📦 Plugin distribué automatiquement dans : ${targetFile.absolutePath}")
            }
        }
    }
}
