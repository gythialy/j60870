import org.gradle.testing.jacoco.plugins.JacocoTaskExtension

plugins {
    alias(libs.plugins.java)
    alias(libs.plugins.jacoco)
    alias(libs.plugins.spotless)
}

val projectVersion: String = project.property("projectVersion") as String
val projectGroup: String = project.property("projectGroup") as String
val javaVersion: String = project.property("javaVersion") as String

group = projectGroup
version = projectVersion

// Projects that make up the distribution (the library itself and the CLI app).
// Replaces the project lists that used to live in configuration.gradle.
val distributionProjects: List<Project> =
    allprojects.filter { it.path == ":" || it.path == ":cli-app" }

// Copy behaviour of the jarAll task, formerly provided by configuration.gradle.
val cfgCopyDependencies: Boolean =
    providers.gradleProperty("cfgCopyDependencies").getOrElse("false").toBoolean()
val cfgCopyToRoot: Boolean =
    providers.gradleProperty("cfgCopyToRoot").getOrElse("false").toBoolean()

java {
    sourceCompatibility = JavaVersion.toVersion(javaVersion)
    targetCompatibility = JavaVersion.toVersion(javaVersion)
}

spotless {
    java {
        trimTrailingWhitespace()
        endWithNewline()
        palantirJavaFormat()
    }
    kotlinGradle {
        // target("*.gradle.kts")
        ktlint()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// JaCoCo ant tooling used by the offline "instrument" and "report" tasks.
val jacocoConfiguration: Configuration = configurations.create("jacoco")
val jacocoRuntime: Configuration = configurations.create("jacocoRuntime")

dependencies {
    testImplementation(platform(libs.junit))
    testImplementation(libs.junit.api)
    testImplementation(libs.junit.params)
    testRuntimeOnly(libs.junit.engine)
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(libs.mockito)
    testImplementation(libs.awaitility)

    jacocoConfiguration(variantOf(libs.jacoco.ant) { classifier("nodeps") })
    jacocoRuntime(variantOf(libs.jacoco.agent) { classifier("runtime") })
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn("test")

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

tasks.named<Test>("test") { finalizedBy("jacocoTestReport") }

tasks.named<Jar>("jar") {
    manifest {
        attributes(
            "Bundle-Name" to "j60870",
            "Export-Package" to "!org.openmuc.j60870.internal.internal.*,*",
        )
    }
}

tasks.register<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

tasks.named("build") { dependsOn("jar", "javadocAll", "sourcesJar", "jarAll") }

tasks.register<Javadoc>("javadocAll") {
    source = sourceSets.main.get().allJava
    classpath = configurations.compileClasspath.get() + sourceSets.main.get().output
    destinationDir = layout.projectDirectory.dir("docs/javadoc").asFile
}

val instrumentedClassesDir = layout.buildDirectory.dir("classes-instrumented")
val rawInstrumentedClassesDir = layout.buildDirectory.dir("instrumented_classes")

// Offline JaCoCo instrumentation, migrated from build.gradle.
tasks.register("instrument") {
    dependsOn("classes")

    doLast {
        val jacocoAntClasspath = jacocoConfiguration.asPath
        val rawInstrumentedDir = rawInstrumentedClassesDir.get().asFile

        sourceSets.main.get().output.classesDirs.forEach { classesDir ->
            copy {
                from(classesDir)
                into(rawInstrumentedDir)
            }
        }

        ant.withGroovyBuilder {
            "taskdef"(
                "name" to "instrument",
                "classname" to "org.jacoco.ant.InstrumentTask",
                "classpath" to jacocoAntClasspath,
            )
            "instrument"("destdir" to instrumentedClassesDir.get().asFile.path) {
                "fileset"("dir" to rawInstrumentedDir.path)
            }
        }
    }
}

// Run the tests against the instrumented classes whenever "instrument" is part
// of the build, so that "report" can produce a coverage report.
tasks.named<Test>("test") {
    doFirst {
        if (gradle.taskGraph.hasTask(":instrument")) {
            systemProperty(
                "jacoco-agent.destfile",
                layout.buildDirectory
                    .file("jacoco/tests.exec")
                    .get()
                    .asFile.path,
            )
            classpath = files(instrumentedClassesDir) + classpath + jacocoRuntime
        }
    }
}

// Otherwise the online agent of the JaCoCo plugin would capture the runtime
// data and the offline instrumentation of "instrument" would stay unused.
gradle.taskGraph.addTaskExecutionGraphListener { graph ->
    if (graph.hasTask(":instrument")) {
        tasks.named<Test>("test") {
            extensions.configure<JacocoTaskExtension> { isEnabled = false }
        }
    }
}

// JaCoCo html report for the instrumentation performed by "instrument".
tasks.register("report") {
    dependsOn("instrument", "test")

    doLast {
        val execFile =
            layout.buildDirectory
                .file("jacoco/tests.exec")
                .get()
                .asFile
        if (!execFile.exists()) {
            return@doLast
        }
        val jacocoAntClasspath = jacocoConfiguration.asPath
        val reportDir =
            layout.buildDirectory
                .dir("reports/jacoco")
                .get()
                .asFile

        ant.withGroovyBuilder {
            "taskdef"(
                "name" to "report",
                "classname" to "org.jacoco.ant.ReportTask",
                "classpath" to jacocoAntClasspath,
            )
            "report" {
                "executiondata" {
                    "file"("file" to execFile.path)
                }
                "structure"("name" to "Example") {
                    "classfiles" {
                        "fileset"("dir" to rawInstrumentedClassesDir.get().asFile.path)
                    }
                    "sourcefiles" {
                        "fileset"("dir" to "src/main/java")
                    }
                }
                "html"("destdir" to reportDir.path)
            }
        }
    }
}

// Copies the built artifacts (and optionally their dependencies) into build/libs-all.
tasks.register<Copy>("jarAll") {
    val defaultArtifacts = configurations.getByName("default").allArtifacts
    dependsOn(defaultArtifacts.buildDependencies)
    from(defaultArtifacts.files)
    if (cfgCopyDependencies) {
        from(configurations.getByName("runtimeClasspath"))
    }
    into(
        if (cfgCopyToRoot) {
            rootDir.resolve("build/libs-all")
        } else {
            layout.buildDirectory.dir("libs-all")
        },
    )
}

// Writes the settings file that ships with the distribution tarball.
tasks.register("writeSettings") {
    doLast {
        val settingsFile =
            layout.buildDirectory
                .file("settings.gradle")
                .get()
                .asFile
        val includedProjects = distributionProjects.filter { it.projectDir != projectDir }

        settingsFile.parentFile.mkdirs()
        settingsFile.bufferedWriter().use { out ->
            out.write("include ")
            includedProjects.forEachIndexed { index, included ->
                if (index > 0) {
                    out.write(", ")
                }
                out.write("\"" + included.name + "\"")
            }
            out.write("\n\n")

            includedProjects.forEach { included ->
                println(included.name)
                val relativePath =
                    included.projectDir.absolutePath.substring(projectDir.absolutePath.length + 1)
                out.write(
                    "project(\":" + included.name + "\").projectDir = file(\"" + relativePath + "\")\n",
                )
            }
        }
    }
}

// Builds every project that is part of the distribution.
tasks.register("buildDistProjects") {
    dependsOn(distributionProjects.map { it.tasks.named("build") })
}

tasks.register<Tar>("tar") {
    dependsOn("build", "writeSettings")

    compression = Compression.GZIP
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    archiveFileName.set("${project.name}-${project.version}.tgz")

    into(project.name) {
        from(".") {
            include("*.gradle.kts")
            include("docs/CHANGELOG.txt")
            include("run-scripts/**")
            include("gradle/wrapper/**")
            include("gradlew")
            include("gradlew.bat")
            include("build/libs/**")
            include("src/**")
            include("cli-app/**")
        }

        exclude("**/dependencies/**/src")
        exclude("**/bin")
        exclude("**/.project")
        exclude("**/.classpath")
        exclude("**/.gradle")
        exclude("**/.settings")

        from(layout.buildDirectory) { include("settings.gradle") }
    }

    into("${project.name}/docs/") {
        from(layout.buildDirectory.dir("docs/javadoc")) { include("**") }
    }
}

tasks.register<Tar>("tarFull") {
    dependsOn("tar")
    archiveFileName.set("${project.name}-${project.version}_full.tgz")
}
