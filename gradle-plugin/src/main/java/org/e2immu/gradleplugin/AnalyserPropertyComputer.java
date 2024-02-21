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

import org.e2immu.analyser.cli.GradleConfiguration;
import org.e2immu.analyser.cli.Main;
import org.e2immu.analyser.config.AnnotatedAPIConfiguration;
import org.e2immu.support.FlipSwitch;
import org.e2immu.support.SetOnce;
import org.gradle.api.Project;
import org.gradle.api.artifacts.*;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Property names are identical to those of the CLI (.cli.Main). In the system properties,
 * they have to be prefixed by the PREFIX defined in this class.
 */
public class AnalyserPropertyComputer {
    private final Map<String, ActionBroadcast<AnalyserProperties>> actionBroadcastMap;
    private final Project targetProject;

    public AnalyserPropertyComputer(Map<String, ActionBroadcast<AnalyserProperties>> actionBroadcastMap,
                                    Project targetProject) {
        this.targetProject = targetProject;
        this.actionBroadcastMap = actionBroadcastMap;
    }

    private static final Logger LOGGER = Logging.getLogger(AnalyserPropertyComputer.class);
    public static final String PREFIX = "e2immu-analyser.";
    // used for round trip String[] -> String -> String[]; TODO this should be done in a better way
    public static final String M_A_G_I_C = "__M_A_G_I_C__";

