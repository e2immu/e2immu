package org.e2immu.analyser.pattern;

/*

The goal of this pattern is to standardize a number of constructs involving return statements.



 */
public class JoinReturnStatements {
    boolean a, b, c;

    int x = a? (b? 3: 4): 4;
}
