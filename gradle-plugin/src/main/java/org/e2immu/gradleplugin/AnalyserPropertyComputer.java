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

package org.e2immu.gradleplugin;

import org.e2immu.analyser.config.AnnotatedAPIConfiguration;
import org.gradle.api.Project;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.JavaCompile;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.e2immu.analyser.cli.Main;

/**
 * Property names are identical to those of the CLI (.cli.Main). In the system properties,
 * they have to be prefixed by the PREFIX defined in this class.
 */
public record AnalyserPropertyComputer(
        Map<String, ActionBroadcast<AnalyserProperties>> actionBroadcastMap,
        Project targetProject) {

    private static final Logger LOGGER = Logging.getLogger(AnalyserPropertyComputer.class);
    public static final String PREFIX = "e2immu-analyser.";

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
        detectProperties(project, rawProperties, extension);

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

    private void detectProperties(final Project project, final Map<String, Object> properties, AnalyserExtension extension) {
        properties.put(Main.DEBUG, extension.getDebug());
        properties.put(Main.SOURCE_PACKAGES, extension.getSourcePackages());
        properties.put(Main.JRE, extension.getJre());
        properties.put(Main.IGNORE_ERRORS, extension.isIgnoreErrors());
        properties.put(Main.SKIP_ANALYSIS, extension.isSkipAnalysis());

        properties.put(Main.UPLOAD, extension.getUpload() == null || extension.getUpload());
        properties.put(Main.UPLOAD_PROJECT, project.getName());
        properties.put(Main.UPLOAD_URL, extension.getUploadUrl());
        properties.put(Main.UPLOAD_PACKAGES, extension.getUploadPackages());

        String workDir = project.getProjectDir().getAbsolutePath();
        File buildDir = project.getBuildDir();
        properties.put(Main.READ_ANNOTATED_API_PACKAGES, getOrDefault(extension.getReadAnnotatedAPIPackages(),
                AnnotatedAPIConfiguration.DO_NOT_READ_ANNOTATED_API));
        properties.put(Main.ANNOTATED_API_WRITE_MODE, getOrDefault(extension.getAnnotatedAPIWriteMode(),
                AnnotatedAPIConfiguration.WriteMode.DO_NOT_WRITE.toString()));
        properties.put(Main.WRITE_ANNOTATED_API_DIR, getOrDefault(extension.getWriteAnnotatedAPIDir(),
                new File(buildDir, "annotatedAPIs").getAbsolutePath()));
        properties.put(Main.WRITE_ANNOTATED_API_PACKAGES, extension.getWriteAnnotatedAPIPackages());
        properties.put(Main.WRITE_ANNOTATED_API_DESTINATION_PACKAGE, extension.getWriteAnnotatedAPIDestinationPackage());

        properties.put(Main.READ_ANNOTATION_XML_PACKAGES, extension.getReadAnnotationXMLPackages());
        properties.put(Main.WRITE_ANNOTATION_XML, extension.isWriteAnnotationXML());
        properties.put(Main.WRITE_ANNOTATION_XML_DIR, getOrDefault(extension.getWriteAnnotationXMLDir(),
                new File(buildDir, "annotationXml").getAbsolutePath()));
        properties.put(Main.WRITE_ANNOTATION_XML_PACKAGES, extension.getWriteAnnotationXMLPackages());

        project.getPlugins().withType(JavaPlugin.class, javaPlugin -> {
            boolean hasSource = detectSourceDirsAndJavaClasspath(project, properties, extension.getJmods());
            if (hasSource) {
                detectSourceEncoding(project, properties);
            }
        });
    }

    private static String getOrDefault(String property, String defaultValue) {
        return property == null || property.isBlank() ? defaultValue : property;
    }

    private static void detectSourceEncoding(Project project, final Map<String, Object> properties) {
        project.getTasks().withType(JavaCompile.class, compile -> {
            String encoding = compile.getOptions().getEncoding();
            if (encoding != null) {
                properties.put(Main.SOURCE_ENCODING, encoding);
            }
        });
    }

    private static boolean detectSourceDirsAndJavaClasspath(Project project, Map<String, Object> properties, String jmods) {
        JavaPluginConvention javaPluginConvention = new DslObject(project).getConvention().getPlugin(JavaPluginConvention.class);

        SourceSet main = javaPluginConvention.getSourceSets().getAt("main");
        String sourceDirectoriesPathSeparated = sourcePathFromSourceSet(main);
        properties.put(Main.SOURCE, sourceDirectoriesPathSeparated);
        properties.put(Main.ANNOTATED_API_SOURCE, sourceDirectoriesPathSeparated);

        SourceSet test = javaPluginConvention.getSourceSets().getAt("test");
        String testDirectoriesPathSeparated = sourcePathFromSourceSet(test);
        properties.put(Main.TEST_SOURCE, testDirectoriesPathSeparated);

        String jmodsSeparated;
        if (jmods == null || jmods.trim().isEmpty()) jmodsSeparated = "";
        else {
            jmodsSeparated = Arrays.stream(jmods.trim().split("[" + Main.COMMA + Main.PATH_SEPARATOR + "]"))
                    .map(s -> Main.PATH_SEPARATOR + "jmods/" + s)
                    .collect(Collectors.joining());
        }
        String classPathSeparated = jmodsSeparated + Main.PATH_SEPARATOR + librariesFromSourceSet(main);
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
