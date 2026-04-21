package packettracer.exapp;

import com.cisco.pt.ipc.system.CepInstance;
import com.cisco.pt.ipc.system.IPCManager;
import packettracer.exapp.core.AppLogger;
import packettracer.exapp.core.AppRuntimeContext;
import packettracer.exapp.core.BootstrapReport;
import packettracer.exapp.core.LaunchOptions;
import packettracer.exapp.core.PacketTracerBootstrapCoordinator;
import packettracer.exapp.utils.LaunchOptionsParser;

public final class PacketTracerPtExApp {
    private static final String PT_IPC_PORT_FLAG = "--pt-ipc-port";
    private static final String RESIDENT_SECONDS_PROPERTY = "packettracer.ptexapp.residentSeconds";
    private static final String RESIDENT_SECONDS_ENV = "PACKET_TRACER_PTEXAPP_RESIDENT_SECONDS";

    private PacketTracerPtExApp() {
    }

    public static void main(String[] args) {
        AppLogger.log("Packet Tracer Java ExApp bootstrap attempt starting.");
        AppLogger.log("Framework API visible on classpath: %s | %s", IPCManager.class.getName(), CepInstance.class.getName());
        AppLogger.log("Process arguments supplied by launcher: %d", Integer.valueOf(args.length));
        LaunchOptions launchOptions = LaunchOptionsParser.parse(args, PT_IPC_PORT_FLAG);

        for (String detail : launchOptions.getDetails()) {
            AppLogger.log("%s", detail);
        }

        installShutdownHook();
        BootstrapReport bootstrapReport = PacketTracerBootstrapCoordinator.attemptBootstrap(launchOptions);

        for (String detail : bootstrapReport.getDetails()) {
            AppLogger.log("%s", detail);
        }

        PacketTracerBootstrapCoordinator.runSyntheticListenerExerciseIfConfigured();

        if (bootstrapReport.isSuccessful()) {
            AppLogger.log("Bootstrap status: success. Resident wait loop is now active.");
        } else if (bootstrapReport.isPartiallyBlocked()) {
            AppLogger.log("Bootstrap status: partially blocked. A real PTMP/IPC bootstrap step started but did not complete cleanly.");
        } else {
            AppLogger.log("Bootstrap status: failed. Resident wait loop stays active for conservative MVP observation.");
        }

        ResidentMode residentMode = resolveResidentMode();

        try {
            stayResident(residentMode);
        } finally {
            PacketTracerBootstrapCoordinator.closeBootstrapResources();
        }
    }

    private static void installShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                AppRuntimeContext.setKeepRunning(false);
                PacketTracerBootstrapCoordinator.closeBootstrapResources();
                AppLogger.log("Shutdown signal received; resident wait loop ending.");
            }
        }, "packettracer-ptexapp-shutdown"));
    }

    private static void stayResident(ResidentMode residentMode) {
        long deadlineMillis = residentMode.persistent ? Long.MAX_VALUE : System.currentTimeMillis() + (residentMode.residentSeconds * 1000L);

        if (residentMode.persistent) {
            AppLogger.log("Resident mode: persistent. ExApp stays alive until Packet Tracer or the launcher process shuts it down. Set %s or -D%s to a positive value to restore a bounded timeout for local verification.", RESIDENT_SECONDS_ENV, RESIDENT_SECONDS_PROPERTY);
        } else {
            AppLogger.log("Resident hold window: %d seconds. Set %s or -D%s to override, or set either to 'persistent' for no timeout.", Long.valueOf(residentMode.residentSeconds), RESIDENT_SECONDS_ENV, RESIDENT_SECONDS_PROPERTY);
        }

        while (AppRuntimeContext.isKeepRunning() && System.currentTimeMillis() < deadlineMillis) {
            long sleepMillis = residentMode.persistent ? 1000L : Math.min(1000L, deadlineMillis - System.currentTimeMillis());

            if (sleepMillis <= 0L) {
                break;
            }

            try {
                Thread.sleep(sleepMillis);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                AppLogger.log("Resident wait loop interrupted: %s", exception.getMessage() == null || exception.getMessage().isEmpty() ? "(no message)" : exception.getMessage());
                break;
            }
        }

        AppLogger.log(residentMode.persistent ? "Resident loop ended; exiting main process." : "Resident hold complete; exiting main process.");
    }

    private static ResidentMode resolveResidentMode() {
        String configuredValue = System.getProperty(RESIDENT_SECONDS_PROPERTY);

        if (configuredValue == null || configuredValue.isEmpty()) {
            configuredValue = System.getenv(RESIDENT_SECONDS_ENV);
        }

        if (configuredValue == null || configuredValue.isEmpty()) {
            return ResidentMode.persistent();
        }

        String normalizedValue = configuredValue.trim();

        if (normalizedValue.equalsIgnoreCase("persistent") || normalizedValue.equalsIgnoreCase("infinite") || normalizedValue.equals("0")) {
            return ResidentMode.persistent();
        }

        try {
            long parsedValue = Long.parseLong(normalizedValue);

            if (parsedValue > 0L) {
                return ResidentMode.bounded(parsedValue);
            }
        } catch (NumberFormatException exception) {
            AppLogger.log("Invalid resident mode override '%s'; using persistent mode instead.", configuredValue);
            return ResidentMode.persistent();
        }

        AppLogger.log("Non-positive resident mode override '%s'; using persistent mode instead.", configuredValue);
        return ResidentMode.persistent();
    }

    private static final class ResidentMode {
        private final boolean persistent;
        private final long residentSeconds;

        private ResidentMode(boolean persistent, long residentSeconds) {
            this.persistent = persistent;
            this.residentSeconds = residentSeconds;
        }

        private static ResidentMode persistent() {
            return new ResidentMode(true, 0L);
        }

        private static ResidentMode bounded(long residentSeconds) {
            return new ResidentMode(false, residentSeconds);
        }
    }
}
