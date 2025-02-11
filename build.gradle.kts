plugins {
    base
    `java-library`
    `maven-publish`
    signing
    kotlin("jvm") version "1.8.20"
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.versions)
    alias(libs.plugins.versions.filter)
    alias(libs.plugins.versions.update)
}

// These are credentials required for the task `uploadArchives`. They are read either from gradle.properties or from the command
// line using -PsonatypeUsername=abc
val sonatypeUsername: String by project
val sonatypePassword: String by project

repositories {
    mavenCentral()
}

subprojects {
    apply(plugin = "maven-publish")
    apply(plugin = "java-library")
    apply(plugin = "kotlin")
    apply(plugin = "signing")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "io.gitlab.arturbosch.detekt")

    group = "com.metricstream.jdbc"
    version = "3.9.0"

    repositories {
        mavenCentral()
    }

    kotlin {
        jvmToolchain {
            languageVersion.set(JavaLanguageVersion.of(11))
        }
    }

    tasks.withType<AbstractArchiveTask> {
        setProperty("archiveBaseName", "sqlbuilder")
        setProperty("archiveAppendix", project.name)
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    tasks.withType<JavaCompile> {
        options.compilerArgs.add("-Xlint:deprecation")
        options.compilerArgs.add("-Xlint:unchecked")
    }

    java {
        withJavadocJar()
        withSourcesJar()
    }

    publishing {
        publications {
            create<MavenPublication>("maven${project.name}") {
                artifactId = "sqlbuilder-${project.name}"

                from(components["java"])

                pom {
                    name.set("SQLBuilder")
                    description.set("SQLBuilder is a Java library which aims to be a better PreparedStatement")
                    url.set("https://github.com/MetricStream/SQLBuilder")
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    developers {
                        developer {
                            name.set("Norbert Kiesel")
                            email.set("nk@iname.com")
                            organizationUrl.set("https://metricstream.com")
                        }
                    }
                    scm {
                        connection.set("scm:git:git@github.com:MetricStream/SQLBuilder.git")
                        developerConnection.set("scm:git:git@github.com:MetricStream/SQLBuilder.git")
                        url.set("https://github.com/MetricStream/SQLBuilder")
                    }
                }
            }
        }

        repositories {
            maven {
                name = "staging"
                url = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2")
                credentials {
                    username = sonatypeUsername
                    password = sonatypePassword
                }
            }
        }
    }

    signing {
        useGpgCmd()
        sign(publishing.publications["maven${project.name}"])
    }

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        // See https://github.com/pinterest/ktlint/issues/527
        disabledRules.set(setOf("import-ordering"))
    }

    detekt {
        config = files("detekt-config.yml")
        buildUponDefaultConfig = true
    }
}
