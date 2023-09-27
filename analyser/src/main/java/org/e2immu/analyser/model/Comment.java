package org.e2immu.analyser.model;

import org.e2immu.analyser.output.OutputBuilder;

/*
Comments can be attached to statements, methods, fields, etc.
 */
public interface Comment {

    String text();

    OutputBuilder output(Qualification qualification);
}
