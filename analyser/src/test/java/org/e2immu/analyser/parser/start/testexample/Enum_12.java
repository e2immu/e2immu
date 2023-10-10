package org.e2immu.analyser.parser.start.testexample;


public class Enum_12 {

    public enum Level {
        ABSENT(-1),
        BASE(ABSENT.level+1),
        MUTABLE(-1-2),
        IMMUTABLE_HC(MUTABLE.level+ BASE.level),

        IMMUTABLE(2),
        INDEPENDENT_HC(0), INDEPENDENT(2),
        NOT_NULL(0), NOT_NULL_1(1),
        CONTAINER(0),
        IGNORE_MODS(0);

        public final int level;

        Level(int level) {
            this.level = level;
        }
    }
}
