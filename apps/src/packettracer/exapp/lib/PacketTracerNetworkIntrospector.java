package packettracer.exapp.lib;

import packettracer.exapp.lib.introspect.IntrospectPayloadApi;

/**
 * Public facade for Packet Tracer network introspection JSON payloads.
 * Implementation lives in {@link IntrospectPayloadApi} and supporting classes under
 * {@code packettracer.exapp.lib.introspect}.
 */
public final class PacketTracerNetworkIntrospector {
    private PacketTracerNetworkIntrospector() {
    }

    public static String buildListDevicesJsonPayload() {
        return IntrospectPayloadApi.buildListDevicesJsonPayload();
    }

    public static String buildListComponentsJsonPayload(String rawFilterPayload) {
        return IntrospectPayloadApi.buildListComponentsJsonPayload(rawFilterPayload);
    }

    public static String buildListPortsJsonPayload() {
        return IntrospectPayloadApi.buildListPortsJsonPayload();
    }

    public static String buildListLinksJsonPayload() {
        return IntrospectPayloadApi.buildListLinksJsonPayload();
    }

    public static String buildGetDeviceDetailJsonPayload(String selector) {
        return IntrospectPayloadApi.buildGetDeviceDetailJsonPayload(selector);
    }

    public static String buildReadInterfaceStatusJsonPayload(String selector) {
        return IntrospectPayloadApi.buildReadInterfaceStatusJsonPayload(selector);
    }

    public static String buildReadPortPowerStateJsonPayload(String payload) {
        return IntrospectPayloadApi.buildReadPortPowerStateJsonPayload(payload);
    }

    public static String buildAddDeviceJsonPayload(String payload) {
        return IntrospectPayloadApi.buildAddDeviceJsonPayload(payload);
    }

    public static String buildConnectDevicesJsonPayload(String payload) {
        return IntrospectPayloadApi.buildConnectDevicesJsonPayload(payload);
    }

    public static String buildSetInterfaceIpJsonPayload(String payload) {
        return IntrospectPayloadApi.buildSetInterfaceIpJsonPayload(payload);
    }

    public static String buildSetDefaultGatewayJsonPayload(String payload) {
        return IntrospectPayloadApi.buildSetDefaultGatewayJsonPayload(payload);
    }

    public static String buildAddStaticRouteJsonPayload(String payload) {
        return IntrospectPayloadApi.buildAddStaticRouteJsonPayload(payload);
    }

    public static String buildRunDeviceCliJsonPayload(String payload) {
        return IntrospectPayloadApi.buildRunDeviceCliJsonPayload(payload);
    }

    public static String buildSetPortPowerStateJsonPayload(String payload) {
        return IntrospectPayloadApi.buildSetPortPowerStateJsonPayload(payload);
    }

    public static String buildRunPingJsonPayload(String payload) {
        return IntrospectPayloadApi.buildRunPingJsonPayload(payload);
    }

    public static String buildProbeTerminalTranscriptJsonPayload(String payload) {
        return IntrospectPayloadApi.buildProbeTerminalTranscriptJsonPayload(payload);
    }

    public static String buildRemoveDeviceJsonPayload(String payload) {
        return IntrospectPayloadApi.buildRemoveDeviceJsonPayload(payload);
    }

    public static String buildDeleteLinkJsonPayload(String payload) {
        return IntrospectPayloadApi.buildDeleteLinkJsonPayload(payload);
    }

    public static String buildGetDeviceModuleLayoutJsonPayload(String payload) {
        return IntrospectPayloadApi.buildGetDeviceModuleLayoutJsonPayload(payload);
    }

    public static String buildAddModuleJsonPayload(String payload) {
        return IntrospectPayloadApi.buildAddModuleJsonPayload(payload);
    }

    public static String buildRemoveModuleJsonPayload(String payload) {
        return IntrospectPayloadApi.buildRemoveModuleJsonPayload(payload);
    }

    public static String buildAddModuleAtJsonPayload(String payload) {
        return IntrospectPayloadApi.buildAddModuleAtJsonPayload(payload);
    }
}
