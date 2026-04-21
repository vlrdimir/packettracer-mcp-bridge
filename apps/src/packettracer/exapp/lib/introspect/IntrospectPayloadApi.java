package packettracer.exapp.lib.introspect;

import com.cisco.pt.ipc.ui.IPC;
import packettracer.exapp.core.AppRuntimeContext;
import packettracer.exapp.utils.ThrowableUtils;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Public JSON payload entry points (migrated from {@code PacketTracerNetworkIntrospector}).
 */
public final class IntrospectPayloadApi {
    private IntrospectPayloadApi() {
    }

    public static String buildListDevicesJsonPayload() {
        IPC ipc = AppRuntimeContext.getActiveIpc();

        if (ipc == null) {
            return "{\"observed\":false,\"count\":0,\"devices\":[],\"reason\":\"ipc-unavailable\"}";
        }

        try {
            Method networkMethod = ipc.getClass().getMethod("network");
            Object network;

            try {
                network = networkMethod.invoke(ipc);
            } catch (Throwable throwable) {
                throw IntrospectReflect.debugFailure("list_devices:ipc.network()", throwable);
            }

            if (network == null) {
                return "{\"observed\":false,\"count\":0,\"devices\":[],\"reason\":\"network-unavailable\"}";
            }

            Method getDeviceCountMethod = network.getClass().getMethod("getDeviceCount");
            Method getDeviceAtMethod = network.getClass().getMethod("getDeviceAt", Integer.TYPE);
            int deviceCount;

            try {
                deviceCount = ((Number) getDeviceCountMethod.invoke(network)).intValue();
            } catch (Throwable throwable) {
                throw IntrospectReflect.debugFailure("list_devices:network.getDeviceCount()", throwable);
            }

            StringBuilder json = new StringBuilder();
            json.append("{\"observed\":true,\"count\":").append(deviceCount).append(",\"devices\":[");

            for (int index = 0; index < deviceCount; index++) {
                Object device;

                try {
                    device = getDeviceAtMethod.invoke(network, Integer.valueOf(index));
                } catch (Throwable throwable) {
                    throw IntrospectReflect.debugFailure("list_devices:network.getDeviceAt(" + index + ")", throwable);
                }

                if (index > 0) {
                    json.append(',');
                }

                json.append('{');
                json.append("\"index\":").append(index);
                json.append(",\"name\":").append(IntrospectJson.jsonQuoted(IntrospectReflect.readStringProperty(device, "getName")));
                json.append(",\"type\":").append(IntrospectJson.jsonQuoted(IntrospectReflect.readStringProperty(device, "getType")));
                json.append(",\"portCount\":").append(IntrospectReflect.readIntProperty(device, "getPortCount"));
                json.append('}');
            }

            json.append("]}");
            return json.toString();
        } catch (Throwable throwable) {
            return "{\"observed\":false,\"count\":0,\"devices\":[],\"reason\":" + IntrospectJson.jsonQuoted("enumeration-failed:" + ThrowableUtils.describeThrowable(throwable)) + "}";
        }
    }

    public static String buildListComponentsJsonPayload(String rawFilterPayload) {
        IPC ipc = AppRuntimeContext.getActiveIpc();
        String categoryFilterId = IntrospectLogic.normalizeComponentCategoryFilter(rawFilterPayload);

        if (ipc == null) {
            return "{\"observed\":false,\"categories\":[],\"devices\":[],\"modules\":[],\"connections\":[],\"reason\":\"ipc-unavailable\"}";
        }

        if (IntrospectLogic.isUnknownComponentCategoryFilter(rawFilterPayload, categoryFilterId)) {
            return new StringBuilder()
                .append("{\"observed\":false,\"categories\":[],\"devices\":[],\"modules\":[],\"connections\":[],\"reason\":")
                .append(IntrospectJson.jsonQuoted("unknown-component-category-filter:" + rawFilterPayload.trim()))
                .append(",\"validCategoryIds\":")
                .append(IntrospectJson.toJsonStringArray(IntrospectLogic.supportedComponentCategoryIds()))
                .append('}')
                .toString();
        }

        try {
            Object hardwareFactory;

            try {
                hardwareFactory = ipc.getClass().getMethod("hardwareFactory").invoke(ipc);
            } catch (Throwable throwable) {
                throw IntrospectReflect.debugFailure("list_components:ipc.hardwareFactory()", throwable);
            }

            if (hardwareFactory == null) {
                return "{\"observed\":false,\"categories\":[],\"devices\":[],\"modules\":[],\"connections\":[],\"reason\":\"hardware-factory-unavailable\"}";
            }

            Object deviceFactory;

            try {
                deviceFactory = hardwareFactory.getClass().getMethod("devices").invoke(hardwareFactory);
            } catch (Throwable throwable) {
                throw IntrospectReflect.debugFailure("list_components:hardwareFactory.devices()", throwable);
            }

            Object moduleFactory;

            try {
                moduleFactory = hardwareFactory.getClass().getMethod("modules").invoke(hardwareFactory);
            } catch (Throwable throwable) {
                throw IntrospectReflect.debugFailure("list_components:hardwareFactory.modules()", throwable);
            }

            List<ComponentCategorySummary> categories = new ArrayList<ComponentCategorySummary>();
            List<String> devices = new ArrayList<String>();
            List<String> modules = new ArrayList<String>();
            List<String> connections = new ArrayList<String>();
            String deviceReason = "";
            String moduleReason = "";
            String connectionReason = "";
            boolean devicesObserved = false;
            boolean modulesObserved = false;
            boolean connectionsObserved = false;

            try {
                IntrospectLogic.appendDeviceCatalog(deviceFactory, categories, devices, categoryFilterId);
                devicesObserved = deviceFactory != null;

                if (deviceFactory == null) {
                    deviceReason = "device-factory-unavailable";
                }
            } catch (Throwable throwable) {
                deviceReason = "device-catalog-failed:" + ThrowableUtils.describeThrowable(throwable);
            }

            if (categoryFilterId.isEmpty()) {
                try {
                    IntrospectLogic.appendModuleCatalog(moduleFactory, modules);
                    modulesObserved = moduleFactory != null;

                    if (moduleFactory == null) {
                        moduleReason = "module-factory-unavailable";
                    }
                } catch (Throwable throwable) {
                    moduleReason = "module-catalog-failed:" + ThrowableUtils.describeThrowable(throwable);
                }

                try {
                    connectionsObserved = IntrospectLogic.appendConnectionCatalog(connections);

                    if (!connectionsObserved) {
                        connectionReason = "connection-catalog-unavailable";
                    }
                } catch (Throwable throwable) {
                    connectionReason = "connection-catalog-failed:" + ThrowableUtils.describeThrowable(throwable);
                }
            } else {
                moduleReason = "excluded-by-device-category-filter";
                connectionReason = "excluded-by-device-category-filter";
            }

            boolean partial =
                !deviceReason.isEmpty() ||
                !moduleReason.isEmpty() ||
                !connectionReason.isEmpty();

            StringBuilder json = new StringBuilder();
            json.append("{\"observed\":true");
            json.append(",\"partial\":").append(partial ? "true" : "false");

            if (!categoryFilterId.isEmpty()) {
                json.append(",\"filter\":{");
                json.append("\"categoryId\":").append(IntrospectJson.jsonQuoted(categoryFilterId));
                json.append(",\"scope\":\"device-category\"");
                json.append('}');
            }

            json.append(",\"summary\":{");
            json.append("\"categoryCount\":").append(categories.size());
            json.append(",\"deviceCount\":").append(devices.size());
            json.append(",\"moduleCount\":").append(modules.size());
            json.append(",\"connectionCount\":").append(connections.size());
            json.append("}");
            json.append(",\"sources\":{");
            IntrospectJson.appendSourceJson(json, "devices", devicesObserved, deviceReason);
            json.append(',');
            IntrospectJson.appendSourceJson(json, "modules", modulesObserved, moduleReason);
            json.append(',');
            IntrospectJson.appendSourceJson(json, "connections", connectionsObserved, connectionReason);
            json.append('}');
            IntrospectJson.appendJsonArray(json, "categories", categories);
            IntrospectJson.appendJsonArray(json, "devices", devices);
            IntrospectJson.appendJsonArray(json, "modules", modules);
            IntrospectJson.appendJsonArray(json, "connections", connections);
            json.append("}");
            return json.toString();
        } catch (Throwable throwable) {
            return "{\"observed\":false,\"categories\":[],\"devices\":[],\"modules\":[],\"connections\":[],\"reason\":" + IntrospectJson.jsonQuoted("component-catalog-failed:" + ThrowableUtils.describeThrowable(throwable)) + "}";
        }
    }

