package org.e2immu.analyser.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;

import java.util.Set;

import static org.e2immu.analyser.log.LogTarget.MAIN_PACKAGE;
import static org.e2immu.analyser.log.LogTarget.SOURCE;

public class InitLoggers {

    public static void go(Set<LogTarget> logTargets, boolean quiet) {
        ((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(Level.ERROR);
        boolean multipleRunners = detectMultipleRunners(logTargets, quiet);
        if (multipleRunners) {
            System.err.println("Detected multiple runners, switching off logging system");
        }
        Logger overall = (Logger) LoggerFactory.getLogger(MAIN_PACKAGE);
        Level overallLevel = quiet || multipleRunners ? Level.ERROR : Level.INFO;
        Level detailedLevel = quiet || multipleRunners ? Level.ERROR : Level.DEBUG;
        overall.setLevel(overallLevel);
        for (LogTarget logTarget : LogTarget.values()) {
            Level level = logTargets.contains(logTarget) ? detailedLevel : overallLevel;
            for (String prefix : logTarget.prefixes()) {
                ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
                        LoggerFactory.getLogger(MAIN_PACKAGE + "." + prefix);
                logger.setLevel(level);
            }
        }
        if (multipleRunners) {
            overall.getLoggerContext().resetTurboFilterList();
        } else {
            // filter out debug statements in PrimaryTypeAnalyserImpl in the shallow/a-api phase
            if (logTargets.contains(LogTarget.COMPUTING_ANALYSERS)) {
                overall.getLoggerContext().addTurboFilter(new TurboFilter() {
                    @Override
                    public FilterReply decide(Marker marker,
                                              Logger logger,
                                              Level level,
                                              String format,
                                              Object[] params,
                                              Throwable t) {
                        if (marker == null) return FilterReply.NEUTRAL;
                        return marker.equals(SOURCE) ? FilterReply.ACCEPT : FilterReply.DENY;
                    }
                });
            }
        }
    }

    /*
     We'd rather not log anything than have the wrong loggers on DEBUG. So if we see that a wrong
     logger is already activated, we must conclude we're running multiple tests (with different logging
     requirements) at the same time. If so, we'll switch off. As soon as all currently running loggers
     are switched off, the next one can have its normal logging again.
     */
    private static boolean detectMultipleRunners(Set<LogTarget> logTargets, boolean quiet) {
        Level overallLevel = quiet ? Level.ERROR : Level.INFO;
        Level detailedLevel = quiet ? Level.ERROR : Level.DEBUG;
        for (LogTarget logTarget : LogTarget.values()) {
            Level level = logTargets.contains(logTarget) ? detailedLevel : overallLevel;
            for (String prefix : logTarget.prefixes()) {
                ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
                        LoggerFactory.getLogger(MAIN_PACKAGE + "." + prefix);
                Level effectiveLevel = logger.getEffectiveLevel();
                if (!effectiveLevel.isGreaterOrEqual(level)) {
                    return true;
                }
            }
        }
        return false;
    }

}
