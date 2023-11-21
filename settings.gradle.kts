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

rootProject.name = "org.e2immu"
include("graph")
include("analyser")
include("gradle-plugin")
include("analyser-cli")
include("analyser-store-uploader")
include("annotatedAPIs")

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            library("e2immuSupport", "org.e2immu:e2immu-support:0.6.2") // LGPL 3.0

            library("asm", "org.ow2.asm:asm:9.5") // 3-clause BSD permissive license
            library("commonsCli", "commons-cli:commons-cli:1.4") // Apache License 2.0
            library("googleGson", "com.google.code.gson:gson:2.8.9") // Apache License 2.0
            library("httpClient", "org.apache.httpcomponents:httpclient:4.5.13") // Apache License 2.0
            library("httpCore", "org.apache.httpcomponents:httpcore:4.4.13") // Apache License 2.0
            library("javaParser", "com.github.javaparser:javaparser-core:3.25.3")
            library("junitJupiterApi", "org.junit.jupiter:junit-jupiter-api:5.9.3") // EPL v2 License
            library("junitJupiterEngine", "org.junit.jupiter:junit-jupiter-engine:5.9.3") // EPL v2 License
            library("logbackClassic", "ch.qos.logback:logback-classic:1.2.11") // EPL v1.0 and the LGPL 2.1
            library("slf4jApi", "org.slf4j:slf4j-api:1.7.36")
            library("jgraphtCore", "org.jgrapht:jgrapht-core:1.5.2")
            library("jgraphtIO", "org.jgrapht:jgrapht-io:1.5.2")
        }
    }
}