    public static String buildListPortsJsonPayload() {
        IPC ipc = AppRuntimeContext.getActiveIpc();

        if (ipc == null) {
            return "{\"observed\":false,\"count\":0,\"ports\":[],\"reason\":\"ipc-unavailable\"}";
        }

        try {
            Method networkMethod = ipc.getClass().getMethod("network");
            Object network = networkMethod.invoke(ipc);

            if (network == null) {
                return "{\"observed\":false,\"count\":0,\"ports\":[],\"reason\":\"network-unavailable\"}";
            }

            Method getDeviceCountMethod = network.getClass().getMethod("getDeviceCount");
            Method getDeviceAtMethod = network.getClass().getMethod("getDeviceAt", Integer.TYPE);
            int deviceCount = ((Number) getDeviceCountMethod.invoke(network)).intValue();
            StringBuilder portsJson = new StringBuilder();
            int totalPorts = 0;
            boolean firstPort = true;

            for (int deviceIndex = 0; deviceIndex < deviceCount; deviceIndex++) {
                Object device = getDeviceAtMethod.invoke(network, Integer.valueOf(deviceIndex));
                String deviceName = IntrospectReflect.readStringProperty(device, "getName");
                String deviceType = IntrospectReflect.readStringProperty(device, "getType");
                List<Object> ports = IntrospectLogic.readPortObjects(device);

                for (int portIndex = 0; portIndex < ports.size(); portIndex++) {
                    Object port = ports.get(portIndex);

                    if (!firstPort) {
                        portsJson.append(',');
                    }

                    firstPort = false;
                    totalPorts++;
                    portsJson.append('{');
                    portsJson.append("\"deviceIndex\":").append(deviceIndex);
                    portsJson.append(",\"deviceName\":").append(IntrospectJson.jsonQuoted(deviceName));
                    portsJson.append(",\"deviceType\":").append(IntrospectJson.jsonQuoted(deviceType));
                    portsJson.append(",\"portIndex\":").append(portIndex);
                    portsJson.append(",\"name\":").append(IntrospectJson.jsonQuoted(IntrospectReflect.readStringProperty(port, "getName")));
                    portsJson.append(",\"linked\":").append(IntrospectReflect.readBooleanProperty(port, "getLink", true) ? "true" : "false");
                    portsJson.append('}');
                }
            }

            return new StringBuilder()
                .append("{\"observed\":true,\"count\":")
                .append(totalPorts)
                .append(",\"ports\":[")
                .append(portsJson)
                .append("]}")
                .toString();
        } catch (Throwable throwable) {
            return "{\"observed\":false,\"count\":0,\"ports\":[],\"reason\":" + IntrospectJson.jsonQuoted("enumeration-failed:" + throwable.getClass().getName() + ":" + ThrowableUtils.safeMessage(throwable)) + "}";
        }
    }

    public static String buildListLinksJsonPayload() {
        Object network = IntrospectLogic.readNetworkObject();

        if (network == null) {
            return AppRuntimeContext.getActiveIpc() == null
                ? "{\"observed\":false,\"count\":0,\"links\":[],\"reason\":\"ipc-unavailable\"}"
                : "{\"observed\":false,\"count\":0,\"links\":[],\"reason\":\"network-unavailable\"}";
        }

        try {
            Method getLinkCountMethod = network.getClass().getMethod("getLinkCount");
            Method getLinkAtMethod = network.getClass().getMethod("getLinkAt", Integer.TYPE);
            int linkCount = ((Number) getLinkCountMethod.invoke(network)).intValue();
            List<LinkEndpoints> resolvedEndpoints = IntrospectLogic.shouldUsePortStateGraph(network, linkCount)
                ? IntrospectLogic.derivePortStateLinkEndpoints(network, linkCount)
                : IntrospectLogic.resolveAllLinkEndpoints(network);
            StringBuilder json = new StringBuilder();
            json.append("{\"observed\":true,\"count\":").append(linkCount).append(",\"links\":[");

            for (int linkIndex = 0; linkIndex < linkCount; linkIndex++) {
                Object link = getLinkAtMethod.invoke(network, Integer.valueOf(linkIndex));

                if (linkIndex > 0) {
                    json.append(',');
                }

                LinkEndpoints endpoints = resolvedEndpoints.get(linkIndex);
                json.append('{');
                json.append("\"index\":").append(linkIndex);
                json.append(",\"connectionType\":").append(IntrospectJson.jsonQuoted(IntrospectReflect.readStringProperty(link, "getConnectionType")));
                IntrospectLogic.appendLinkEndpointJson(json, "left", endpoints.left);
                IntrospectLogic.appendLinkEndpointJson(json, "right", endpoints.right);
                json.append('}');
            }

            json.append("]}");
            return json.toString();
        } catch (Throwable throwable) {
            return "{\"observed\":false,\"count\":0,\"links\":[],\"reason\":" + IntrospectJson.jsonQuoted("enumeration-failed:" + throwable.getClass().getName() + ":" + ThrowableUtils.safeMessage(throwable)) + "}";
        }
    }

    public static String buildGetDeviceDetailJsonPayload(String selector) {
        Object network = IntrospectLogic.readNetworkObject();

        if (network == null) {
            return AppRuntimeContext.getActiveIpc() == null
                ? "{\"observed\":false,\"device\":null,\"reason\":\"ipc-unavailable\"}"
                : "{\"observed\":false,\"device\":null,\"reason\":\"network-unavailable\"}";
        }

        String normalizedSelector = selector == null ? "" : selector.trim();

        if (normalizedSelector.isEmpty()) {
            return "{\"observed\":false,\"device\":null,\"reason\":\"selector-required\"}";
        }

        try {
            DeviceSelection selection = IntrospectLogic.resolveDeviceSelection(network, normalizedSelector);

            if (selection == null) {
                return "{\"observed\":false,\"device\":null,\"reason\":" + IntrospectJson.jsonQuoted("device-not-found:" + normalizedSelector) + "}";
            }

            StringBuilder json = new StringBuilder();
            json.append("{\"observed\":true,\"device\":{");
            json.append("\"selector\":").append(IntrospectJson.jsonQuoted(normalizedSelector));
            json.append(",\"index\":").append(selection.deviceIndex);
            json.append(",\"name\":").append(IntrospectJson.jsonQuoted(selection.deviceName));
            json.append(",\"type\":").append(IntrospectJson.jsonQuoted(selection.deviceType));
            json.append(",\"portCount\":").append(selection.ports.size());
            IntrospectLogic.appendPortsJson(json, selection);
            IntrospectLogic.appendNeighborsJson(json, network, selection);
            json.append("}}");
            return json.toString();
        } catch (Throwable throwable) {
            return "{\"observed\":false,\"device\":null,\"reason\":" + IntrospectJson.jsonQuoted("enumeration-failed:" + throwable.getClass().getName() + ":" + ThrowableUtils.safeMessage(throwable)) + "}";
        }
    }

