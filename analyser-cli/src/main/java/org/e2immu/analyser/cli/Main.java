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

package org.e2immu.analyser.cli;

import org.apache.commons.cli.*;
import org.e2immu.analyser.config.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Consumer;

public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static final String PATH_SEPARATOR = System.getProperty("path.separator");
    public static final String FILE_SEPARATOR = System.getProperty("file.separator");

    public static final String COMMA = ",";
    public static final String COMMA_ALLOW_SPACE = ",\\s*";

    public static final String GRAPH_DIRECTORY = "graph-directory";

    public static final String UPLOAD_PROJECT = "upload-project";
    public static final String UPLOAD_PACKAGES = "upload-packages";
    public static final String UPLOAD_URL = "upload-url";
    public static final String UPLOAD = "upload";

    public static final String READ_ANNOTATION_XML_PACKAGES = "read-annotation-xml-packages";
    public static final String WRITE_ANNOTATION_XML = "write-annotation-xml";
    public static final String WRITE_ANNOTATION_XML_DIR = "write-annotation-xml-dir";
    public static final String WRITE_ANNOTATION_XML_PACKAGES = "write-annotation-xml-packages";

    public static final String ANNOTATED_API_SOURCE = "annotated-api-source";
    public static final String READ_ANNOTATED_API_PACKAGES = "read-annotated-api-packages";
    public static final String ANNOTATED_API_WRITE_MODE = "write-annotated-api";
    public static final String WRITE_ANNOTATED_API_DIR = "write-annotated-api-dir";
    public static final String WRITE_ANNOTATED_API_DESTINATION_PACKAGE = "write-annotated-api-destination-package";
    public static final String WRITE_ANNOTATED_API_PACKAGES = "write-annotated-api-packages";

    public static final String SOURCE_PACKAGES = "source-packages";
    public static final String TEST_SOURCE_PACKAGES = "test-source-packages";
    public static final String JRE = "jre";
    public static final String CLASSPATH = "classpath"; // ~ compileClassPath in Gradle
    public static final String RUNTIME_CLASSPATH = "runtime-classpath";
    public static final String TEST_CLASSPATH = "test-classpath";
    public static final String TESTS_RUNTIME_CLASSPATH = "test-runtime-classpath";
    public static final String SOURCE = "source";
    public static final String TEST_SOURCE = "test-source";
    public static final String SOURCE_ENCODING = "source-encoding";
    public static final String DEPENDENCIES = "dependencies";

    public static final String HELP = "help";

    public static final String DEBUG = "debug";
    public static final String IGNORE_ERRORS = "ignore-errors";
    public static final String QUIET = "quiet";
    public static final String SKIP_ANALYSIS = "skip-analysis"; // not available on CMD line
    public static final String PARALLEL = "parallel";

    public static final String ACTION = "action";
    public static final String ACTION_PARAMETER = "action-parameter";

    public static final int EXIT_OK = 0;
    public static final int EXIT_INTERNAL_EXCEPTION = 1;
    public static final int EXIT_PARSER_ERROR = 2;
    public static final int EXIT_INSPECTION_ERROR = 3;
    public static final int EXIT_IO_EXCEPTION = 4;
    public static final int EXIT_ANALYSER_ERROR = 5; // analyser found errors

    public static String exitMessage(int exitValue) {
        return switch (exitValue) {
            case EXIT_OK -> "OK";
            case EXIT_INTERNAL_EXCEPTION -> "Internal exception";
            case EXIT_PARSER_ERROR -> "Parser error(s)";
            case EXIT_INSPECTION_ERROR -> "Inspection error(s)";
            case EXIT_IO_EXCEPTION -> "IO exception";
            case EXIT_ANALYSER_ERROR -> "Analyser error(s)";
            default -> throw new UnsupportedOperationException("don't know value " + exitValue);
        };
    }

    public static void main(String[] args) {
        try {
            int exitValue = execute(args);
            if (exitValue != EXIT_OK) {
                LOGGER.error(exitMessage(exitValue));
            }
            System.exit(exitValue);
        } catch (ParseException parseException) {
            LOGGER.error("Parse exception: ", parseException);
            System.exit(EXIT_INTERNAL_EXCEPTION);
        }
    }

    private static int execute(String[] args) throws ParseException {
        CommandLineParser commandLineParser = new DefaultParser();
        Options options = createOptions();
        CommandLine cmd = commandLineParser.parse(options, args);
        String action = cmd.getOptionValue(ACTION);
        Configuration configuration = parseConfiguration(cmd, options);
        if (action != null) {
            String[] actionParameters = cmd.getOptionValues(ACTION_PARAMETER);
            return Action.execAction(action, actionParameters, configuration);
        }
        configuration.initializeLoggers();
        // the following will be output if the CONFIGURATION logger is active!
        LOGGER.debug("Configuration:\n{}", configuration);
        RunAnalyser runAnalyser = new RunAnalyser(configuration);
        runAnalyser.run();
        if (!configuration.quiet()) {
            runAnalyser.getMessageStream().forEach(m -> System.out.println(m.detailedMessage()));
        }
        return runAnalyser.getExitValue();
    }

    private static Configuration parseConfiguration(CommandLine cmd, Options options) {
        if (cmd.hasOption(HELP)) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.setOptionComparator(null);
            formatter.setWidth(128);
            formatter.printHelp("e2immu-analyser", options);
            System.exit(EXIT_OK);
        }
        Configuration.Builder builder = new Configuration.Builder();

        InputConfiguration inputConfiguration = parseInputConfiguration(cmd);
        builder.setInputConfiguration(inputConfiguration);

        InspectorConfiguration.Builder inspectorBuilder = new InspectorConfiguration.Builder();
        String graphDirectory = cmd.getOptionValue(GRAPH_DIRECTORY);
        inspectorBuilder.setGraphDirectory(graphDirectory);
        builder.setInspectorConfiguration(inspectorBuilder.build());

        String[] debugLogTargets = cmd.getOptionValues(DEBUG);
        splitAndAdd(debugLogTargets, COMMA_ALLOW_SPACE, builder::addDebugLogTargets);
        boolean ignoreErrors = cmd.hasOption(IGNORE_ERRORS);
        builder.setIgnoreErrors(ignoreErrors);

        boolean parallel = cmd.hasOption(PARALLEL);
        builder.setParallel(parallel);

        UploadConfiguration.Builder uploadBuilder = new UploadConfiguration.Builder();
        boolean upload = cmd.hasOption(UPLOAD);
        uploadBuilder.setUpload(upload);
        String uploadUrl = cmd.getOptionValue(UPLOAD_URL);
        uploadBuilder.setAnnotationServerUrl(uploadUrl);
        String projectName = cmd.getOptionValue(UPLOAD_PROJECT);
        uploadBuilder.setProjectName(projectName);
        String[] uploadPackages = cmd.getOptionValues(UPLOAD_PACKAGES);
        splitAndAdd(uploadPackages, COMMA_ALLOW_SPACE, uploadBuilder::addUploadPackage);
        builder.setUploadConfiguration(uploadBuilder.build());

        AnnotationXmlConfiguration.Builder xmlBuilder = new AnnotationXmlConfiguration.Builder();
        boolean writeAnnotationXml = cmd.hasOption(WRITE_ANNOTATION_XML);
        xmlBuilder.setAnnotationXml(writeAnnotationXml);
        xmlBuilder.setWriteAnnotationXmlDir(cmd.getOptionValue(WRITE_ANNOTATION_XML_DIR));
        String[] annotationXmlWritePackages = cmd.getOptionValues(WRITE_ANNOTATION_XML_PACKAGES);
        splitAndAdd(annotationXmlWritePackages, COMMA_ALLOW_SPACE, xmlBuilder::addAnnotationXmlWritePackages);
        String[] annotationXmlReadPackages = cmd.getOptionValues(READ_ANNOTATION_XML_PACKAGES);
        splitAndAdd(annotationXmlReadPackages, COMMA_ALLOW_SPACE, xmlBuilder::addAnnotationXmlReadPackages);
        builder.setAnnotationXmConfiguration(xmlBuilder.build());

        AnnotatedAPIConfiguration.Builder apiBuilder = new AnnotatedAPIConfiguration.Builder();
        String[] annotatedAPISources = cmd.getOptionValues(ANNOTATED_API_SOURCE);
        splitAndAdd(annotatedAPISources, PATH_SEPARATOR, apiBuilder::addAnnotatedAPISourceDirs);

        String writeAnnotatedAPIs = cmd.getOptionValue(ANNOTATED_API_WRITE_MODE);
        if (writeAnnotatedAPIs != null) {
            apiBuilder.setWriteMode(AnnotatedAPIConfiguration.WriteMode.valueOf(writeAnnotatedAPIs.trim().toUpperCase()));
        }
        apiBuilder.setWriteAnnotatedAPIsDir(cmd.getOptionValue(WRITE_ANNOTATED_API_DIR));
        apiBuilder.setDestinationPackage(cmd.getOptionValue(WRITE_ANNOTATED_API_DESTINATION_PACKAGE));
        String[] writeAnnotatedAPIPackages = cmd.getOptionValues(WRITE_ANNOTATED_API_PACKAGES);
        splitAndAdd(writeAnnotatedAPIPackages, COMMA_ALLOW_SPACE, apiBuilder::addWriteAnnotatedAPIPackages);
        String[] readAnnotatedAPIPackages = cmd.getOptionValues(READ_ANNOTATED_API_PACKAGES);
        splitAndAdd(readAnnotatedAPIPackages, COMMA_ALLOW_SPACE, apiBuilder::addReadAnnotatedAPIPackages);
        AnnotatedAPIConfiguration api = apiBuilder.build();
        builder.setAnnotatedAPIConfiguration(api);

        return builder.build();

    }

    private static InputConfiguration parseInputConfiguration(CommandLine cmd) {
        InputConfiguration.Builder inputBuilder = new InputConfiguration.Builder();

        String[] sources = cmd.getOptionValues(SOURCE);
        splitAndAdd(sources, PATH_SEPARATOR, inputBuilder::addSources);

        String[] testSources = cmd.getOptionValues(TEST_SOURCE);
        splitAndAdd(testSources, PATH_SEPARATOR, inputBuilder::addTestSources);

        String[] classPaths = cmd.getOptionValues(CLASSPATH);
        splitAndAdd(classPaths, PATH_SEPARATOR, inputBuilder::addClassPath);

        /* currently not available on cmd line, but only read via the Gradle plugin.
        If you activate them, also make new options in createOptions()

        String[] runtimeClassPaths = cmd.getOptionValues(RUNTIME_CLASSPATH);
        splitAndAdd(runtimeClassPaths, PATH_SEPARATOR, inputBuilder::addRuntimeClassPath);

        String[] testClassPaths = cmd.getOptionValues(TEST_CLASSPATH);
        splitAndAdd(testClassPaths, PATH_SEPARATOR, inputBuilder::addTestClassPath);

        String[] testRuntimeClassPaths = cmd.getOptionValues(TESTS_RUNTIME_CLASSPATH);
        splitAndAdd(testRuntimeClassPaths, PATH_SEPARATOR, inputBuilder::addTestRuntimeClassPath);

        String[] dependencies = cmd.getOptionValues(DEPENDENCIES);
        splitAndAdd(dependencies, COMMA, inputBuilder::addDependencies);
        */

        String alternativeJREDirectory = cmd.getOptionValue(JRE);
        inputBuilder.setAlternativeJREDirectory(alternativeJREDirectory);

        String sourceEncoding = cmd.getOptionValue(SOURCE_ENCODING);
        inputBuilder.setSourceEncoding(sourceEncoding);

        String[] restrictSourceToPackages = cmd.getOptionValues(SOURCE_PACKAGES);
        splitAndAdd(restrictSourceToPackages, COMMA_ALLOW_SPACE, inputBuilder::addRestrictSourceToPackages);

        String[] restrictTestSourceToPackages = cmd.getOptionValues(TEST_SOURCE_PACKAGES);
        splitAndAdd(restrictTestSourceToPackages, COMMA_ALLOW_SPACE, inputBuilder::addRestrictTestSourceToPackages);
        return inputBuilder.build();
    }

    private static void splitAndAdd(String[] strings, String separator, Consumer<String> adder) {
        if (strings != null) {
            for (String string : strings) {
                String[] parts = string.split(separator);
                for (String part : parts) {
                    if (part != null && !part.trim().isEmpty()) {
                        adder.accept(part);
                    }
                }
            }
        }
    }

    private static Options createOptions() {
        Options options = new Options();


        // input options

        options.addOption(Option.builder("s").longOpt(SOURCE).hasArg().argName("DIRS")
                .desc("Add a directory where the source files can be found. Use the Java path separator '" +
                        PATH_SEPARATOR + "' to separate directories, " +
                        "or use this options multiple times. Default, when this option is absent, is '"
                        + InputConfiguration.DEFAULT_SOURCE_DIRS + "'.").build());
        options.addOption(Option.builder().longOpt(TEST_SOURCE).hasArg().argName("DIRS")
                .desc("Add a directory where the test source files can be found. Use the Java path separator '" +
                        PATH_SEPARATOR + "' to separate directories, " +
                        "or use this options multiple times. Default, when this option is absent, is '"
                        + InputConfiguration.DEFAULT_SOURCE_DIRS + "'.").build());
        options.addOption(Option.builder("aas").longOpt(ANNOTATED_API_SOURCE).hasArg().argName("DIRS")
                .desc("Add a directory where the Annotated API source files can be found. Use the Java path separator '" +
                        PATH_SEPARATOR + "' to separate directories, " +
                        "or use this options multiple times. Default, when this option is absent, is empty.").build());
        options.addOption(Option.builder("cp").longOpt(CLASSPATH).hasArg().argName("CLASSPATH")
                .desc("Add classpath components, separated by the Java path separator '" + PATH_SEPARATOR +
                        "'. Default, when this option is absent, is '"
                        + Arrays.toString(InputConfiguration.DEFAULT_CLASSPATH) + "'.").build());
        options.addOption(Option.builder("cp").longOpt(TEST_CLASSPATH).hasArg().argName("CLASSPATH")
                .desc("Add test classpath components, separated by the Java path separator '" + PATH_SEPARATOR +
                        "'. All classpath components are already available. Default empty.").build());
        options.addOption(Option.builder().longOpt(JRE).hasArg().argName("DIR")
                .desc("Provide an alternative location for the Java Runtime Environment (JRE). " +
                        "If absent, the JRE from the analyser is used: '" + System.getProperty("java.home") + "'.").build());
        options.addOption(Option.builder()
                .longOpt(SOURCE_PACKAGES)
                .hasArg().argName("PATH")
                .desc("Restrict the sources parsed to the paths" +
                        " specified in the argument. Use ',' to separate paths, or use this option multiple times." +
                        " Use a dot at the end of a package name to accept sub-packages.").build());
        options.addOption(Option.builder()
                .longOpt(TEST_SOURCE_PACKAGES)
                .hasArg().argName("PATH")
                .desc("Restrict the test sources parsed to the paths" +
                        " specified in the argument. Use ',' to separate paths, or use this option multiple times." +
                        " Use a dot at the end of a package name to accept sub-packages.").build());

        options.addOption(Option.builder().longOpt(GRAPH_DIRECTORY).hasArg().argName("GRAPH_DIRECTORY")
                .desc("Directory to write the type graph and method call graphs").build());

        // common options

        options.addOption(Option.builder("d").longOpt(DEBUG).hasArg().argName("LOG TARGETS").desc(
                "Log targets to be activated for debug output of the analyser. " +
                        "Separate with comma, or use the option multiple times.").build());
        options.addOption("h", HELP, false, "Print help.");
        options.addOption("i", IGNORE_ERRORS, false,
                "Return exit code 0, even if the analyser raises errors.");
        options.addOption("p", PARALLEL, false, "Parallelize as much as possible.");
        options.addOption("q", QUIET, false,
                "Silent mode. Do not write warnings, errors, etc. to stdout. " +
                        "They are still uploaded when the --upload option is activated.");

        // output options: upload

        options.addOption("u", UPLOAD, false, "Upload annotations to the annotation server.");
        options.addOption(Option.builder()
                .longOpt(UPLOAD_URL)
                .hasArg().argName("URL")
                .desc("Alternative location for the annotation server; default is '" + UploadConfiguration.DEFAULT_ANNOTATION_SERVER_URL + "'.").build());
        options.addOption(Option.builder()
                .longOpt(UPLOAD_PROJECT)
                .hasArg().argName("NAME")
                .desc("Name of the project to upload the annotations to. Default is '" + UploadConfiguration.DEFAULT_PROJECT + "'.").build());
        options.addOption(Option.builder()
                .longOpt(UPLOAD_PACKAGES)
                .hasArg().argName("PACKAGES")
                .desc("A comma-separated list of package names for" +
                        " which annotations are to be uploaded. The default is to upload all annotations of all types " +
                        "encountered during the parsing process.").build());

        // output options: annotation.xml

        options.addOption("w", WRITE_ANNOTATION_XML, false, "Write annotation.xml files");
        options.addOption(Option.builder()
                .longOpt(WRITE_ANNOTATION_XML_PACKAGES)
                .hasArg().argName("PACKAGES")
                .desc("A comma-separated list of package names for" +
                        " which annotation.xml files are to be generated." +
                        " Use a dot at the end of a package name to accept sub-packages." +
                        "The default is to write annotation.xml files for all the packages of .java files parsed.").build());
        options.addOption(Option.builder()
                .longOpt(READ_ANNOTATION_XML_PACKAGES)
                .hasArg().argName("PACKAGES")
                .desc("A comma-separated list of package names for" +
                        " which annotation.xml files are to be read." +
                        " Use a dot at the end of a package name to accept sub-packages." +
                        "The default is to read all annotation.xml files. Write 'none' to refuse all.").build());
        options.addOption(Option.builder()
                .longOpt(WRITE_ANNOTATION_XML_DIR)
                .hasArg().argName("DIR")
                .desc("Alternative location to write the Xml files." +
                        " Defaults to the resources directory of the project.").build());

        // output options: annotated_api

        options.addOption(Option.builder("a")
                .longOpt(ANNOTATED_API_WRITE_MODE)
                .hasArg(true)
                .desc("Write mode for annotated API files. Must be one of the following four values: "
                        + "DO_NOT_WRITE (default), INSPECTED, ANALYSED, USAGE.").build());
        options.addOption(Option.builder()
                .longOpt(WRITE_ANNOTATED_API_PACKAGES)
                .hasArg().argName("PACKAGES")
                .desc("A comma-separated list of package names for" +
                        " which annotated API files are to be generated." +
                        " Use a dot at the end of a package name to accept sub-packages." +
                        "The default is to write annotated API files for all the packages of .java files parsed.").build());
        options.addOption(Option.builder()
                .longOpt(READ_ANNOTATED_API_PACKAGES)
                .hasArg().argName("PACKAGES")
                .desc("A comma-separated list of package names for" +
                        " which annotated API files are to be read." +
                        " Use a dot at the end of a package name to accept sub-packages." +
                        "The default is to read annotated API files from all the packages of .java files parsed.").build());
        options.addOption(Option.builder()
                .longOpt(WRITE_ANNOTATED_API_DIR)
                .hasArg().argName("DIR")
                .desc("Alternative location to write the annotated API files." +
                        " The default is '" + AnnotatedAPIConfiguration.DEFAULT_DESTINATION_DIRECTORY + "'").build());
        options.addOption(Option.builder()
                .longOpt(WRITE_ANNOTATED_API_DESTINATION_PACKAGE)
                .hasArg().argName("DIR")
                .desc("Destination package for annotated API files to be written. Default: '" +
                        AnnotatedAPIConfiguration.DEFAULT_DESTINATION_PACKAGE + "'.").build());
        return options;
    }

    public static Configuration fromProperties(Map<String, String> analyserProperties) {
        Configuration.Builder builder = new Configuration.Builder();
        builder.setInputConfiguration(inputConfigurationFromProperties(analyserProperties));
        builder.setInspectorConfiguration(inspectorConfigurationPromProperties(analyserProperties));
        builder.setUploadConfiguration(uploadConfigurationFromProperties(analyserProperties));
        builder.setAnnotatedAPIConfiguration(annotatedAPIConfigurationFromProperties(analyserProperties));
        builder.setAnnotationXmConfiguration(annotationXmlConfigurationFromProperties(analyserProperties));

        setBooleanProperty(analyserProperties, QUIET, builder::setQuiet);
        setBooleanProperty(analyserProperties, IGNORE_ERRORS, builder::setIgnoreErrors);
        setBooleanProperty(analyserProperties, PARALLEL, builder::setParallel);
        setBooleanProperty(analyserProperties, SKIP_ANALYSIS, builder::setSkipAnalysis);

        setSplitStringProperty(analyserProperties, COMMA_ALLOW_SPACE, DEBUG, builder::addDebugLogTargets);

        return builder.build();
    }

    public static InspectorConfiguration inspectorConfigurationPromProperties(Map<String, String> map) {
        InspectorConfiguration.Builder builder = new InspectorConfiguration.Builder();
        setStringProperty(map, GRAPH_DIRECTORY, builder::setGraphDirectory);
        return builder.build();
    }

    public static UploadConfiguration uploadConfigurationFromProperties(Map<String, String> analyserProperties) {
        UploadConfiguration.Builder builder = new UploadConfiguration.Builder();
        setBooleanProperty(analyserProperties, UPLOAD, builder::setUpload);
        setStringProperty(analyserProperties, UPLOAD_PROJECT, builder::setProjectName);
        setStringProperty(analyserProperties, UPLOAD_URL, builder::setAnnotationServerUrl);
        setSplitStringProperty(analyserProperties, COMMA_ALLOW_SPACE, UPLOAD_PACKAGES, builder::addUploadPackage);
        return builder.build();
    }

    public static AnnotationXmlConfiguration annotationXmlConfigurationFromProperties(Map<String, String> analyserProperties) {
        AnnotationXmlConfiguration.Builder builder = new AnnotationXmlConfiguration.Builder();
        setBooleanProperty(analyserProperties, WRITE_ANNOTATION_XML, builder::setAnnotationXml);
        setStringProperty(analyserProperties, WRITE_ANNOTATION_XML_DIR, builder::setWriteAnnotationXmlDir);
        setSplitStringProperty(analyserProperties, COMMA_ALLOW_SPACE, WRITE_ANNOTATION_XML_PACKAGES, builder::addAnnotationXmlWritePackages);
        return builder.build();
    }

    public static InputConfiguration inputConfigurationFromProperties(Map<String, String> analyserProperties) {
        InputConfiguration.Builder builder = new InputConfiguration.Builder();
        setStringProperty(analyserProperties, JRE, builder::setAlternativeJREDirectory);
        setStringProperty(analyserProperties, SOURCE_ENCODING, builder::setSourceEncoding);
        setSplitStringProperty(analyserProperties, PATH_SEPARATOR, SOURCE, builder::addSources);
        setSplitStringProperty(analyserProperties, PATH_SEPARATOR, TEST_SOURCE, builder::addTestSources);
        setSplitStringProperty(analyserProperties, PATH_SEPARATOR, CLASSPATH, builder::addClassPath);
        setSplitStringProperty(analyserProperties, PATH_SEPARATOR, RUNTIME_CLASSPATH, builder::addRuntimeClassPath);
        setSplitStringProperty(analyserProperties, PATH_SEPARATOR, TEST_CLASSPATH, builder::addTestClassPath);
        setSplitStringProperty(analyserProperties, PATH_SEPARATOR, TESTS_RUNTIME_CLASSPATH, builder::addTestRuntimeClassPath);
        setSplitStringProperty(analyserProperties, COMMA, DEPENDENCIES, builder::addDependencies);
        setSplitStringProperty(analyserProperties, COMMA_ALLOW_SPACE, SOURCE_PACKAGES, builder::addRestrictSourceToPackages);
        return builder.build();
    }

    public static AnnotatedAPIConfiguration annotatedAPIConfigurationFromProperties(Map<String, String> analyserProperties) {
        AnnotatedAPIConfiguration.Builder builder = new AnnotatedAPIConfiguration.Builder();
        setWriteModeProperty(analyserProperties, builder::setWriteMode);
        setStringProperty(analyserProperties, Main.WRITE_ANNOTATED_API_DESTINATION_PACKAGE, builder::setDestinationPackage);
        setStringProperty(analyserProperties, Main.WRITE_ANNOTATED_API_DIR, builder::setWriteAnnotatedAPIsDir);
        setSplitStringProperty(analyserProperties, Main.COMMA_ALLOW_SPACE, Main.WRITE_ANNOTATED_API_PACKAGES, builder::addWriteAnnotatedAPIPackages);
        setSplitStringProperty(analyserProperties, Main.COMMA_ALLOW_SPACE, Main.READ_ANNOTATED_API_PACKAGES, builder::addReadAnnotatedAPIPackages);
        setSplitStringProperty(analyserProperties, PATH_SEPARATOR, ANNOTATED_API_SOURCE, builder::addAnnotatedAPISourceDirs);
        return builder.build();
    }

    static void setWriteModeProperty(Map<String, String> properties,
                                     Consumer<AnnotatedAPIConfiguration.WriteMode> consumer) {
        String value = properties.get(Main.ANNOTATED_API_WRITE_MODE);
        if (value != null) {
            String trimToUpper = value.trim().toUpperCase();
            consumer.accept(AnnotatedAPIConfiguration.WriteMode.valueOf(trimToUpper));
        }
    }

    static void setStringProperty(Map<String, String> properties, String key, Consumer<String> consumer) {
        String value = properties.get(key);
        if (value != null) {
            String trim = value.trim();
            if (!trim.isEmpty()) consumer.accept(trim);
        }
    }

    public static void setSplitStringProperty(Map<String, String> properties, String separator, String key, Consumer<String> consumer) {
        String value = properties.get(key);
        if (value != null) {
            String[] parts = value.split(separator);
            for (String part : parts) {
                if (part != null && !part.trim().isEmpty()) {
                    consumer.accept(part);
                }
            }
        }
    }

    public static void setBooleanProperty(Map<String, String> properties, String key, Consumer<Boolean> consumer) {
        String value = properties.get(key);
        if (value != null) {
            consumer.accept("true".equalsIgnoreCase(value.trim()));
        }
    }
}
