import org.asciidoctor.gradle.jvm.AsciidoctorTask

plugins {
    alias(libs.plugins.java)
    alias(libs.plugins.jacoco)
    alias(libs.plugins.asciidoctor.convert)
    alias(libs.plugins.asciidoctor.pdf)
    alias(libs.plugins.spotless)
}

// Project properties
val projectVersion: String by project
val projectGroup: String by project
val javaVersion: String by project

group = projectGroup
version = projectVersion

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

dependencies {
    testImplementation(libs.powermock.junit4)
    testImplementation(libs.powermock.mockito2)
    testImplementation(libs.junit.params)
    testImplementation(libs.awaitility)
}

extensions.configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.toVersion(javaVersion)
    targetCompatibility = JavaVersion.toVersion(javaVersion)
}

extensions.configure<JacocoPluginExtension> {
    toolVersion = "0.8.13"
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn("test")

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

tasks.named<Test>("test") {
    finalizedBy("jacocoTestReport")
}

tasks.register<AsciidoctorTask>("generateDocs") {
    setSourceDir(layout.projectDirectory.dir("src/docs/asciidoc"))
    setOutputDir(layout.buildDirectory.dir("asciidoc"))

    forkOptions {
        jvmArgs =
            listOf(
                "--add-opens",
                "java.base/sun.nio.ch=ALL-UNNAMED",
                "--add-opens",
                "java.base/java.io=ALL-UNNAMED",
            )
    }

    resources {
        from(sourceDir) {
            include("images/**")
            include("common-settings.txt")
            include("openmuc-asciidoc.css")
            include("pdf-theme.yml")
        }
    }

    attributes(
        mapOf(
            "project-root" to project.rootDir,
            "stylesheet" to "openmuc-asciidoc.css",
            "toc2" to "left",
            "source-highlighter" to "coderay",
            "pdf-theme" to "pdf-theme.yml",
        ),
    )

    outputOptions {
        setBackends(listOf("html5", "pdf"))
    }

    doLast {
        project.copy {
            from("$outputDir/html5")
            into("docs")
            include("j60870-doc.html", "images/**")
            rename("j60870-doc.html", "index.html")
        }

        project.copy {
            from("$outputDir/pdf")
            into("docs")
            include("j60870-doc.pdf")
        }
    }

    baseDirFollowsSourceDir()
}

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

tasks.named("build") {
    dependsOn("jar", "javadocAll", "sourcesJar")
}

tasks.register<Javadoc>("javadocAll") {
    source = sourceSets.main.get().allJava
    classpath = configurations.compileClasspath.get() + sourceSets.main.get().output
    destinationDir = layout.projectDirectory.dir("docs/javadoc").asFile
}

tasks.register<Tar>("tar") {
    dependsOn("build", "generateDocs")

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

        from(layout.buildDirectory) {
            include("settings.gradle")
        }
    }

    into("${project.name}/docs/user-guide/") {
        from(layout.buildDirectory.dir("asciidoc/html5")) {
            include("**")
        }
        from(layout.buildDirectory.dir("asciidoc/pdf")) {
            // 修正路径
            include("*.pdf")
        }
    }

    into("${project.name}/docs/") {
        from(layout.buildDirectory.dir("docs/javadoc-all")) {
            include("**")
        }
    }
}

tasks.register<Tar>("tarFull") {
    dependsOn("tar")
    archiveFileName.set("${project.name}-${project.version}_full.tgz")
}
