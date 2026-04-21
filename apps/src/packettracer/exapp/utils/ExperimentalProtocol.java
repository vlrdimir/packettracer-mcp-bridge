package packettracer.exapp.utils;

public final class ExperimentalProtocol {
    public static final String LOCAL_PROTOCOL_PREFIX = "local-experimental";
    public static final String LOCAL_OPERATION_HANDSHAKE = "handshake";
    public static final String LOCAL_OPERATION_ECHO = "echo";
    public static final String LOCAL_OPERATION_LIST_DEVICES = "list_devices";
    public static final String LOCAL_OPERATION_LIST_COMPONENTS = "list_components";
    public static final String LOCAL_OPERATION_LIST_PORTS = "list_ports";
    public static final String LOCAL_OPERATION_LIST_LINKS = "list_links";
    public static final String LOCAL_OPERATION_GET_DEVICE_DETAIL = "get_device_detail";
    public static final String LOCAL_OPERATION_READ_INTERFACE_STATUS = "read_interface_status";
    public static final String LOCAL_OPERATION_READ_PORT_POWER_STATE = "read_port_power_state";
    public static final String LOCAL_OPERATION_ADD_DEVICE = "add_device";
    public static final String LOCAL_OPERATION_CONNECT_DEVICES = "connect_devices";
    public static final String LOCAL_OPERATION_SET_INTERFACE_IP = "set_interface_ip";
    public static final String LOCAL_OPERATION_SET_DEFAULT_GATEWAY = "set_default_gateway";
    public static final String LOCAL_OPERATION_ADD_STATIC_ROUTE = "add_static_route";
    public static final String LOCAL_OPERATION_RUN_DEVICE_CLI = "run_device_cli";
    public static final String LOCAL_OPERATION_SET_PORT_POWER_STATE = "set_port_power_state";
    public static final String LOCAL_OPERATION_RUN_PING = "run_ping";
    public static final String LOCAL_OPERATION_PROBE_TERMINAL_TRANSCRIPT = "probe_terminal_transcript";
    public static final String LOCAL_OPERATION_REMOVE_DEVICE = "remove_device";
    public static final String LOCAL_OPERATION_DELETE_LINK = "delete_link";
    public static final String LOCAL_OPERATION_GET_DEVICE_MODULE_LAYOUT = "get_device_module_layout";
    public static final String LOCAL_OPERATION_ADD_MODULE = "add_module";
    public static final String LOCAL_OPERATION_REMOVE_MODULE = "remove_module";
    public static final String LOCAL_OPERATION_ADD_MODULE_AT = "add_module_at";

    private ExperimentalProtocol() {
    }

