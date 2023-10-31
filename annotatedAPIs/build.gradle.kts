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

tasks.jar {
    from(sourceSets.main.get().output)
}


dependencies {
    implementation("org.e2immu:e2immu-support:0.6.2")
    implementation(project(":analyser"))
    implementation(project(":analyser-store-uploader"))
    implementation(project(":analyser-cli"))

    implementation("org.slf4j:slf4j-api:1.7.36")
    implementation("ch.qos.logback:logback-classic:1.2.11")

    implementation("com.github.javaparser:javaparser-core:3.25.3")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.3")                  // EPL v2.0
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.3")
}

tasks.test {
    exclude("**/testfailing/*.class")

    useJUnitPlatform()
}

// ********************************* Generate AnnotationXML from AnnotatedAPI files in annotatedAPIs project
/*
task generateAnnotationXml(type: JavaExec) {
    group = "Execution"
    description = "Convert all annotations in the annotatedAPIs to annotation.xml files"
    classpath = sourceSets.main.runtimeClasspath
    main = 'org.e2immu.analyser.cli.Main'
    Set<File> reducedClassPath = sourceSets.main.runtimeClasspath.toList()
    reducedClassPath += sourceSets.test.runtimeClasspath
    reducedClassPath.removeIf({ f -> f.path.contains("build/classes") || f.path.contains("build/resources") })
    args('--classpath=' + reducedClassPath.join(":") + ":jmods/java.base.jmod:jmods/java.xml.jmod",

            '--jre=/Library/Java/JavaVirtualMachines/adoptopenjdk-16.jdk/Contents/Home',
            '--source=non_existing_dir',
            '--annotated-api-source=src/main/java',

            '-w',
            '--write-annotation-xml-dir=build/annotations',
            '--write-annotation-xml-packages=java.,org.slf4j.',
            '--read-annotation-xml-packages=none',
            '--debug=ANNOTATION_XML_WRITER,ANNOTATION_XML_READER,CONFIGURATION'
    )
}


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
            groupId = "org.e2immu"
            artifactId = "annotatedAPIs"
            version = "0.6.2"

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
