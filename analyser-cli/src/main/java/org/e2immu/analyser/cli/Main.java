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
import org.e2immu.analyser.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Consumer;

public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static final String PATH_SEPARATOR = System.getProperty("path.separator");

    public static final String UPLOAD_PROJECT = "upload-project";
    public static final String UPLOAD_PACKAGES = "upload-packages";
    public static final String UPLOAD_URL = "upload-url";
    public static final String UPLOAD = "upload";
    public static final String WRITE_ANNOTATED_API = "write-annotated-api";
    public static final String WRITE_ANNOTATION_XML = "write-annotation-xml";
    public static final String WRITE_ANNOTATION_XML_DIR = "write-annotation-xml-dir";
    public static final String WRITE_ANNOTATED_API_DIR = "write-annotated-api-dir";
    public static final String WRITE_ANNOTATION_XML_PACKAGES = "write-annotation-xml-packages";
    public static final String WRITE_ANNOTATED_API_PACKAGES = "write-annotated-api-packages";
    public static final String COMMA = ",";
    public static final String SOURCE_PACKAGES = "source-packages";
    public static final String JRE = "jre";
    public static final String CLASSPATH = "classpath";
    public static final String TEST_CLASSPATH = "test-classpath";
    public static final String SOURCE = "source";
    public static final String TEST_SOURCE = "test-source";
    public static final String SOURCE_ENCODING = "source-encoding";
    public static final String HELP = "help";
    public static final String DEBUG = "debug";
    public static final String IGNORE_ERRORS = "ignore-errors";
    public static final String QUIET = "quiet";
    public static final String SKIP_ANALYSIS = "skip-analysis"; // not available on CMD line

    public static void main(String[] args) {
        CommandLineParser parser = new DefaultParser();
        Options options = createOptions();
        try {
            CommandLine cmd = parser.parse(options, args);
            if (cmd.hasOption(HELP)) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.setOptionComparator(null);
                formatter.setWidth(128);
                formatter.printHelp("e2immu-analyser", options);
                System.exit(0);
            }
            Configuration.Builder builder = new Configuration.Builder();
            InputConfiguration.Builder inputBuilder = new InputConfiguration.Builder();
            String[] sources = cmd.getOptionValues(SOURCE);
            splitAndAdd(sources, PATH_SEPARATOR, inputBuilder::addSources);
            String[] classPaths = cmd.getOptionValues(CLASSPATH);
            splitAndAdd(classPaths, PATH_SEPARATOR, inputBuilder::addClassPath);

            String alternativeJREDirectory = cmd.getOptionValue(JRE);
            inputBuilder.setAlternativeJREDirectory(alternativeJREDirectory);
            String sourceEncoding = cmd.getOptionValue(SOURCE_ENCODING);
            inputBuilder.setSourceEncoding(sourceEncoding);

            String[] restrictSourceToPackages = cmd.getOptionValues(SOURCE_PACKAGES);
            splitAndAdd(restrictSourceToPackages, COMMA, inputBuilder::addRestrictSourceToPackages);
            builder.setInputConfiguration(inputBuilder.build());

            String[] debugLogTargets = cmd.getOptionValues(DEBUG);
            splitAndAdd(debugLogTargets, COMMA, builder::addDebugLogTargets);
            boolean ignoreErrors = cmd.hasOption(IGNORE_ERRORS);
            builder.setIgnoreErrors(ignoreErrors);
            boolean quiet = cmd.hasOption(QUIET);
            builder.setQuiet(quiet);

            UploadConfiguration.Builder uploadBuilder = new UploadConfiguration.Builder();
            boolean upload = cmd.hasOption(UPLOAD);
            uploadBuilder.setUpload(upload);
            String uploadUrl = cmd.getOptionValue(UPLOAD_URL);
            uploadBuilder.setAnnotationServerUrl(uploadUrl);
            String projectName = cmd.getOptionValue(UPLOAD_PROJECT);
            uploadBuilder.setProjectName(projectName);
            String[] uploadPackages = cmd.getOptionValues(UPLOAD_PACKAGES);
            splitAndAdd(uploadPackages, COMMA, uploadBuilder::addUploadPackage);
            builder.setUploadConfiguration(uploadBuilder.build());

            AnnotationXmlConfiguration.Builder xmlBuilder = new AnnotationXmlConfiguration.Builder();
            boolean writeAnnotationXml = cmd.hasOption(WRITE_ANNOTATION_XML);
            xmlBuilder.setAnnotationXml(writeAnnotationXml);
            xmlBuilder.setWriteAnnotationXmlDir(cmd.getOptionValue(WRITE_ANNOTATION_XML_DIR));
            String[] annotationXmlPackages = cmd.getOptionValues(WRITE_ANNOTATION_XML_PACKAGES);
            splitAndAdd(annotationXmlPackages, COMMA, xmlBuilder::addAnnotationXmlPackages);
            builder.setWriteAnnotationXmConfiguration(xmlBuilder.build());

            AnnotatedAPIConfiguration.Builder apiBuilder = new AnnotatedAPIConfiguration.Builder();
            boolean writeAnnotatedAPIs = cmd.hasOption(WRITE_ANNOTATED_API);
            apiBuilder.setAnnotatedAPIs(writeAnnotatedAPIs);
            apiBuilder.setWriteAnnotatedAPIsDir(cmd.getOptionValue(WRITE_ANNOTATED_API_DIR));
            String[] annotatedAPIPackages = cmd.getOptionValues(WRITE_ANNOTATED_API_PACKAGES);
            splitAndAdd(annotatedAPIPackages, COMMA, apiBuilder::addAnnotatedAPIPackages);
            builder.setAnnotatedAPIConfiguration(apiBuilder.build());

            Configuration configuration = builder.build();
            configuration.initializeLoggers();
            new Parser(configuration).run();

        } catch (ParseException parseException) {
            parseException.printStackTrace();
            System.err.println("Caught parse exception: " + parseException.getMessage());
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Caught IO exception during run: " + e.getMessage());
            System.exit(1);
        }
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
        options.addOption(Option.builder("cp").longOpt(CLASSPATH).hasArg().argName("CLASSPATH")
                .desc("Add classpath components, separated by the Java path separator '" + PATH_SEPARATOR +
                        "'. Default, when this option is absent, is '"
                        + Arrays.toString(InputConfiguration.DEFAULT_CLASSPATH) + "'.").build());

        options.addOption(Option.builder().longOpt(JRE).hasArg().argName("DIR")
                .desc("Provide an alternative location for the Java Runtime Environment (JRE). " +
                        "If absent, the JRE from the analyser is used: '" + System.getProperty("java.home") + "'.").build());
        options.addOption(Option.builder()
                .longOpt(SOURCE_PACKAGES)
                .hasArg().argName("PATH")
                .desc("Restrict the sources parsed to the paths" +
                        " specified in the argument. Use ',' to separate paths, or use this option multiple times." +
                        " Use a dot at the end of a package name to accept sub-packages.").build());

        // common options

        options.addOption(Option.builder("d").longOpt(DEBUG).hasArg().argName("LOG TARGETS").desc(
                "Log targets to be activated for debug output of the analyser. " +
                        "Separate with comma, or use the option multiple times.").build());
        options.addOption("h", HELP, false, "Print help.");
        options.addOption("i", IGNORE_ERRORS, false,
                "Return exit code 0, even if the analyser raises errors.");
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
                .longOpt(WRITE_ANNOTATION_XML_DIR)
                .hasArg().argName("DIR")
                .desc("Alternative location to write the Xml files." +
                        " Defaults to the resources directory of the project.").build());

        // output options: annotated_api

        options.addOption("a", WRITE_ANNOTATED_API, false, "Write annotated API files.");
        options.addOption(Option.builder()
                .longOpt(WRITE_ANNOTATED_API_PACKAGES)
                .hasArg().argName("PACKAGES")
                .desc("A comma-separated list of package names for" +
                        " which annotated API files are to be generated." +
                        " Use a dot at the end of a package name to accept sub-packages." +
                        "The default is to write annotated API files for all the packages of .java files parsed.").build());
        options.addOption(Option.builder()
                .longOpt(WRITE_ANNOTATED_API_DIR)
                .hasArg().argName("DIR")
                .desc("Alternative location to write the annotated API files." +
                        " The default is the user working directory set by the Java System property 'user.dir', currently '"
                        + System.getProperty("user.dir") + "'.").build());

        return options;
    }

    public static Configuration fromProperties(Map<String, String> analyserProperties) {
        Configuration.Builder builder = new Configuration.Builder();
        builder.setInputConfiguration(inputConfigurationFromProperties(analyserProperties));
        builder.setUploadConfiguration(uploadConfigurationFromProperties(analyserProperties));
        builder.setAnnotatedAPIConfiguration(annotatedAPIConfigurationFromProperties(analyserProperties));
        builder.setWriteAnnotationXmConfiguration(annotationXmlConfigurationFromProperties(analyserProperties));

        setBooleanProperty(analyserProperties, QUIET, builder::setQuiet);
        setBooleanProperty(analyserProperties, IGNORE_ERRORS, builder::setIgnoreErrors);
        setBooleanProperty(analyserProperties, SKIP_ANALYSIS, builder::setSkipAnalysis);

        setSplitStringProperty(analyserProperties, COMMA, DEBUG, builder::addDebugLogTargets);

        return builder.build();
    }

    public static UploadConfiguration uploadConfigurationFromProperties(Map<String, String> analyserProperties) {
        UploadConfiguration.Builder builder = new UploadConfiguration.Builder();
        setBooleanProperty(analyserProperties, UPLOAD, builder::setUpload);
        setStringProperty(analyserProperties, UPLOAD_PROJECT, builder::setProjectName);
        setStringProperty(analyserProperties, UPLOAD_URL, builder::setAnnotationServerUrl);
        setSplitStringProperty(analyserProperties, COMMA, UPLOAD_PACKAGES, builder::addUploadPackage);
        return builder.build();
    }

    public static AnnotationXmlConfiguration annotationXmlConfigurationFromProperties(Map<String, String> analyserProperties) {
        AnnotationXmlConfiguration.Builder builder = new AnnotationXmlConfiguration.Builder();
        setBooleanProperty(analyserProperties, WRITE_ANNOTATION_XML, builder::setAnnotationXml);
        setStringProperty(analyserProperties, WRITE_ANNOTATION_XML_DIR, builder::setWriteAnnotationXmlDir);
        setSplitStringProperty(analyserProperties, COMMA, WRITE_ANNOTATION_XML_PACKAGES, builder::addAnnotationXmlPackages);
        return builder.build();
    }

    public static InputConfiguration inputConfigurationFromProperties(Map<String, String> analyserProperties) {
        InputConfiguration.Builder builder = new InputConfiguration.Builder();
        setStringProperty(analyserProperties, JRE, builder::setAlternativeJREDirectory);
        setStringProperty(analyserProperties, SOURCE_ENCODING, builder::setSourceEncoding);
        setSplitStringProperty(analyserProperties, PATH_SEPARATOR, SOURCE, builder::addSources);
        setSplitStringProperty(analyserProperties, PATH_SEPARATOR, CLASSPATH, builder::addClassPath);
        setSplitStringProperty(analyserProperties, COMMA, SOURCE_PACKAGES, builder::addRestrictSourceToPackages);
        return builder.build();
    }

    public static AnnotatedAPIConfiguration annotatedAPIConfigurationFromProperties(Map<String, String> analyserProperties) {
        AnnotatedAPIConfiguration.Builder builder = new AnnotatedAPIConfiguration.Builder();
        setBooleanProperty(analyserProperties, Main.WRITE_ANNOTATED_API, builder::setAnnotatedAPIs);
        setStringProperty(analyserProperties, Main.WRITE_ANNOTATED_API_DIR, builder::setWriteAnnotatedAPIsDir);
        setSplitStringProperty(analyserProperties, Main.COMMA, Main.WRITE_ANNOTATED_API_PACKAGES, builder::addAnnotatedAPIPackages);
        return builder.build();
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
        LOGGER.debug("Have {}: {}", key, value);
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