    public static String buildReadInterfaceStatusJsonPayload(String selector) {
        Object network = IntrospectLogic.readNetworkObject();

        if (network == null) {
            return AppRuntimeContext.getActiveIpc() == null
                ? "{\"observed\":false,\"device\":null,\"reason\":\"ipc-unavailable\"}"
                : "{\"observed\":false,\"device\":null,\"reason\":\"network-unavailable\"}";
        }

        String normalizedSelector = selector == null ? "" : selector.trim();

        if (normalizedSelector.isEmpty()) {
            return "{\"observed\":false,\"device\":null,\"reason\":\"selector-required\"}";
        }

        try {
            DeviceSelection selection = IntrospectLogic.resolveDeviceSelection(network, normalizedSelector);

            if (selection == null) {
                return "{\"observed\":false,\"device\":null,\"reason\":" + IntrospectJson.jsonQuoted("device-not-found:" + normalizedSelector) + "}";
            }

            StringBuilder json = new StringBuilder();
            json.append("{\"observed\":true,\"device\":{");
            json.append("\"selector\":").append(IntrospectJson.jsonQuoted(normalizedSelector));
            json.append(",\"index\":").append(selection.deviceIndex);
            json.append(",\"name\":").append(IntrospectJson.jsonQuoted(selection.deviceName));
            json.append(",\"type\":").append(IntrospectJson.jsonQuoted(selection.deviceType));
            json.append(",\"interfaces\":[");

            for (int portIndex = 0; portIndex < selection.ports.size(); portIndex++) {
                Object port = selection.ports.get(portIndex);

                if (portIndex > 0) {
                    json.append(',');
                }

                json.append('{');
                json.append("\"portIndex\":").append(portIndex);
                json.append(",\"name\":").append(IntrospectJson.jsonQuoted(IntrospectReflect.readStringProperty(port, "getName")));
                json.append(",\"linked\":").append(IntrospectReflect.readBooleanProperty(port, "getLink", true) ? "true" : "false");
                json.append(",\"portUp\":").append(IntrospectReflect.readBooleanProperty(port, "isPortUp", false) ? "true" : "false");
                json.append(",\"protocolUp\":").append(IntrospectReflect.readBooleanProperty(port, "isProtocolUp", false) ? "true" : "false");
                json.append(",\"remotePortName\":").append(IntrospectJson.jsonQuoted(IntrospectReflect.readStringProperty(port, "getRemotePortName")));
                json.append(",\"lightStatus\":").append(IntrospectJson.jsonQuoted(IntrospectReflect.readStringProperty(port, "getLightStatus")));
                json.append('}');
            }

            json.append("]}}");
            return json.toString();
        } catch (Throwable throwable) {
            return "{\"observed\":false,\"device\":null,\"reason\":" + IntrospectJson.jsonQuoted("enumeration-failed:" + throwable.getClass().getName() + ":" + ThrowableUtils.safeMessage(throwable)) + "}";
        }
    }

    public static String buildReadPortPowerStateJsonPayload(String payload) {
        Object network = IntrospectLogic.readNetworkObject();

        if (network == null) {
            return AppRuntimeContext.getActiveIpc() == null
                ? "{\"observed\":false,\"port\":null,\"reason\":\"ipc-unavailable\"}"
                : "{\"observed\":false,\"port\":null,\"reason\":\"network-unavailable\"}";
        }

        try {
            PortPowerStateRequest request = IntrospectLogic.parsePortPowerStateRequest(payload);

            if (request == null) {
                return "{\"observed\":false,\"port\":null,\"reason\":\"read-port-power-state-args-required\"}";
            }

            DeviceSelection device = IntrospectLogic.resolveDeviceSelection(network, request.deviceSelector);

            if (device == null) {
                return "{\"observed\":false,\"port\":null,\"reason\":" + IntrospectJson.jsonQuoted("device-not-found:" + request.deviceSelector) + "}";
            }

            PortSelection port = IntrospectLogic.resolvePortSelection(device, request.portSelector);

            if (port == null) {
                return "{\"observed\":false,\"port\":null,\"reason\":" + IntrospectJson.jsonQuoted("port-not-found:" + request.portSelector) + "}";
            }

            return IntrospectLogic.buildPortStateJsonPayload(true, device, port, null);
        } catch (Throwable throwable) {
            return "{\"observed\":false,\"port\":null,\"reason\":" + IntrospectJson.jsonQuoted("read-port-power-state-failed:" + ThrowableUtils.describeThrowable(throwable)) + "}";
        }
    }

    public static String buildAddDeviceJsonPayload(String payload) {
        IPC ipc = AppRuntimeContext.getActiveIpc();

        if (ipc == null) {
            return "{\"observed\":false,\"ok\":false,\"device\":null,\"reason\":\"ipc-unavailable\"}";
        }

        try {
            AddDeviceRequest request = IntrospectLogic.parseAddDeviceRequest(payload);

            if (request == null) {
                return "{\"observed\":false,\"ok\":false,\"device\":null,\"reason\":\"add-device-args-required\"}";
            }

            Object logicalWorkspace = readLogicalWorkspace(ipc);

            if (logicalWorkspace == null) {
                return "{\"observed\":false,\"ok\":false,\"device\":null,\"reason\":\"logical-workspace-unavailable\"}";
            }

            Object deviceType = IntrospectReflect.readDeviceTypeEnum(request.deviceType);

            if (deviceType == null) {
                return new StringBuilder()
                    .append("{\"observed\":false,\"ok\":false,\"device\":null,\"reason\":")
                    .append(IntrospectJson.jsonQuoted("unknown-device-type:" + request.deviceType))
                    .append('}')
                    .toString();
            }

            Method addDeviceMethod = logicalWorkspace.getClass().getMethod(
                "addDevice",
                Class.forName("com.cisco.pt.ipc.enums.DeviceType"),
                String.class,
                Double.TYPE,
                Double.TYPE
            );

            Object created = addDeviceMethod.invoke(
                logicalWorkspace,
                deviceType,
                request.model,
                Double.valueOf(request.x),
                Double.valueOf(request.y)
            );
            String createdName = created == null ? "" : String.valueOf(created);

            if (createdName.isEmpty()) {
                return "{\"observed\":true,\"ok\":false,\"device\":null,\"reason\":\"add-device-returned-empty-name\"}";
            }

            Object network = IntrospectLogic.readNetworkObject();
            DeviceSelection selection = network == null ? null : IntrospectLogic.resolveDeviceSelection(network, createdName);

            StringBuilder json = new StringBuilder();
            json.append('{');
            json.append("\"observed\":true");
            json.append(",\"ok\":true");
            json.append(",\"device\":{");
            json.append("\"selector\":").append(IntrospectJson.jsonQuoted(createdName));
            json.append(",\"name\":").append(IntrospectJson.jsonQuoted(createdName));
            json.append(",\"requestedType\":").append(IntrospectJson.jsonQuoted(request.deviceType));
            json.append(",\"requestedModel\":").append(IntrospectJson.jsonQuoted(request.model));
            json.append(",\"x\":").append(request.x);
            json.append(",\"y\":").append(request.y);

            if (selection != null) {
                json.append(",\"observedType\":").append(IntrospectJson.jsonQuoted(selection.deviceType));
                json.append(",\"portCount\":").append(selection.ports.size());
            }

            json.append('}');
            json.append('}');
            return json.toString();
        } catch (Throwable throwable) {
            return "{\"observed\":false,\"ok\":false,\"device\":null,\"reason\":"
                + IntrospectJson.jsonQuoted("add-device-failed:" + ThrowableUtils.describeThrowable(throwable))
                + "}";
        }
    }

