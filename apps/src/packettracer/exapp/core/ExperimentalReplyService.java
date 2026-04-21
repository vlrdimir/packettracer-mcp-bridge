package packettracer.exapp.core;

import com.cisco.pt.UUID;
import com.cisco.pt.ipc.system.CepInstance;
import packettracer.exapp.lib.PacketTracerNetworkIntrospector;
import packettracer.exapp.utils.ExperimentalProtocol;
import packettracer.exapp.utils.ExperimentalProtocol.ParsedExperimentalMessage;

public final class ExperimentalReplyService {
    private ExperimentalReplyService() {
    }

    public static String buildExperimentalReplyPayload(String responderInstanceId, ParsedExperimentalMessage parsedMessage) {
        if (ExperimentalProtocol.LOCAL_OPERATION_HANDSHAKE.equals(parsedMessage.getOperation())) {
            return ExperimentalProtocol.LOCAL_PROTOCOL_PREFIX + "|" + ExperimentalProtocol.LOCAL_OPERATION_HANDSHAKE + "|ok|instance=" + responderInstanceId;
        }

        if (ExperimentalProtocol.LOCAL_OPERATION_LIST_DEVICES.equals(parsedMessage.getOperation())) {
            return ExperimentalProtocol.LOCAL_PROTOCOL_PREFIX + "|" + ExperimentalProtocol.LOCAL_OPERATION_LIST_DEVICES + "|ok|" + PacketTracerNetworkIntrospector.buildListDevicesJsonPayload();
        }

        if (ExperimentalProtocol.LOCAL_OPERATION_LIST_COMPONENTS.equals(parsedMessage.getOperation())) {
            return ExperimentalProtocol.LOCAL_PROTOCOL_PREFIX + "|" + ExperimentalProtocol.LOCAL_OPERATION_LIST_COMPONENTS + "|ok|" + PacketTracerNetworkIntrospector.buildListComponentsJsonPayload(parsedMessage.getPayload());
        }

        if (ExperimentalProtocol.LOCAL_OPERATION_LIST_PORTS.equals(parsedMessage.getOperation())) {
            return ExperimentalProtocol.LOCAL_PROTOCOL_PREFIX + "|" + ExperimentalProtocol.LOCAL_OPERATION_LIST_PORTS + "|ok|" + PacketTracerNetworkIntrospector.buildListPortsJsonPayload();
        }

        if (ExperimentalProtocol.LOCAL_OPERATION_LIST_LINKS.equals(parsedMessage.getOperation())) {
            return ExperimentalProtocol.LOCAL_PROTOCOL_PREFIX + "|" + ExperimentalProtocol.LOCAL_OPERATION_LIST_LINKS + "|ok|" + PacketTracerNetworkIntrospector.buildListLinksJsonPayload();
        }

        if (ExperimentalProtocol.LOCAL_OPERATION_GET_DEVICE_DETAIL.equals(parsedMessage.getOperation())) {
            return ExperimentalProtocol.LOCAL_PROTOCOL_PREFIX + "|" + ExperimentalProtocol.LOCAL_OPERATION_GET_DEVICE_DETAIL + "|ok|" + PacketTracerNetworkIntrospector.buildGetDeviceDetailJsonPayload(parsedMessage.getPayload());
        }

        if (ExperimentalProtocol.LOCAL_OPERATION_READ_INTERFACE_STATUS.equals(parsedMessage.getOperation())) {
            return ExperimentalProtocol.LOCAL_PROTOCOL_PREFIX + "|" + ExperimentalProtocol.LOCAL_OPERATION_READ_INTERFACE_STATUS + "|ok|" + PacketTracerNetworkIntrospector.buildReadInterfaceStatusJsonPayload(parsedMessage.getPayload());
        }

        if (ExperimentalProtocol.LOCAL_OPERATION_READ_PORT_POWER_STATE.equals(parsedMessage.getOperation())) {
            return ExperimentalProtocol.LOCAL_PROTOCOL_PREFIX + "|" + ExperimentalProtocol.LOCAL_OPERATION_READ_PORT_POWER_STATE + "|ok|" + PacketTracerNetworkIntrospector.buildReadPortPowerStateJsonPayload(parsedMessage.getPayload());
        }

        if (ExperimentalProtocol.LOCAL_OPERATION_ADD_DEVICE.equals(parsedMessage.getOperation())) {
            return ExperimentalProtocol.LOCAL_PROTOCOL_PREFIX + "|" + ExperimentalProtocol.LOCAL_OPERATION_ADD_DEVICE + "|ok|" + PacketTracerNetworkIntrospector.buildAddDeviceJsonPayload(parsedMessage.getPayload());
        }

        if (ExperimentalProtocol.LOCAL_OPERATION_CONNECT_DEVICES.equals(parsedMessage.getOperation())) {
            return ExperimentalProtocol.LOCAL_PROTOCOL_PREFIX + "|" + ExperimentalProtocol.LOCAL_OPERATION_CONNECT_DEVICES + "|ok|" + PacketTracerNetworkIntrospector.buildConnectDevicesJsonPayload(parsedMessage.getPayload());
        }

        if (ExperimentalProtocol.LOCAL_OPERATION_SET_INTERFACE_IP.equals(parsedMessage.getOperation())) {
            return ExperimentalProtocol.LOCAL_PROTOCOL_PREFIX + "|" + ExperimentalProtocol.LOCAL_OPERATION_SET_INTERFACE_IP + "|ok|" + PacketTracerNetworkIntrospector.buildSetInterfaceIpJsonPayload(parsedMessage.getPayload());
        }

        if (ExperimentalProtocol.LOCAL_OPERATION_SET_DEFAULT_GATEWAY.equals(parsedMessage.getOperation())) {
            return ExperimentalProtocol.LOCAL_PROTOCOL_PREFIX + "|" + ExperimentalProtocol.LOCAL_OPERATION_SET_DEFAULT_GATEWAY + "|ok|" + PacketTracerNetworkIntrospector.buildSetDefaultGatewayJsonPayload(parsedMessage.getPayload());
        }

        if (ExperimentalProtocol.LOCAL_OPERATION_ADD_STATIC_ROUTE.equals(parsedMessage.getOperation())) {
            return ExperimentalProtocol.LOCAL_PROTOCOL_PREFIX + "|" + ExperimentalProtocol.LOCAL_OPERATION_ADD_STATIC_ROUTE + "|ok|" + PacketTracerNetworkIntrospector.buildAddStaticRouteJsonPayload(parsedMessage.getPayload());
        }

        if (ExperimentalProtocol.LOCAL_OPERATION_RUN_DEVICE_CLI.equals(parsedMessage.getOperation())) {
            return ExperimentalProtocol.LOCAL_PROTOCOL_PREFIX + "|" + ExperimentalProtocol.LOCAL_OPERATION_RUN_DEVICE_CLI + "|ok|" + PacketTracerNetworkIntrospector.buildRunDeviceCliJsonPayload(parsedMessage.getPayload());
        }

        if (ExperimentalProtocol.LOCAL_OPERATION_SET_PORT_POWER_STATE.equals(parsedMessage.getOperation())) {
            return ExperimentalProtocol.LOCAL_PROTOCOL_PREFIX + "|" + ExperimentalProtocol.LOCAL_OPERATION_SET_PORT_POWER_STATE + "|ok|" + PacketTracerNetworkIntrospector.buildSetPortPowerStateJsonPayload(parsedMessage.getPayload());
        }

        if (ExperimentalProtocol.LOCAL_OPERATION_RUN_PING.equals(parsedMessage.getOperation())) {
            return ExperimentalProtocol.LOCAL_PROTOCOL_PREFIX + "|" + ExperimentalProtocol.LOCAL_OPERATION_RUN_PING + "|ok|" + PacketTracerNetworkIntrospector.buildRunPingJsonPayload(parsedMessage.getPayload());
        }

        if (ExperimentalProtocol.LOCAL_OPERATION_PROBE_TERMINAL_TRANSCRIPT.equals(parsedMessage.getOperation())) {
            return ExperimentalProtocol.LOCAL_PROTOCOL_PREFIX + "|" + ExperimentalProtocol.LOCAL_OPERATION_PROBE_TERMINAL_TRANSCRIPT + "|ok|" + PacketTracerNetworkIntrospector.buildProbeTerminalTranscriptJsonPayload(parsedMessage.getPayload());
        }

        if (ExperimentalProtocol.LOCAL_OPERATION_REMOVE_DEVICE.equals(parsedMessage.getOperation())) {
            return ExperimentalProtocol.LOCAL_PROTOCOL_PREFIX + "|" + ExperimentalProtocol.LOCAL_OPERATION_REMOVE_DEVICE + "|ok|" + PacketTracerNetworkIntrospector.buildRemoveDeviceJsonPayload(parsedMessage.getPayload());
        }

        if (ExperimentalProtocol.LOCAL_OPERATION_DELETE_LINK.equals(parsedMessage.getOperation())) {
            return ExperimentalProtocol.LOCAL_PROTOCOL_PREFIX + "|" + ExperimentalProtocol.LOCAL_OPERATION_DELETE_LINK + "|ok|" + PacketTracerNetworkIntrospector.buildDeleteLinkJsonPayload(parsedMessage.getPayload());
        }

        if (ExperimentalProtocol.LOCAL_OPERATION_GET_DEVICE_MODULE_LAYOUT.equals(parsedMessage.getOperation())) {
            return ExperimentalProtocol.LOCAL_PROTOCOL_PREFIX + "|" + ExperimentalProtocol.LOCAL_OPERATION_GET_DEVICE_MODULE_LAYOUT + "|ok|" + PacketTracerNetworkIntrospector.buildGetDeviceModuleLayoutJsonPayload(parsedMessage.getPayload());
        }

        if (ExperimentalProtocol.LOCAL_OPERATION_ADD_MODULE.equals(parsedMessage.getOperation())) {
            return ExperimentalProtocol.LOCAL_PROTOCOL_PREFIX + "|" + ExperimentalProtocol.LOCAL_OPERATION_ADD_MODULE + "|ok|" + PacketTracerNetworkIntrospector.buildAddModuleJsonPayload(parsedMessage.getPayload());
        }

        if (ExperimentalProtocol.LOCAL_OPERATION_REMOVE_MODULE.equals(parsedMessage.getOperation())) {
            return ExperimentalProtocol.LOCAL_PROTOCOL_PREFIX + "|" + ExperimentalProtocol.LOCAL_OPERATION_REMOVE_MODULE + "|ok|" + PacketTracerNetworkIntrospector.buildRemoveModuleJsonPayload(parsedMessage.getPayload());
        }

        if (ExperimentalProtocol.LOCAL_OPERATION_ADD_MODULE_AT.equals(parsedMessage.getOperation())) {
            return ExperimentalProtocol.LOCAL_PROTOCOL_PREFIX + "|" + ExperimentalProtocol.LOCAL_OPERATION_ADD_MODULE_AT + "|ok|" + PacketTracerNetworkIntrospector.buildAddModuleAtJsonPayload(parsedMessage.getPayload());
        }

        return ExperimentalProtocol.LOCAL_PROTOCOL_PREFIX + "|" + ExperimentalProtocol.LOCAL_OPERATION_ECHO + "|ok|" + parsedMessage.getPayload();
    }

    public static String safeLocalInstanceId(CepInstance instance) {
        if (instance == null) {
            return "unknown";
        }

        try {
            UUID instanceId = instance.getInstanceId();
            return instanceId == null ? "unknown" : instanceId.toString();
        } catch (Throwable throwable) {
            return "unknown";
        }
    }
}
