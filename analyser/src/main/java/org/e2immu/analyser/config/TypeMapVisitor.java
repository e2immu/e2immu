package org.e2immu.analyser.config;

import org.e2immu.analyser.parser.TypeContext;

public interface TypeMapVisitor {
    void visit(TypeContext typeContext);
}
