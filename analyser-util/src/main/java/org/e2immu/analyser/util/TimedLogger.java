package org.e2immu.analyser.util;

import org.slf4j.Logger;

import java.time.Instant;

public class TimedLogger {

    private final Logger logger;
    private final long delayMillis;
    private long latest;

    public TimedLogger(Logger logger, long delayMillis) {
        this.logger = logger;
        this.delayMillis = delayMillis;
        // give it some time before we start logging
        latest = Instant.now().toEpochMilli() + delayMillis;
    }

    public void info(String string, Object... objects) {
        if (allow()) {
            logger.info(string, objects);
        }
    }

    private boolean allow() {
        long now = Instant.now().toEpochMilli();
        boolean ok = now - latest >= delayMillis;
        if (ok) latest = now;
        return ok;
    }
}
