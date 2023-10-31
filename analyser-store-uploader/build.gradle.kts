/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

plugins {
    java
    application
    id("maven-publish")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
}

dependencies {
    implementation(libs.e2immuSupport)
    implementation(project(":analyser"))
    implementation(libs.httpClient)
    implementation(libs.httpCore)
    implementation(libs.googleGson)
    implementation(libs.slf4jApi)
    implementation(libs.logbackClassic)

    testImplementation(libs.junitJupiterApi)
    testRuntimeOnly(libs.junitJupiterEngine)
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            pom {
                name = "store uploader for e2immu analyser"
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
