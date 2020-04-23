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

package org.e2immu.analyser.util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.google.common.collect.ImmutableSet;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.UtilityClass;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

@UtilityClass
public class Logger {

    private static LogMethod logger;
    private static Set<LogTarget> logTargets;

    @FunctionalInterface
    public interface LogMethod {
        void log(LogTarget logTarget, String message, Object... objects);
    }

    public enum LogTarget {
        LAMBDA,      // understanding a lambda
        METHOD_CALL, // understanding a method call
        RESOLVE,     // parsing of method bodies
        CONTEXT,     // expression context
        VARIABLE_PROPERTIES,

        DELAYED,

        LINKED_VARIABLES, DEBUG_LINKED_VARIABLES,
        INDEPENDENT,
        MODIFY_CONTENT, DEBUG_MODIFY_CONTENT,
        E1IMMUTABLE,
        CONTAINER,
        E2IMMUTABLE,
        UTILITY_CLASS,
        NULL_NOT_ALLOWED,
        NOT_MODIFIED,
        CONSTANT,
        NOT_NULL,
        FLUENT,
        IDENTITY,
        FINAL,
        ASSIGNMENT,
        SIDE_EFFECT,

        INSPECT,
        ANALYSER,    // main analyser info
        DEFAULT,

        RESOURCES,
        UPLOAD,

        CONFIGURATION,
        BYTECODE_INSPECTOR,
        BYTECODE_INSPECTOR_DEBUG,
        ANNOTATION_XML_READER,
        ANNOTATION_XML_WRITER,

        ANNOTATION_EXPRESSION,
        MERGE_ANNOTATIONS,

        STATIC_METHOD_CALLS,
    }

    private Logger() {
    }


    // convenience for tests, bypassing this system, going over slf4j without this class's log() method

    public static void configure(Level level) {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).setLevel(level);
    }

    // convenience method for tests, going over logback classic

    @NotModified
    public static void activate(LogTarget... logTargetArray) {
        activate(Arrays.stream(logTargetArray).collect(Collectors.toSet()));
    }

    // activated from Main, going over logback classic

    @NotModified
    public static void activate(Collection<LogTarget> logTargetSet) {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        for (LogTarget logTarget : logTargetSet) {
            loggerContext.getLogger(logTarget.toString()).setLevel(Level.DEBUG);
        }
        activate(Logger::logViaLogBackClassic, logTargetSet);
    }

    private static void logViaLogBackClassic(LogTarget logTarget, String s, Object[] objects) {
        LoggerFactory.getLogger(logTarget.toString()).debug(s, objects);
    }

    // activated from Gradle, going over Gradle's log system

    @NotModified
    public static void activate(LogMethod logMethod, Collection<LogTarget> logTargetSet) {
        logger = logMethod;
        logTargets = ImmutableSet.copyOf(logTargetSet);
    }

    public static boolean isLogEnabled(LogTarget logTarget) {
        return logTargets.contains(logTarget);
    }

    @NotModified
    public static void log(LogTarget logTarget, String msg, Object... objects) {
        if (logTargets == null) throw new UnsupportedOperationException("You must activate the log system");
        if (logTargets.contains(logTarget)) {
            logger.log(logTarget, msg, objects);
        }
    }

}
