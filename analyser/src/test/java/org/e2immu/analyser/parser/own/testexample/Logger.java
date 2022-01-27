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

package org.e2immu.analyser.parser.own.testexample;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import org.e2immu.annotation.*;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Container
public class Logger {

    public static final String ACTIVATE_THE_LOG_SYSTEM = "You must activate the log system";

    @Variable // not eventually immutable, the logger can be overwritten
    private static LogMethod logger;
    private static Set<LogTarget> logTargets;

    @Container
    public interface LogMethod {
        @NotModified
        void log(LogTarget logTarget, String message, Object... objects);
    }

    public enum LogTarget {
        /**
         * Composing the configuration
         */
        CONFIGURATION,

        /**
         * Logger in Resources: loading from jars and class files
         */
        RESOURCES,

        /**
         * Primary byte code inspector logger
         */
        BYTECODE_INSPECTOR,

        /**
         * Extra byte code inspector logger (fine debug level)
         */
        BYTECODE_INSPECTOR_DEBUG,

        /**
         * Logger of annotation XML reader
         */
        ANNOTATION_XML_READER,

        /**
         * Main inspector logger
         */
        INSPECTOR,

        /**
         * Main resolver logger
         */
        RESOLVER,

        /**
         * fine debug level in expressions
         */
        EXPRESSION,

        /**
         * fine print debugging of order of pta
         */
        PRIMARY_TYPE_ANALYSER,

        /**
         * Main analyser logger
         */
        ANALYSER,

        /**
         * Upload annotations to annotation store
         */
        UPLOAD,

        /**
         * Output the source code at the end of RunAnalyser
         */
        OUTPUT,

        /**
         * Logger of annotation xml writer
         */
        ANNOTATION_XML_WRITER,

        /**
         * Logger of annotated API composer
         */
        ANNOTATED_API_WRITER,

        /* ************************ part of the inspection system ************************ */

        EXPRESSION_CONTEXT,
        LAMBDA,
        METHOD_CALL,

        /* ************************ part of the analysis system ************************ */

        /**
         * probably most important debugging help: why is something delayed?
         * Cross-cuts all analyser aspects: all delays use this logger.
         */
        DELAYED,

        /**
         * analysis of companion methods
         */
        COMPANION,

        /**
         * debug logger for all things immutable (_LOG so no clash with variable property)
         */
        IMMUTABLE_LOG,

        /**
         * debug logger for eventual immutability
         */
        EVENTUALLY,

        /**
         * debug logger for method analyser specifics @Identity, @Fluent
         */
        METHOD_ANALYSER,

        /**
         * debug logger for final fields and their value
         */
        FINAL,

        /**
         * debug logger for everything @Independent, @Dependent
         */
        INDEPENDENCE,

        /**
         * debug logger for variable linking
         */
        LINKED_VARIABLES,

        /**
         * debug logger for @NotModified, @Modified
         */
        MODIFICATION,
        /**
         * debug logger for context modification (@PropagateModification,
         * Modified1, @NotModified1, @Dependent1, ...)
         */
        CONTEXT_MODIFICATION,
        /**
         * debug logger for everything @NotNull
         */
        NOT_NULL,
        /**
         * debug logger for condition, state, precondition
         */
        PRECONDITION,

        /**
         * debug logger for the type analyser; general topics such as
         * utility class, extension class, singleton
         */
        TYPE_ANALYSER,
    }

    private Logger() {
    }


    // convenience for tests, bypassing this system, going over slf4j without this class's log() method

    public static void configure(Level level) {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).setLevel(level);
    }

    // convenience method for tests, going over logback classic

    @Modified
    public static void activate(LogTarget... logTargetArray) {
        activate(Arrays.stream(logTargetArray).collect(Collectors.toSet()));
    }

    // activated from Main, going over logback classic

    @Modified
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

    @Modified
    public static void activate(@NotModified @NotNull LogMethod logMethod,
                                @NotNull1 Collection<LogTarget> logTargetSet) {
        logger = Objects.requireNonNull(logMethod);
        logTargets = Set.copyOf(logTargetSet);
    }

    @NotModified
    public static boolean isLogEnabled(LogTarget logTarget) {
        if (logTargets == null || logger == null) {
            throw new UnsupportedOperationException(ACTIVATE_THE_LOG_SYSTEM);
        }
        return logTargets.contains(logTarget);
    }

    @NotModified
    public static void log(LogTarget logTarget, String msg, Object... objects) {
        if (logTargets == null || logger == null) {
            throw new UnsupportedOperationException(ACTIVATE_THE_LOG_SYSTEM);
        }
        if (logTargets.contains(logTarget)) {
            logger.log(logTarget, msg, objects);
        }
    }

    @NotModified
    public static void log(LogTarget logTarget, String msg, Object object, Supplier<Object> supplier) {
        if (logTargets == null || logger == null) {
            throw new UnsupportedOperationException(ACTIVATE_THE_LOG_SYSTEM);
        }
        if (logTargets.contains(logTarget)) {
            logger.log(logTarget, msg, object, supplier.get());
        }
    }

    @NotModified
    public static void log(LogTarget logTarget, String msg, Supplier<Object> supplier) {
        if (logTargets == null || logger == null) {
            throw new UnsupportedOperationException(ACTIVATE_THE_LOG_SYSTEM);
        }
        if (logTargets.contains(logTarget)) {
            logger.log(logTarget, msg, supplier.get());
        }
    }
}
