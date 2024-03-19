package org.e2immu.analyser.cli;

import org.e2immu.analyser.config.Configuration;
import org.e2immu.graph.analyser.Main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

public class ExecuteAction {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExecuteAction.class);

    public static int run(String actionNameOrClass, String[] actionParameters, Configuration configuration) {
        if ("Graph".equals(actionNameOrClass)) {
            try {
                graph(actionParameters);
                return 0;
            } catch (IOException e) {
                LOGGER.error("Caught exception", e);
            }
        }
        // try to instantiate
        Action action = instantiate(actionNameOrClass);
        if (action != null) {
            try {
                return action.run(actionParameters, configuration);
            } catch (RuntimeException re) {
                LOGGER.error("Caught exception", re);
                return 1;
            }
        }
        LOGGER.error("Action '{}' not recognized or not loadable", actionNameOrClass);
        return 1;
    }

    private static Action instantiate(String actionNameOrClass) {
        try {
            Class<?> actionClass = Class.forName(actionNameOrClass);
            return (Action) actionClass.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException | NoSuchMethodException | InvocationTargetException | InstantiationException |
                 IllegalAccessException e) {
            LOGGER.error("Exception during instantiation", e);
        }
        return null;
    }

    private static void graph(String[] actionParameters) throws IOException {
        Main.main(actionParameters);
    }

}
