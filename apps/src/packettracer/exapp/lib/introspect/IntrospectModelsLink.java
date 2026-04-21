package packettracer.exapp.lib.introspect;

import java.util.Map;
import java.util.List;
import java.util.Set;

final class LinkEndpoint {
    final boolean observed;
    final int deviceIndex;
    final String deviceName;
    final String deviceType;
    final int portIndex;
    final String portName;

    LinkEndpoint(boolean observed, int deviceIndex, String deviceName, String deviceType, int portIndex, String portName) {
        this.observed = observed;
        this.deviceIndex = deviceIndex;
        this.deviceName = deviceName;
        this.deviceType = deviceType;
        this.portIndex = portIndex;
        this.portName = portName;
    }

    static LinkEndpoint missing() {
        return new LinkEndpoint(false, -1, "", "", -1, "");
    }
}

final class LinkEndpoints {
    final LinkEndpoint left;
    final LinkEndpoint right;

    LinkEndpoints(LinkEndpoint left, LinkEndpoint right) {
        this.left = left;
        this.right = right;
    }
}

final class PortCandidate {
    final int deviceIndex;
    final String deviceName;
    final String deviceType;
    final int portIndex;
    final String portName;
    final int score;

    PortCandidate(int deviceIndex, String deviceName, String deviceType, int portIndex, String portName, int score) {
        this.deviceIndex = deviceIndex;
        this.deviceName = deviceName;
        this.deviceType = deviceType;
        this.portIndex = portIndex;
        this.portName = portName;
        this.score = score;
    }
}

final class LinkResolutionContext {
    final Set<String> usedPortKeys;
    final Map<Integer, Integer> deviceUsageCounts;

    LinkResolutionContext(Set<String> usedPortKeys, Map<Integer, Integer> deviceUsageCounts) {
        this.usedPortKeys = usedPortKeys;
        this.deviceUsageCounts = deviceUsageCounts;
    }
}

final class DevicePortBucket {
    final int deviceIndex;
    final String deviceName;
    final String deviceType;
    final List<PortCandidate> ports;

    DevicePortBucket(int deviceIndex, String deviceName, String deviceType, List<PortCandidate> ports) {
        this.deviceIndex = deviceIndex;
        this.deviceName = deviceName;
        this.deviceType = deviceType;
        this.ports = ports;
    }

    int remaining() {
        return ports.size();
    }

    PortCandidate takeFirst() {
        return ports.isEmpty() ? null : ports.remove(0);
    }
}
