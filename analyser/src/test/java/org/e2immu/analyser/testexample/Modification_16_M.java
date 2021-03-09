package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Container;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;

import java.util.ArrayList;
import java.util.List;

/*
From the manual
 */
public class Modification_16_M {

    @Container
    static class ErrorMessage {
        private String message;

        public ErrorMessage(String message) {
            this.message = message;
        }

        @NotModified
        public String getMessage() {
            return message;
        }

        @Modified
        public void setMessage(String message) {
            this.message = message;
        }
    }

    @Container
    interface ErrorRegistry {
        @NotModified
        List<ErrorMessage> getErrors();

        @Modified
        void addError(@NotModified ErrorMessage errorMessage);
    }

    static class FaultyImplementation implements ErrorRegistry {

        private final List<ErrorMessage> messages = new ArrayList<>();

        @Override
        public List<ErrorMessage> getErrors() {
            return messages;
        }

        @Override
        public void addError(ErrorMessage errorMessage) {
            messages.add(errorMessage);
            errorMessage.setMessage("Added: " + errorMessage.message); // raises error!
        }
    }
}
