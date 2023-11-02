package org.e2immu.analyser.util;

import org.slf4j.Logger;

import java.time.Instant;

public class TimedLogger {

    private final Logger logger;
    private final long delay;
    private long latest;

    public TimedLogger(Logger logger, long delay) {
        this.logger = logger;
        this.delay = delay;
    }

    public void info(String string, Object... objects) {
        if (allow()) {
            logger.info(string, objects);
        }
    }

    private boolean allow() {
        long now = Instant.now().toEpochMilli();
        boolean ok = now - latest >= delay;
        if(ok) latest = now;
        return ok;
    }
}
