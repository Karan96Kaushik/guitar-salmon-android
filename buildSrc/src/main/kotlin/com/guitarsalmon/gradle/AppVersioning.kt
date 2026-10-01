package com.guitarsalmon.gradle

import java.io.File
import java.util.Properties
import org.gradle.api.Project

/**
 * Reads and optionally bumps [app/version.properties].
 *
 * Rebuilds that produce an installable artifact (assemble / install / bundle) bump
 * the patch component and the Android versionCode by default. Configuration-only
 * runs such as IDE sync leave the file alone.
 */
object AppVersioning {

    data class Version(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val code: Int,
    ) {
        val name: String get() = "$major.$minor.$patch"
    }

    fun resolve(project: Project): Version {
        val file = project.file("version.properties")
        val props = load(file)
        var version = fromProps(props)

        val skip = project.findProperty("skipVersionBump")?.toString()
            .equals("true", ignoreCase = true) == true
        val bumpKind = project.findProperty("versionBump")?.toString()?.lowercase()

        val shouldBump = !skip && (bumpKind != null || isArtifactBuild(project))
        if (shouldBump) {
            version = bump(version, bumpKind ?: "patch")
            save(file, version)
            project.logger.lifecycle(
                "GuitarSalmon version -> ${version.name} (code ${version.code})"
            )
        }

        return version
    }

    private fun isArtifactBuild(project: Project): Boolean {
        // Task names may be fully qualified (:app:assembleDebug) or bare.
        return project.gradle.startParameter.taskNames.any { raw ->
            val name = raw.substringAfterLast(':').lowercase()
            name.startsWith("assemble") ||
                name.startsWith("install") ||
                name.startsWith("bundle") ||
                name == "build"
        }
    }

    private fun bump(current: Version, kind: String): Version = when (kind) {
        "major" -> Version(current.major + 1, 0, 0, current.code + 1)
        "minor" -> Version(current.major, current.minor + 1, 0, current.code + 1)
        "patch", "build" -> Version(current.major, current.minor, current.patch + 1, current.code + 1)
        else -> error(
            "Unknown versionBump='$kind'. Use major, minor, or patch " +
                "(or pass -PskipVersionBump=true)."
        )
    }

    private fun load(file: File): Properties {
        val props = Properties()
        if (file.exists()) {
            file.inputStream().use { props.load(it) }
        }
        return props
    }

    private fun fromProps(props: Properties): Version = Version(
        major = props.getProperty("major", "1").toInt(),
        minor = props.getProperty("minor", "0").toInt(),
        patch = props.getProperty("patch", "0").toInt(),
        code = props.getProperty("code", "1").toInt(),
    )

    private fun save(file: File, version: Version) {
        file.parentFile?.mkdirs()
        file.writeText(
            """
            |# Semantic version for GuitarSalmon. Bumped automatically on assemble / install /
            |# bundle builds (patch + versionCode). Override with -PversionBump=major|minor|patch,
            |# or skip with -PskipVersionBump.
            |major=${version.major}
            |minor=${version.minor}
            |patch=${version.patch}
            |code=${version.code}
            |
            """.trimMargin()
        )
    }
}