    public static String buildConnectDevicesJsonPayload(String payload) {
        Object network = IntrospectLogic.readNetworkObject();

        if (network == null) {
            return AppRuntimeContext.getActiveIpc() == null
                ? "{\"observed\":false,\"ok\":false,\"link\":null,\"reason\":\"ipc-unavailable\"}"
                : "{\"observed\":false,\"ok\":false,\"link\":null,\"reason\":\"network-unavailable\"}";
        }

        try {
            ConnectRequest request = IntrospectLogic.parseConnectRequest(payload);

            if (request == null) {
                return "{\"observed\":false,\"ok\":false,\"link\":null,\"reason\":\"connect-devices-args-required\"}";
            }

            IPC ipc = AppRuntimeContext.getActiveIpc();
            Object logicalWorkspace = readLogicalWorkspace(ipc);

            if (logicalWorkspace == null) {
                return "{\"observed\":false,\"ok\":false,\"link\":null,\"reason\":\"logical-workspace-unavailable\"}";
            }

            DeviceSelection leftDevice = IntrospectLogic.resolveDeviceSelection(network, request.leftDeviceSelector);
            DeviceSelection rightDevice = IntrospectLogic.resolveDeviceSelection(network, request.rightDeviceSelector);

            if (leftDevice == null) {
                return "{\"observed\":false,\"ok\":false,\"link\":null,\"reason\":"
                    + IntrospectJson.jsonQuoted("left-device-not-found:" + request.leftDeviceSelector)
                    + "}";
            }

            if (rightDevice == null) {
                return "{\"observed\":false,\"ok\":false,\"link\":null,\"reason\":"
                    + IntrospectJson.jsonQuoted("right-device-not-found:" + request.rightDeviceSelector)
                    + "}";
            }

            PortSelection leftPort = IntrospectLogic.resolvePortSelection(leftDevice, request.leftPortSelector);
            PortSelection rightPort = IntrospectLogic.resolvePortSelection(rightDevice, request.rightPortSelector);

            if (leftPort == null) {
                return "{\"observed\":false,\"ok\":false,\"link\":null,\"reason\":"
                    + IntrospectJson.jsonQuoted("left-port-not-found:" + request.leftPortSelector)
                    + "}";
            }

            if (rightPort == null) {
                return "{\"observed\":false,\"ok\":false,\"link\":null,\"reason\":"
                    + IntrospectJson.jsonQuoted("right-port-not-found:" + request.rightPortSelector)
                    + "}";
            }

            Object connectType = IntrospectReflect.readConnectTypeEnum(request.connectionType);

            if (connectType == null) {
                return "{\"observed\":false,\"ok\":false,\"link\":null,\"reason\":"
                    + IntrospectJson.jsonQuoted("unknown-connection-type:" + request.connectionType)
                    + "}";
            }

            Method createLinkMethod = logicalWorkspace.getClass().getMethod(
                "createLink",
                String.class,
                String.class,
                String.class,
                String.class,
                Class.forName("com.cisco.pt.ipc.enums.ConnectType")
            );

            boolean ok = Boolean.TRUE.equals(
                createLinkMethod.invoke(
                    logicalWorkspace,
                    leftDevice.deviceName,
                    leftPort.portName,
                    rightDevice.deviceName,
                    rightPort.portName,
                    connectType
                )
            );

            StringBuilder json = new StringBuilder();
            json.append('{');
            json.append("\"observed\":true");
            json.append(",\"ok\":").append(ok ? "true" : "false");
            json.append(",\"link\":{");
            json.append("\"leftDevice\":").append(IntrospectJson.jsonQuoted(leftDevice.deviceName));
            json.append(",\"leftPort\":").append(IntrospectJson.jsonQuoted(leftPort.portName));
            json.append(",\"rightDevice\":").append(IntrospectJson.jsonQuoted(rightDevice.deviceName));
            json.append(",\"rightPort\":").append(IntrospectJson.jsonQuoted(rightPort.portName));
            json.append(",\"connectionType\":").append(IntrospectJson.jsonQuoted(request.connectionType));
            json.append('}');
            json.append('}');
            return json.toString();
        } catch (Throwable throwable) {
            return "{\"observed\":false,\"ok\":false,\"link\":null,\"reason\":"
                + IntrospectJson.jsonQuoted("connect-devices-failed:" + ThrowableUtils.describeThrowable(throwable))
                + "}";
        }
    }

    public static String buildSetInterfaceIpJsonPayload(String payload) {
        Object network = IntrospectLogic.readNetworkObject();

        if (network == null) {
            return AppRuntimeContext.getActiveIpc() == null
                ? "{\"observed\":false,\"ok\":false,\"port\":null,\"reason\":\"ipc-unavailable\"}"
                : "{\"observed\":false,\"ok\":false,\"port\":null,\"reason\":\"network-unavailable\"}";
        }

        try {
            InterfaceIpRequest request = IntrospectLogic.parseInterfaceIpRequest(payload);

            if (request == null) {
                return "{\"observed\":false,\"ok\":false,\"port\":null,\"reason\":\"set-interface-ip-args-required\"}";
            }

            DeviceSelection device = IntrospectLogic.resolveDeviceSelection(network, request.deviceSelector);

            if (device == null) {
                return "{\"observed\":false,\"ok\":false,\"port\":null,\"reason\":"
                    + IntrospectJson.jsonQuoted("device-not-found:" + request.deviceSelector)
                    + "}";
            }

            PortSelection port = IntrospectLogic.resolvePortSelection(device, request.portSelector);

            if (port == null) {
                return "{\"observed\":false,\"ok\":false,\"port\":null,\"reason\":"
                    + IntrospectJson.jsonQuoted("port-not-found:" + request.portSelector)
                    + "}";
            }

            Object ipAddress = IntrospectReflect.createIpAddress(request.ipAddress);
            Object subnetMask = IntrospectReflect.createIpAddress(request.subnetMask);

            if (ipAddress == null || subnetMask == null) {
                return "{\"observed\":false,\"ok\":false,\"port\":null,\"reason\":"
                    + IntrospectJson.jsonQuoted("ip-address-parse-failed:" + request.ipAddress + "/" + request.subnetMask)
                    + "}";
            }

            IntrospectReflect.invokeMethodIfPresent(port.portObject, "setDhcpClientFlag", Boolean.FALSE);
            Method setter = IntrospectReflect.findIpSubnetMaskSetter(port.portObject.getClass());

            if (setter == null) {
                return "{\"observed\":true,\"ok\":false,\"port\":null,\"reason\":\"set-ip-subnet-mask-unavailable\"}";
            }

            setter.invoke(port.portObject, ipAddress, subnetMask);
            PortStateMutationResult mutation = new PortStateMutationResult(true, true, "port.setIpSubnetMask", request.ipAddress + "/" + request.subnetMask);
            return IntrospectLogic.buildPortStateJsonPayload(true, device, port, mutation);
        } catch (Throwable throwable) {
            return "{\"observed\":false,\"ok\":false,\"port\":null,\"reason\":"
                + IntrospectJson.jsonQuoted("set-interface-ip-failed:" + ThrowableUtils.describeThrowable(throwable))
                + "}";
        }
    }

    public static String buildSetDefaultGatewayJsonPayload(String payload) {
        Object network = IntrospectLogic.readNetworkObject();

        if (network == null) {
            return AppRuntimeContext.getActiveIpc() == null
                ? "{\"observed\":false,\"ok\":false,\"device\":null,\"reason\":\"ipc-unavailable\"}"
                : "{\"observed\":false,\"ok\":false,\"device\":null,\"reason\":\"network-unavailable\"}";
        }

        try {
            DefaultGatewayRequest request = IntrospectLogic.parseDefaultGatewayRequest(payload);

            if (request == null) {
                return "{\"observed\":false,\"ok\":false,\"device\":null,\"reason\":\"set-default-gateway-args-required\"}";
            }

            DeviceSelection device = IntrospectLogic.resolveDeviceSelection(network, request.deviceSelector);

            if (device == null) {
                return "{\"observed\":false,\"ok\":false,\"device\":null,\"reason\":"
                    + IntrospectJson.jsonQuoted("device-not-found:" + request.deviceSelector)
                    + "}";
            }

            Object gateway = IntrospectReflect.createIpAddress(request.gatewayIpAddress);

            if (gateway == null) {
                return "{\"observed\":false,\"ok\":false,\"device\":null,\"reason\":"
                    + IntrospectJson.jsonQuoted("gateway-ip-parse-failed:" + request.gatewayIpAddress)
                    + "}";
            }

            String appliedVia = IntrospectLogic.tryApplyDefaultGateway(device, gateway);
            boolean ok = appliedVia != null;
            StringBuilder json = new StringBuilder();
            json.append('{');
            json.append("\"observed\":true");
            json.append(",\"ok\":").append(ok ? "true" : "false");
            json.append(",\"device\":{");
            json.append("\"selector\":").append(IntrospectJson.jsonQuoted(device.deviceName));
            json.append(",\"gateway\":").append(IntrospectJson.jsonQuoted(request.gatewayIpAddress));
            json.append(",\"type\":").append(IntrospectJson.jsonQuoted(device.deviceType));
            json.append('}');
            json.append(",\"appliedVia\":").append(IntrospectJson.jsonQuoted(appliedVia == null ? "" : appliedVia));
            json.append(",\"response\":").append(IntrospectJson.jsonQuoted(ok ? "default-gateway-applied" : "default-gateway-setter-unavailable"));
            json.append('}');
            return json.toString();
        } catch (Throwable throwable) {
            return "{\"observed\":false,\"ok\":false,\"device\":null,\"reason\":"
                + IntrospectJson.jsonQuoted("set-default-gateway-failed:" + ThrowableUtils.describeThrowable(throwable))
                + "}";
        }
    }

