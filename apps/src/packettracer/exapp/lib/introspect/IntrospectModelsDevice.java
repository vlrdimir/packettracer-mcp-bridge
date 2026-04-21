package packettracer.exapp.lib.introspect;

import java.util.List;

final class DescriptorHelperResult {
    final Object value;
    final Throwable failure;

    DescriptorHelperResult(Object value, Throwable failure) {
        this.value = value;
        this.failure = failure;
    }
}

final class DeviceSelection {
    final int deviceIndex;
    final Object device;
    final String deviceName;
    final String deviceType;
    final List<Object> ports;

    DeviceSelection(int deviceIndex, Object device, String deviceName, String deviceType, List<Object> ports) {
        this.deviceIndex = deviceIndex;
        this.device = device;
        this.deviceName = deviceName;
        this.deviceType = deviceType;
        this.ports = ports;
    }
}

final class PortSelection {
    final int portIndex;
    final String portName;
    final Object portObject;
    final PortIdentity identity;

    PortSelection(int portIndex, String portName, Object portObject, PortIdentity identity) {
        this.portIndex = portIndex;
        this.portName = portName;
        this.portObject = portObject;
        this.identity = identity;
    }
}

final class PortIdentity {
    final String primaryName;
    final String secondaryName;
    final String tertiaryName;
    final String portNameNumber;
    final String remotePortName;
    final String deviceName;
    final String deviceType;

    PortIdentity(
        String primaryName,
        String secondaryName,
        String tertiaryName,
        String portNameNumber,
        String remotePortName,
        String deviceName,
        String deviceType
    ) {
        this.primaryName = primaryName;
        this.secondaryName = secondaryName;
        this.tertiaryName = tertiaryName;
        this.portNameNumber = portNameNumber;
        this.remotePortName = remotePortName;
        this.deviceName = deviceName;
        this.deviceType = deviceType;
    }
}
