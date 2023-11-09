package org.e2immu.analyser.resolver.testexample;

/*
simply take the first one, if 'null' is involved
 */
public class MethodCall_66 {
    static class SofBoException extends RuntimeException {

    }
    interface Logger {
        void logError(String msg, SofBoException boEx);

        void logError(String msg, Throwable boEx);
    }

    void method1(Logger logger) {
        logger.logError("hello", null);
    }
    
    void method2(Logger logger) {
        logger.logError("hello", (Throwable) null);
    }
}