    public Map<String, Object> computeProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        computeProperties(targetProject, properties, "");
        return properties;
    }

    private void computeProperties(Project project, Map<String, Object> properties, String prefix) {
        AnalyserExtension extension = project.getExtensions().getByType(AnalyserExtension.class);
        if (extension.skipProject) {
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
        // convert all the properties from subprojects into dot-notated properties
        flattenProperties(rawProperties, prefix, properties);

        LOGGER.debug("Resulting map is " + properties);

        List<Project> enabledChildProjects = project.getChildProjects().values().stream()
                .filter(p -> !p.getExtensions().getByType(AnalyserExtension.class).skipProject)
                .toList();

        List<Project> skippedChildProjects = project.getChildProjects().values().stream()
                .filter(p -> p.getExtensions().getByType(AnalyserExtension.class).skipProject)
                .toList();

        if (!skippedChildProjects.isEmpty()) {
            LOGGER.debug("Skipping collecting Analyser properties on: " +
                    skippedChildProjects.stream().map(Project::toString).collect(Collectors.joining(", ")));
        }

        // recurse
        for (Project childProject : enabledChildProjects) {
            String moduleId = childProject.getPath();
            String modulePrefix = !prefix.isEmpty() ? (prefix + "." + moduleId) : moduleId;
            computeProperties(childProject, properties, modulePrefix);
        }
    }

    private void detectProperties(Project project, Map<String, Object> properties, AnalyserExtension extension) {
        properties.put(Main.DEBUG, extension.debug);
        properties.put(Main.SOURCE_PACKAGES, extension.sourcePackages);
        properties.put(Main.TEST_SOURCE_PACKAGES, extension.testSourcePackages);
        properties.put(Main.JRE, extension.jre);
        properties.put(Main.IGNORE_ERRORS, extension.ignoreErrors);
        properties.put(Main.SKIP_ANALYSIS, extension.skipAnalysis);
        properties.put(Main.PARALLEL, extension.parallel);

        properties.put(Main.GRAPH_DIRECTORY, extension.graphDirectory);

        properties.put(Main.UPLOAD, extension.upload == null || extension.upload);
        properties.put(Main.UPLOAD_PROJECT, project.getName());
        properties.put(Main.UPLOAD_URL, extension.uploadUrl);
        properties.put(Main.UPLOAD_PACKAGES, extension.uploadPackages);

        File buildDir = project.getLayout().getBuildDirectory().get().getAsFile();
        properties.put(Main.READ_ANNOTATED_API_PACKAGES, getOrDefault(extension.readAnnotatedAPIPackages,
                AnnotatedAPIConfiguration.DO_NOT_READ_ANNOTATED_API));
        properties.put(Main.ANNOTATED_API_WRITE_MODE, getOrDefault(extension.annotatedAPIWriteMode,
                AnnotatedAPIConfiguration.WriteMode.DO_NOT_WRITE.toString()));
        properties.put(Main.WRITE_ANNOTATED_API_DIR, getOrDefault(extension.writeAnnotatedAPIDir,
                new File(buildDir, "annotatedAPIs").getAbsolutePath()));
        properties.put(Main.WRITE_ANNOTATED_API_PACKAGES, extension.writeAnnotatedAPIPackages);
        properties.put(Main.WRITE_ANNOTATED_API_DESTINATION_PACKAGE, extension.writeAnnotatedAPIDestinationPackage);

        properties.put(Main.READ_ANNOTATION_XML_PACKAGES, extension.readAnnotationXMLPackages);
        properties.put(Main.WRITE_ANNOTATION_XML, extension.writeAnnotationXML);
        properties.put(Main.WRITE_ANNOTATION_XML_DIR, getOrDefault(extension.writeAnnotationXMLDir,
                new File(buildDir, "annotationXml").getAbsolutePath()));
        properties.put(Main.WRITE_ANNOTATION_XML_PACKAGES, extension.writeAnnotationXMLPackages);

        properties.put(Main.ACTION, extension.action);
        if (extension.actionParameters != null) {
            String joined = String.join(M_A_G_I_C, extension.actionParameters);
            properties.put(Main.ACTION_PARAMETER, joined);
        }
        project.getPlugins().withType(JavaPlugin.class, javaPlugin -> {
            boolean hasSource = detectSourceDirsAndJavaClasspath(project, properties, extension.jmods);
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

    private static final String[] UNRESOLVABLE_CONFIGURATIONS =
            Arrays.stream(GradleConfiguration.values()).filter(c -> !c.transitive)
                    .map(c -> c.gradle).toArray(String[]::new);

    private static final String[] RESOLVABLE_CONFIGURATIONS =
            Arrays.stream(GradleConfiguration.values()).filter(c -> c.transitive)
                    .map(c -> c.gradle).toArray(String[]::new);

    private static final Map<String, String> CONFIG_SHORTHAND =
            Arrays.stream(GradleConfiguration.values()).collect(Collectors.toUnmodifiableMap(
                    c -> c.gradle, c -> c.abbrev
            ));


    record Dependent(String group, String module, String requestedVersion) implements Comparable<Dependent> {
        @Override
        public int compareTo(@NotNull AnalyserPropertyComputer.Dependent o) {
            int c = group.compareTo(o.group);
            return c == 0 ? module.compareTo(o.module) : c;
        }
    }

    record Dependency(String group,
                      String module,
                      String requestedVersionOrNull,
                      boolean transitiveNotInUnresolvableConfiguration,
                      SetOnce<String> resolvedVersion,
                      FlipSwitch resolvedByConstraint,
                      String configuration,
                      Map<String, Dependent> dependents,
                      SetOnce<File> file) implements Comparable<Dependency> {
        @Override
        public int compareTo(@NotNull AnalyserPropertyComputer.Dependency o) {
            int c = group.compareTo(o.group);
            return c == 0 ? module.compareTo(o.module) : c;
        }
    }

    private static boolean detectSourceDirsAndJavaClasspath(Project project, Map<String, Object> properties, String jmods) {
        JavaPluginExtension javaPluginExtension = new DslObject(project).getExtensions().getByType(JavaPluginExtension.class);

        SourceSet main = javaPluginExtension.getSourceSets().getAt("main");
        String sourceDirectoriesPathSeparated = sourcePathFromSourceSet(main);
        properties.put(Main.SOURCE, sourceDirectoriesPathSeparated);
        properties.put(Main.ANNOTATED_API_SOURCE, sourceDirectoriesPathSeparated);

        SourceSet test = javaPluginExtension.getSourceSets().getAt("test");
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

        String runtimeClassPathSeparated = runtimeLibrariesFromSourceSet(main);
        properties.put(Main.RUNTIME_CLASSPATH, runtimeClassPathSeparated);

        String testClassPathSeparated = librariesFromSourceSet(test);
        properties.put(Main.TEST_CLASSPATH, jmodsSeparated + Main.PATH_SEPARATOR + testClassPathSeparated);

        String testRuntimeClassPathSeparated = runtimeLibrariesFromSourceSet(test);
        properties.put(Main.TESTS_RUNTIME_CLASSPATH, testRuntimeClassPathSeparated);

        List<String> dependencyList = new LinkedList<>();
        Map<String, Dependency> dependencyMap = new HashMap<>();

        Set<String> seen = new HashSet<>();
        for (String configurationName : UNRESOLVABLE_CONFIGURATIONS) {
            Configuration configuration = project.getConfigurations().getByName(configurationName);
            String configShortHand = Objects.requireNonNull(CONFIG_SHORTHAND.get(configurationName));
            for (org.gradle.api.artifacts.Dependency d : configuration.getDependencies()) {
                String description = d.getGroup() + ":" + d.getName() + ":" + d.getVersion();
                seen.add(description);
                String excludes;
                if (d instanceof ModuleDependency md && !md.getExcludeRules().isEmpty()) {
                    excludes = "[-" + md.getExcludeRules().stream()
                            .map(er -> er.getGroup() + ":" + er.getModule())
                            .collect(Collectors.joining(";")) + "]";
                } else {
                    excludes = "";
                }
                dependencyList.add(description + ":" + configShortHand + excludes);

                String groupModule = d.getGroup() + ":" + d.getName();
                Dependency dependency = dependencyMap.get(groupModule);
                if (dependency == null) {
                    Dependency newDependency = new Dependency(d.getGroup(), d.getName(), d.getVersion(),
                            false,
                            new SetOnce<>(), new FlipSwitch(), configurationName, new HashMap<>(),
                            new SetOnce<>());
                    dependencyMap.put(groupModule, newDependency);
                }
            }
        }
        // now the resolved path
        for (String configurationName : RESOLVABLE_CONFIGURATIONS) {
            Configuration configuration = project.getConfigurations().getByName(configurationName);
            String configShortHand = Objects.requireNonNull(CONFIG_SHORTHAND.get(configurationName));

            Set<ResolvedComponentResult> set = configuration.getIncoming().getResolutionResult().getAllComponents();
            LOGGER.debug("CONFIGURATION: {}", configurationName);
            // if ("implementation".equals(configurationName)) {
            for (ResolvedComponentResult rcr : set) {
                ModuleVersionIdentifier mvi = rcr.getModuleVersion();
                if (mvi != null) {
                    LOGGER.debug("RCR: {}:{}:{} -- {}", mvi.getGroup(), mvi.getName(), mvi.getVersion(),
                            rcr.getSelectionReason());
                    String groupModule = mvi.getGroup() + ":" + mvi.getName();
                    Dependency inMap = dependencyMap.get(groupModule);
                    Dependency dependency;
                    if (inMap == null) {
                        dependency = new Dependency(mvi.getGroup(), mvi.getName(), null,
                                true,
                                new SetOnce<>(), new FlipSwitch(), configurationName, new HashMap<>(),
                                new SetOnce<>());
                        dependencyMap.put(groupModule, dependency);
                        LOGGER.debug("*** transitive dependency {}, not explicitly mentioned", groupModule);
                    } else {
                        dependency = inMap;
                    }
                    if ("constraint".equals(rcr.getSelectionReason().toString())
                            && !dependency.resolvedByConstraint.isSet()) {
                        dependency.resolvedByConstraint.set();
                    }
                    if (!dependency.resolvedVersion.isSet()) {
                        dependency.resolvedVersion.set(mvi.getVersion());
                    }
                    for (DependencyResult dr : rcr.getDependents()) {
                        ModuleVersionIdentifier moduleVersion = dr.getFrom().getModuleVersion();
                        if (moduleVersion != null) {
                            if (!"_java".equals(moduleVersion.getName())) {
                                String requested;
                                if (dr.getRequested() instanceof DefaultModuleComponentSelector cs) {
                                    requested = moduleVersion.getVersion().equals(cs.getVersion()) ? ""
                                            : " -- requested " + cs.getVersion();
                                    Dependent dependent = new Dependent(moduleVersion.getGroup(), moduleVersion.getName(), cs.getVersion());
                                    String dependentKey = moduleVersion.getGroup() + ":" + moduleVersion.getModule();
                                    if (!dependentKey.equals(groupModule)) {
                                        dependency.dependents.put(dependentKey, dependent);
                                    }
                                } else {
                                    requested = "??";
                                }
                                LOGGER.debug("    {}:{}:{} {}", moduleVersion.getGroup(),
                                        moduleVersion.getName(), moduleVersion.getVersion(), requested);
                            }
                        } else {
                            LOGGER.debug("??? no module version: {}", dr);
                        }
                    }
                }
            }
            //  }
            for (ResolvedArtifactResult rar : configuration.getIncoming().getArtifacts().getArtifacts()) {
                if (rar.getVariant().getOwner() instanceof ModuleComponentIdentifier mci) {
                    String description = mci.getGroup() + ":" + mci.getModule() + ":" + mci.getVersion();
                    if (seen.add(description)) {
                        dependencyList.add(description + ":" + configShortHand);
                    }
                    String groupModule = mci.getGroup() + ":" + mci.getModule();
                    Dependency dependency = dependencyMap.get(groupModule);
                    if (dependency != null && !dependency.file.isSet()) {
                        dependency.file.set(rar.getFile());
                    }
                }
            }
        }
        dependencyMap.remove(":_java");

        LOGGER.info("Dependency map:");
        dependencyMap.values().stream().sorted().forEach(dependency -> {
            String resolved;
            if (dependency.resolvedVersion.isSet() && dependency.resolvedVersion.get().equals(dependency.requestedVersionOrNull)) {
                resolved = dependency.resolvedVersion.get();
            } else if (dependency.resolvedVersion.isSet()) {
                resolved = yellow(dependency.resolvedVersion.get());
            } else {
                resolved = yellow("??");
            }
            LOGGER.info("{}:{}  requested {} resolved {} {} {}", dependency.group, dependency.module,
                    dependency.requestedVersionOrNull, resolved,
                    dependency.transitiveNotInUnresolvableConfiguration ? blue(dependency.configuration) : dependency.configuration,
                    dependency.file.isSet() ? "" : "<no file>");
            String version = dependency.resolvedVersion.isSet() ? dependency.resolvedVersion.get() : dependency.requestedVersionOrNull;
            dependency.dependents.values().stream().sorted().forEach(dependent -> {
                String reqVer;
                if (dependent.requestedVersion.equals(version)) {
                    reqVer = dependent.requestedVersion;
                } else {
                    reqVer = yellow(dependent.requestedVersion);
                }
                LOGGER.info("      {}:{}  requested {}", dependent.group, dependent.module, reqVer);
            });
        });
        String dependencies = String.join(",", dependencyList);
        properties.put(Main.DEPENDENCIES, dependencies);

        return !sourceDirectoriesPathSeparated.isEmpty() || !testDirectoriesPathSeparated.isEmpty();
    }

    private static String yellow(String s) {
        return "\033[33;1m" + s + "\033[0m";
    }

    private static String blue(String s) {
        return "\033[36;1m" + s + "\033[0m";
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

    private static String runtimeLibrariesFromSourceSet(SourceSet sourceSet) {
        return sourceSet.getRuntimeClasspath().getFiles()
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