    public static String buildAddStaticRouteJsonPayload(String payload) {
        Object network = IntrospectLogic.readNetworkObject();

        if (network == null) {
            return AppRuntimeContext.getActiveIpc() == null
                ? "{\"observed\":false,\"ok\":false,\"route\":null,\"reason\":\"ipc-unavailable\"}"
                : "{\"observed\":false,\"ok\":false,\"route\":null,\"reason\":\"network-unavailable\"}";
        }

        try {
            StaticRouteRequest request = IntrospectLogic.parseStaticRouteRequest(payload);

            if (request == null) {
                return "{\"observed\":false,\"ok\":false,\"route\":null,\"reason\":\"add-static-route-args-required\"}";
            }

            DeviceSelection device = IntrospectLogic.resolveDeviceSelection(network, request.deviceSelector);

            if (device == null) {
                return "{\"observed\":false,\"ok\":false,\"route\":null,\"reason\":"
                    + IntrospectJson.jsonQuoted("device-not-found:" + request.deviceSelector)
                    + "}";
            }

            Object networkIp = IntrospectReflect.createIpAddress(request.networkIpAddress);
            Object subnetMask = IntrospectReflect.createIpAddress(request.subnetMask);
            Object nextHop = IntrospectReflect.createIpAddress(request.nextHopIpAddress);

            if (networkIp == null || subnetMask == null || nextHop == null) {
                return "{\"observed\":false,\"ok\":false,\"route\":null,\"reason\":"
                    + IntrospectJson.jsonQuoted("static-route-ip-parse-failed")
                    + "}";
            }

            RouteExecutionResult result = IntrospectLogic.tryAddStaticRoute(device, request, networkIp, subnetMask, nextHop);
            StringBuilder json = new StringBuilder();
            json.append('{');
            json.append("\"observed\":").append(result.observed ? "true" : "false");
            json.append(",\"ok\":").append(result.ok ? "true" : "false");
            json.append(",\"route\":{");
            json.append("\"device\":").append(IntrospectJson.jsonQuoted(device.deviceName));
            json.append(",\"network\":").append(IntrospectJson.jsonQuoted(request.networkIpAddress));
            json.append(",\"subnetMask\":").append(IntrospectJson.jsonQuoted(request.subnetMask));
            json.append(",\"nextHop\":").append(IntrospectJson.jsonQuoted(request.nextHopIpAddress));
            json.append(",\"portSelector\":").append(IntrospectJson.jsonQuoted(request.portSelector));
            json.append(",\"adminDistance\":").append(request.adminDistance);
            json.append('}');
            json.append(",\"appliedVia\":").append(IntrospectJson.jsonQuoted(result.appliedVia));
            json.append(",\"appliedPortSelector\":").append(IntrospectJson.jsonQuoted(result.appliedPortSelector));
            json.append(",\"response\":").append(IntrospectJson.jsonQuoted(result.response));
            json.append('}');
            return json.toString();
        } catch (Throwable throwable) {
            return "{\"observed\":false,\"ok\":false,\"route\":null,\"reason\":"
                + IntrospectJson.jsonQuoted("add-static-route-failed:" + ThrowableUtils.describeThrowable(throwable))
                + "}";
        }
    }

    public static String buildRunDeviceCliJsonPayload(String payload) {
        Object network = IntrospectLogic.readNetworkObject();

        if (network == null) {
            return AppRuntimeContext.getActiveIpc() == null
                ? "{\"observed\":false,\"ok\":false,\"device\":null,\"reason\":\"ipc-unavailable\"}"
                : "{\"observed\":false,\"ok\":false,\"device\":null,\"reason\":\"network-unavailable\"}";
        }

        try {
            DeviceCliRequest request = IntrospectLogic.parseDeviceCliRequest(payload);

            if (request == null) {
                return "{\"observed\":false,\"ok\":false,\"device\":null,\"reason\":\"run-device-cli-args-required\"}";
            }

            DeviceSelection device = IntrospectLogic.resolveDeviceSelection(network, request.deviceSelector);

            if (device == null) {
                return "{\"observed\":false,\"ok\":false,\"device\":null,\"reason\":"
                    + IntrospectJson.jsonQuoted("device-not-found:" + request.deviceSelector)
                    + "}";
            }

            CliExecutionResult result = IntrospectLogic.executeDeviceCli(device.device, request.mode, request.command);
            StringBuilder json = new StringBuilder();
            json.append('{');
            json.append("\"observed\":").append(result.observed ? "true" : "false");
            json.append(",\"ok\":").append(result.ok ? "true" : "false");
            json.append(",\"device\":").append(IntrospectJson.jsonQuoted(device.deviceName));
            json.append(",\"mode\":").append(IntrospectJson.jsonQuoted(request.mode));
            json.append(",\"command\":").append(IntrospectJson.jsonQuoted(request.command));
            json.append(",\"transport\":").append(IntrospectJson.jsonQuoted(result.transport));
            json.append(",\"response\":").append(IntrospectJson.jsonQuoted(result.response));
            json.append('}');
            return json.toString();
        } catch (Throwable throwable) {
            return "{\"observed\":false,\"ok\":false,\"device\":null,\"reason\":"
                + IntrospectJson.jsonQuoted("run-device-cli-failed:" + ThrowableUtils.describeThrowable(throwable))
                + "}";
        }
    }

    public static String buildSetPortPowerStateJsonPayload(String payload) {
        Object network = IntrospectLogic.readNetworkObject();

        if (network == null) {
            return AppRuntimeContext.getActiveIpc() == null
                ? "{\"observed\":false,\"ok\":false,\"port\":null,\"reason\":\"ipc-unavailable\"}"
                : "{\"observed\":false,\"ok\":false,\"port\":null,\"reason\":\"network-unavailable\"}";
        }

        try {
            PortPowerStateRequest request = IntrospectLogic.parsePortPowerStateRequest(payload);

            if (request == null) {
                return "{\"observed\":false,\"ok\":false,\"port\":null,\"reason\":\"set-port-power-state-args-required\"}";
            }

            DeviceSelection device = IntrospectLogic.resolveDeviceSelection(network, request.deviceSelector);
            if (device == null) {
                return "{\"observed\":false,\"ok\":false,\"port\":null,\"reason\":"
                    + IntrospectJson.jsonQuoted("device-not-found:" + request.deviceSelector)
                    + "}";
            }

            PortSelection port = IntrospectLogic.resolvePortSelection(device, request.portSelector);
            if (port == null) {
                return "{\"observed\":false,\"ok\":false,\"port\":null,\"reason\":"
                    + IntrospectJson.jsonQuoted("port-not-found:" + request.portSelector)
                    + "}";
            }

            String appliedVia = IntrospectLogic.trySetPortPowerState(device, port, request.powerOn);
            PortStateMutationResult mutation = new PortStateMutationResult(true, appliedVia != null, appliedVia == null ? "" : appliedVia, request.powerOn ? "power-on-requested" : "power-off-requested");
            return IntrospectLogic.buildPortStateJsonPayload(true, device, port, mutation);
        } catch (Throwable throwable) {
            return "{\"observed\":false,\"ok\":false,\"port\":null,\"reason\":"
                + IntrospectJson.jsonQuoted("set-port-power-state-failed:" + ThrowableUtils.describeThrowable(throwable))
                + "}";
        }
    }

