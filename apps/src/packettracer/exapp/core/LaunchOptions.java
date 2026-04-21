package packettracer.exapp.core;

import java.util.ArrayList;
import java.util.List;

public final class LaunchOptions {
    private final List<String> details = new ArrayList<String>();
    private final List<String> unrecognizedArguments = new ArrayList<String>();
    private Integer packetTracerIpcPort;

    public void addDetail(String detail) {
        details.add(detail);
    }

    public List<String> getDetails() {
        return details;
    }

    public void addUnrecognizedArgument(String argument) {
        unrecognizedArguments.add(argument);
    }

    public List<String> getUnrecognizedArguments() {
        return unrecognizedArguments;
    }

    public boolean hasPacketTracerIpcPort() {
        return packetTracerIpcPort != null;
    }

    public Integer getPacketTracerIpcPort() {
        return packetTracerIpcPort;
    }

    public void capturePacketTracerIpcPort(String rawValue) {
        if (rawValue == null) {
            addDetail("Ignoring null --pt-ipc-port value.");
            return;
        }

        String trimmedValue = rawValue.trim();

        if (trimmedValue.isEmpty()) {
            addDetail("Ignoring empty --pt-ipc-port value.");
            return;
        }

        try {
            int parsedPort = Integer.parseInt(trimmedValue);

            if (parsedPort <= 0 || parsedPort > 65535) {
                addDetail(String.format("Ignoring out-of-range --pt-ipc-port value '%s'.", trimmedValue));
                return;
            }

            packetTracerIpcPort = Integer.valueOf(parsedPort);
            addDetail(String.format("Parsed Packet Tracer IPC port from launcher args: %d", packetTracerIpcPort));
        } catch (NumberFormatException exception) {
            addDetail(String.format("Ignoring non-numeric --pt-ipc-port value '%s'.", trimmedValue));
        }
    }
}
