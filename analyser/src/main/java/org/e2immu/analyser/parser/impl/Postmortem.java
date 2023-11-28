package org.e2immu.analyser.parser.impl;

import org.e2immu.analyser.inspector.InspectionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;

/*
Keeps a log of all activity inside the byte code inspector; with the aim of being able to analyse what went
wrong during a parallel run.
 */
public class Postmortem {
    private static final String FILE = "./build/e2immu_typemap_postmortem.txt";
    private static final Logger LOGGER = LoggerFactory.getLogger(Postmortem.class);
    private final List<String> lines = new LinkedList<>();

    // synchronization is done in TypeMapImpl
    public void acceptByteCode(String startFqn, String typeFqn, InspectionState from, InspectionState to) {
        String line = startFqn + "\t" + typeFqn + "\t" + from + "\t" + to;
        add(line);
    }

    private void add(String line) {
        String date = Instant.now().toString();
        synchronized (lines) {
            lines.add(date + "\t" + line);
        }
    }

    public void write() {
        File file = new File(FILE);
        if (file.getParentFile().mkdirs()) {
            LOGGER.error("Created directory {}", file.getParentFile().getAbsolutePath());
        }
        LOGGER.error("Writing postmortem file to {}", FILE);
        // there could be another thread still "add"ing while we're writing here
        synchronized (lines) {
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(FILE))) {
                for (String line : lines) {
                    bw.write(line);
                    bw.newLine();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void acceptJavaParser(String fullyQualifiedName, InspectionState inspectionState) {
        String line = fullyQualifiedName + "\t" + inspectionState;
        add(line);
    }
}
