package org.e2immu.analyser.parser.minor.testexample;

import org.e2immu.annotation.Commutable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Commutable_1 {

    private final List<String> headers = new ArrayList<>();
    private final List<String> parameters = new ArrayList<>();

    private int timeout;
    private int maxCalls;

    public List<String> getHeaders() {
        return headers;
    }

    public List<String> getParameters() {
        return parameters;
    }

    public int getMaxCalls() {
        return maxCalls;
    }

    public int getTimeout() {
        return timeout;
    }


    //@Commutable(implied = true) not yet implemented
    public void setMaxCalls(int maxCalls) {
        this.maxCalls = maxCalls;
    }

    @Commutable(contract = true)
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    @Commutable(contract = true, seq = "header")
    public void addHeader(String header) {
        headers.add(header);
    }

    @Commutable(contract = true, seq = "header")
    public void addHeaders(String... headers) {
        Collections.addAll(this.headers, headers);
    }

    @Commutable(contract = true, seq = "parameter")
    public void addParameter(String parameter) {
        headers.add(parameter);
    }

    @Commutable(contract = true, seq = "parameter")
    public void addParameters(String... parameters) {
        Collections.addAll(this.headers, parameters);
    }
}
