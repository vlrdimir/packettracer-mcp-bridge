package packettracer.exapp.core;

import com.cisco.pt.ipc.IPCFactory;
import com.cisco.pt.ipc.events.CepInstanceEventRegistry;
import com.cisco.pt.ipc.events.IPCEventManager;
import com.cisco.pt.ipc.system.CepInstance;
import com.cisco.pt.ipc.system.IPCManager;
import com.cisco.pt.ipc.ui.IPC;
import com.cisco.pt.ptmp.PacketTracerSession;
import com.cisco.pt.ptmp.PacketTracerSessionFactory;
import com.cisco.pt.ptmp.impl.PacketTracerSessionFactoryImpl;
import packettracer.exapp.PacketTracerPtExApp;
import packettracer.exapp.component.ExperimentalCepMessageListener;
import packettracer.exapp.component.LocalHostBridgeServer;
import packettracer.exapp.lib.NegotiationSupport;
import packettracer.exapp.lib.NegotiationSupport.NegotiationBootstrap;
import packettracer.exapp.utils.ExperimentalProtocol;
import packettracer.exapp.utils.ThrowableUtils;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public final class PacketTracerBootstrapCoordinator {
    private static final String PT_LOOPBACK_HOST = "127.0.0.1";
    private static final String[] IPC_MANAGER_BOOTSTRAP_CLASSES = {
        "com.cisco.pt.ipc.system.IPCManager",
        "com.cisco.pt.ipc.IPCFactory",
        "com.cisco.pt.ipc.ui.IPC",
        "com.cisco.pt.ipc.system.impl.IPCManagerImpl"
    };
    private static final String PACKET_TRACER_SESSION_CLASS = "com.cisco.pt.ptmp.PacketTracerSession";
    private static final String CEP_INSTANCE_EVENT_REGISTRY_CLASS = "com.cisco.pt.ipc.events.CepInstanceEventRegistry";
    private static final String CEP_INSTANCE_EVENT_LISTENER_CLASS = "com.cisco.pt.ipc.events.CepInstanceEventListener";
    private static final String AUTH_APPLICATION_ENV = "PACKET_TRACER_PTEXAPP_AUTH_APPLICATION";
    private static final String AUTH_APPLICATION_PROPERTY = "packettracer.ptexapp.auth.application";
    private static final String AUTH_SECRET_ENV = "PACKET_TRACER_PTEXAPP_AUTH_SECRET";
    private static final String AUTH_SECRET_PROPERTY = "packettracer.ptexapp.auth.secret";
    private static final String PT_APP_META_FILENAME = "PT_APP_META.xml";
    private static final String LOCAL_BRIDGE_HOST = "127.0.0.1";
    private static final int LOCAL_BRIDGE_MIN_PORT = 39150;
    private static final int LOCAL_BRIDGE_MAX_PORT = 39159;
    private static final String SYNTHETIC_MESSAGE_PROPERTY = "packettracer.ptexapp.syntheticMessage";
    private static final String SYNTHETIC_MESSAGE_ENV = "PACKET_TRACER_PTEXAPP_SYNTHETIC_MESSAGE";

    private PacketTracerBootstrapCoordinator() {
    }

    public static BootstrapReport attemptBootstrap(LaunchOptions launchOptions) {
        BootstrapReport report = new BootstrapReport();
        report.addDetail(describeFrameworkTypeAvailability(PACKET_TRACER_SESSION_CLASS));
        report.addDetail(describeFrameworkTypeAvailability(CEP_INSTANCE_EVENT_REGISTRY_CLASS));
        report.addDetail(describeFrameworkTypeAvailability(CEP_INSTANCE_EVENT_LISTENER_CLASS));
        IPCManager ipcManager = null;

        if (launchOptions.hasPacketTracerIpcPort()) {
            ipcManager = attemptPortBootstrap(launchOptions.getPacketTracerIpcPort().intValue(), report);
        } else {
            report.addDetail("No --pt-ipc-port launch argument was supplied, so the app cannot attempt a real PTMP connection bootstrap toward Packet Tracer.");
        }

        if (ipcManager == null) {
            report.addDetail("Falling back to in-process IPCManager discovery so the app can still report whether Packet Tracer injected any local lifecycle state.");
            ipcManager = tryAcquireIpcManager(report);
        }

        if (ipcManager == null) {
            report.addDetail("Bootstrap attempt could not acquire an IPCManager instance from this process.");
            report.addDetail("Observed framework surface still confirms the documented lifecycle path: PacketTracerSessionFactory.openSession(host, port, ConnectionNegotiationProperties) -> IPCFactory(session) -> getIPC() -> ipcManager() -> thisInstance().");
            return report;
        }

        report.addDetail(String.format("Acquired IPCManager implementation: %s", ipcManager.getClass().getName()));
        report.addDetail("Calling IPCManager.thisInstance().");

        CepInstance thisInstance;

        try {
            thisInstance = ipcManager.thisInstance();
        } catch (Throwable throwable) {
            report.addDetail(String.format("IPCManager.thisInstance() threw %s: %s", throwable.getClass().getName(), ThrowableUtils.safeMessage(throwable)));
            return report;
        }

        if (thisInstance == null) {
            report.addDetail("IPCManager.thisInstance() returned null; no active CepInstance is available yet.");
            report.markPartiallyBlocked();
            return report;
        }

        report.addDetail(String.format("CepInstance implementation: %s", thisInstance.getClass().getName()));
        report.addDetail(describeInstanceId(thisInstance));
        report.addDetail(describeCommandLineArg(thisInstance));
        registerCepMessageListener(ipcManager, thisInstance, report);
        startLocalHostBridge(thisInstance, report);

        if (AppRuntimeContext.getActiveLocalHostBridgeServer() == null) {
            report.addDetail("PTMP/IPC bootstrap reached CepInstance.thisInstance(), but the local experimental host bridge did not start.");
            report.markPartiallyBlocked();
            return report;
        }

        report.markSuccessful();
        return report;
    }

    public static void runSyntheticListenerExerciseIfConfigured() {
        String syntheticMessage = ThrowableUtils.firstConfiguredValue(System.getProperty(SYNTHETIC_MESSAGE_PROPERTY), System.getenv(SYNTHETIC_MESSAGE_ENV));

        if (syntheticMessage == null) {
            return;
        }

        ExperimentalCepMessageListener listener = AppRuntimeContext.getActiveCepMessageListener();

        if (listener == null) {
            listener = new ExperimentalCepMessageListener(null, null);
        }

        AppLogger.log("Running synthetic local listener exercise from %s/%s with payload: %s", SYNTHETIC_MESSAGE_PROPERTY, SYNTHETIC_MESSAGE_ENV, syntheticMessage);
        listener.handleSyntheticMessage("synthetic-sender-app", null, syntheticMessage);
    }

    public static void closeBootstrapResources() {
        LocalHostBridgeServer bridgeServer = AppRuntimeContext.getActiveLocalHostBridgeServer();
        AppRuntimeContext.setActiveLocalHostBridgeServer(null);

        if (bridgeServer != null) {
            bridgeServer.close();
        }

        CepInstanceEventRegistry eventRegistry = AppRuntimeContext.getActiveCepInstanceEventRegistry();
        ExperimentalCepMessageListener messageListener = AppRuntimeContext.getActiveCepMessageListener();
        AppRuntimeContext.setActiveCepInstanceEventRegistry(null);
        AppRuntimeContext.setActiveCepMessageListener(null);

        if (eventRegistry != null && messageListener != null) {
            try {
                eventRegistry.removeListener(messageListener);
                AppLogger.log("Removed CepInstanceEventListener during shutdown.");
            } catch (Throwable throwable) {
                AppLogger.log("Removing CepInstanceEventListener failed with %s: %s", throwable.getClass().getName(), ThrowableUtils.safeMessage(throwable));
            }
        }

        PacketTracerSessionFactory sessionFactory = AppRuntimeContext.getActiveSessionFactory();
        AppRuntimeContext.setActiveSessionFactory(null);
        PacketTracerSession session = AppRuntimeContext.getActiveSession();
        AppRuntimeContext.setActiveSession(null);
        AppRuntimeContext.setActiveIpc(null);
        AppRuntimeContext.setPacketTracerLogMirroringDisabled(false);

        if (!shouldAttemptFrameworkClose(session)) {
            if (session != null || sessionFactory != null) {
                AppLogger.log("Skipping explicit PTMP shutdown because the framework already reported a disconnected session state.");
            }
            return;
        }

        if (sessionFactory != null) {
            try {
                sessionFactory.close();
                AppLogger.log("Closed PacketTracerSessionFactory during shutdown.");
            } catch (Throwable throwable) {
                AppLogger.log("Closing PacketTracerSessionFactory failed with %s: %s", throwable.getClass().getName(), ThrowableUtils.safeMessage(throwable));
            }
            return;
        }

        if (session != null) {
            try {
                session.close();
                AppLogger.log("Closed PTMP session during shutdown.");
            } catch (Throwable throwable) {
                AppLogger.log("Closing PTMP session failed with %s: %s", throwable.getClass().getName(), ThrowableUtils.safeMessage(throwable));
            }
        }
    }

    private static void startLocalHostBridge(CepInstance thisInstance, BootstrapReport report) {
        if (AppRuntimeContext.getActiveLocalHostBridgeServer() != null) {
            report.addDetail(String.format("Local experimental host bridge already running at %s.", AppRuntimeContext.getActiveLocalHostBridgeServer().describeEndpoint()));
            return;
        }

        LocalHostBridgeServer bridgeServer;

        try {
            bridgeServer = LocalHostBridgeServer.start(thisInstance, LOCAL_BRIDGE_HOST, LOCAL_BRIDGE_MIN_PORT, LOCAL_BRIDGE_MAX_PORT);
        } catch (java.io.IOException exception) {
            report.addDetail(String.format("Local experimental host bridge startup failed: %s: %s", exception.getClass().getName(), ThrowableUtils.safeMessage(exception)));
            return;
        }

        AppRuntimeContext.setActiveLocalHostBridgeServer(bridgeServer);
        report.addDetail(String.format("Started local experimental host bridge at %s. Supported requests are: handshake, echo, list_devices, list_components, list_ports, list_links, get_device_detail, read_interface_status, read_port_power_state, add_device, connect_devices, set_interface_ip, set_default_gateway, add_static_route, run_device_cli, set_port_power_state, run_ping, probe_terminal_transcript, remove_device, delete_link, get_device_module_layout, add_module, remove_module, and add_module_at.", bridgeServer.describeEndpoint()));
        report.addDetail("The local experimental host bridge is repo-local proof wiring only; it is not an official Cisco Packet Tracer protocol claim.");
    }

    private static void registerCepMessageListener(IPCManager ipcManager, CepInstance thisInstance, BootstrapReport report) {
        PacketTracerSession session = AppRuntimeContext.getActiveSession();

        if (session == null) {
            report.addDetail("MESSAGE_RECEIVED listener registration skipped because no live PacketTracerSession is available for event-manager access.");
            return;
        }

        IPCEventManager eventManager;

        try {
            eventManager = session.getEventManager();
        } catch (Throwable throwable) {
            report.addDetail(String.format("PacketTracerSession.getEventManager() threw %s: %s", throwable.getClass().getName(), ThrowableUtils.safeMessage(throwable)));
            return;
        }

        if (eventManager == null) {
            report.addDetail("PacketTracerSession.getEventManager() returned null; MESSAGE_RECEIVED listener registration is blocked.");
            return;
        }

        CepInstanceEventRegistry registry;

        try {
            registry = eventManager.getCepInstanceEvents();
        } catch (Throwable throwable) {
            report.addDetail(String.format("IPCEventManager.getCepInstanceEvents() threw %s: %s", throwable.getClass().getName(), ThrowableUtils.safeMessage(throwable)));
            return;
        }

        if (registry == null) {
            report.addDetail("IPCEventManager.getCepInstanceEvents() returned null; MESSAGE_RECEIVED listener registration is blocked.");
            return;
        }

        ExperimentalCepMessageListener listener = new ExperimentalCepMessageListener(ipcManager, thisInstance);

        try {
            registry.addListener(listener);
            AppRuntimeContext.setActiveCepInstanceEventRegistry(registry);
            AppRuntimeContext.setActiveCepMessageListener(listener);
            report.addDetail("Registered CepInstanceEventListener for MESSAGE_RECEIVED handling via PacketTracerSession.getEventManager().getCepInstanceEvents().addListener(...).");
        report.addDetail("Inbound payload handling stays intentionally local and experimental: supported messages are local-experimental|handshake, local-experimental|list_devices, local-experimental|list_components, local-experimental|list_ports, local-experimental|list_links, local-experimental|get_device_detail|<selector>, local-experimental|read_interface_status|<selector>, local-experimental|read_port_power_state|<device>|<port>, local-experimental|add_device|<deviceType>|<model>|<x>|<y>, local-experimental|connect_devices|<leftDevice>|<leftPort>|<rightDevice>|<rightPort>|<connectionType?>, local-experimental|set_interface_ip|<device>|<port>|<ip>|<mask>, local-experimental|set_default_gateway|<device>|<gateway>, local-experimental|add_static_route|<device>|<network>|<mask>|<nextHop>|<port?>|<adminDistance?>, local-experimental|run_device_cli|<device>|<mode?>|<command>, local-experimental|set_port_power_state|<device>|<port>|<on|off>, local-experimental|run_ping|<device>|<dest>|<repeat?>|<timeout?>|<size?>|<sourcePort?>, local-experimental|probe_terminal_transcript|<device>|<mode?>|<command>, local-experimental|remove_device|<selector>, local-experimental|delete_link|<linkIndex-or-leftDevice|rightDevice>, local-experimental|get_device_module_layout|<device>, local-experimental|add_module|<device>|<slot>|<moduleType>|<model>, local-experimental|remove_module|<device>|<slot>|<moduleType>, local-experimental|add_module_at|<device>|<parentModulePath>|<slotIndex>|<model>, and local-experimental|echo|<payload>.");
        } catch (Throwable throwable) {
            report.addDetail(String.format("CepInstanceEventRegistry.addListener(...) threw %s: %s", throwable.getClass().getName(), ThrowableUtils.safeMessage(throwable)));
        }
    }

    private static IPCManager attemptPortBootstrap(int packetTracerIpcPort, BootstrapReport report) {
        report.addDetail(String.format("Attempting PTMP session bootstrap toward %s:%d via PacketTracerSessionFactory.openSession(host, port, ConnectionNegotiationProperties).", PT_LOOPBACK_HOST, Integer.valueOf(packetTracerIpcPort)));
        PacketTracerSessionFactory sessionFactory;

        try {
            sessionFactory = PacketTracerSessionFactoryImpl.getInstance();
        } catch (Throwable throwable) {
            report.addDetail(String.format("PacketTracerSessionFactoryImpl.getInstance() threw %s: %s", throwable.getClass().getName(), ThrowableUtils.safeMessage(throwable)));
            return null;
        }

        if (sessionFactory == null) {
            report.addDetail("PacketTracerSessionFactoryImpl.getInstance() returned null; PTMP bootstrap cannot proceed.");
            return null;
        }

        report.addDetail(String.format("Using PacketTracerSessionFactory implementation: %s", sessionFactory.getClass().getName()));

        NegotiationBootstrap negotiationBootstrap = NegotiationSupport.createNegotiationBootstrap(
            report,
            PacketTracerPtExApp.class,
            AUTH_APPLICATION_PROPERTY,
            AUTH_APPLICATION_ENV,
            AUTH_SECRET_PROPERTY,
            AUTH_SECRET_ENV,
            PT_APP_META_FILENAME
        );

        if (negotiationBootstrap == null) {
            report.addDetail("ConnectionNegotiationProperties could not be prepared, so the documented PTMP session path could not be attempted.");
            return null;
        }

        PacketTracerSession session;

        try {
            session = NegotiationSupport.openSessionWithNegotiation(sessionFactory, PT_LOOPBACK_HOST, packetTracerIpcPort, negotiationBootstrap, report);
        } catch (Throwable throwable) {
            report.addDetail(String.format("openSession(%s, %d, ConnectionNegotiationProperties) threw %s", PT_LOOPBACK_HOST, Integer.valueOf(packetTracerIpcPort), ThrowableUtils.describeThrowable(throwable)));
            return null;
        }

        if (session == null) {
            report.addDetail(String.format("openSession(%s, %d, ConnectionNegotiationProperties) returned null.", PT_LOOPBACK_HOST, Integer.valueOf(packetTracerIpcPort)));
            return null;
        }

        AppRuntimeContext.setActiveSessionFactory(sessionFactory);
        AppRuntimeContext.setActiveSession(session);
        report.addDetail(String.format("PTMP session implementation: %s", session.getClass().getName()));
        report.addDetail(describeSessionConnectivity(session));
        report.markPartiallyBlocked();

        IPCFactory ipcFactory;

        try {
            ipcFactory = new IPCFactory(session);
        } catch (Throwable throwable) {
            report.addDetail(String.format("new IPCFactory(session) threw %s: %s", throwable.getClass().getName(), ThrowableUtils.safeMessage(throwable)));
            return null;
        }

        IPC ipc;

        try {
            ipc = ipcFactory.getIPC();
        } catch (Throwable throwable) {
            report.addDetail(String.format("IPCFactory.getIPC() threw %s: %s", throwable.getClass().getName(), ThrowableUtils.safeMessage(throwable)));
            return null;
        }

        if (ipc == null) {
            report.addDetail("IPCFactory.getIPC() returned null; PTMP session exists but IPC facade is not ready.");
            return null;
        }

        report.addDetail(String.format("IPC facade implementation: %s", ipc.getClass().getName()));

        try {
            IPCManager ipcManager = ipc.ipcManager();
            AppRuntimeContext.setActiveIpc(ipc);
            AppRuntimeContext.setPacketTracerLogMirroringDisabled(false);

            if (ipcManager == null) {
                report.addDetail("IPC.ipcManager() returned null; PTMP session is present but IPCManager bootstrap is still blocked.");
                return null;
            }

            report.addDetail(String.format("IPC.ipcManager() returned: %s", ipcManager.getClass().getName()));
            report.addDetail(describeListeningPort(ipcManager));
            return ipcManager;
        } catch (Throwable throwable) {
            report.addDetail(String.format("IPC.ipcManager() threw %s: %s", throwable.getClass().getName(), ThrowableUtils.safeMessage(throwable)));
            return null;
        }
    }

    private static IPCManager tryAcquireIpcManager(BootstrapReport report) {
        report.addDetail("Trying the smallest credible IPCManager bootstrap path available from the framework classes already on the classpath.");

        for (String className : IPC_MANAGER_BOOTSTRAP_CLASSES) {
            try {
                Class<?> candidateClass = Class.forName(className, false, PacketTracerPtExApp.class.getClassLoader());
                Method[] methods = candidateClass.getMethods();
                boolean foundStaticAccessor = false;

                for (Method method : methods) {
                    if (!Modifier.isStatic(method.getModifiers())) {
                        continue;
                    }

                    if (method.getParameterCount() != 0) {
                        continue;
                    }

                    if (!IPCManager.class.isAssignableFrom(method.getReturnType())) {
                        continue;
                    }

                    foundStaticAccessor = true;
                    report.addDetail(String.format("Attempting static IPCManager accessor: %s#%s()", className, method.getName()));

                    Object value = method.invoke(null);

                    if (value == null) {
                        report.addDetail(String.format("%s#%s() returned null.", className, method.getName()));
                        continue;
                    }

                    return IPCManager.class.cast(value);
                }

                if (!foundStaticAccessor) {
                    report.addDetail(String.format("No public static zero-arg IPCManager accessor found on %s.", className));
                }
            } catch (ClassNotFoundException exception) {
                report.addDetail(String.format("Bootstrap helper class not present on classpath: %s", className));
            } catch (Throwable throwable) {
                report.addDetail(String.format("Bootstrap probe on %s failed with %s: %s", className, throwable.getClass().getName(), ThrowableUtils.safeMessage(throwable)));
            }
        }

        report.addDetail("Local framework inspection found no public zero-arg constructor path for IPCFactory, IPCManagerImpl, or CepInstanceImpl, so this MVP does not fabricate Packet Tracer session state.");
        return null;
    }

    private static String describeSessionConnectivity(PacketTracerSession session) {
        return String.format(
            "PTMP session state: connected=%s, ipcReady=%s, muReady=%s, sessionStatus=%s",
            safeSessionFlag(session, SessionBooleanProbe.CONNECTED),
            safeSessionFlag(session, SessionBooleanProbe.IPC_READY),
            safeSessionFlag(session, SessionBooleanProbe.MU_READY),
            describeSessionStatus(session)
        );
    }

    private static String describeSessionStatus(PacketTracerSession session) {
        try {
            Object sessionStatus = session.getSessionStatus();

            if (sessionStatus == null) {
                return "null";
            }

            return sessionStatus.toString();
        } catch (Throwable throwable) {
            return String.format("unavailable (%s: %s)", throwable.getClass().getName(), ThrowableUtils.safeMessage(throwable));
        }
    }

    private static String safeSessionFlag(PacketTracerSession session, SessionBooleanProbe probe) {
        try {
            return String.valueOf(probe.read(session));
        } catch (Throwable throwable) {
            return String.format("unavailable (%s: %s)", throwable.getClass().getName(), ThrowableUtils.safeMessage(throwable));
        }
    }

    private static String describeListeningPort(IPCManager ipcManager) {
        try {
            return String.format("IPCManager.getListeningPort(): %d", Integer.valueOf(ipcManager.getListeningPort()));
        } catch (Throwable throwable) {
            return String.format("IPCManager.getListeningPort() threw %s: %s", throwable.getClass().getName(), ThrowableUtils.safeMessage(throwable));
        }
    }

    private static String describeInstanceId(CepInstance instance) {
        try {
            Object instanceId = instance.getInstanceId();

            if (instanceId == null) {
                return "CepInstance.getInstanceId(): unavailable (returned null).";
            }

            return String.format("CepInstance.getInstanceId(): %s", instanceId);
        } catch (Throwable throwable) {
            return String.format("CepInstance.getInstanceId() threw %s: %s", throwable.getClass().getName(), ThrowableUtils.safeMessage(throwable));
        }
    }

    private static String describeCommandLineArg(CepInstance instance) {
        try {
            String commandLineArg = instance.getCommandLineArg();

            if (commandLineArg == null || commandLineArg.isEmpty()) {
                return "CepInstance.getCommandLineArg(): unavailable (returned empty).";
            }

            return String.format("CepInstance.getCommandLineArg(): %s", commandLineArg);
        } catch (Throwable throwable) {
            return String.format("CepInstance.getCommandLineArg() threw %s: %s", throwable.getClass().getName(), ThrowableUtils.safeMessage(throwable));
        }
    }

    private static String describeFrameworkTypeAvailability(String className) {
        try {
            Class.forName(className, false, PacketTracerPtExApp.class.getClassLoader());
            return String.format("Framework bootstrap-adjacent type available: %s", className);
        } catch (ClassNotFoundException exception) {
            return String.format("Framework bootstrap-adjacent type missing from classpath: %s", className);
        } catch (Throwable throwable) {
            return String.format("Framework bootstrap-adjacent type %s could not be inspected cleanly: %s: %s", className, throwable.getClass().getName(), ThrowableUtils.safeMessage(throwable));
        }
    }

    private static boolean shouldAttemptFrameworkClose(PacketTracerSession session) {
        if (session == null) {
            return true;
        }

        String sessionStatus = describeSessionStatus(session);
        return sessionStatus.indexOf("Disconnected") < 0;
    }

    private interface SessionBooleanProbe {
        SessionBooleanProbe CONNECTED = new SessionBooleanProbe() {
            @Override
            public boolean read(PacketTracerSession session) {
                return session.isConnected();
            }
        };
        SessionBooleanProbe IPC_READY = new SessionBooleanProbe() {
            @Override
            public boolean read(PacketTracerSession session) {
                return session.isIpcReady();
            }
        };
        SessionBooleanProbe MU_READY = new SessionBooleanProbe() {
            @Override
            public boolean read(PacketTracerSession session) {
                return session.isMuReady();
            }
        };

        boolean read(PacketTracerSession session);
    }
}
