plugins {
    java
    id("maven-publish")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.jar {
    from(sourceSets.main.get().allSource)
}

tasks.test {
    useJUnitPlatform()
}

repositories {
    mavenLocal()
    maven {
        url = uri(project.findProperty("publishUri") as String)
        credentials {
            username = project.findProperty("publishUsername") as String
            password = project.findProperty("publishPassword") as String
        }
    }
    mavenCentral()
}

dependencies {
    implementation(libs.e2immuSupport)
    implementation(libs.slf4jApi)

    testImplementation(libs.junitJupiterApi)
    testRuntimeOnly(libs.junitJupiterEngine)
    testRuntimeOnly(libs.logbackClassic)
}

publishing {
    repositories {
        maven {
            url = uri(project.findProperty("publishUri") as String)
            credentials {
                username = project.findProperty("publishUsername") as String
                password = project.findProperty("publishPassword") as String
            }
        }
    }
    publications {
        register<MavenPublication>("awsCodeArtifact") {
            from(components["java"])

            artifactId = "analyser-util"
            groupId = "org.e2immu"

            pom {
                name = "Utility classes for e2immu analyser"
                description = "Static code analyser focusing on modication and immutability"
                url = "https://e2immu.org"
                licenses {
                    license {
                        name = "GNU Lesser General Public License, version 3.0"
                        url = "https://www.gnu.org/licenses/lgpl-3.0.html"
                    }
                }
                developers {
                    developer {
                        id = "bnaudts"
                        name = "Bart Naudts"
                        email = "bart.naudts@e2immu.org"
                    }
                }
            }
        }
    }
}

