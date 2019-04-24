/**
 * Copyright 2014-19 Fraunhofer ISE
 *
 * This file is part of j60870.
 * For more information visit http://www.openmuc.org
 *
 * j60870 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * j60870 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with j60870.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.openmuc.j60870.internal.cli;

import static java.lang.System.exit;
import static java.lang.System.out;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Map;

public class ActionProcessor {

    private static final String SEPARATOR_LINE = "------------------------------------------------------";

    private final BufferedReader reader;
    private final ActionListener actionListener;

    private final Map<String, Action> actionMap = new LinkedHashMap<>();

    private final Action helpAction = new Action("h", "print help message");
    private final Action quitAction = new Action("q", "quit the application");

    public ActionProcessor(ActionListener actionListener) {
        reader = new BufferedReader(new InputStreamReader(System.in));
        this.actionListener = actionListener;
    }

    public void addAction(Action action) {
        actionMap.put(action.getKey(), action);
    }

    public BufferedReader getReader() {
        return reader;
    }

    public void start() {

        actionMap.put(helpAction.getKey(), helpAction);
        actionMap.put(quitAction.getKey(), quitAction);

        printHelp();

        try {

            String actionKey;
            while (true) {
                System.out.println("\n** Enter action key: ");

                try {
                    actionKey = reader.readLine();
                } catch (IOException e) {
                    System.err.printf("%s. Application is being shut down.%n", e.getMessage());
                    exit(2);
                    return;
                }

                if (actionMap.get(actionKey) == null) {
                    System.err.println("Illegal action key.\n");
                    printHelp();
                    continue;
                }

                if (actionKey.equals(helpAction.getKey())) {
                    printHelp();
                    continue;
                }

                if (actionKey.equals(quitAction.getKey())) {
                    actionListener.quit();
                    return;
                }

                actionListener.actionCalled(actionKey);

            }

        } catch (Exception e) {
            e.printStackTrace();
            actionListener.quit();
        } finally {
            close();
        }
    }

    private void printHelp() {
        final String message = " %s - %s%n";
        out.flush();
        out.println();
        out.println(SEPARATOR_LINE);

        for (Action action : actionMap.values()) {
            out.printf(message, action.getKey(), action.getDescription());
        }

        out.println(SEPARATOR_LINE);

    }

    public void close() {
        try {
            reader.close();
        } catch (IOException e) {
        }

    }

}
