package packettracer.exapp.lib.introspect;

import java.util.ArrayList;
import java.util.List;

final class HistoryObservation {
    final boolean observed;
    final String className;
    final int size;
    final String methodHint;

    HistoryObservation(boolean observed, String className, int size, String methodHint) {
        this.observed = observed;
        this.className = className;
        this.size = size;
        this.methodHint = methodHint;
    }

    static HistoryObservation unavailable() {
        return new HistoryObservation(false, "", -1, "");
    }

    String toJson() {
        return new StringBuilder()
            .append('{')
            .append("\"observed\":").append(observed ? "true" : "false")
            .append(",\"className\":").append(IntrospectJson.jsonQuoted(className))
            .append(",\"size\":").append(size)
            .append(",\"methodHint\":").append(IntrospectJson.jsonQuoted(methodHint))
            .append('}')
            .toString();
    }
}

final class CliExecutionResult {
    final boolean observed;
    final boolean ok;
    final String transport;
    final String response;

    CliExecutionResult(boolean observed, boolean ok, String transport, String response) {
        this.observed = observed;
        this.ok = ok;
        this.transport = transport;
        this.response = response;
    }
}

final class TerminalCommandObservation {
    final boolean observed;
    final boolean ok;
    final String requestedMode;
    final String requestedCommand;
    final String response;
    final String rawOutput;
    final String consoleOutputSnapshot;
    final String consoleOutputDelta;
    final String currentMode;
    final String prompt;
    final String commandInput;
    final int historySize;
    final HistoryObservation currentHistory;
    final HistoryObservation userHistory;
    final HistoryObservation configHistory;
    final int expectedCommands;
    final int completedCommands;
    final boolean transcriptObserved;
    final boolean commandLifecycleObserved;
    final CommandLogObservation commandLog;

    TerminalCommandObservation(
        boolean observed,
        boolean ok,
        String requestedMode,
        String requestedCommand,
        String response,
        String rawOutput,
        String consoleOutputSnapshot,
        String consoleOutputDelta,
        String currentMode,
        String prompt,
        String commandInput,
        int historySize,
        HistoryObservation currentHistory,
        HistoryObservation userHistory,
        HistoryObservation configHistory,
        int expectedCommands,
        int completedCommands,
        boolean transcriptObserved,
        boolean commandLifecycleObserved,
        CommandLogObservation commandLog
    ) {
        this.observed = observed;
        this.ok = ok;
        this.requestedMode = requestedMode;
        this.requestedCommand = requestedCommand;
        this.response = response;
        this.rawOutput = rawOutput;
        this.consoleOutputSnapshot = consoleOutputSnapshot;
        this.consoleOutputDelta = consoleOutputDelta;
        this.currentMode = currentMode;
        this.prompt = prompt;
        this.commandInput = commandInput;
        this.historySize = historySize;
        this.currentHistory = currentHistory;
        this.userHistory = userHistory;
        this.configHistory = configHistory;
        this.expectedCommands = expectedCommands;
        this.completedCommands = completedCommands;
        this.transcriptObserved = transcriptObserved;
        this.commandLifecycleObserved = commandLifecycleObserved;
        this.commandLog = commandLog;
    }

    static TerminalCommandObservation unavailable() {
        return new TerminalCommandObservation(
            false,
            false,
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            -1,
            HistoryObservation.unavailable(),
            HistoryObservation.unavailable(),
            HistoryObservation.unavailable(),
            0,
            0,
            false,
            false,
            CommandLogObservation.unavailable("command-log-unavailable")
        );
    }

    static TerminalCommandObservation empty() {
        return new TerminalCommandObservation(
            true,
            false,
            "",
            "",
            "terminal-line-command-sequence-empty",
            "",
            "",
            "",
            "",
            "",
            "",
            -1,
            HistoryObservation.unavailable(),
            HistoryObservation.unavailable(),
            HistoryObservation.unavailable(),
            0,
            0,
            false,
            false,
            CommandLogObservation.unavailable("command-log-unavailable")
        );
    }

    static TerminalCommandObservation failed(String response) {
        return new TerminalCommandObservation(
            true,
            false,
            "",
            "",
            response,
            "",
            "",
            "",
            "",
            "",
            "",
            -1,
            HistoryObservation.unavailable(),
            HistoryObservation.unavailable(),
            HistoryObservation.unavailable(),
            0,
            0,
            false,
            false,
            CommandLogObservation.unavailable("command-log-unavailable")
        );
    }
}

final class CommandLogCapture {
    final Object commandLog;
    final int beforeCount;
    final int afterCount;
    final boolean enabledBefore;
    final boolean enableAttempted;
    final String reason;
    final boolean active;
    final List<CommandLogEntryObservation> entries;

