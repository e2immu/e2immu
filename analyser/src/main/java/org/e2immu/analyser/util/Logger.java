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

package org.e2immu.analyser.util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.UtilityClass;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.function.Supplier;
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
        COMPANION,
        LINKED_VARIABLES, DEBUG_LINKED_VARIABLES,
        INDEPENDENT,
        DEBUG_MODIFY_CONTENT,
        E1IMMUTABLE,
        CONTAINER,
        E2IMMUTABLE,
        UTILITY_CLASS,
        EXTENSION_CLASS,
        NOT_MODIFIED,
        CONSTANT,
        NOT_NULL, NOT_NULL_DEBUG,
        FLUENT,
        IDENTITY,
        FINAL,
        ASSIGNMENT,
        SIDE_EFFECT,
        CNF,
        DYNAMIC,
        PROPAGATE_MODIFICATION,
        PATTERN,

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

        MARK,
        OBJECT_FLOW,

        TRANSFORM,
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
        logTargets = Set.copyOf(logTargetSet);
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

    @NotModified
    public static void log(LogTarget logTarget, String msg, Object object, Supplier<Object> supplier) {
        if (logTargets == null) throw new UnsupportedOperationException("You must activate the log system");
        if (logTargets.contains(logTarget)) {
            logger.log(logTarget, msg, object, supplier.get());
        }
    }

    @NotModified
    public static void log(LogTarget logTarget, String msg, Supplier<Object> supplier) {
        if (logTargets == null) throw new UnsupportedOperationException("You must activate the log system");
        if (logTargets.contains(logTarget)) {
            logger.log(logTarget, msg, supplier.get());
        }
    }
}
