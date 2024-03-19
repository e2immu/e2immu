package org.e2immu.analyser.cli;

import org.e2immu.analyser.config.Configuration;

public interface Action {
    // return value 0 = success
    int run(String[] args, Configuration configuration);
}
