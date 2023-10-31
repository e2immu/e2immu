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

version = "0.6.2"
group "org.e2immu"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
}


dependencies {
    implementation ("org.e2immu:e2immu-support:0.6.2")      // LGPL 3.0

    implementation(project(":analyser"))

    implementation ("org.apache.httpcomponents:httpclient:4.5.13") // Apache License 2.0
    implementation ("org.apache.httpcomponents:httpcore:4.4.13") // Apache License 2.0
    implementation ("com.google.code.gson:gson:2.8.9")             // Apache License 2.0

    implementation ("org.slf4j:slf4j-api:1.7.36")
    implementation ("ch.qos.logback:logback-classic:1.2.11")
    // EPL v1.0 and the LGPL 2.1

    testImplementation ("org.junit.jupiter:junit-jupiter-api:5.9.3")         // EPL v2 License
    testRuntimeOnly ("org.junit.jupiter:junit-jupiter-engine:5.9.3")
}

tasks.test {
    useJUnitPlatform()
}


publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = "org.e2immu"
            artifactId = "analyser-store-uploader"
            version = "0.6.2"

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
