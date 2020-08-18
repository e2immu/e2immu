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

package org.e2immu.analyser.cli;

import ch.qos.logback.classic.Level;
import org.apache.commons.cli.*;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.parser.Parser;

import java.io.IOException;
import java.util.Arrays;
import java.util.function.Consumer;

public class Main {
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
}