    CommandLogCapture(Object commandLog, int beforeCount, boolean enabledBefore, boolean enableAttempted, String reason) {
        this.commandLog = commandLog;
        this.beforeCount = beforeCount;
        this.afterCount = beforeCount;
        this.enabledBefore = enabledBefore;
        this.enableAttempted = enableAttempted;
        this.reason = reason == null ? "" : reason;
        this.active = true;
        this.entries = new ArrayList<CommandLogEntryObservation>();
    }

    CommandLogCapture(String reason) {
        this.commandLog = null;
        this.beforeCount = -1;
        this.afterCount = -1;
        this.enabledBefore = false;
        this.enableAttempted = false;
        this.reason = reason == null ? "" : reason;
        this.active = false;
        this.entries = new ArrayList<CommandLogEntryObservation>();
    }

    static CommandLogCapture unavailable(String reason) {
        return new CommandLogCapture(reason);
    }

    CommandLogCapture withAfterCountAndEntries(int updatedAfterCount, List<CommandLogEntryObservation> updatedEntries) {
        if (!active) {
            return this;
        }

        CommandLogCapture capture = new CommandLogCapture(commandLog, beforeCount, enabledBefore, enableAttempted, reason);
        capture.entries.clear();
        capture.entries.addAll(updatedEntries);
        return new CommandLogCapture(commandLog, beforeCount, updatedAfterCount, enabledBefore, enableAttempted, reason, updatedEntries);
    }

    CommandLogCapture(
        Object commandLog,
        int beforeCount,
        int afterCount,
        boolean enabledBefore,
        boolean enableAttempted,
        String reason,
        List<CommandLogEntryObservation> entries
    ) {
        this.commandLog = commandLog;
        this.beforeCount = beforeCount;
        this.afterCount = afterCount;
        this.enabledBefore = enabledBefore;
        this.enableAttempted = enableAttempted;
        this.reason = reason == null ? "" : reason;
        this.active = true;
        this.entries = entries == null ? new ArrayList<CommandLogEntryObservation>() : entries;
    }
}

final class CommandLogObservation {
    final boolean observed;
    final String reason;
    final boolean enabledBefore;
    final boolean enableAttempted;
    final int countBefore;
    final int countAfter;
    final int newEntryCount;
    final List<CommandLogEntryObservation> entries;

    CommandLogObservation(
        boolean observed,
        String reason,
        boolean enabledBefore,
        boolean enableAttempted,
        int countBefore,
        int countAfter,
        int newEntryCount,
        List<CommandLogEntryObservation> entries
    ) {
        this.observed = observed;
        this.reason = reason == null ? "" : reason;
        this.enabledBefore = enabledBefore;
        this.enableAttempted = enableAttempted;
        this.countBefore = countBefore;
        this.countAfter = countAfter;
        this.newEntryCount = newEntryCount;
        this.entries = entries == null ? new ArrayList<CommandLogEntryObservation>() : entries;
    }

    static CommandLogObservation unavailable(String reason) {
        return new CommandLogObservation(false, reason, false, false, -1, -1, 0, new ArrayList<CommandLogEntryObservation>());
    }

    String toSummaryText() {
        if (entries.isEmpty()) {
            return "";
        }

        StringBuilder summary = new StringBuilder();

        for (int index = 0; index < entries.size(); index++) {
            CommandLogEntryObservation entry = entries.get(index);

            if (index > 0) {
                summary.append('\n');
            }

            summary.append(entry.toSummaryText());
        }

        return summary.toString();
    }

    String toJson() {
        StringBuilder json = new StringBuilder();
        json.append('{');
        json.append("\"observed\":").append(observed ? "true" : "false");
        json.append(",\"reason\":").append(IntrospectJson.jsonQuoted(reason));
        json.append(",\"enabledBefore\":").append(enabledBefore ? "true" : "false");
        json.append(",\"enableAttempted\":").append(enableAttempted ? "true" : "false");
        json.append(",\"countBefore\":").append(countBefore);
        json.append(",\"countAfter\":").append(countAfter);
        json.append(",\"newEntryCount\":").append(newEntryCount);
        json.append(",\"entries\":[");

        for (int index = 0; index < entries.size(); index++) {
            if (index > 0) {
                json.append(',');
            }

            json.append(entries.get(index).toJson());
        }

        json.append("]}");
        return json.toString();
    }
}

final class CommandLogEntryObservation {
    final int index;
    final String timestamp;
    final String deviceName;
    final String prompt;
    final String command;
    final String resolvedCommand;
    final String error;

    CommandLogEntryObservation(int index, String timestamp, String deviceName, String prompt, String command, String resolvedCommand) {
        this(index, timestamp, deviceName, prompt, command, resolvedCommand, "");
    }

    CommandLogEntryObservation(
        int index,
        String timestamp,
        String deviceName,
        String prompt,
        String command,
        String resolvedCommand,
        String error
    ) {
        this.index = index;
        this.timestamp = timestamp == null ? "" : timestamp;
        this.deviceName = deviceName == null ? "" : deviceName;
        this.prompt = prompt == null ? "" : prompt;
        this.command = command == null ? "" : command;
        this.resolvedCommand = resolvedCommand == null ? "" : resolvedCommand;
        this.error = error == null ? "" : error;
    }

