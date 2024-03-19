import java.util.stream.Collectors

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
    `maven-publish`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.jar {
    from(sourceSets.main.get().allSource)
}

dependencies {
    implementation(libs.e2immuSupport)
    implementation(project(":analyser"))
    implementation(project(":analyser-store-uploader"))
    implementation(project(":analyser-cli"))
    implementation(libs.slf4jApi)
    implementation(libs.logbackClassic)
    implementation(libs.javaParser)

    testImplementation(libs.junitJupiterApi)
    testRuntimeOnly(libs.junitJupiterEngine)
}

tasks.test {
    exclude("**/testfailing/*.class")

    useJUnitPlatform()
}

// ********************************* Generate AnnotationXML from AnnotatedAPI files in annotatedAPIs project
// execute with "gradle generateAnnotationXml"

tasks.register<JavaExec>("generateAnnotationXml") {
    group = "Execution"
    description = "Convert all annotations in the annotatedAPIs to annotation.xml files"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "org.e2immu.analyser.cli.Main"
    val reducedClassPath = sourceSets.main.get().runtimeClasspath.toMutableList()
    reducedClassPath += sourceSets.test.get().runtimeClasspath
    reducedClassPath.removeIf { f -> f.path.contains("build/classes") || f.path.contains("build/resources") }
    args("--classpath=" + reducedClassPath.stream().map { f -> f.path }.collect(Collectors.joining(":"))
            + ":jmods/java.base.jmod:jmods/java.xml.jmod:jmods/java.net.http.jmod",

            "--source=non_existing_dir",
            "--annotated-api-source=src/main/java",

            "-w",
            "--write-annotation-xml-dir=build/annotations",
            "--write-annotation-xml-packages=java.,org.slf4j.",
            "--read-annotation-xml-packages=none",
            "--debug=ANNOTATION_XML_WRITER,ANNOTATION_XML_READER,CONFIGURATION"
    )
}
/*

// TODO no idea how to make the same file, but then with a .jar extension
// the Jar task 'hijacks' the Zip, and adds other content :-(

task annotationXmlJar(type: Zip) {
    from(buildDir.path + "/annotations/")
    archivesBaseName = buildDir.path + "/annotation-xml"
    archiveExtension.set("zip")

    dependsOn generateAnnotationXml
}

def annotationXmlJar = file(buildDir.path + "/annotation-xml-" + project.version + ".jar")

// TODO upload the annotation-xml jar/zip file
def annotationXmlArtifact = artifacts.add('archives', annotationXmlJar, {
    type ('jar')
    builtBy ('annotationXmlJar')
})
*/
// ********************************* Publishing


publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            pom {
                name = "Annotated APIs for e2immu analyser"
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
