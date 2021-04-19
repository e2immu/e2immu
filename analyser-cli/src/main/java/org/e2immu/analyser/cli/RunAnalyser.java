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

import com.github.javaparser.ParseProblemException;
import org.e2immu.analyser.annotatedapi.Composer;
import org.e2immu.analyser.annotationxml.AnnotationXmlWriter;
import org.e2immu.analyser.config.AnnotatedAPIConfiguration;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.WithInspectionAndAnalysis;
import org.e2immu.analyser.output.Formatter;
import org.e2immu.analyser.output.FormattingOptions;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.parser.Parser;
import org.e2immu.analyser.resolver.SortedType;
import org.e2immu.analyser.upload.AnnotationUploader;
import org.e2immu.analyser.usage.CollectUsages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.e2immu.analyser.inspector.TypeNotFoundException;

import static org.e2immu.analyser.cli.Main.*;
import static org.e2immu.analyser.util.Logger.LogTarget.ANNOTATED_API_WRITER;
import static org.e2immu.analyser.util.Logger.LogTarget.OUTPUT;
import static org.e2immu.analyser.util.Logger.log;

public class RunAnalyser implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(RunAnalyser.class);

    private final Configuration configuration;
    private final Messages messages = new Messages();
    private int exitValue;

    public RunAnalyser(Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    public void run() {

        try {
            LOGGER.info(configuration.toString());

            Parser parser = new Parser(configuration);
            AnnotatedAPIConfiguration api = configuration.annotatedAPIConfiguration();
            if (api.writeMode() == AnnotatedAPIConfiguration.WriteMode.ANALYSED) {
                throw new UnsupportedOperationException("Not yet implemented!");
            }
            if (api.writeMode() == AnnotatedAPIConfiguration.WriteMode.INSPECTED) {
                log(ANNOTATED_API_WRITER, "Writing annotated API files based on inspection");
                Parser.ComposerData composerData = parser.primaryTypesForAnnotatedAPIComposing();
                Composer composer = new Composer(composerData.typeMap(),
                        api.destinationPackage(), w -> true);
                Collection<TypeInfo> apiTypes = composer.compose(composerData.primaryTypes());
                log(ANNOTATED_API_WRITER, "Created {} java types, one for each package", apiTypes.size());
                composer.write(apiTypes, api.writeAnnotatedAPIsDir());
            } else {
                /* normal run */
                Parser.RunResult runResult;
                try {
                    runResult = parser.run();
                } catch (TypeNotFoundException typeNotFoundException) {
                    exitValue = EXIT_INSPECTION_ERROR;
                    return;
                } catch (ParseProblemException re) {
                    exitValue = EXIT_PARSER_ERROR;
                    return;
                }
                LOGGER.info("Have {} messages from analyser", parser.countMessages());
                messages.addAll(parser.getMessages());

                if(org.e2immu.analyser.util.Logger.isLogEnabled(OUTPUT)) {
                    for (SortedType sortedType : runResult.sourceSortedTypes()) {
                        OutputBuilder outputBuilder = sortedType.primaryType().output();
                        Formatter formatter = new Formatter(FormattingOptions.DEFAULT);
                        log(OUTPUT, "Annotated Java for {}:\n{}\n", sortedType.primaryType().fullyQualifiedName,
                                formatter.write(outputBuilder));
                    }
                }
                Set<TypeInfo> allTypes = configuration.annotationXmlConfiguration().writeAnnotationXml() ||
                        configuration.uploadConfiguration().upload() ? runResult.allTypes() : Set.of();

                if (configuration.annotationXmlConfiguration().writeAnnotationXml()) {
                    LOGGER.info("Write AnnotationXML");
                    AnnotationXmlWriter.write(configuration.annotationXmlConfiguration(), allTypes);
                }
                if (configuration.uploadConfiguration().upload()) {
                    AnnotationUploader annotationUploader = new AnnotationUploader(configuration.uploadConfiguration());
                    Map<String, String> map = annotationUploader.createMap(allTypes, messages.getMessageStream());
                    annotationUploader.writeMap(map);
                }
                if (api.writeMode() == AnnotatedAPIConfiguration.WriteMode.USAGE) {
                    Set<TypeInfo> sourceTypes = runResult.sourceSortedTypes()
                            .stream().map(SortedType::primaryType).collect(Collectors.toSet());
                    log(ANNOTATED_API_WRITER, "Writing annotated API files for usage of {} Java sources",
                            sourceTypes.size());
                    CollectUsages collectUsages = new CollectUsages(api.writeAnnotatedAPIPackages());
                    Set<WithInspectionAndAnalysis> usage = collectUsages.collect(sourceTypes);
                    log(ANNOTATED_API_WRITER, "Found {} objects in usage set", usage.size());
                    Set<TypeInfo> types = usage.stream().filter(w -> w instanceof TypeInfo)
                            .map(WithInspectionAndAnalysis::primaryType).collect(Collectors.toSet());
                    log(ANNOTATED_API_WRITER, "Found {} primary types in usage set", types.size());
                    Composer composer = new Composer(runResult.typeMap(), api.destinationPackage(), usage::contains);
                    Collection<TypeInfo> apiTypes = composer.compose(types);
                    log(ANNOTATED_API_WRITER, "Created {} java types, one for each package", apiTypes.size());
                    composer.write(apiTypes, api.writeAnnotatedAPIsDir());
                }
                if (!configuration.ignoreErrors()
                        && parser.getMessages().anyMatch(m -> m.severity == Message.Severity.ERROR)) {
                    exitValue = EXIT_ANALYSER_ERROR;
                    return;
                }
            }
            exitValue = EXIT_OK;
        } catch (IOException e) {
            LOGGER.error("ERROR: Caught IO exception during run: " + e.getMessage());
            exitValue = EXIT_IO_EXCEPTION;
        }
    }

    public int getExitValue() {
        return exitValue;
    }

    public Stream<Message> getMessageStream() {
        return messages.getMessageStream();
    }
}