    public static ParsedExperimentalMessage parse(String rawPayload) {
        if (rawPayload == null) {
            return null;
        }

        String trimmedPayload = rawPayload.trim();

        if (trimmedPayload.isEmpty()) {
            return null;
        }

        int firstSeparator = trimmedPayload.indexOf('|');

        if (firstSeparator <= 0) {
            return null;
        }

        String protocolPrefix = trimmedPayload.substring(0, firstSeparator);

        if (!LOCAL_PROTOCOL_PREFIX.equals(protocolPrefix)) {
            return null;
        }

        int secondSeparator = trimmedPayload.indexOf('|', firstSeparator + 1);
        String operation = secondSeparator < 0 ? trimmedPayload.substring(firstSeparator + 1) : trimmedPayload.substring(firstSeparator + 1, secondSeparator);

        if (LOCAL_OPERATION_HANDSHAKE.equals(operation)) {
            return secondSeparator < 0 ? new ParsedExperimentalMessage(LOCAL_OPERATION_HANDSHAKE, "") : null;
        }

        if (LOCAL_OPERATION_ECHO.equals(operation)) {
            return secondSeparator >= 0 ? new ParsedExperimentalMessage(LOCAL_OPERATION_ECHO, trimmedPayload.substring(secondSeparator + 1)) : null;
        }

        if (LOCAL_OPERATION_LIST_DEVICES.equals(operation)) {
            return secondSeparator < 0 ? new ParsedExperimentalMessage(LOCAL_OPERATION_LIST_DEVICES, "") : null;
        }

        if (LOCAL_OPERATION_LIST_COMPONENTS.equals(operation)) {
            return new ParsedExperimentalMessage(LOCAL_OPERATION_LIST_COMPONENTS, secondSeparator < 0 ? "" : trimmedPayload.substring(secondSeparator + 1));
        }

        if (LOCAL_OPERATION_LIST_PORTS.equals(operation)) {
            return secondSeparator < 0 ? new ParsedExperimentalMessage(LOCAL_OPERATION_LIST_PORTS, "") : null;
        }

        if (LOCAL_OPERATION_LIST_LINKS.equals(operation)) {
            return secondSeparator < 0 ? new ParsedExperimentalMessage(LOCAL_OPERATION_LIST_LINKS, "") : null;
        }

        if (LOCAL_OPERATION_GET_DEVICE_DETAIL.equals(operation)) {
            return secondSeparator >= 0 ? new ParsedExperimentalMessage(LOCAL_OPERATION_GET_DEVICE_DETAIL, trimmedPayload.substring(secondSeparator + 1)) : null;
        }

        if (LOCAL_OPERATION_READ_INTERFACE_STATUS.equals(operation)) {
            return secondSeparator >= 0 ? new ParsedExperimentalMessage(LOCAL_OPERATION_READ_INTERFACE_STATUS, trimmedPayload.substring(secondSeparator + 1)) : null;
        }

        if (LOCAL_OPERATION_READ_PORT_POWER_STATE.equals(operation)) {
            return secondSeparator >= 0 ? new ParsedExperimentalMessage(LOCAL_OPERATION_READ_PORT_POWER_STATE, trimmedPayload.substring(secondSeparator + 1)) : null;
        }

        if (LOCAL_OPERATION_ADD_DEVICE.equals(operation)) {
            return secondSeparator >= 0 ? new ParsedExperimentalMessage(LOCAL_OPERATION_ADD_DEVICE, trimmedPayload.substring(secondSeparator + 1)) : null;
        }

        if (LOCAL_OPERATION_CONNECT_DEVICES.equals(operation)) {
            return secondSeparator >= 0 ? new ParsedExperimentalMessage(LOCAL_OPERATION_CONNECT_DEVICES, trimmedPayload.substring(secondSeparator + 1)) : null;
        }

        if (LOCAL_OPERATION_SET_INTERFACE_IP.equals(operation)) {
            return secondSeparator >= 0 ? new ParsedExperimentalMessage(LOCAL_OPERATION_SET_INTERFACE_IP, trimmedPayload.substring(secondSeparator + 1)) : null;
        }

        if (LOCAL_OPERATION_SET_DEFAULT_GATEWAY.equals(operation)) {
            return secondSeparator >= 0 ? new ParsedExperimentalMessage(LOCAL_OPERATION_SET_DEFAULT_GATEWAY, trimmedPayload.substring(secondSeparator + 1)) : null;
        }

        if (LOCAL_OPERATION_ADD_STATIC_ROUTE.equals(operation)) {
            return secondSeparator >= 0 ? new ParsedExperimentalMessage(LOCAL_OPERATION_ADD_STATIC_ROUTE, trimmedPayload.substring(secondSeparator + 1)) : null;
        }

        if (LOCAL_OPERATION_RUN_DEVICE_CLI.equals(operation)) {
            return secondSeparator >= 0 ? new ParsedExperimentalMessage(LOCAL_OPERATION_RUN_DEVICE_CLI, trimmedPayload.substring(secondSeparator + 1)) : null;
        }

        if (LOCAL_OPERATION_SET_PORT_POWER_STATE.equals(operation)) {
            return secondSeparator >= 0 ? new ParsedExperimentalMessage(LOCAL_OPERATION_SET_PORT_POWER_STATE, trimmedPayload.substring(secondSeparator + 1)) : null;
        }

        if (LOCAL_OPERATION_RUN_PING.equals(operation)) {
            return secondSeparator >= 0 ? new ParsedExperimentalMessage(LOCAL_OPERATION_RUN_PING, trimmedPayload.substring(secondSeparator + 1)) : null;
        }

        if (LOCAL_OPERATION_PROBE_TERMINAL_TRANSCRIPT.equals(operation)) {
            return secondSeparator >= 0 ? new ParsedExperimentalMessage(LOCAL_OPERATION_PROBE_TERMINAL_TRANSCRIPT, trimmedPayload.substring(secondSeparator + 1)) : null;
        }

        if (LOCAL_OPERATION_REMOVE_DEVICE.equals(operation)) {
            return secondSeparator >= 0 ? new ParsedExperimentalMessage(LOCAL_OPERATION_REMOVE_DEVICE, trimmedPayload.substring(secondSeparator + 1)) : null;
        }

        if (LOCAL_OPERATION_DELETE_LINK.equals(operation)) {
            return secondSeparator >= 0 ? new ParsedExperimentalMessage(LOCAL_OPERATION_DELETE_LINK, trimmedPayload.substring(secondSeparator + 1)) : null;
        }

        if (LOCAL_OPERATION_GET_DEVICE_MODULE_LAYOUT.equals(operation)) {
            return secondSeparator >= 0 ? new ParsedExperimentalMessage(LOCAL_OPERATION_GET_DEVICE_MODULE_LAYOUT, trimmedPayload.substring(secondSeparator + 1)) : null;
        }

        if (LOCAL_OPERATION_ADD_MODULE.equals(operation)) {
            return secondSeparator >= 0 ? new ParsedExperimentalMessage(LOCAL_OPERATION_ADD_MODULE, trimmedPayload.substring(secondSeparator + 1)) : null;
        }

        if (LOCAL_OPERATION_REMOVE_MODULE.equals(operation)) {
            return secondSeparator >= 0 ? new ParsedExperimentalMessage(LOCAL_OPERATION_REMOVE_MODULE, trimmedPayload.substring(secondSeparator + 1)) : null;
        }

        if (LOCAL_OPERATION_ADD_MODULE_AT.equals(operation)) {
            return secondSeparator >= 0 ? new ParsedExperimentalMessage(LOCAL_OPERATION_ADD_MODULE_AT, trimmedPayload.substring(secondSeparator + 1)) : null;
        }

        return null;
    }

    public static final class ParsedExperimentalMessage {
        private final String operation;
        private final String payload;

        private ParsedExperimentalMessage(String operation, String payload) {
            this.operation = operation;
            this.payload = payload;
        }

        public String getOperation() {
            return operation;
        }

        public String getPayload() {
            return payload;
        }
    }
}
