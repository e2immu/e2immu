package org.e2immu.analyser.analyser.delay;

import org.e2immu.analyser.analyser.DV;

public class FlowDataConstants {
    public static final DV DEFAULT_EXECUTION = new NoDelay(3, "DEFAULT_EXECUTION");
    public static final DV ALWAYS = new NoDelay(2, "ALWAYS");
    public static final DV CONDITIONALLY = new NoDelay(1, "CONDITIONALLY");
    public static final DV NEVER = new NoDelay(0, "NEVER");
}
