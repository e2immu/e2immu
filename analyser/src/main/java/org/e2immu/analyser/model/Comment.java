package org.e2immu.analyser.model;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.TypeDeclaration;
import org.e2immu.analyser.output.OutputBuilder;

/*
Comments can be attached to statements, methods, fields, etc.
 */
public interface Comment {

    String text();

    OutputBuilder output(Qualification qualification);
}
