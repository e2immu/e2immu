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

package org.e2immu.analyser.annotatedapi;

import org.apache.commons.io.FileUtils;
import org.e2immu.analyser.config.AnnotatedAPIConfiguration;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.config.InputConfiguration;
import org.e2immu.analyser.config.UploadConfiguration;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.output.Formatter;
import org.e2immu.analyser.output.FormattingOptions;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.parser.Parser;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestComposer {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestComposer.class);

    @Test
    public void test() throws IOException {
        InputConfiguration.Builder inputConfigurationBuilder = new InputConfiguration.Builder()
                .addSources("none")
                .addClassPath("jmods/java.base.jmod");
        AnnotatedAPIConfiguration annotatedAPIConfiguration = new AnnotatedAPIConfiguration.Builder()
                .setAnnotatedAPIs(true)
                .addAnnotatedAPIPackages("java.util.", "java.lang")
                .setWriteAnnotatedAPIsDir("build/annotatedApi")
                .build();

        Configuration configuration = new Configuration.Builder()
                .setInputConfiguration(inputConfigurationBuilder.build())
                .setAnnotatedAPIConfiguration(annotatedAPIConfiguration)
                .setUploadConfiguration(new UploadConfiguration.Builder()
                        .setUpload(true).build())
                .addDebugLogTargets(INSPECT + "," + ANALYSER + "," + CONFIGURATION)
                .build();

        configuration.initializeLoggers();
        Parser parser = new Parser(configuration);

        Parser.ComposerData composerData = parser.primaryTypesForAnnotatedAPIComposing();
        Composer composer = new Composer(composerData.typeMap(), "org.e2immu.testannotatedapi");
        Collection<TypeInfo> apiTypes = composer.compose(composerData.primaryTypes());
        for (TypeInfo apiType : apiTypes) {

            OutputBuilder outputBuilder = apiType.output();
            Formatter formatter = new Formatter(FormattingOptions.DEFAULT);
            LOGGER.info("Stream:\n{}\n", formatter.write(outputBuilder));
        }

        File defaultDestination = new File(AnnotatedAPIConfiguration.DEFAULT_DESTINATION_DIRECTORY);
        FileUtils.deleteDirectory(defaultDestination);
        composer.write(apiTypes, AnnotatedAPIConfiguration.DEFAULT_DESTINATION_DIRECTORY);

        // should not exist, there is no dot after java.lang in the package filter
        File javaLangAnnotations = new File(defaultDestination, "org/e2immu/testannotatedapi/JavaLangAnnotation.java");
        assertFalse(javaLangAnnotations.exists());
        File javaLang = new File(defaultDestination, "org/e2immu/testannotatedapi/JavaLang.java");
        assertTrue(javaLang.exists());
    }
}
