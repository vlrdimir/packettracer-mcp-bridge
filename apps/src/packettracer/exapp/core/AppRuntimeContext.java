package packettracer.exapp.core;

import com.cisco.pt.ipc.events.CepInstanceEventRegistry;
import com.cisco.pt.ipc.ui.IPC;
import com.cisco.pt.ptmp.PacketTracerSession;
import com.cisco.pt.ptmp.PacketTracerSessionFactory;
import packettracer.exapp.component.ExperimentalCepMessageListener;
import packettracer.exapp.component.LocalHostBridgeServer;

public final class AppRuntimeContext {
    private static volatile boolean keepRunning = true;
    private static volatile PacketTracerSessionFactory activeSessionFactory;
    private static volatile PacketTracerSession activeSession;
    private static volatile IPC activeIpc;
    private static volatile boolean packetTracerLogMirroringDisabled;
    private static volatile CepInstanceEventRegistry activeCepInstanceEventRegistry;
    private static volatile ExperimentalCepMessageListener activeCepMessageListener;
    private static volatile LocalHostBridgeServer activeLocalHostBridgeServer;

    private AppRuntimeContext() {
    }

    public static boolean isKeepRunning() {
        return keepRunning;
    }

    public static void setKeepRunning(boolean keepRunningValue) {
        keepRunning = keepRunningValue;
    }

    public static PacketTracerSessionFactory getActiveSessionFactory() {
        return activeSessionFactory;
    }

    public static void setActiveSessionFactory(PacketTracerSessionFactory sessionFactory) {
        activeSessionFactory = sessionFactory;
    }

    public static PacketTracerSession getActiveSession() {
        return activeSession;
    }

    public static void setActiveSession(PacketTracerSession session) {
        activeSession = session;
    }

    public static IPC getActiveIpc() {
        return activeIpc;
    }

    public static void setActiveIpc(IPC ipc) {
        activeIpc = ipc;
    }

    public static boolean isPacketTracerLogMirroringDisabled() {
        return packetTracerLogMirroringDisabled;
    }

    public static void setPacketTracerLogMirroringDisabled(boolean disabled) {
        packetTracerLogMirroringDisabled = disabled;
    }

    public static CepInstanceEventRegistry getActiveCepInstanceEventRegistry() {
        return activeCepInstanceEventRegistry;
    }

    public static void setActiveCepInstanceEventRegistry(CepInstanceEventRegistry registry) {
        activeCepInstanceEventRegistry = registry;
    }

    public static ExperimentalCepMessageListener getActiveCepMessageListener() {
        return activeCepMessageListener;
    }

    public static void setActiveCepMessageListener(ExperimentalCepMessageListener listener) {
        activeCepMessageListener = listener;
    }

    public static LocalHostBridgeServer getActiveLocalHostBridgeServer() {
        return activeLocalHostBridgeServer;
    }

    public static void setActiveLocalHostBridgeServer(LocalHostBridgeServer bridgeServer) {
        activeLocalHostBridgeServer = bridgeServer;
    }
}