    public static String buildRunPingJsonPayload(String payload) {
        Object network = IntrospectLogic.readNetworkObject();

        if (network == null) {
            return AppRuntimeContext.getActiveIpc() == null
                ? "{\"observed\":false,\"ok\":false,\"device\":null,\"reason\":\"ipc-unavailable\"}"
                : "{\"observed\":false,\"ok\":false,\"device\":null,\"reason\":\"network-unavailable\"}";
        }

        try {
            PingRequest request = IntrospectLogic.parsePingRequest(payload);
            if (request == null) {
                return "{\"observed\":false,\"ok\":false,\"device\":null,\"reason\":\"run-ping-args-required\"}";
            }

            DeviceSelection device = IntrospectLogic.resolveDeviceSelection(network, request.deviceSelector);
            if (device == null) {
                return "{\"observed\":false,\"ok\":false,\"device\":null,\"reason\":"
                    + IntrospectJson.jsonQuoted("device-not-found:" + request.deviceSelector)
                    + "}";
            }

            Object destinationIp = IntrospectReflect.createIpAddress(request.destinationIpAddress);
            if (destinationIp == null) {
                return "{\"observed\":false,\"ok\":false,\"device\":null,\"reason\":"
                    + IntrospectJson.jsonQuoted("ping-ip-parse-failed:" + request.destinationIpAddress)
                    + "}";
            }

            PingExecutionResult result = IntrospectLogic.tryRunPing(device, request, destinationIp);
            return new StringBuilder()
                .append('{')
                .append("\"observed\":").append(result.observed ? "true" : "false")
                .append(",\"ok\":").append(result.ok ? "true" : "false")
                .append(",\"device\":").append(IntrospectJson.jsonQuoted(device.deviceName))
                .append(",\"destination\":").append(IntrospectJson.jsonQuoted(request.destinationIpAddress))
                .append(",\"transport\":").append(IntrospectJson.jsonQuoted(result.transport))
                .append(",\"sentCount\":").append(result.sentCount)
                .append(",\"receivedCount\":").append(result.receivedCount)
                .append(",\"lastDelay\":").append(result.lastDelay)
                .append(",\"lastTtl\":").append(result.lastTtl)
                .append(",\"response\":").append(IntrospectJson.jsonQuoted(result.response))
                .append('}')
                .toString();
        } catch (Throwable throwable) {
            return "{\"observed\":false,\"ok\":false,\"device\":null,\"reason\":"
                + IntrospectJson.jsonQuoted("run-ping-failed:" + ThrowableUtils.describeThrowable(throwable))
                + "}";
        }
    }

    public static String buildProbeTerminalTranscriptJsonPayload(String payload) {
        Object network = IntrospectLogic.readNetworkObject();

        if (network == null) {
            return AppRuntimeContext.getActiveIpc() == null
                ? "{\"observed\":false,\"ok\":false,\"device\":null,\"reason\":\"ipc-unavailable\"}"
                : "{\"observed\":false,\"ok\":false,\"device\":null,\"reason\":\"network-unavailable\"}";
        }

        try {
            DeviceCliRequest request = IntrospectLogic.parseDeviceCliRequest(payload);
            if (request == null) {
                return "{\"observed\":false,\"ok\":false,\"device\":null,\"reason\":\"probe-terminal-transcript-args-required\"}";
            }

            DeviceSelection device = IntrospectLogic.resolveDeviceSelection(network, request.deviceSelector);
            if (device == null) {
                return "{\"observed\":false,\"ok\":false,\"device\":null,\"reason\":"
                    + IntrospectJson.jsonQuoted("device-not-found:" + request.deviceSelector)
                    + "}";
            }

            Object terminalLine = IntrospectReflect.readFirstNonNullObject(device.device, "getIpcTerminalLine", "getCommandLine", "getConsoleLine", "getCommandPrompt");
            if (terminalLine == null) {
                return "{\"observed\":false,\"ok\":false,\"device\":null,\"reason\":\"terminal-line-unavailable\"}";
            }

            return IntrospectLogic.buildTerminalTranscriptJsonPayload(device.deviceName, request, IntrospectLogic.executeTerminalLineObservation(terminalLine, request.mode, request.command));
        } catch (Throwable throwable) {
            return "{\"observed\":false,\"ok\":false,\"device\":null,\"reason\":"
                + IntrospectJson.jsonQuoted("probe-terminal-transcript-failed:" + ThrowableUtils.describeThrowable(throwable))
                + "}";
        }
    }

    public static String buildRemoveDeviceJsonPayload(String payload) {
        IPC ipc = AppRuntimeContext.getActiveIpc();
        if (ipc == null) {
            return "{\"observed\":false,\"ok\":false,\"device\":null,\"reason\":\"ipc-unavailable\"}";
        }

        String selector = payload == null ? "" : payload.trim();
        if (selector.isEmpty()) {
            return "{\"observed\":false,\"ok\":false,\"device\":null,\"reason\":\"remove-device-selector-required\"}";
        }

        try {
            Object network = IntrospectLogic.readNetworkObject();
            DeviceSelection device = network == null ? null : IntrospectLogic.resolveDeviceSelection(network, selector);
            if (device == null) {
                return "{\"observed\":false,\"ok\":false,\"device\":null,\"reason\":"
                    + IntrospectJson.jsonQuoted("device-not-found:" + selector)
                    + "}";
            }

            Object logicalWorkspace = readLogicalWorkspace(ipc);
            if (logicalWorkspace == null) {
                return "{\"observed\":false,\"ok\":false,\"device\":null,\"reason\":\"logical-workspace-unavailable\"}";
            }

            Method removeDeviceMethod = logicalWorkspace.getClass().getMethod("removeDevice", String.class);
            boolean ok = Boolean.TRUE.equals(removeDeviceMethod.invoke(logicalWorkspace, device.deviceName));
            return new StringBuilder()
                .append('{')
                .append("\"observed\":true")
                .append(",\"ok\":").append(ok ? "true" : "false")
                .append(",\"device\":{")
                .append("\"selector\":").append(IntrospectJson.jsonQuoted(selector))
                .append(",\"name\":").append(IntrospectJson.jsonQuoted(device.deviceName))
                .append(",\"type\":").append(IntrospectJson.jsonQuoted(device.deviceType))
                .append('}')
                .append('}')
                .toString();
        } catch (Throwable throwable) {
            return "{\"observed\":false,\"ok\":false,\"device\":null,\"reason\":"
                + IntrospectJson.jsonQuoted("remove-device-failed:" + ThrowableUtils.describeThrowable(throwable))
                + "}";
        }
    }

