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
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.parser.Parser;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.e2immu.analyser.util.Logger.LogTarget;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.log;

public class AnalyserTask extends ConventionTask {
    private static final Set<LogTarget> DEBUG_TARGETS = Set.of(CONFIGURATION, BYTECODE_INSPECTOR, INSPECT, ANALYSER);

    private static final Logger LOGGER = Logging.getLogger(AnalyserTask.class);
    private Map<String, String> analyserProperties;

    @TaskAction
    public void run() {
        Map<String, String> properties = getProperties();

        if (properties.isEmpty()) {
            LOGGER.warn("Skipping e2immu analysis: no properties configured, was it skipped in all projects?");
            return;
        }
        if (!LOGGER.isInfoEnabled()) {
            properties.put(Main.QUIET, "true");
        } else if (LOGGER.isDebugEnabled()) {
            properties.put(Main.DEBUG, DEBUG_TARGETS.stream()
                    .map(LogTarget::toString)
                    .collect(Collectors.joining(",")));
        }
        org.e2immu.analyser.util.Logger.activate(AnalyserTask::logMessage, Set.of(CONFIGURATION));

        Configuration configuration = Configuration.fromProperties(properties);
        log(CONFIGURATION, "Configuration:\n{}", configuration);
        org.e2immu.analyser.util.Logger.activate(AnalyserTask::logMessage, configuration.logTargets);

        try {
            Parser parser = new Parser(configuration);
            parser.run();
        } catch (IOException ioe) {
            LOGGER.error("Caught IOException {}", ioe.getMessage());
        }
    }

    private static void logMessage(LogTarget logTarget, String msg, Object[] objects) {
        LOGGER.debug(logTarget + ": " + msg, objects);
    }

    /**
     * @return The String key/value pairs to be passed to the analyser.
     * {@code null} values are not permitted.
     */
    @Input
    public Map<String, String> getProperties() {
        if (analyserProperties == null) {
            analyserProperties = new LinkedHashMap<>();
        }
        return analyserProperties;
    }

}
