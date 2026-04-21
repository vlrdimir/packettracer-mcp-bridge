package packettracer.exapp.lib.introspect;

final class ConnectRequest {
    final String leftDeviceSelector;
    final String leftPortSelector;
    final String rightDeviceSelector;
    final String rightPortSelector;
    final String connectionType;

    ConnectRequest(
        String leftDeviceSelector,
        String leftPortSelector,
        String rightDeviceSelector,
        String rightPortSelector,
        String connectionType
    ) {
        this.leftDeviceSelector = leftDeviceSelector;
        this.leftPortSelector = leftPortSelector;
        this.rightDeviceSelector = rightDeviceSelector;
        this.rightPortSelector = rightPortSelector;
        this.connectionType = connectionType;
    }
}

final class InterfaceIpRequest {
    final String deviceSelector;
    final String portSelector;
    final String ipAddress;
    final String subnetMask;

    InterfaceIpRequest(String deviceSelector, String portSelector, String ipAddress, String subnetMask) {
        this.deviceSelector = deviceSelector;
        this.portSelector = portSelector;
        this.ipAddress = ipAddress;
        this.subnetMask = subnetMask;
    }
}

final class PortPowerStateRequest {
    final String deviceSelector;
    final String portSelector;
    final boolean powerOn;

    PortPowerStateRequest(String deviceSelector, String portSelector, boolean powerOn) {
        this.deviceSelector = deviceSelector;
        this.portSelector = portSelector;
        this.powerOn = powerOn;
    }
}

final class DefaultGatewayRequest {
    final String deviceSelector;
    final String gatewayIpAddress;

    DefaultGatewayRequest(String deviceSelector, String gatewayIpAddress) {
        this.deviceSelector = deviceSelector;
        this.gatewayIpAddress = gatewayIpAddress;
    }
}

final class StaticRouteRequest {
    final String deviceSelector;
    final String networkIpAddress;
    final String subnetMask;
    final String nextHopIpAddress;
    final String portSelector;
    final int adminDistance;

    StaticRouteRequest(
        String deviceSelector,
        String networkIpAddress,
        String subnetMask,
        String nextHopIpAddress,
        String portSelector,
        int adminDistance
    ) {
        this.deviceSelector = deviceSelector;
        this.networkIpAddress = networkIpAddress;
        this.subnetMask = subnetMask;
        this.nextHopIpAddress = nextHopIpAddress;
        this.portSelector = portSelector;
        this.adminDistance = adminDistance;
    }
}

final class DeviceCliRequest {
    final String deviceSelector;
    final String mode;
    final String command;

    DeviceCliRequest(String deviceSelector, String mode, String command) {
        this.deviceSelector = deviceSelector;
        this.mode = mode;
        this.command = command;
    }
}

final class PingRequest {
    final String deviceSelector;
    final String destinationIpAddress;
    final int repeatCount;
    final int timeoutSeconds;
    final int packetSize;
    final String sourcePortName;

    PingRequest(
        String deviceSelector,
        String destinationIpAddress,
        int repeatCount,
        int timeoutSeconds,
        int packetSize,
        String sourcePortName
    ) {
        this.deviceSelector = deviceSelector;
        this.destinationIpAddress = destinationIpAddress;
        this.repeatCount = repeatCount;
        this.timeoutSeconds = timeoutSeconds;
        this.packetSize = packetSize;
        this.sourcePortName = sourcePortName;
    }
}

final class AddDeviceRequest {
    final String deviceType;
    final String model;
    final double x;
    final double y;

    AddDeviceRequest(String deviceType, String model, double x, double y) {
        this.deviceType = deviceType;
        this.model = model;
        this.x = x;
        this.y = y;
    }
}

final class ModuleMutationRequest {
    final String deviceSelector;
    final String slot;
    final String moduleType;
    final String model;

    ModuleMutationRequest(String deviceSelector, String slot, String moduleType, String model) {
        this.deviceSelector = deviceSelector;
        this.slot = slot;
        this.moduleType = moduleType;
        this.model = model;
    }
}

final class ModuleAtRequest {
    final String deviceSelector;
    final String parentModulePath;
    final int slotIndex;
    final String model;

    ModuleAtRequest(String deviceSelector, String parentModulePath, int slotIndex, String model) {
        this.deviceSelector = deviceSelector;
        this.parentModulePath = parentModulePath;
        this.slotIndex = slotIndex;
        this.model = model;
    }
}