    public static String buildDeleteLinkJsonPayload(String payload) {
        IPC ipc = AppRuntimeContext.getActiveIpc();
        Object network = IntrospectLogic.readNetworkObject();
        if (ipc == null) {
            return "{\"observed\":false,\"ok\":false,\"link\":null,\"reason\":\"ipc-unavailable\"}";
        }
        if (network == null) {
            return "{\"observed\":false,\"ok\":false,\"link\":null,\"reason\":\"network-unavailable\"}";
        }

        try {
            String raw = payload == null ? "" : payload.trim();
            if (raw.isEmpty()) {
                return "{\"observed\":false,\"ok\":false,\"link\":null,\"reason\":\"delete-link-args-required\"}";
            }

            String leftDevice;
            String rightDevice;
            Integer requestedIndex = IntrospectReflect.parseSelectorIndex(raw);
            if (requestedIndex != null) {
                Method getLinkCountMethod = network.getClass().getMethod("getLinkCount");
                int linkCount = ((Number) getLinkCountMethod.invoke(network)).intValue();
                if (requestedIndex.intValue() < 0 || requestedIndex.intValue() >= linkCount) {
                    return "{\"observed\":false,\"ok\":false,\"link\":null,\"reason\":"
                        + IntrospectJson.jsonQuoted("link-index-out-of-range:" + raw)
                        + "}";
                }
                List<LinkEndpoints> endpoints = IntrospectLogic.shouldUsePortStateGraph(network, linkCount)
                    ? IntrospectLogic.derivePortStateLinkEndpoints(network, linkCount)
                    : IntrospectLogic.resolveAllLinkEndpoints(network);
                LinkEndpoints selected = endpoints.get(requestedIndex.intValue());
                leftDevice = selected.left.deviceName;
                rightDevice = selected.right.deviceName;
            } else {
                String[] parts = raw.split("\\|", -1);
                if (parts.length < 2 || parts[0].trim().isEmpty() || parts[1].trim().isEmpty()) {
                    return "{\"observed\":false,\"ok\":false,\"link\":null,\"reason\":\"delete-link-args-required\"}";
                }
                leftDevice = parts[0].trim();
                rightDevice = parts[1].trim();
            }

            Object logicalWorkspace = readLogicalWorkspace(ipc);
            if (logicalWorkspace == null) {
                return "{\"observed\":false,\"ok\":false,\"link\":null,\"reason\":\"logical-workspace-unavailable\"}";
            }

            Method deleteLinkMethod = logicalWorkspace.getClass().getMethod("deleteLink", String.class, String.class);
            boolean ok = Boolean.TRUE.equals(deleteLinkMethod.invoke(logicalWorkspace, leftDevice, rightDevice));
            return new StringBuilder()
                .append('{')
                .append("\"observed\":true")
                .append(",\"ok\":").append(ok ? "true" : "false")
                .append(",\"link\":{")
                .append("\"leftDevice\":").append(IntrospectJson.jsonQuoted(leftDevice))
                .append(",\"rightDevice\":").append(IntrospectJson.jsonQuoted(rightDevice))
                .append('}')
                .append('}')
                .toString();
        } catch (Throwable throwable) {
            return "{\"observed\":false,\"ok\":false,\"link\":null,\"reason\":"
                + IntrospectJson.jsonQuoted("delete-link-failed:" + ThrowableUtils.describeThrowable(throwable))
                + "}";
        }
    }

    public static String buildGetDeviceModuleLayoutJsonPayload(String payload) {
        Object network = IntrospectLogic.readNetworkObject();
        if (network == null) {
            return AppRuntimeContext.getActiveIpc() == null
                ? "{\"observed\":false,\"device\":null,\"reason\":\"ipc-unavailable\"}"
                : "{\"observed\":false,\"device\":null,\"reason\":\"network-unavailable\"}";
        }

        String selector = payload == null ? "" : payload.trim();
        if (selector.isEmpty()) {
            return "{\"observed\":false,\"device\":null,\"reason\":\"device-selector-required\"}";
        }

        try {
            DeviceSelection device = IntrospectLogic.resolveDeviceSelection(network, selector);
            if (device == null) {
                return "{\"observed\":false,\"device\":null,\"reason\":"
                    + IntrospectJson.jsonQuoted("device-not-found:" + selector)
                    + "}";
            }
            Object rootModule = IntrospectReflect.readObjectProperty(device.device, "getRootModule");
            StringBuilder json = new StringBuilder();
            json.append('{');
            json.append("\"observed\":true");
            json.append(",\"device\":{");
            json.append("\"selector\":").append(IntrospectJson.jsonQuoted(device.deviceName));
            json.append(",\"type\":").append(IntrospectJson.jsonQuoted(device.deviceType));
            json.append(",\"supportedModules\":").append(toJsonStringArray(readSupportedModules(device.device)));
            json.append(",\"rootModule\":");
            appendModuleJson(json, rootModule);
            json.append('}');
            json.append('}');
            return json.toString();
        } catch (Throwable throwable) {
            return "{\"observed\":false,\"device\":null,\"reason\":"
                + IntrospectJson.jsonQuoted("get-device-module-layout-failed:" + ThrowableUtils.describeThrowable(throwable))
                + "}";
        }
    }

    public static String buildAddModuleJsonPayload(String payload) {
        Object network = IntrospectLogic.readNetworkObject();
        if (network == null) {
            return AppRuntimeContext.getActiveIpc() == null
                ? "{\"observed\":false,\"ok\":false,\"module\":null,\"reason\":\"ipc-unavailable\"}"
                : "{\"observed\":false,\"ok\":false,\"module\":null,\"reason\":\"network-unavailable\"}";
        }

        try {
            ModuleMutationRequest request = IntrospectLogic.parseModuleMutationRequest(payload, true);
            if (request == null) {
                return "{\"observed\":false,\"ok\":false,\"module\":null,\"reason\":\"add-module-args-required\"}";
            }
            DeviceSelection device = IntrospectLogic.resolveDeviceSelection(network, request.deviceSelector);
            if (device == null) {
                return "{\"observed\":false,\"ok\":false,\"module\":null,\"reason\":"
                    + IntrospectJson.jsonQuoted("device-not-found:" + request.deviceSelector)
                    + "}";
            }
            Object moduleType = IntrospectReflect.readModuleTypeEnum(request.moduleType);
            if (moduleType == null) {
                return "{\"observed\":false,\"ok\":false,\"module\":null,\"reason\":"
                    + IntrospectJson.jsonQuoted("unknown-module-type:" + request.moduleType)
                    + "}";
            }
            Method addModuleMethod = device.device.getClass().getMethod("addModule", String.class, Class.forName("com.cisco.pt.ipc.enums.ModuleType"), String.class);
            boolean ok = Boolean.TRUE.equals(addModuleMethod.invoke(device.device, request.slot, moduleType, request.model));
            return moduleMutationResponse(ok, device.deviceName, request.slot, request.model, request.moduleType, "add_module");
        } catch (Throwable throwable) {
            return mutationFailure("module", "add-module-failed", throwable);
        }
    }

    public static String buildRemoveModuleJsonPayload(String payload) {
        Object network = IntrospectLogic.readNetworkObject();
        if (network == null) {
            return AppRuntimeContext.getActiveIpc() == null
                ? "{\"observed\":false,\"ok\":false,\"module\":null,\"reason\":\"ipc-unavailable\"}"
                : "{\"observed\":false,\"ok\":false,\"module\":null,\"reason\":\"network-unavailable\"}";
        }

        try {
            ModuleMutationRequest request = IntrospectLogic.parseModuleMutationRequest(payload, false);
            if (request == null) {
                return "{\"observed\":false,\"ok\":false,\"module\":null,\"reason\":\"remove-module-args-required\"}";
            }
            DeviceSelection device = IntrospectLogic.resolveDeviceSelection(network, request.deviceSelector);
            if (device == null) {
                return mutationNotFound("module", request.deviceSelector);
            }
            Method removeModuleMethod = device.device.getClass().getMethod("removeModule", String.class);
            boolean ok = Boolean.TRUE.equals(removeModuleMethod.invoke(device.device, request.slot));
            return moduleMutationResponse(ok, device.deviceName, request.slot, request.model, request.moduleType, "remove_module");
        } catch (Throwable throwable) {
            return mutationFailure("module", "remove-module-failed", throwable);
        }
    }

