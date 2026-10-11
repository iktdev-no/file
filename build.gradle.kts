import java.io.ByteArrayOutputStream
import org.gradle.api.tasks.bundling.Jar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.3.21"
    `java-library`
    `maven-publish`
}

val groupId = "no.iktdev"
val artifactName = "file"
val fallbackVersion = "1.0.0-SNAPSHOT"

group = groupId
version = resolveProjectVersion()

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.lz4:lz4-java:1.8.0")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withSourcesJar()
    withJavadocJar()
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<Jar>().configureEach {
    manifest {
        attributes(
            mapOf(
                "Implementation-Title" to artifactName,
                "Implementation-Version" to project.version.toString(),
                "Implementation-Vendor" to "no.iktdev"
            )
        )
    }
}

val snapshotRepositoryUrl = providers.environmentVariable("REPOSILITE_SNAPSHOT_URL")
    .orElse("https://reposilite.iktdev.no/snapshots").get()
val releaseRepositoryUrl = providers.environmentVariable("REPOSILITE_RELEASE_URL")
    .orElse("https://reposilite.iktdev.no/releases").get()
val publicationRepositoryUrl = if (version.toString().endsWith("-SNAPSHOT")) {
    snapshotRepositoryUrl
} else {
    releaseRepositoryUrl
}

publishing {
    publications {
        create<MavenPublication>("reposilite") {
            groupId = groupId
            artifactId = artifactName
            version = project.version.toString()
            from(components["java"])

            pom {
                name.set(artifactName)
                description.set("Kotlin file abstraction with java.io.File and test-friendly implementations")
                url.set("https://reposilite.iktdev.no")
            }
        }
    }

    repositories {
        mavenLocal()
        maven {
            name = "reposilite"
            url = uri(publicationRepositoryUrl)
            credentials {
                username = providers.environmentVariable("REPOSILITE_USERNAME")
                    .orElse(providers.environmentVariable("reposiliteUsername")).orNull
                password = providers.environmentVariable("REPOSILITE_PASSWORD")
                    .orElse(providers.environmentVariable("reposilitePassword")).orNull
            }
        }
    }
}

fun resolveProjectVersion(): String {
    providers.gradleProperty("releaseVersion").orNull?.let { return normalizeTagVersion(it) }
    providers.environmentVariable("RELEASE_VERSION").orNull?.let { return normalizeTagVersion(it) }

    val ref = sequenceOf("GITHUB_REF", "GITEA_REF", "CI_COMMIT_TAG")
        .mapNotNull { providers.environmentVariable(it).orNull }
        .firstOrNull { it.isNotBlank() }
    if (ref?.startsWith("refs/tags/") == true) {
        return normalizeTagVersion(ref.removePrefix("refs/tags/"))
    }

    val latestTag = gitOutput("describe", "--tags", "--abbrev=0")
    return latestTag?.let(::nextSnapshotVersion) ?: fallbackVersion
}

fun normalizeTagVersion(tag: String): String {
    val normalized = tag.trim().removePrefix("v")
    require(Regex("\\d+(?:\\.\\d+)+(?:[-+][0-9A-Za-z.-]+)?").matches(normalized)) {
        "Release tag '$tag' must be a numeric version such as v1.9 or v1.2.3"
    }
    return normalized
}

fun nextSnapshotVersion(tag: String): String {
    val match = Regex("^(?:v)?(\\d+(?:\\.\\d+)+)(?:[-+].*)?$").matchEntire(tag.trim())
        ?: return fallbackVersion
    val parts = match.groupValues[1].split('.').toMutableList()
    val last = parts.last().toIntOrNull() ?: return fallbackVersion
    parts[parts.lastIndex] = (last + 1).toString()
    return "${parts.joinToString(".")}-SNAPSHOT"
}

fun gitOutput(vararg arguments: String): String? = try {
    val output = ByteArrayOutputStream()
    val result = providers.exec {
        commandLine("git", *arguments)
        standardOutput = output
        isIgnoreExitValue = true
    }.result.get()
    output.toString().trim().takeIf { result.exitValue == 0 && it.isNotBlank() }
} catch (_: Exception) {
    null
}