    static CommandLogEntryObservation failed(int index, String error) {
        return new CommandLogEntryObservation(index, "", "", "", "", "", error);
    }

    String toSummaryText() {
        if (!error.isEmpty()) {
            return "command-log-entry-error[" + index + "]: " + error;
        }

        StringBuilder summary = new StringBuilder();

        if (!timestamp.isEmpty()) {
            summary.append('[').append(timestamp).append("] ");
        }

        if (!deviceName.isEmpty()) {
            summary.append(deviceName).append(' ');
        }

        if (!prompt.isEmpty()) {
            summary.append(prompt);
        }

        if (!command.isEmpty()) {
            summary.append(command);
        }

        if (!resolvedCommand.isEmpty() && !resolvedCommand.equals(command)) {
            summary.append(" -> ").append(resolvedCommand);
        }

        return summary.toString().trim();
    }

    String toJson() {
        return new StringBuilder()
            .append('{')
            .append("\"index\":").append(index)
            .append(",\"timestamp\":").append(IntrospectJson.jsonQuoted(timestamp))
            .append(",\"deviceName\":").append(IntrospectJson.jsonQuoted(deviceName))
            .append(",\"prompt\":").append(IntrospectJson.jsonQuoted(prompt))
            .append(",\"command\":").append(IntrospectJson.jsonQuoted(command))
            .append(",\"resolvedCommand\":").append(IntrospectJson.jsonQuoted(resolvedCommand))
            .append(",\"error\":").append(IntrospectJson.jsonQuoted(error))
            .append('}')
            .toString();
    }
}

final class PortStateMutationResult {
    final boolean observed;
    final boolean ok;
    final String appliedVia;
    final String response;

    PortStateMutationResult(boolean observed, boolean ok, String appliedVia, String response) {
        this.observed = observed;
        this.ok = ok;
        this.appliedVia = appliedVia;
        this.response = response;
    }
}

final class TerminalOutputCapture {
    final StringBuilder output;
    final int[] completedCommands;
    final long[] lastEventAt;
    final int expectedCommandCount;
    final Object registry;
    final Object listener;
    final boolean active;

    TerminalOutputCapture(String outputText, int completedCommandCount) {
        this.output = new StringBuilder(outputText == null ? "" : outputText);
        this.completedCommands = new int[] { completedCommandCount };
        this.lastEventAt = new long[] { System.currentTimeMillis() };
        this.expectedCommandCount = 0;
        this.registry = null;
        this.listener = null;
        this.active = false;
    }

    TerminalOutputCapture(
        StringBuilder outputBuffer,
        int[] completedCommandCounter,
        long[] lastObservedEventAt,
        int expectedCommands,
        Object eventRegistry,
        Object eventListener
    ) {
        this.output = outputBuffer;
        this.completedCommands = completedCommandCounter;
        this.lastEventAt = lastObservedEventAt;
        this.expectedCommandCount = expectedCommands;
        this.registry = eventRegistry;
        this.listener = eventListener;
        this.active = true;
    }
}

final class TerminalPingParseResult {
    final boolean ok;
    final int sentCount;
    final int receivedCount;
    final int lastDelay;
    final int lastTtl;

    TerminalPingParseResult(boolean ok, int sentCount, int receivedCount, int lastDelay, int lastTtl) {
        this.ok = ok;
        this.sentCount = sentCount;
        this.receivedCount = receivedCount;
        this.lastDelay = lastDelay;
        this.lastTtl = lastTtl;
    }

    static TerminalPingParseResult empty() {
        return new TerminalPingParseResult(false, 0, 0, 0, 0);
    }
}

final class RouteExecutionResult {
    final boolean observed;
    final boolean ok;
    final String appliedVia;
    final String response;
    final String appliedPortSelector;

    RouteExecutionResult(boolean observed, boolean ok, String appliedVia, String response, String appliedPortSelector) {
        this.observed = observed;
        this.ok = ok;
        this.appliedVia = appliedVia;
        this.response = response;
        this.appliedPortSelector = appliedPortSelector;
    }
}

final class PingExecutionResult {
    final boolean observed;
    final boolean ok;
    final String transport;
    final int sentCount;
    final int receivedCount;
    final int lastDelay;
    final int lastTtl;
    final String response;

    PingExecutionResult(
        boolean observed,
        boolean ok,
        String transport,
        int sentCount,
        int receivedCount,
        int lastDelay,
        int lastTtl,
        String response
    ) {
        this.observed = observed;
        this.ok = ok;
        this.transport = transport;
        this.sentCount = sentCount;
        this.receivedCount = receivedCount;
        this.lastDelay = lastDelay;
        this.lastTtl = lastTtl;
        this.response = response;
    }
}
