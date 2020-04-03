/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.gradleplugin;

import org.e2immu.analyser.cli.Main;
import org.gradle.api.Project;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.JavaCompile;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Property names are identical to those of the CLI (.cli.Main). In the system properties,
 * they have to be prefixed by the PREFIX defined in this class.
 * <p>
 * The following properties are set by the plugin:
 *
 * <ol>
 *     <li>SOURCE_ENCODING: the source encoding</li>
 *
 *     <li>SOURCE: the source path of the 'main' group</li>
 *     <li>CLASSPATH: the path for all libraries for the 'main' group</li>
 *
 *     <li>TEST_SOURCE: the source path for the 'test' group</li>
 *     <li>TEST_CLASSPATH: the path for all libraries for the 'test' group</li>
 *
 *     <li>UPLOAD_PROJECT: the name of the project when uploading to the annotation server</li>
 *
 *     <li>WRITE_ANNOTATION_XML: set to true</li>
 *     <li>WRITE_ANNOTATION_XML_DIR: set to ${work.dir}/annotationXml</li>
 *
 *     <li>WRITE_ANNOTATED_API: set to true</li>
 *     <li>WRITE_ANNOTATED_API_DIR: set to ${work.dir}/annotatedAPIs</li>
 *
 *     <li></li>
 * </ol>
 */
public class AnalyserPropertyComputer {

    private static final Logger LOGGER = Logging.getLogger(AnalyserPropertyComputer.class);
    public static final String PREFIX = "e2immu-analyser.";

    private final Map<String, ActionBroadcast<AnalyserProperties>> actionBroadcastMap;
    private final Project targetProject;

    public AnalyserPropertyComputer(Map<String, ActionBroadcast<AnalyserProperties>> actionBroadcastMap, Project targetProject) {
        this.actionBroadcastMap = actionBroadcastMap;
        this.targetProject = targetProject;
    }

    public Map<String, Object> computeProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        computeProperties(targetProject, properties, "");
        return properties;
    }

    private void computeProperties(Project project, Map<String, Object> properties, String prefix) {
        AnalyserExtension extension = project.getExtensions().getByType(AnalyserExtension.class);
        if (extension.isSkipProject()) {
            return;
        }
        Map<String, Object> rawProperties = new LinkedHashMap<>();
        detectProperties(project, rawProperties);

        ActionBroadcast<AnalyserProperties> actionBroadcast = actionBroadcastMap.get(project.getPath());
        if (actionBroadcast != null) {
            AnalyserProperties analyserProperties = new AnalyserProperties(properties);
            actionBroadcast.execute(analyserProperties);
        }

        // with the highest priority, override directly for this project from the system properties
        if (project.equals(targetProject)) {
            addSystemProperties(rawProperties);
        }
        // convert all the properties from sub-projects into dot-notated properties
        flattenProperties(rawProperties, prefix, properties);

        LOGGER.debug("Resulting map is " + properties);

        List<Project> enabledChildProjects = project.getChildProjects().values().stream()
                .filter(p -> !p.getExtensions().getByType(AnalyserExtension.class).isSkipProject())
                .collect(Collectors.toList());

        List<Project> skippedChildProjects = project.getChildProjects().values().stream()
                .filter(p -> p.getExtensions().getByType(AnalyserExtension.class).isSkipProject())
                .collect(Collectors.toList());

        if (!skippedChildProjects.isEmpty()) {
            LOGGER.debug("Skipping collecting Analyser properties on: " +
                    skippedChildProjects.stream().map(Project::toString).collect(Collectors.joining(", ")));
        }

        // recurse
        for (Project childProject : enabledChildProjects) {
            String moduleId = childProject.getPath();
            String modulePrefix = (prefix.length() > 0) ? (prefix + "." + moduleId) : moduleId;
            computeProperties(childProject, properties, modulePrefix);
        }
    }

    private void detectProperties(final Project project, final Map<String, Object> properties) {
        properties.put(Main.UPLOAD_PROJECT, project.getName());
        String workDir = project.getProjectDir().getAbsolutePath();

        properties.put(Main.WRITE_ANNOTATED_API, "true");
        properties.put(Main.WRITE_ANNOTATED_API_DIR, workDir + Main.PATH_SEPARATOR + "annotatedAPIs");

        properties.put(Main.WRITE_ANNOTATION_XML, "true");
        properties.put(Main.WRITE_ANNOTATION_XML_DIR, workDir + Main.PATH_SEPARATOR + "annotationXml");

        project.getPlugins().withType(JavaPlugin.class, javaPlugin -> {
            boolean hasSource = detectSourceDirsAndJavaClasspath(project, properties);
            if (hasSource) {
                detectSourceEncoding(project, properties);
            }
        });
    }

    private static void detectSourceEncoding(Project project, final Map<String, Object> properties) {
        project.getTasks().withType(JavaCompile.class, compile -> {
            String encoding = compile.getOptions().getEncoding();
            if (encoding != null) {
                properties.put(Main.SOURCE_ENCODING, encoding);
            }
        });
    }

    private static boolean detectSourceDirsAndJavaClasspath(Project project, Map<String, Object> properties) {
        JavaPluginConvention javaPluginConvention = new DslObject(project).getConvention().getPlugin(JavaPluginConvention.class);

        SourceSet main = javaPluginConvention.getSourceSets().getAt("main");
        String sourceDirectoriesPathSeparated = sourcePathFromSourceSet(main);
        properties.put(Main.SOURCE, sourceDirectoriesPathSeparated);

        SourceSet test = javaPluginConvention.getSourceSets().getAt("test");
        String testDirectoriesPathSeparated = sourcePathFromSourceSet(test);
        properties.put(Main.TEST_SOURCE, testDirectoriesPathSeparated);

        String classPathSeparated = librariesFromSourceSet(main) + Main.PATH_SEPARATOR + "jmods/java.base.jmod";
        properties.put(Main.CLASSPATH, classPathSeparated);

        String testClassPathSeparated = librariesFromSourceSet(test);
        properties.put(Main.TEST_CLASSPATH, testClassPathSeparated);

        return !sourceDirectoriesPathSeparated.isEmpty() || !testDirectoriesPathSeparated.isEmpty();
    }

    private static String sourcePathFromSourceSet(SourceSet sourceSet) {
        return sourceSet.getAllJava().getSrcDirs()
                .stream()
                .filter(File::canRead)
                .map(File::getAbsolutePath)
                .collect(Collectors.joining(Main.PATH_SEPARATOR));
    }

    private static String librariesFromSourceSet(SourceSet sourceSet) {
        return sourceSet.getCompileClasspath().getFiles()
                .stream()
                .filter(File::canRead)
                .map(File::getAbsolutePath)
                .collect(Collectors.joining(Main.PATH_SEPARATOR));
    }

    private static void addSystemProperties(Map<String, Object> properties) {
        for (Map.Entry<Object, Object> entry : System.getProperties().entrySet()) {
            String key = entry.getKey().toString();
            if (key.startsWith(PREFIX)) {
                LOGGER.debug("Overwriting property from system: {}", key);
                String strippedKey = key.substring(PREFIX.length());
                properties.put(strippedKey, entry.getValue().toString());
            }
        }
    }

    private static void flattenProperties(Map<String, Object> rawProperties,
                                          String projectPrefix,
                                          Map<String, Object> properties) {
        for (Map.Entry<String, Object> entry : rawProperties.entrySet()) {
            if (entry.getValue() != null) {
                String key = projectPrefix.isEmpty() ? entry.getKey() : (projectPrefix + "." + entry.getKey());
                properties.put(key, entry.getValue().toString());
            }
        }
    }
}
