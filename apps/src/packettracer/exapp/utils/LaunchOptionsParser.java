package packettracer.exapp.utils;

import packettracer.exapp.core.LaunchOptions;

public final class LaunchOptionsParser {
    private LaunchOptionsParser() {
    }

    public static LaunchOptions parse(String[] args, String packetTracerPortFlag) {
        LaunchOptions launchOptions = new LaunchOptions();

        for (int index = 0; index < args.length; index++) {
            String argument = args[index];

            if (packetTracerPortFlag.equals(argument)) {
                if (index + 1 >= args.length) {
                    launchOptions.addDetail("Launch argument --pt-ipc-port was provided without a following value.");
                    continue;
                }

                index++;
                launchOptions.capturePacketTracerIpcPort(args[index]);
                continue;
            }

            if (argument.startsWith(packetTracerPortFlag + "=")) {
                launchOptions.capturePacketTracerIpcPort(argument.substring((packetTracerPortFlag + "=").length()));
                continue;
            }

            launchOptions.addUnrecognizedArgument(argument);
        }

        if (!launchOptions.hasPacketTracerIpcPort()) {
            launchOptions.addDetail("Packet Tracer IPC port argument not detected in launcher arguments.");
        }

        if (!launchOptions.getUnrecognizedArguments().isEmpty()) {
            launchOptions.addDetail(String.format("Unrecognized launch arguments preserved for logging only: %s", launchOptions.getUnrecognizedArguments()));
        }

        return launchOptions;
    }
}
