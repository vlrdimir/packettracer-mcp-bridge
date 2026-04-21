package packettracer.exapp.lib.introspect;

import java.util.ArrayList;
import java.util.List;

final class ComponentCategory {
    final String id;
    final String displayName;

    ComponentCategory(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }
}

final class ComponentCategorySummary {
    final String id;
    final String displayName;
    int deviceCount;
    final List<String> deviceTypes;

    ComponentCategorySummary(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
        this.deviceCount = 0;
        this.deviceTypes = new ArrayList<String>();
    }

    @Override
    public String toString() {
        return new StringBuilder()
            .append('{')
            .append("\"categoryId\":").append(IntrospectJson.jsonQuoted(id))
            .append(",\"displayName\":").append(IntrospectJson.jsonQuoted(displayName))
            .append(",\"deviceCount\":").append(deviceCount)
            .append(",\"deviceTypes\":").append(IntrospectJson.toJsonStringArray(deviceTypes))
            .append('}')
            .toString();
    }
}
