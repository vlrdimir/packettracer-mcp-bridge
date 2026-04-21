package packettracer.exapp.core;

import com.cisco.pt.ipc.ui.IPC;
import packettracer.exapp.utils.ThrowableUtils;
import java.lang.reflect.Method;

public final class AppLogger {
    private static final int MAX_PACKET_TRACER_MIRROR_CHARS = 2048;

    private AppLogger() {
    }

    public static void log(String message, Object... args) {
        String formattedMessage = String.format(message, args);
        System.out.println(formattedMessage);
        mirrorLogToPacketTracer(formattedMessage);
    }

    private static void mirrorLogToPacketTracer(String message) {
        IPC ipc = AppRuntimeContext.getActiveIpc();

        if (ipc == null || AppRuntimeContext.isPacketTracerLogMirroringDisabled()) {
            return;
        }

        try {
            Method appWindowMethod = ipc.getClass().getMethod("appWindow");
            Object appWindow = appWindowMethod.invoke(ipc);

            if (appWindow == null) {
                return;
            }

            Method writeToPtMethod = appWindow.getClass().getMethod("writeToPT", String.class);
            writeToPtMethod.invoke(appWindow, summarizeForPacketTracerMirror(message));
        } catch (Throwable throwable) {
            AppRuntimeContext.setPacketTracerLogMirroringDisabled(true);
            System.out.println(String.format("Packet Tracer IPC log mirroring became unavailable: %s: %s", throwable.getClass().getName(), ThrowableUtils.safeMessage(throwable)));
        }
    }

    public static String summarizeForLog(String message) {
        return summarize(message, 512);
    }

    private static String summarizeForPacketTracerMirror(String message) {
        return summarize(message, MAX_PACKET_TRACER_MIRROR_CHARS);
    }

    private static String summarize(String message, int maxChars) {
        String value = message == null ? "" : message;

        if (value.length() <= maxChars) {
            return value;
        }

        return value.substring(0, maxChars) + String.format(" ... [truncated %d chars]", Integer.valueOf(value.length() - maxChars));
    }
}