    public static String buildAddModuleAtJsonPayload(String payload) {
        Object network = IntrospectLogic.readNetworkObject();
        if (network == null) {
            return AppRuntimeContext.getActiveIpc() == null
                ? "{\"observed\":false,\"ok\":false,\"module\":null,\"reason\":\"ipc-unavailable\"}"
                : "{\"observed\":false,\"ok\":false,\"module\":null,\"reason\":\"network-unavailable\"}";
        }

        try {
            ModuleAtRequest request = IntrospectLogic.parseModuleAtRequest(payload);
            if (request == null) {
                return "{\"observed\":false,\"ok\":false,\"module\":null,\"reason\":\"add-module-at-args-required\"}";
            }
            DeviceSelection device = IntrospectLogic.resolveDeviceSelection(network, request.deviceSelector);
            if (device == null) {
                return mutationNotFound("module", request.deviceSelector);
            }
            Object rootModule = IntrospectReflect.readObjectProperty(device.device, "getRootModule");
            Object parent = findModuleByPath(rootModule, request.parentModulePath);
            if (parent == null) {
                return mutationFailureMessage("module", "parent-module-path-not-found:" + request.parentModulePath);
            }
            Method addModuleAtMethod = parent.getClass().getMethod("addModuleAt", String.class, Integer.TYPE);
            boolean ok = Boolean.TRUE.equals(addModuleAtMethod.invoke(parent, request.model, Integer.valueOf(request.slotIndex)));
            return new StringBuilder()
                .append('{').append("\"observed\":true")
                .append(",\"ok\":").append(ok ? "true" : "false")
                .append(",\"module\":{")
                .append("\"device\":").append(IntrospectJson.jsonQuoted(device.deviceName))
                .append(",\"parentModulePath\":").append(IntrospectJson.jsonQuoted(request.parentModulePath))
                .append(",\"slotIndex\":").append(request.slotIndex)
                .append(",\"model\":").append(IntrospectJson.jsonQuoted(request.model))
                .append('}').append('}')
                .toString();
        } catch (Throwable throwable) {
            return mutationFailure("module", "add-module-at-failed", throwable);
        }
    }

    private static Object readLogicalWorkspace(IPC ipc) {
        if (ipc == null) {
            return null;
        }

        try {
            Object appWindow = ipc.getClass().getMethod("appWindow").invoke(ipc);

            if (appWindow == null) {
                return null;
            }

            Object workspace = appWindow.getClass().getMethod("getActiveWorkspace").invoke(appWindow);

            if (workspace == null) {
                return null;
            }

            return workspace.getClass().getMethod("getLogicalWorkspace").invoke(workspace);
        } catch (Throwable throwable) {
            return null;
        }
    }

    private static String mutationNotFound(String fieldName, String selector) {
        return new StringBuilder()
            .append('{')
            .append("\"observed\":false,\"ok\":false,\"").append(fieldName).append("\":null,\"reason\":")
            .append(IntrospectJson.jsonQuoted("device-not-found:" + selector))
            .append('}')
            .toString();
    }

    private static String mutationFailure(String fieldName, String prefix, Throwable throwable) {
        return mutationFailureMessage(fieldName, prefix + ":" + ThrowableUtils.describeThrowable(throwable));
    }

    private static String mutationFailureMessage(String fieldName, String message) {
        return new StringBuilder()
            .append('{')
            .append("\"observed\":false,\"ok\":false,\"").append(fieldName).append("\":null,\"reason\":")
            .append(IntrospectJson.jsonQuoted(message))
            .append('}')
            .toString();
    }

    private static String moduleMutationResponse(boolean ok, String deviceName, String slot, String model, String moduleType, String operation) {
        return new StringBuilder()
            .append('{').append("\"observed\":true")
            .append(",\"ok\":").append(ok ? "true" : "false")
            .append(",\"operation\":").append(IntrospectJson.jsonQuoted(operation))
            .append(",\"module\":{")
            .append("\"device\":").append(IntrospectJson.jsonQuoted(deviceName))
            .append(",\"slot\":").append(IntrospectJson.jsonQuoted(slot))
            .append(",\"moduleType\":").append(IntrospectJson.jsonQuoted(moduleType))
            .append(",\"model\":").append(IntrospectJson.jsonQuoted(model == null ? "" : model))
            .append('}').append('}')
            .toString();
    }

    private static String toJsonStringArray(List<String> values) {
        return IntrospectJson.toJsonStringArray(values == null ? new ArrayList<String>() : values);
    }

    private static List<String> readSupportedModules(Object device) {
        ArrayList<String> modules = new ArrayList<String>();
        if (device == null) {
            return modules;
        }
        try {
            Object raw = device.getClass().getMethod("getSupportedModule").invoke(device);
            if (raw instanceof Iterable<?>) {
                for (Object item : (Iterable<?>) raw) {
                    if (item != null) {
                        modules.add(String.valueOf(item));
                    }
                }
            } else if (raw != null && raw.getClass().isArray()) {
                int length = Array.getLength(raw);
                for (int i = 0; i < length; i++) {
                    Object item = Array.get(raw, i);
                    if (item != null) {
                        modules.add(String.valueOf(item));
                    }
                }
            }
        } catch (Throwable throwable) {
            return modules;
        }
        return modules;
    }

    private static Object findModuleByPath(Object module, String path) {
        if (module == null) {
            return null;
        }
        String normalized = path == null ? "" : path.trim();
        if (normalized.isEmpty()) {
            return module;
        }
        String slotPath = IntrospectReflect.readStringProperty(module, "getSlotPath");
        if (normalized.equals(slotPath)) {
            return module;
        }
        int count = IntrospectReflect.readIntProperty(module, "getModuleCount");
        try {
            Method getModuleAtMethod = module.getClass().getMethod("getModuleAt", Integer.TYPE);
            for (int i = 0; i < count; i++) {
                Object child = getModuleAtMethod.invoke(module, Integer.valueOf(i));
                Object match = findModuleByPath(child, normalized);
                if (match != null) {
                    return match;
                }
            }
        } catch (Throwable throwable) {
            return null;
        }
        return null;
    }

    private static void appendModuleJson(StringBuilder json, Object module) {
        if (module == null) {
            json.append("null");
            return;
        }
        json.append('{');
        json.append("\"moduleNumber\":").append(IntrospectReflect.readIntProperty(module, "getModuleNumber"));
        json.append(",\"moduleName\":").append(IntrospectJson.jsonQuoted(IntrospectReflect.readStringProperty(module, "getModuleNameAsString")));
        json.append(",\"slotPath\":").append(IntrospectJson.jsonQuoted(IntrospectReflect.readStringProperty(module, "getSlotPath")));
        json.append(",\"moduleType\":").append(IntrospectJson.jsonQuoted(IntrospectReflect.readStringProperty(module, "getModuleType")));
        Object descriptor = IntrospectReflect.readObjectProperty(module, "getDescriptor");
        json.append(",\"model\":").append(IntrospectJson.jsonQuoted(descriptor == null ? "" : IntrospectReflect.readStringProperty(descriptor, "getModel")));
        json.append(",\"portCount\":").append(IntrospectReflect.readIntProperty(module, "getPortCount"));
        json.append(",\"slotCount\":").append(IntrospectReflect.readIntProperty(module, "getSlotCount"));
        json.append(",\"slots\":[");
        int slotCount = IntrospectReflect.readIntProperty(module, "getSlotCount");
        try {
            Method getSlotTypeAtMethod = module.getClass().getMethod("getSlotTypeAt", Integer.TYPE);
            for (int i = 0; i < slotCount; i++) {
                if (i > 0) json.append(',');
                Object slotType = getSlotTypeAtMethod.invoke(module, Integer.valueOf(i));
                json.append('{').append("\"slotIndex\":").append(i).append(",\"slotType\":").append(IntrospectJson.jsonQuoted(slotType == null ? "" : String.valueOf(slotType))).append('}');
            }
        } catch (Throwable throwable) {
            // leave slots partial
        }
        json.append(']');
        json.append(",\"children\":[");
        int childCount = IntrospectReflect.readIntProperty(module, "getModuleCount");
        try {
            Method getModuleAtMethod = module.getClass().getMethod("getModuleAt", Integer.TYPE);
            for (int i = 0; i < childCount; i++) {
                if (i > 0) json.append(',');
                appendModuleJson(json, getModuleAtMethod.invoke(module, Integer.valueOf(i)));
            }
        } catch (Throwable throwable) {
            // leave children partial
        }
        json.append(']');
        json.append('}');
    }
}
