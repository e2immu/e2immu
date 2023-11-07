package org.e2immu.analyser.resolver.testexample;

import org.e2immu.analyser.resolver.testexample.importhelper.ImplementsErrorHandler;
import org.e2immu.analyser.resolver.testexample.importhelper.c.ErrorHandler;

public class Import_13 {

    ImplementsErrorHandler errorHandler = new ImplementsErrorHandler();

    public int method(String s) {
      return  ErrorHandler.handle(s);
    }
}
