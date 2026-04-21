package packettracer.exapp.lib.introspect;

import com.cisco.pt.ipc.ui.IPC;
import packettracer.exapp.core.AppRuntimeContext;
import packettracer.exapp.utils.ThrowableUtils;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Introspection helpers migrated from the former monolithic class.
 */
final class IntrospectLogic {
    private IntrospectLogic() {
    }

    static Object readNetworkObject() {
        IPC ipc = AppRuntimeContext.getActiveIpc();

        if (ipc == null) {
            return null;
        }

        try {
            Method networkMethod = ipc.getClass().getMethod("network");
            return networkMethod.invoke(ipc);
        } catch (Throwable throwable) {
            return null;
        }
    }

    static Object readLogicalWorkspaceObject() {
        IPC ipc = AppRuntimeContext.getActiveIpc();

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

    static void appendDeviceCatalog(
        Object deviceFactory,
        List<ComponentCategorySummary> categories,
        List<String> devices,
        String categoryFilterId
    ) throws Exception {
        if (deviceFactory == null) {
            return;
        }

        Method getAvailableDeviceCountMethod = deviceFactory.getClass().getMethod("getAvailableDeviceCount");
        Method getAvailableDeviceAtMethod = deviceFactory.getClass().getMethod("getAvailableDeviceAt", Integer.TYPE);
        int deviceCount = ((Number) getAvailableDeviceCountMethod.invoke(deviceFactory)).intValue();

        for (int index = 0; index < deviceCount; index++) {
            Object descriptor = readAvailableDeviceDescriptor(deviceFactory, getAvailableDeviceAtMethod, index);

            if (descriptor == null) {
                continue;
            }

            String typeId = IntrospectReflect.readStringProperty(descriptor, "getType");
            String model = IntrospectReflect.readStringProperty(descriptor, "getModel");
            ComponentCategory category = categorizeDeviceType(typeId);

            if (!categoryFilterId.isEmpty() && !categoryFilterId.equals(category.id)) {
                continue;
            }

            List<String> specifiedModels = IntrospectReflect.readIndexedStringValues(descriptor, "getSpecifiedModelCount", "getSpecifiedModelAt");
            List<String> supportedModules = IntrospectReflect.readIndexedStringValues(descriptor, "getSupportedModuleTypeCount", "getSupportedModuleTypeAt");

            devices.add(
                new StringBuilder()
                    .append('{')
                    .append("\"typeId\":").append(IntrospectJson.jsonQuoted(typeId))
                    .append(",\"model\":").append(IntrospectJson.jsonQuoted(model))
                    .append(",\"displayName\":").append(IntrospectJson.jsonQuoted(IntrospectJson.preferDisplayName(model, typeId)))
                    .append(",\"uiCategoryId\":").append(IntrospectJson.jsonQuoted(category.id))
                    .append(",\"uiCategoryDisplayName\":").append(IntrospectJson.jsonQuoted(category.displayName))
                    .append(",\"modelSupported\":").append(IntrospectReflect.readBooleanProperty(descriptor, "isModelSupported", false) ? "true" : "false")
                    .append(",\"specifiedModels\":").append(IntrospectJson.toJsonStringArray(specifiedModels))
                    .append(",\"supportedModules\":").append(IntrospectJson.toJsonStringArray(supportedModules))
                    .append('}')
                    .toString()
            );

            incrementCategory(categories, category, typeId);
        }
    }

    static void appendModuleCatalog(Object moduleFactory, List<String> modules) throws Exception {
        if (moduleFactory == null) {
            return;
        }

        Method getAvailableModuleCountMethod = moduleFactory.getClass().getMethod("getAvailableModuleCount");
        Method getAvailableModuleAtMethod = moduleFactory.getClass().getMethod("getAvailableModuleAt", Integer.TYPE);
        int moduleCount = ((Number) getAvailableModuleCountMethod.invoke(moduleFactory)).intValue();

        for (int index = 0; index < moduleCount; index++) {
            Object descriptor = readAvailableModuleDescriptor(moduleFactory, getAvailableModuleAtMethod, index);

            if (descriptor == null) {
                continue;
            }

            String typeId = IntrospectReflect.readStringProperty(descriptor, "getType");
            String model = IntrospectReflect.readStringProperty(descriptor, "getModel");
            String group = IntrospectReflect.readStringProperty(descriptor, "getGroup");
            List<String> slotTypes = IntrospectReflect.readIndexedStringValues(descriptor, "getSlotCount", "getSlotTypeAt");

            modules.add(
                new StringBuilder()
                    .append('{')
                    .append("\"typeId\":").append(IntrospectJson.jsonQuoted(typeId))
                    .append(",\"model\":").append(IntrospectJson.jsonQuoted(model))
                    .append(",\"displayName\":").append(IntrospectJson.jsonQuoted(IntrospectJson.preferDisplayName(model, typeId)))
                    .append(",\"group\":").append(IntrospectJson.jsonQuoted(group))
                    .append(",\"slotTypes\":").append(IntrospectJson.toJsonStringArray(slotTypes))
                    .append(",\"hotSwappable\":").append(IntrospectReflect.readBooleanProperty(descriptor, "isHotSwappable", false) ? "true" : "false")
                    .append('}')
                    .toString()
            );
        }
    }

    static boolean appendConnectionCatalog(List<String> connections) throws Exception {
        Class<?> enumClass = Class.forName("com.cisco.pt.ipc.enums.ConnectType");
        Object[] values = (Object[]) enumClass.getMethod("values").invoke(null);

        for (int index = 0; index < values.length; index++) {
            String typeId = String.valueOf(values[index]);

            if ("LAST_TYPE".equals(typeId)) {
                continue;
            }

            connections.add(
                new StringBuilder()
                    .append('{')
                    .append("\"connectionType\":").append(IntrospectJson.jsonQuoted(typeId))
                    .append(",\"displayName\":").append(IntrospectJson.jsonQuoted(IntrospectJson.humanizeEnumName(typeId)))
                    .append('}')
                    .toString()
            );
        }

        return true;
    }


    static void incrementCategory(
        List<ComponentCategorySummary> categories,
        ComponentCategory category,
        String typeId
    ) {
        for (int index = 0; index < categories.size(); index++) {
            ComponentCategorySummary existing = categories.get(index);

            if (existing.id.equals(category.id)) {
                existing.deviceCount += 1;

                if (!typeId.isEmpty() && existing.deviceTypes.indexOf(typeId) < 0) {
                    existing.deviceTypes.add(typeId);
                }

                return;
            }
        }

        ComponentCategorySummary created = new ComponentCategorySummary(category.id, category.displayName);
        created.deviceCount = 1;

        if (!typeId.isEmpty()) {
            created.deviceTypes.add(typeId);
        }

        categories.add(created);
    }



    static ComponentCategory categorizeDeviceType(String typeId) {
        String normalized = typeId == null ? "" : typeId.trim().toUpperCase(Locale.US);

        if (normalized.indexOf("ROUTER") >= 0 ||
            normalized.indexOf("SWITCH") >= 0 ||
            normalized.indexOf("BRIDGE") >= 0 ||
            normalized.indexOf("REPEATER") >= 0 ||
            normalized.indexOf("HUB") >= 0 ||
            normalized.indexOf("CONTROLLER") >= 0 ||
            normalized.indexOf("PATCH_PANEL") >= 0 ||
            normalized.indexOf("WALL_MOUNT") >= 0 ||
            normalized.indexOf("DISTRIBUTION") >= 0) {
            return new ComponentCategory("network_devices", "Network Devices");
        }

        if (normalized.indexOf("WIRELESS") >= 0 ||
            normalized.indexOf("ACCESS_POINT") >= 0 ||
            normalized.indexOf("CELL_TOWER") >= 0) {
            return new ComponentCategory("wireless_devices", "Wireless Devices");
        }

        if (normalized.indexOf("SECURITY") >= 0 || normalized.indexOf("ASA") >= 0 || normalized.indexOf("SNIFFER") >= 0) {
            return new ComponentCategory("security", "Security");
        }

        if (normalized.indexOf("IO_E") >= 0 ||
            normalized.indexOf("THING") >= 0 ||
            normalized.indexOf("MCU") >= 0 ||
            normalized.indexOf("PLC") >= 0 ||
            normalized.indexOf("CYBER") >= 0 ||
            normalized.indexOf("HISTORIAN") >= 0) {
            return new ComponentCategory("iot", "IoT");
        }

        if (normalized.indexOf("PHONE") >= 0 || normalized.indexOf("VOIP") >= 0 || normalized.indexOf("SBC") >= 0) {
            return new ComponentCategory("collaboration", "Collaboration");
        }

        if (normalized.indexOf("PC") >= 0 ||
            normalized.indexOf("SERVER") >= 0 ||
            normalized.indexOf("PRINTER") >= 0 ||
            normalized.indexOf("LAPTOP") >= 0 ||
            normalized.indexOf("TABLET") >= 0 ||
            normalized.indexOf("PDA") >= 0 ||
            normalized.indexOf("TV") >= 0 ||
            normalized.indexOf("MODEM") >= 0 ||
            normalized.indexOf("END_DEVICE") >= 0) {
            return new ComponentCategory("end_devices", "End Devices");
        }

        if (normalized.indexOf("CLOUD") >= 0 || normalized.indexOf("MULTI_USER") >= 0 || normalized.indexOf("REMOTE_NETWORK") >= 0) {
            return new ComponentCategory("wan_emulation", "WAN Emulation");
        }

        return new ComponentCategory("miscellaneous", "Miscellaneous");
    }


    static String normalizeComponentCategoryFilter(String rawFilterPayload) {
        if (rawFilterPayload == null) {
            return "";
        }

        String trimmed = rawFilterPayload.trim();

        if (trimmed.isEmpty()) {
            return "";
        }

        String normalized = trimmed.toLowerCase(Locale.US).replace('-', '_').replace(' ', '_');

        if ("all".equals(normalized)) {
            return "";
        }

        if ("network".equals(normalized) || "network_device".equals(normalized) || "network_devices".equals(normalized)) {
            return "network_devices";
        }

        if ("wireless".equals(normalized) || "wireless_device".equals(normalized) || "wireless_devices".equals(normalized)) {
            return "wireless_devices";
        }

        if ("security".equals(normalized)) {
            return "security";
        }

        if ("iot".equals(normalized) || "io_t".equals(normalized)) {
            return "iot";
        }

        if ("collaboration".equals(normalized) || "voice".equals(normalized)) {
            return "collaboration";
        }

        if ("end".equals(normalized) || "end_device".equals(normalized) || "end_devices".equals(normalized)) {
            return "end_devices";
        }

        if ("wan".equals(normalized) || "wan_emulation".equals(normalized)) {
            return "wan_emulation";
        }

        if ("misc".equals(normalized) || "miscellaneous".equals(normalized)) {
            return "miscellaneous";
        }

        return normalized;
    }

    static boolean isUnknownComponentCategoryFilter(String rawFilterPayload, String normalizedFilter) {
        if (rawFilterPayload == null || rawFilterPayload.trim().isEmpty()) {
            return false;
        }

        if (normalizedFilter.isEmpty()) {
            return false;
        }

        return IntrospectLogic.supportedComponentCategoryIds().indexOf(normalizedFilter) < 0;
    }

    static List<String> supportedComponentCategoryIds() {
        List<String> categories = new ArrayList<String>();
        categories.add("network_devices");
        categories.add("wireless_devices");
        categories.add("security");
        categories.add("iot");
        categories.add("collaboration");
        categories.add("end_devices");
        categories.add("wan_emulation");
        categories.add("miscellaneous");
        return categories;
    }

    static LinkEndpoints resolveLinkEndpoints(Object network, Object link) {
        return resolveLinkEndpoints(network, link, new LinkResolutionContext(new HashSet<String>(), new HashMap<Integer, Integer>()));
    }

    static List<LinkEndpoints> derivePortStateLinkEndpoints(Object network, int expectedLinkCount) throws Exception {
        Method getDeviceCountMethod = network.getClass().getMethod("getDeviceCount");
        Method getDeviceAtMethod = network.getClass().getMethod("getDeviceAt", Integer.TYPE);
        int deviceCount = ((Number) getDeviceCountMethod.invoke(network)).intValue();
        List<DevicePortBucket> buckets = new ArrayList<DevicePortBucket>();
        int linkedPortCount = 0;

        for (int deviceIndex = 0; deviceIndex < deviceCount; deviceIndex++) {
            Object device = getDeviceAtMethod.invoke(network, Integer.valueOf(deviceIndex));
            String deviceName = IntrospectReflect.readStringProperty(device, "getName");
            String deviceType = IntrospectReflect.readStringProperty(device, "getType");
            List<Object> ports = IntrospectLogic.readPortObjects(device);
            List<PortCandidate> linkedPorts = new ArrayList<PortCandidate>();

            for (int portIndex = 0; portIndex < ports.size(); portIndex++) {
                Object port = ports.get(portIndex);
                if (!IntrospectReflect.readBooleanProperty(port, "getLink", true)) {
                    continue;
                }

                PortIdentity identity = readPortIdentity(port);
                linkedPorts.add(new PortCandidate(deviceIndex, deviceName, deviceType, portIndex, preferredPortName(identity), 0));
                linkedPortCount++;
            }

            if (!linkedPorts.isEmpty()) {
                buckets.add(new DevicePortBucket(deviceIndex, deviceName, deviceType, linkedPorts));
            }
        }

        int targetEdgeCount = Math.min(expectedLinkCount, linkedPortCount / 2);
        List<LinkEndpoints> edges = new ArrayList<LinkEndpoints>();

        while (edges.size() < targetEdgeCount) {
            DevicePortBucket source = selectBucketWithMostPorts(buckets, -1);

            if (source == null) {
                break;
            }

            PortCandidate left = source.takeFirst();
            DevicePortBucket target = selectBucketWithMostPorts(buckets, source.deviceIndex);

            if (left == null || target == null) {
                break;
            }

            PortCandidate right = target.takeFirst();

            if (right == null) {
                break;
            }

            edges.add(new LinkEndpoints(
                new LinkEndpoint(true, left.deviceIndex, left.deviceName, left.deviceType, left.portIndex, left.portName),
                new LinkEndpoint(true, right.deviceIndex, right.deviceName, right.deviceType, right.portIndex, right.portName)
            ));
        }

        return edges;
    }

    static boolean shouldUsePortStateGraph(Object network, int expectedLinkCount) throws Exception {
        Method getDeviceCountMethod = network.getClass().getMethod("getDeviceCount");
        Method getDeviceAtMethod = network.getClass().getMethod("getDeviceAt", Integer.TYPE);
        int deviceCount = ((Number) getDeviceCountMethod.invoke(network)).intValue();
        int linkedPortCount = 0;

        for (int deviceIndex = 0; deviceIndex < deviceCount; deviceIndex++) {
            Object device = getDeviceAtMethod.invoke(network, Integer.valueOf(deviceIndex));
            String deviceType = IntrospectReflect.readStringProperty(device, "getType");
            List<Object> ports = IntrospectLogic.readPortObjects(device);

            for (Object port : ports) {
                if (!IntrospectReflect.readBooleanProperty(port, "getLink", true)) {
                    continue;
                }

                linkedPortCount++;

                if (!"ROUTER".equals(deviceType)) {
                    return false;
                }
            }
        }

        return linkedPortCount == expectedLinkCount * 2;
    }

    static DevicePortBucket selectBucketWithMostPorts(List<DevicePortBucket> buckets, int disallowedDeviceIndex) {
        DevicePortBucket best = null;

        for (DevicePortBucket bucket : buckets) {
            if (bucket.deviceIndex == disallowedDeviceIndex || bucket.remaining() <= 0) {
                continue;
            }

            if (best == null || bucket.remaining() > best.remaining()) {
                best = bucket;
            }
        }

        return best;
    }

    static List<LinkEndpoints> resolveAllLinkEndpoints(Object network) throws Exception {
        Method getLinkCountMethod = network.getClass().getMethod("getLinkCount");
        Method getLinkAtMethod = network.getClass().getMethod("getLinkAt", Integer.TYPE);
        int linkCount = ((Number) getLinkCountMethod.invoke(network)).intValue();
        List<LinkEndpoints> results = new ArrayList<LinkEndpoints>();
        LinkResolutionContext context = new LinkResolutionContext(new HashSet<String>(), new HashMap<Integer, Integer>());

        for (int linkIndex = 0; linkIndex < linkCount; linkIndex++) {
            Object link = getLinkAtMethod.invoke(network, Integer.valueOf(linkIndex));
            LinkEndpoints endpoints = resolveLinkEndpoints(network, link, context);
            results.add(endpoints);

            if (endpoints.left.observed) {
                context.usedPortKeys.add(linkEndpointKey(endpoints.left));
                incrementDeviceUsage(context.deviceUsageCounts, endpoints.left.deviceIndex);
            }

            if (endpoints.right.observed) {
                context.usedPortKeys.add(linkEndpointKey(endpoints.right));
                incrementDeviceUsage(context.deviceUsageCounts, endpoints.right.deviceIndex);
            }
        }

        return results;
    }

    static LinkEndpoints resolveLinkEndpoints(Object network, Object link, LinkResolutionContext context) {
        Object leftPort = IntrospectReflect.readObjectProperty(link, "getPort1");
        Object rightPort = IntrospectReflect.readObjectProperty(link, "getPort2");
        PortIdentity leftIdentity = readPortIdentity(leftPort);
        PortIdentity rightIdentity = readPortIdentity(rightPort);
        PortCandidate leftCandidate = findBestPortCandidate(network, leftPort, leftIdentity, null);
        PortCandidate rightCandidate = findBestPortCandidate(network, rightPort, rightIdentity, leftCandidate);

        if (rightCandidate == null && leftCandidate != null) {
            rightCandidate = findBestPortCandidate(network, rightPort, rightIdentity, null);
        }

        if (leftCandidate == null && rightCandidate != null) {
            leftCandidate = findBestPortCandidate(network, leftPort, leftIdentity, rightCandidate);
        }

        if (leftCandidate != null && context.usedPortKeys.contains(portCandidateKey(leftCandidate))) {
            PortCandidate alternativeLeft = findLinkedAlternativePortCandidate(network, leftIdentity, rightCandidate, leftCandidate, context);
            if (alternativeLeft != null) {
                leftCandidate = alternativeLeft;
            }
        }

        if (rightCandidate != null && context.usedPortKeys.contains(portCandidateKey(rightCandidate))) {
            PortCandidate alternativeRight = findLinkedAlternativePortCandidate(network, rightIdentity, leftCandidate, rightCandidate, context);
            if (alternativeRight != null) {
                rightCandidate = alternativeRight;
            }
        }

        if (leftCandidate != null && rightCandidate != null && leftCandidate.deviceIndex == rightCandidate.deviceIndex) {
            PortCandidate alternativeRight = findLinkedAlternativePortCandidate(network, rightIdentity, leftCandidate, rightCandidate, context);

            if (alternativeRight != null && alternativeRight.deviceIndex != leftCandidate.deviceIndex) {
                rightCandidate = alternativeRight;
            } else {
                PortCandidate alternativeLeft = findLinkedAlternativePortCandidate(network, leftIdentity, rightCandidate, leftCandidate, context);

                if (alternativeLeft != null && alternativeLeft.deviceIndex != rightCandidate.deviceIndex) {
                    leftCandidate = alternativeLeft;
                }
            }
        }

        return new LinkEndpoints(
            toLinkEndpoint(leftCandidate, leftIdentity),
            toLinkEndpoint(rightCandidate, rightIdentity)
        );
    }

    static PortCandidate findBestPortCandidate(
        Object network,
        Object targetPort,
        PortIdentity targetIdentity,
        PortCandidate disallowedCandidate
    ) {
        try {
            Method getDeviceCountMethod = network.getClass().getMethod("getDeviceCount");
            Method getDeviceAtMethod = network.getClass().getMethod("getDeviceAt", Integer.TYPE);
            int deviceCount = ((Number) getDeviceCountMethod.invoke(network)).intValue();
            List<PortCandidate> exactCandidates = new ArrayList<PortCandidate>();
            PortCandidate bestCandidate = null;
            int bestScore = Integer.MIN_VALUE;

            for (int deviceIndex = 0; deviceIndex < deviceCount; deviceIndex++) {
                Object device = getDeviceAtMethod.invoke(network, Integer.valueOf(deviceIndex));
                String deviceName = IntrospectReflect.readStringProperty(device, "getName");
                String deviceType = IntrospectReflect.readStringProperty(device, "getType");

                if (!targetIdentity.deviceName.isEmpty() && !targetIdentity.deviceName.equals(deviceName)) {
                    continue;
                }

                List<Object> ports = IntrospectLogic.readPortObjects(device);

                for (int portIndex = 0; portIndex < ports.size(); portIndex++) {
                    Object candidatePort = ports.get(portIndex);
                    PortIdentity candidateIdentity = readPortIdentity(candidatePort);

                    if (strictPortIdentityMatches(candidateIdentity, targetIdentity)) {
                        if (disallowedCandidate == null || !matchesCandidate(deviceIndex, portIndex, disallowedCandidate)) {
                            exactCandidates.add(new PortCandidate(
                                deviceIndex,
                                deviceName,
                                deviceType,
                                portIndex,
                                preferredPortName(candidateIdentity),
                                100000
                            ));
                        }
                        continue;
                    }

                    int score = scorePortMatch(candidatePort, targetPort, candidateIdentity, targetIdentity, deviceName, deviceType);

                    if (disallowedCandidate != null && matchesCandidate(deviceIndex, portIndex, disallowedCandidate)) {
                        score -= 10000;
                    }

                    if (score > bestScore) {
                        bestScore = score;
                        bestCandidate = new PortCandidate(
                            deviceIndex,
                            deviceName,
                            deviceType,
                            portIndex,
                            preferredPortName(candidateIdentity),
                            score
                        );
                    }
                }
            }

            if (exactCandidates.size() == 1) {
                return exactCandidates.get(0);
            }

            if (exactCandidates.size() > 1) {
                return null;
            }

            if (bestCandidate != null && bestScore > 0) {
                return bestCandidate;
            }
        } catch (Throwable throwable) {
            return null;
        }

        return null;
    }

    static Object readAvailableDeviceDescriptor(
        Object deviceFactory,
        Method directMethod,
        int index
    ) throws Exception {
        return readAvailableDescriptor(
            deviceFactory,
            directMethod,
            Integer.valueOf(index),
            "com.cisco.pt.ipc.sim.DeviceFactory",
            "getAvailableDeviceAt"
        );
    }

    static Object readAvailableModuleDescriptor(
        Object moduleFactory,
        Method directMethod,
        int index
    ) throws Exception {
        return readAvailableDescriptor(
            moduleFactory,
            directMethod,
            Integer.valueOf(index),
            "com.cisco.pt.ipc.sim.ModuleFactory",
            "getAvailableModuleAt"
        );
    }

    static Object readAvailableDescriptor(
        Object factory,
        Method directMethod,
        Integer index,
        String factoryInterfaceClassName,
        String helperMethodName
    ) throws Exception {
        try {
            return directMethod.invoke(factory, index);
        } catch (Throwable directFailure) {
            DescriptorHelperResult helperResult = readAvailableDescriptorViaIpcFactory(
                factory,
                index,
                factoryInterfaceClassName,
                helperMethodName
            );

            if (helperResult.value != null) {
                return helperResult.value;
            }

            throw IntrospectReflect.debugFailure(
                helperMethodName + "(" + index + ") via " + factoryInterfaceClassName,
                combineDescriptorFailures(directFailure, helperResult.failure)
            );

        }
    }

    static DescriptorHelperResult readAvailableDescriptorViaIpcFactory(
        Object factory,
        Integer index,
        String factoryInterfaceClassName,
        String helperMethodName
    ) {
        if (factory == null) {
            return new DescriptorHelperResult(null, null);
        }

        try {
            Class<?> ipcFactoryClass = Class.forName("com.cisco.pt.ipc.IPCFactory");
            Class<?> factoryInterfaceClass = Class.forName(factoryInterfaceClassName);
            Method helperMethod = ipcFactoryClass.getMethod(helperMethodName, factoryInterfaceClass, Integer.TYPE);
            return new DescriptorHelperResult(helperMethod.invoke(null, factory, Integer.valueOf(index.intValue())), null);
        } catch (Throwable throwable) {
            return new DescriptorHelperResult(null, throwable);
        }
    }

    static Throwable combineDescriptorFailures(Throwable directFailure, Throwable helperFailure) {
        if (helperFailure == null) {
            return directFailure;
        }

        return new RuntimeException(
            "descriptor-direct=" + ThrowableUtils.describeThrowable(directFailure) +
            "; descriptor-helper=" + ThrowableUtils.describeThrowable(helperFailure),
            directFailure
        );
    }

    static DeviceSelection resolveDeviceSelection(Object network, String selector) throws Exception {
        Method getDeviceCountMethod = network.getClass().getMethod("getDeviceCount");
        Method getDeviceAtMethod = network.getClass().getMethod("getDeviceAt", Integer.TYPE);
        int deviceCount = ((Number) getDeviceCountMethod.invoke(network)).intValue();
        Integer requestedIndex = IntrospectReflect.parseSelectorIndex(selector);
        DeviceSelection caseInsensitiveFallback = null;

        for (int deviceIndex = 0; deviceIndex < deviceCount; deviceIndex++) {
            Object device = getDeviceAtMethod.invoke(network, Integer.valueOf(deviceIndex));
            String deviceName = IntrospectReflect.readStringProperty(device, "getName");
            String deviceType = IntrospectReflect.readStringProperty(device, "getType");

            if (requestedIndex != null && requestedIndex.intValue() == deviceIndex) {
                return new DeviceSelection(deviceIndex, device, deviceName, deviceType, IntrospectLogic.readPortObjects(device));
            }

            if (deviceName.equals(selector)) {
                return new DeviceSelection(deviceIndex, device, deviceName, deviceType, IntrospectLogic.readPortObjects(device));
            }

            if (caseInsensitiveFallback == null && deviceName.equalsIgnoreCase(selector)) {
                caseInsensitiveFallback = new DeviceSelection(deviceIndex, device, deviceName, deviceType, IntrospectLogic.readPortObjects(device));
            }
        }

        return caseInsensitiveFallback;
    }

    static PortSelection resolvePortSelection(DeviceSelection deviceSelection, String selector) {
        if (deviceSelection == null || selector == null) {
            return null;
        }

        String normalizedSelector = selector.trim();

        if (normalizedSelector.isEmpty()) {
            return null;
        }

        Integer requestedIndex = IntrospectReflect.parseSelectorIndex(normalizedSelector);
        PortSelection caseInsensitiveFallback = null;

        for (int portIndex = 0; portIndex < deviceSelection.ports.size(); portIndex++) {
            Object port = deviceSelection.ports.get(portIndex);
            PortIdentity identity = readPortIdentity(port);
            String canonicalName = preferredPortName(identity);

            if (requestedIndex != null && requestedIndex.intValue() == portIndex) {
                return new PortSelection(portIndex, canonicalName, port, identity);
            }

            if (matchesPortSelector(identity, normalizedSelector, false)) {
                return new PortSelection(portIndex, canonicalName, port, identity);
            }

            if (caseInsensitiveFallback == null && matchesPortSelector(identity, normalizedSelector, true)) {
                caseInsensitiveFallback = new PortSelection(portIndex, canonicalName, port, identity);
            }
        }

        return caseInsensitiveFallback;
    }

    static boolean matchesPortSelector(PortIdentity identity, String selector, boolean caseInsensitive) {
        return stringMatches(identity.primaryName, selector, caseInsensitive)
            || stringMatches(identity.secondaryName, selector, caseInsensitive)
            || stringMatches(identity.tertiaryName, selector, caseInsensitive)
            || stringMatches(identity.portNameNumber, selector, caseInsensitive);
    }

    static boolean stringMatches(String value, String selector, boolean caseInsensitive) {
        if (value == null || value.isEmpty()) {
            return false;
        }

        return caseInsensitive ? value.equalsIgnoreCase(selector) : value.equals(selector);
    }

    static ConnectRequest parseConnectRequest(String payload) {
        if (payload == null) {
            return null;
        }

        String[] parts = payload.split("\\|", -1);

        if (parts.length < 4) {
            return null;
        }

        String leftDeviceSelector = parts[0].trim();
        String leftPortSelector = parts[1].trim();
        String rightDeviceSelector = parts[2].trim();
        String rightPortSelector = parts[3].trim();
        String connectionType = parts.length >= 5 && !parts[4].trim().isEmpty() ? parts[4].trim().toUpperCase(Locale.ROOT) : "AUTO";

        if (leftDeviceSelector.isEmpty() || leftPortSelector.isEmpty() || rightDeviceSelector.isEmpty() || rightPortSelector.isEmpty()) {
            return null;
        }

        return new ConnectRequest(leftDeviceSelector, leftPortSelector, rightDeviceSelector, rightPortSelector, connectionType);
    }

    static InterfaceIpRequest parseInterfaceIpRequest(String payload) {
        if (payload == null) {
            return null;
        }

        String[] parts = payload.split("\\|", -1);

        if (parts.length < 4) {
            return null;
        }

        String deviceSelector = parts[0].trim();
        String portSelector = parts[1].trim();
        String ipAddress = parts[2].trim();
        String subnetMask = parts[3].trim();

        if (deviceSelector.isEmpty() || portSelector.isEmpty() || ipAddress.isEmpty() || subnetMask.isEmpty()) {
            return null;
        }

        return new InterfaceIpRequest(deviceSelector, portSelector, ipAddress, subnetMask);
    }

    static PortPowerStateRequest parsePortPowerStateRequest(String payload) {
        if (payload == null) {
            return null;
        }

        String[] parts = payload.split("\\|", -1);

        if (parts.length < 2) {
            return null;
        }

        String deviceSelector = parts[0].trim();
        String portSelector = parts[1].trim();
        boolean powerOn = true;

        if (parts.length >= 3 && !parts[2].trim().isEmpty()) {
            String rawState = parts[2].trim().toLowerCase(Locale.ROOT);

            if ("on".equals(rawState) || "true".equals(rawState) || "up".equals(rawState)) {
                powerOn = true;
            } else if ("off".equals(rawState) || "false".equals(rawState) || "down".equals(rawState)) {
                powerOn = false;
            } else {
                return null;
            }
        }

        if (deviceSelector.isEmpty() || portSelector.isEmpty()) {
            return null;
        }

        return new PortPowerStateRequest(deviceSelector, portSelector, powerOn);
    }

    static AddDeviceRequest parseAddDeviceRequest(String payload) {
        if (payload == null) {
            return null;
        }

        String[] parts = payload.split("\\|", -1);

        if (parts.length < 4) {
            return null;
        }

        String deviceType = parts[0].trim().toUpperCase(Locale.ROOT);
        String model = parts[1].trim();

        try {
            double x = Double.parseDouble(parts[2].trim());
            double y = Double.parseDouble(parts[3].trim());

            if (deviceType.isEmpty() || model.isEmpty()) {
                return null;
            }

            return new AddDeviceRequest(deviceType, model, x, y);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    static ModuleMutationRequest parseModuleMutationRequest(String payload, boolean requireModel) {
        if (payload == null) {
            return null;
        }

        String[] parts = payload.split("\\|", -1);

        if (parts.length < (requireModel ? 4 : 3)) {
            return null;
        }

        String deviceSelector = parts[0].trim();
        String slot = parts[1].trim();
        String moduleType = parts[2].trim();
        String model = requireModel ? parts[3].trim() : "";

        if (deviceSelector.isEmpty() || slot.isEmpty() || moduleType.isEmpty() || (requireModel && model.isEmpty())) {
            return null;
        }

        return new ModuleMutationRequest(deviceSelector, slot, moduleType, model);
    }

    static ModuleAtRequest parseModuleAtRequest(String payload) {
        if (payload == null) {
            return null;
        }

        String[] parts = payload.split("\\|", -1);

        if (parts.length < 4) {
            return null;
        }

        String deviceSelector = parts[0].trim();
        String parentModulePath = parts[1].trim();
        String slotIndexText = parts[2].trim();
        String model = parts[3].trim();

        if (deviceSelector.isEmpty() || slotIndexText.isEmpty() || model.isEmpty()) {
            return null;
        }

        try {
            return new ModuleAtRequest(deviceSelector, parentModulePath, Integer.parseInt(slotIndexText), model);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    static DefaultGatewayRequest parseDefaultGatewayRequest(String payload) {
        if (payload == null) {
            return null;
        }

        String[] parts = payload.split("\\|", -1);

        if (parts.length < 2) {
            return null;
        }

        String deviceSelector = parts[0].trim();
        String gatewayIpAddress = parts[1].trim();

        if (deviceSelector.isEmpty() || gatewayIpAddress.isEmpty()) {
            return null;
        }

        return new DefaultGatewayRequest(deviceSelector, gatewayIpAddress);
    }

    static StaticRouteRequest parseStaticRouteRequest(String payload) {
        if (payload == null) {
            return null;
        }

        String[] parts = payload.split("\\|", -1);

        if (parts.length < 4) {
            return null;
        }

        String deviceSelector = parts[0].trim();
        String networkIpAddress = parts[1].trim();
        String subnetMask = parts[2].trim();
        String nextHopIpAddress = parts[3].trim();
        String portSelector = parts.length >= 5 ? parts[4].trim() : "";
        int adminDistance = 1;

        if (parts.length >= 6 && !parts[5].trim().isEmpty()) {
            try {
                adminDistance = Integer.parseInt(parts[5].trim());
            } catch (NumberFormatException exception) {
                return null;
            }
        }

        if (deviceSelector.isEmpty() || networkIpAddress.isEmpty() || subnetMask.isEmpty() || nextHopIpAddress.isEmpty()) {
            return null;
        }

        return new StaticRouteRequest(deviceSelector, networkIpAddress, subnetMask, nextHopIpAddress, portSelector, adminDistance);
    }

    static DeviceCliRequest parseDeviceCliRequest(String payload) {
        if (payload == null) {
            return null;
        }

        String[] parts = payload.split("\\|", 3);

        if (parts.length < 2) {
            return null;
        }

        String deviceSelector = parts[0].trim();
        String mode = parts.length >= 3 ? parts[1].trim().toLowerCase(Locale.ROOT) : "user";
        String command = parts.length >= 3 ? IntrospectJson.decodeCliCommand(parts[2].trim()) : IntrospectJson.decodeCliCommand(parts[1].trim());

        if (deviceSelector.isEmpty() || command.isEmpty()) {
            return null;
        }

        if (mode.isEmpty()) {
            mode = "user";
        }

        return new DeviceCliRequest(deviceSelector, mode, command);
    }

    static PingRequest parsePingRequest(String payload) {
        if (payload == null) {
            return null;
        }

        String[] parts = payload.split("\\|", -1);

        if (parts.length < 2) {
            return null;
        }

        String deviceSelector = parts[0].trim();
        String destinationIpAddress = parts[1].trim();
        int repeatCount = 4;
        int timeoutSeconds = 1;
        int packetSize = 32;
        String sourcePortName = parts.length >= 6 ? parts[5].trim() : "";

        try {
            if (parts.length >= 3 && !parts[2].trim().isEmpty()) {
                repeatCount = Integer.parseInt(parts[2].trim());
            }

            if (parts.length >= 4 && !parts[3].trim().isEmpty()) {
                timeoutSeconds = Integer.parseInt(parts[3].trim());
            }

            if (parts.length >= 5 && !parts[4].trim().isEmpty()) {
                packetSize = Integer.parseInt(parts[4].trim());
            }
        } catch (NumberFormatException exception) {
            return null;
        }

        if (deviceSelector.isEmpty() || destinationIpAddress.isEmpty() || repeatCount <= 0 || timeoutSeconds <= 0 || packetSize <= 0) {
            return null;
        }

        return new PingRequest(deviceSelector, destinationIpAddress, repeatCount, timeoutSeconds, packetSize, sourcePortName);
    }


    static String tryApplyDefaultGateway(DeviceSelection device, Object gateway) {
        IntrospectReflect.invokeMethodIfPresent(device.device, "setDhcpFlag", Boolean.FALSE);

        if (IntrospectReflect.invokeMethodIfPresent(device.device, "setDefaultGateway", gateway)) {
            return "device";
        }

        Object hostIp = IntrospectReflect.readProcessObject(device.device, "HostIpProcess", "HostIp");

        if (IntrospectReflect.invokeMethodIfPresent(hostIp, "setDefaultGateway", gateway)) {
            return "host-ip-process";
        }

        for (Object port : device.ports) {
            IntrospectReflect.invokeMethodIfPresent(port, "setDhcpClientFlag", Boolean.FALSE);

            if (IntrospectReflect.invokeMethodIfPresent(port, "setDefaultGateway", gateway)) {
                return "port:" + preferredPortName(readPortIdentity(port));
            }
        }

        return null;
    }

    static RouteExecutionResult tryAddStaticRoute(
        DeviceSelection device,
        StaticRouteRequest request,
        Object networkIp,
        Object subnetMask,
        Object nextHop
    ) {
        Object routingProcess = IntrospectReflect.readProcessObject(device.device, "RoutingProcess", "Routing");

        if (routingProcess != null) {
            try {
                Method addStaticRouteMethod = IntrospectReflect.findMethodByName(routingProcess.getClass(), "addStaticRoute", 5);

                if (addStaticRouteMethod != null) {
                    Object result = addStaticRouteMethod.invoke(
                        routingProcess,
                        networkIp,
                        subnetMask,
                        nextHop,
                        request.portSelector,
                        Integer.valueOf(request.adminDistance)
                    );
                    boolean ok = !(result instanceof Boolean) || ((Boolean) result).booleanValue();
                    return new RouteExecutionResult(true, ok, "routing-process", result == null ? "" : String.valueOf(result), request.portSelector);
                }
            } catch (Throwable throwable) {
                return new RouteExecutionResult(true, false, "routing-process", throwable.getClass().getName() + ":" + ThrowableUtils.safeMessage(throwable), request.portSelector);
            }
        }

        CliExecutionResult cliExecution = IntrospectLogic.executeDeviceCli(device.device, "global", IntrospectLogic.buildStaticRouteCommand(request));

        if (cliExecution.observed) {
            return new RouteExecutionResult(true, cliExecution.ok, cliExecution.transport, cliExecution.response, "");
        }

        return new RouteExecutionResult(false, false, "", "", "");
    }

    static String buildStaticRouteCommand(StaticRouteRequest request) {
        StringBuilder command = new StringBuilder();
        command.append("ip route ")
            .append(request.networkIpAddress)
            .append(' ')
            .append(request.subnetMask)
            .append(' ')
            .append(request.nextHopIpAddress);

        if (request.adminDistance > 0) {
            command.append(' ').append(request.adminDistance);
        }

        return command.toString();
    }

    static CliExecutionResult executeDeviceCli(Object device, String mode, String command) {
        if (device == null) {
            return new CliExecutionResult(false, false, "", "");
        }

        Object terminalLine = IntrospectReflect.readFirstNonNullObject(device, "getIpcTerminalLine", "getCommandLine", "getConsoleLine", "getCommandPrompt");
        String normalizedMode = mode == null ? "user" : mode.trim().toLowerCase(Locale.ROOT);

        if (normalizedMode.isEmpty()) {
            normalizedMode = "user";
        }

        if (shouldUseTerminalLineFlow(normalizedMode, command) && terminalLine != null) {
            return executeTerminalLineCli(terminalLine, normalizedMode, command);
        }

        try {
            Method enterCommandMethod = IntrospectReflect.findMethodByName(device.getClass(), "enterCommand", 2);

            if (enterCommandMethod != null) {
                String consoleOutputBefore = readConsoleOutputSnapshot(terminalLine);
                Object result = enterCommandMethod.invoke(device, command, normalizedMode);
                String consoleOutputAfter = readConsoleOutputSnapshot(terminalLine);
                return evaluateCiscoCommandResult(result, consoleOutputBefore, consoleOutputAfter);
            }
        } catch (Throwable throwable) {
            return new CliExecutionResult(true, false, "cisco-device", throwable.getClass().getName() + ":" + ThrowableUtils.safeMessage(throwable));
        }

        if (terminalLine == null) {
            return new CliExecutionResult(false, false, "", "");
        }

        return executeTerminalLineCli(terminalLine, normalizedMode, command);
    }

    static PingExecutionResult tryRunPing(DeviceSelection device, PingRequest request, Object destinationIp) {
        Object icmpProcess = IntrospectReflect.readProcessObject(device.device, "Icmp");

        if (icmpProcess != null) {
            try {
                Method startPingMethod = IntrospectReflect.findMethodByName(icmpProcess.getClass(), "startPing", 5);

                if (startPingMethod != null) {
                    Object pingId = startPingMethod.invoke(
                        icmpProcess,
                        destinationIp,
                        Integer.valueOf(request.timeoutSeconds),
                        Integer.valueOf(request.packetSize),
                        Integer.valueOf(request.repeatCount),
                        request.sourcePortName
                    );
                    Object pingProcess = readPingProcess(icmpProcess, pingId);

                    if (pingProcess != null) {
                        return observePingProcess(pingProcess, request.repeatCount, request.timeoutSeconds);
                    }

                    return new PingExecutionResult(true, true, "icmp-process", 0, 0, 0, 0, pingId == null ? "" : String.valueOf(pingId));
                }
            } catch (Throwable throwable) {
                return new PingExecutionResult(true, false, "icmp-process", 0, 0, 0, 0, throwable.getClass().getName() + ":" + ThrowableUtils.safeMessage(throwable));
            }
        }

        if (request.repeatCount != 4 || request.timeoutSeconds != 1 || request.packetSize != 32 || !request.sourcePortName.isEmpty()) {
            return new PingExecutionResult(
                true,
                false,
                "terminal-line",
                0,
                0,
                0,
                0,
                "terminal-line-ping-fallback-does-not-support-repeat-timeout-size-or-source-port-options"
            );
        }

        Object terminalLine = IntrospectReflect.readFirstNonNullObject(device.device, "getIpcTerminalLine", "getCommandLine", "getConsoleLine", "getCommandPrompt");
        CliExecutionResult cliExecution = terminalLine != null
            ? executeTerminalLineCli(terminalLine, "user", "ping " + request.destinationIpAddress)
            : IntrospectLogic.executeDeviceCli(device.device, "user", "ping " + request.destinationIpAddress);

        if (cliExecution.observed) {
            TerminalPingParseResult parsedPing = parseTerminalPingExecution(cliExecution.response);
            return new PingExecutionResult(
                true,
                parsedPing.ok,
                cliExecution.transport,
                parsedPing.sentCount,
                parsedPing.receivedCount,
                parsedPing.lastDelay,
                parsedPing.lastTtl,
                cliExecution.response
            );
        }

        return new PingExecutionResult(false, false, "", 0, 0, 0, 0, "");
    }

    static Object readPingProcess(Object icmpProcess, Object pingId) {
        if (icmpProcess == null || pingId == null) {
            return null;
        }

        try {
            Method getPingProcessMethod = IntrospectReflect.findMethodByName(icmpProcess.getClass(), "getPingProcess", 1);

            if (getPingProcessMethod == null) {
                return null;
            }

            return getPingProcessMethod.invoke(icmpProcess, pingId);
        } catch (Throwable throwable) {
            return null;
        }
    }

    static CliExecutionResult evaluateCiscoCommandResult(Object result, String consoleOutputBefore, String consoleOutputAfter) {
        String statusText = readCommandStatusText(result);
        String commandOutput = readCommandOutputText(result);
        String consoleOutputDelta = extractConsoleOutputDelta(consoleOutputBefore, consoleOutputAfter);
        String response = composeCliResponse(statusText, commandOutput, consoleOutputDelta, consoleOutputAfter, result);

        if (!statusText.isEmpty()) {
            String normalizedStatus = statusText.toUpperCase(Locale.ROOT);

            if (
                normalizedStatus.contains("FAIL") ||
                normalizedStatus.contains("ERROR") ||
                normalizedStatus.contains("INVALID")
            ) {
                return new CliExecutionResult(true, false, "cisco-device", response);
            }
        }

        return new CliExecutionResult(true, true, "cisco-device", response);
    }

    static String readCommandStatusText(Object result) {
        if (result == null) {
            return "";
        }

        Object status = IntrospectReflect.readFirstNonNullObject(result, "getFirst", "first", "getKey");

        if (status == null) {
            return "";
        }

        return String.valueOf(status);
    }

    static String readCommandOutputText(Object result) {
        if (result == null) {
            return "";
        }

        Object output = IntrospectReflect.readFirstNonNullObject(result, "getSecond", "second", "getValue");

        if (output == null) {
            return "";
        }

        return String.valueOf(output);
    }

    static String composeCliResponse(
        String statusText,
        String commandOutput,
        String consoleOutputDelta,
        String consoleOutputAfter,
        Object rawResult
    ) {
        if (!statusText.isEmpty() && !commandOutput.isEmpty()) {
            return statusText + ": " + commandOutput;
        }

        if (!commandOutput.isEmpty()) {
            return commandOutput;
        }

        if (!consoleOutputDelta.isEmpty()) {
            return consoleOutputDelta;
        }

        if (!consoleOutputAfter.isEmpty()) {
            return consoleOutputAfter;
        }

        if (!statusText.isEmpty()) {
            return statusText;
        }

        return rawResult == null ? "" : String.valueOf(rawResult);
    }

    static boolean shouldUseTerminalLineFlow(String mode, String command) {
        if (command != null && command.contains("\n")) {
            return true;
        }

        if (command != null && command.trim().toLowerCase(Locale.ROOT).startsWith("ping ")) {
            return true;
        }

        return !"user".equals(mode) && !"enable".equals(mode) && !"global".equals(mode);
    }

    static CliExecutionResult executeTerminalLineCli(Object terminalLine, String mode, String command) {
        TerminalCommandObservation observation = IntrospectLogic.executeTerminalLineObservation(terminalLine, mode, command);
        return new CliExecutionResult(observation.observed, observation.ok, "terminal-line", observation.response);
    }

    static TerminalCommandObservation executeTerminalLineObservation(Object terminalLine, String mode, String command) {
        try {
            Method enterCommandMethod = IntrospectReflect.findMethodByName(terminalLine.getClass(), "enterCommand", 1);

            if (enterCommandMethod == null) {
                return TerminalCommandObservation.unavailable();
            }

            List<String> commands = normalizeTerminalCommands(mode, command);

            if (commands.isEmpty()) {
                return TerminalCommandObservation.empty();
            }

            String consoleOutputBefore = readConsoleOutputSnapshot(terminalLine);
            TerminalOutputCapture capture = startTerminalOutputCapture(terminalLine, commands.size());
            CommandLogCapture commandLogCapture = startCommandLogCapture();

            try {
                for (String commandLine : commands) {
                    enterCommandMethod.invoke(terminalLine, commandLine);
                    waitForTerminalCommandTurnaround(terminalLine, capture, 250L);
                }

                capture = awaitTerminalOutputCapture(capture, terminalLine);
                commandLogCapture = awaitCommandLogCapture(commandLogCapture);
                String consoleOutputAfter = readConsoleOutputSnapshot(terminalLine);
                return createTerminalCommandObservation(terminalLine, mode, command, commands, capture, commandLogCapture, consoleOutputBefore, consoleOutputAfter);
            } finally {
                stopTerminalOutputCapture(capture, terminalLine);
                stopCommandLogCapture(commandLogCapture);
            }
        } catch (Throwable throwable) {
            return TerminalCommandObservation.failed(throwable.getClass().getName() + ":" + ThrowableUtils.safeMessage(throwable));
        }
    }

    static void waitForTerminalCommandTurnaround(Object terminalLine, TerminalOutputCapture capture, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        int completedBefore = capture == null ? 0 : capture.completedCommands[0];
        String previousPrompt = terminalLine == null ? "" : IntrospectReflect.readStringProperty(terminalLine, "getPrompt");

        while (System.currentTimeMillis() < deadline) {
            if (capture != null && capture.completedCommands[0] > completedBefore) {
                return;
            }

            if (terminalLine != null) {
                String currentPrompt = IntrospectReflect.readStringProperty(terminalLine, "getPrompt");

                if (!currentPrompt.isEmpty() && !currentPrompt.equals(previousPrompt)) {
                    return;
                }
            }

            try {
                Thread.sleep(40L);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    static List<String> normalizeTerminalCommands(String mode, String command) {
        ArrayList<String> commands = new ArrayList<String>();

        if (!"user".equals(mode)) {
            commands.add("enable");
        }

        if ("global".equals(mode) || "config".equals(mode) || "global_configuration".equals(mode) || "interface_configuration".equals(mode)) {
            commands.add("configure terminal");
        }

        appendNormalizedCommandLines(commands, command);
        return commands;
    }

    static void appendNormalizedCommandLines(List<String> commands, String rawCommand) {
        if (rawCommand == null) {
            return;
        }

        String[] lines = rawCommand.split("\\r?\\n");

        for (String line : lines) {
            String trimmedLine = line.trim();

            if (!trimmedLine.isEmpty()) {
                commands.add(trimmedLine);
            }
        }
    }

    static String composeTerminalLineResponse(
        String currentMode,
        String prompt,
        List<String> commands,
        String output,
        String consoleOutputDelta,
        String consoleOutputSnapshot,
        CommandLogObservation commandLog
    ) {
        if (output != null && !output.trim().isEmpty()) {
            return output;
        }

        if (consoleOutputDelta != null && !consoleOutputDelta.trim().isEmpty()) {
            return consoleOutputDelta;
        }

        if (consoleOutputSnapshot != null && !consoleOutputSnapshot.trim().isEmpty()) {
            return consoleOutputSnapshot;
        }

        if (commandLog != null && commandLog.observed && commandLog.newEntryCount > 0) {
            String summary = commandLog.toSummaryText();

            if (!summary.isEmpty()) {
                return summary;
            }
        }

        StringBuilder response = new StringBuilder();
        response.append("commands=").append(commands.size());

        if (!currentMode.isEmpty()) {
            response.append(", mode=").append(currentMode);
        }

        if (!prompt.isEmpty()) {
            response.append(", prompt=").append(prompt);
        }

        return response.toString();
    }

    static boolean isSafeTranscriptProbeMode(String mode) {
        return "user".equals(mode) || "enable".equals(mode);
    }

    static TerminalCommandObservation createTerminalCommandObservation(
        Object terminalLine,
        String requestedMode,
        String requestedCommand,
        List<String> commands,
        TerminalOutputCapture capture,
        CommandLogCapture commandLogCapture,
        String consoleOutputBefore,
        String consoleOutputAfter
    ) {
        String prompt = IntrospectReflect.readStringProperty(terminalLine, "getPrompt");
        String currentMode = IntrospectReflect.readStringProperty(terminalLine, "getMode");
        String commandInput = IntrospectReflect.readStringProperty(terminalLine, "getCommandInput");
        int historySize = IntrospectReflect.readIntProperty(terminalLine, "getHistorySize");
        String eventOutput = capture.output.toString();
        String consoleOutputSnapshot = consoleOutputAfter == null ? "" : consoleOutputAfter;
        String consoleOutputDelta = extractConsoleOutputDelta(consoleOutputBefore, consoleOutputAfter);
        String output = mergeObservedTerminalOutput(eventOutput, consoleOutputDelta, consoleOutputSnapshot);
        CommandLogObservation commandLogObservation = buildCommandLogObservation(commandLogCapture);
        String response = composeTerminalLineResponse(currentMode, prompt, commands, output, consoleOutputDelta, consoleOutputSnapshot, commandLogObservation);
        boolean transcriptObserved = output != null && !output.trim().isEmpty();
        boolean commandLifecycleObserved = capture.completedCommands[0] >= commands.size();
        boolean commandLogObserved = commandLogObservation.observed && commandLogObservation.newEntryCount > 0;
        return new TerminalCommandObservation(
            true,
            (transcriptObserved && commandLifecycleObserved) || commandLogObserved,
            requestedMode,
            requestedCommand,
            response,
            output,
            consoleOutputSnapshot,
            consoleOutputDelta,
            currentMode,
            prompt,
            commandInput,
            historySize,
            readCommandHistorySummary(terminalLine, "getCurrentHistory"),
            readCommandHistorySummary(terminalLine, "getUserHistory"),
            readCommandHistorySummary(terminalLine, "getConfigHistory"),
            commands.size(),
            capture.completedCommands[0],
            transcriptObserved,
            commandLifecycleObserved,
            commandLogObservation
        );
    }

    static String buildTerminalTranscriptJsonPayload(
        String deviceName,
        DeviceCliRequest request,
        TerminalCommandObservation observation
    ) {
        return new StringBuilder()
            .append('{')
            .append("\"observed\":").append(observation.observed ? "true" : "false")
            .append(",\"ok\":").append(observation.ok ? "true" : "false")
            .append(",\"device\":").append(IntrospectJson.jsonQuoted(deviceName))
            .append(",\"mode\":").append(IntrospectJson.jsonQuoted(request.mode))
            .append(",\"command\":").append(IntrospectJson.jsonQuoted(request.command))
            .append(",\"transport\":\"terminal-line\"")
            .append(",\"response\":").append(IntrospectJson.jsonQuoted(observation.response))
            .append(",\"rawOutput\":").append(IntrospectJson.jsonQuoted(observation.rawOutput))
            .append(",\"consoleOutputSnapshot\":").append(IntrospectJson.jsonQuoted(observation.consoleOutputSnapshot))
            .append(",\"consoleOutputDelta\":").append(IntrospectJson.jsonQuoted(observation.consoleOutputDelta))
            .append(",\"currentMode\":").append(IntrospectJson.jsonQuoted(observation.currentMode))
            .append(",\"prompt\":").append(IntrospectJson.jsonQuoted(observation.prompt))
            .append(",\"commandInput\":").append(IntrospectJson.jsonQuoted(observation.commandInput))
            .append(",\"historySize\":").append(observation.historySize)
            .append(",\"currentHistory\":").append(observation.currentHistory.toJson())
            .append(",\"userHistory\":").append(observation.userHistory.toJson())
            .append(",\"configHistory\":").append(observation.configHistory.toJson())
            .append(",\"expectedCommands\":").append(observation.expectedCommands)
            .append(",\"completedCommands\":").append(observation.completedCommands)
            .append(",\"transcriptObserved\":").append(observation.transcriptObserved ? "true" : "false")
            .append(",\"commandLifecycleObserved\":").append(observation.commandLifecycleObserved ? "true" : "false")
            .append(",\"commandLog\":").append(observation.commandLog.toJson())
            .append('}')
            .toString();
    }

    static CommandLogCapture startCommandLogCapture() {
        IPC ipc = AppRuntimeContext.getActiveIpc();

        if (ipc == null) {
            return CommandLogCapture.unavailable("ipc-unavailable");
        }

        Object commandLog = IntrospectReflect.readFirstNonNullObject(ipc, "commandLog");

        if (commandLog == null) {
            return CommandLogCapture.unavailable("command-log-unavailable");
        }

        Integer beforeCount = IntrospectReflect.readOptionalIntProperty(commandLog, "getEntryCount");

        if (beforeCount == null) {
            return CommandLogCapture.unavailable("command-log-entry-count-unavailable");
        }

        Method setEnabledMethod = IntrospectReflect.findMethod(commandLog.getClass(), "setEnabled", Boolean.TYPE);
        boolean enabledBefore = setEnabledMethod == null || IntrospectReflect.readBooleanProperty(commandLog, "isEnabled", false);
        boolean enableAttempted = false;
        String reason = "";

        if (!enabledBefore && setEnabledMethod != null) {
            try {
                setEnabledMethod.invoke(commandLog, Boolean.TRUE);
                enableAttempted = true;
            } catch (Throwable throwable) {
                reason = "command-log-enable-failed:" + ThrowableUtils.safeMessage(throwable);
            }
        }

        return new CommandLogCapture(commandLog, beforeCount.intValue(), enabledBefore, enableAttempted, reason);
    }

    static CommandLogCapture awaitCommandLogCapture(CommandLogCapture capture) {
        if (capture == null || !capture.active) {
            return capture;
        }

        int latestCount = capture.beforeCount;
        long lastCountChangeAt = System.currentTimeMillis();
        long deadline = lastCountChangeAt + 5000L;
        long quietPeriodMs = 350L;

        while (System.currentTimeMillis() < deadline) {
            Integer currentCount = IntrospectReflect.readOptionalIntProperty(capture.commandLog, "getEntryCount");

            if (currentCount != null && currentCount.intValue() != latestCount) {
                latestCount = currentCount.intValue();
                lastCountChangeAt = System.currentTimeMillis();
            }

            if (latestCount > capture.beforeCount && System.currentTimeMillis() - lastCountChangeAt >= quietPeriodMs) {
                break;
            }

            try {
                Thread.sleep(50L);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        return capture.withAfterCountAndEntries(latestCount, readCommandLogEntries(capture.commandLog, capture.beforeCount, latestCount));
    }

    static void stopCommandLogCapture(CommandLogCapture capture) {
        if (capture == null || !capture.active || capture.enabledBefore || !capture.enableAttempted) {
            return;
        }

        try {
            Method setEnabledMethod = IntrospectReflect.findMethod(capture.commandLog.getClass(), "setEnabled", Boolean.TYPE);

            if (setEnabledMethod != null) {
                setEnabledMethod.invoke(capture.commandLog, Boolean.FALSE);
            }
        } catch (Throwable throwable) {
            // best-effort cleanup only
        }
    }

    static List<CommandLogEntryObservation> readCommandLogEntries(Object commandLog, int beforeCount, int afterCount) {
        ArrayList<CommandLogEntryObservation> entries = new ArrayList<CommandLogEntryObservation>();

        if (commandLog == null || afterCount <= beforeCount) {
            return entries;
        }

        Method getEntryAtMethod = IntrospectReflect.findMethodByName(commandLog.getClass(), "getEntryAt", 1);

        if (getEntryAtMethod == null) {
            return entries;
        }

        for (int index = beforeCount; index < afterCount; index++) {
            try {
                Object entry = getEntryAtMethod.invoke(commandLog, Integer.valueOf(index));

                if (entry != null) {
                    entries.add(new CommandLogEntryObservation(
                        index,
                        IntrospectReflect.readStringProperty(entry, "getTimeToString"),
                        IntrospectReflect.readStringProperty(entry, "getDeviceName"),
                        IntrospectReflect.readStringProperty(entry, "getPrompt"),
                        IntrospectReflect.readStringProperty(entry, "getCommand"),
                        IntrospectReflect.readStringProperty(entry, "getResolvedCommand")
                    ));
                }
            } catch (Throwable throwable) {
                entries.add(CommandLogEntryObservation.failed(index, throwable.getClass().getName() + ":" + ThrowableUtils.safeMessage(throwable)));
            }
        }

        return entries;
    }

    static CommandLogObservation buildCommandLogObservation(CommandLogCapture capture) {
        if (capture == null) {
            return CommandLogObservation.unavailable("command-log-capture-missing");
        }

        if (!capture.active) {
            return CommandLogObservation.unavailable(capture.reason);
        }

        return new CommandLogObservation(
            true,
            capture.reason,
            capture.enabledBefore,
            capture.enableAttempted,
            capture.beforeCount,
            capture.afterCount,
            Math.max(0, capture.afterCount - capture.beforeCount),
            capture.entries
        );
    }

    static String readConsoleOutputSnapshot(Object terminalLine) {
        if (terminalLine == null) {
            return "";
        }

        Object output = IntrospectReflect.readFirstNonNullObject(terminalLine, "getOutput");

        if (output == null) {
            return "";
        }

        return String.valueOf(output);
    }

    static String extractConsoleOutputDelta(String beforeOutput, String afterOutput) {
        String normalizedBefore = beforeOutput == null ? "" : beforeOutput;
        String normalizedAfter = afterOutput == null ? "" : afterOutput;

        if (normalizedAfter.isEmpty()) {
            return "";
        }

        if (normalizedBefore.isEmpty()) {
            return normalizedAfter;
        }

        if (normalizedAfter.startsWith(normalizedBefore)) {
            return normalizedAfter.substring(normalizedBefore.length()).trim();
        }

        int sharedPrefixLength = sharedPrefixLength(normalizedBefore, normalizedAfter);
        return normalizedAfter.substring(sharedPrefixLength).trim();
    }

    static int sharedPrefixLength(String left, String right) {
        int limit = Math.min(left.length(), right.length());
        int index = 0;

        while (index < limit && left.charAt(index) == right.charAt(index)) {
            index++;
        }

        return index;
    }

    static String mergeObservedTerminalOutput(String eventOutput, String consoleOutputDelta, String consoleOutputSnapshot) {
        if (eventOutput != null && !eventOutput.trim().isEmpty()) {
            return eventOutput;
        }

        if (consoleOutputDelta != null && !consoleOutputDelta.trim().isEmpty()) {
            return consoleOutputDelta;
        }

        return consoleOutputSnapshot == null ? "" : consoleOutputSnapshot;
    }


    static HistoryObservation readCommandHistorySummary(Object terminalLine, String methodName) {
        Object history = IntrospectReflect.readFirstNonNullObject(terminalLine, methodName);

        if (history == null) {
            return HistoryObservation.unavailable();
        }

        Integer size = IntrospectReflect.readOptionalIntProperty(history, "getHistorySize", "getSize", "size", "getCount");
        return new HistoryObservation(true, history.getClass().getName(), size == null ? -1 : size.intValue(), IntrospectReflect.summarizeInterestingMethods(history.getClass()));
    }


    static TerminalOutputCapture startTerminalOutputCapture(Object terminalLine, int expectedCommandCount) {
        Object session = AppRuntimeContext.getActiveSession();

        if (session == null) {
            return new TerminalOutputCapture("", 0);
        }

        Object eventManager = IntrospectReflect.readFirstNonNullObject(session, "getEventManager");

        if (eventManager == null) {
            return new TerminalOutputCapture("", 0);
        }

        Object terminalLineEvents = IntrospectReflect.readFirstNonNullObject(eventManager, "getTerminalLineEvents");

        if (terminalLineEvents == null) {
            return new TerminalOutputCapture("", 0);
        }

        try {
            final StringBuilder output = new StringBuilder();
            final long[] lastEventAt = new long[] { System.currentTimeMillis() };
            final int[] completedCommands = new int[] { 0 };
            Class<?> listenerClass = Class.forName("com.cisco.pt.ipc.events.TerminalLineEventListener");
            Object listener = Proxy.newProxyInstance(
                listenerClass.getClassLoader(),
                new Class<?>[] { listenerClass },
                (proxy, method, args) -> {
                    if (!"handleEvent".equals(method.getName()) || args == null || args.length == 0 || args[0] == null) {
                        return null;
                    }

                    Object event = args[0];
                    lastEventAt[0] = System.currentTimeMillis();
                    String eventType = readTerminalEventType(event);

                    if ("OutputWritten".equals(eventType) || "TerminalUpdated".equals(eventType)) {
                        String newOutput = readTerminalEventOutput(event);

                        if (!newOutput.isEmpty()) {
                            output.append(newOutput);
                        }
                    } else if ("CommandEnded".equals(eventType)) {
                        completedCommands[0] += 1;
                    }

                    return null;
                }
            );

            Method addListenerMethod = IntrospectReflect.findMethodByName(terminalLineEvents.getClass(), "addListener", 2);
            Method removeListenerMethod = IntrospectReflect.findMethodByName(terminalLineEvents.getClass(), "removeListener", 2);

            if (addListenerMethod == null || removeListenerMethod == null) {
                return new TerminalOutputCapture("", 0);
            }

            addListenerMethod.invoke(terminalLineEvents, listener, terminalLine);
            return new TerminalOutputCapture(output, completedCommands, lastEventAt, expectedCommandCount, terminalLineEvents, listener);
        } catch (Throwable throwable) {
            return new TerminalOutputCapture("", 0);
        }
    }

    static TerminalOutputCapture awaitTerminalOutputCapture(TerminalOutputCapture capture, Object terminalLine) {
        if (!capture.active) {
            return capture;
        }

        try {
            long deadline = System.currentTimeMillis() + 5000L;
            long quietPeriodMs = 350L;

            while (System.currentTimeMillis() < deadline) {
                if (capture.completedCommands[0] >= capture.expectedCommandCount && System.currentTimeMillis() - capture.lastEventAt[0] >= quietPeriodMs) {
                    break;
                }

                Thread.sleep(50L);
            }
        } catch (Throwable throwable) {
            return new TerminalOutputCapture(capture.output.toString(), capture.completedCommands[0]);
        }

        return new TerminalOutputCapture(capture.output, capture.completedCommands, capture.lastEventAt, capture.expectedCommandCount, capture.registry, capture.listener);
    }

    static void stopTerminalOutputCapture(TerminalOutputCapture capture, Object terminalLine) {
        if (capture == null || !capture.active || capture.registry == null || capture.listener == null) {
            return;
        }

        try {
            Method removeListenerMethod = IntrospectReflect.findMethodByName(capture.registry.getClass(), "removeListener", 2);

            if (removeListenerMethod != null) {
                removeListenerMethod.invoke(capture.registry, capture.listener, terminalLine);
            }
        } catch (Throwable throwable) {
            // best-effort cleanup only
        }
    }

    static String readTerminalEventType(Object event) {
        Object typeValue = IntrospectReflect.readFirstNonNullObject(event, "getType");

        if (typeValue == null) {
            typeValue = IntrospectReflect.readFieldValue(event, "type");
        }

        return typeValue == null ? "" : String.valueOf(typeValue);
    }

    static String readTerminalEventOutput(Object event) {
        Object output = IntrospectReflect.readFirstNonNullObject(event, "getNewOutput");

        if (output == null) {
            output = IntrospectReflect.readFieldValue(event, "newOutput");
        }

        return output == null ? "" : String.valueOf(output);
    }


    static TerminalPingParseResult parseTerminalPingExecution(String response) {
        if (response == null || response.isEmpty()) {
            return TerminalPingParseResult.empty();
        }

        Pattern packetSummaryPattern = Pattern.compile("Sent\\s*=\\s*(\\d+),\\s*Received\\s*=\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher packetSummaryMatcher = packetSummaryPattern.matcher(response);
        int sentCount = 0;
        int receivedCount = 0;

        if (packetSummaryMatcher.find()) {
            sentCount = Integer.parseInt(packetSummaryMatcher.group(1));
            receivedCount = Integer.parseInt(packetSummaryMatcher.group(2));
        }

        Pattern ttlPattern = Pattern.compile("TTL\\s*[=<]\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher ttlMatcher = ttlPattern.matcher(response);
        int lastTtl = 0;

        while (ttlMatcher.find()) {
            lastTtl = Integer.parseInt(ttlMatcher.group(1));
        }

        Pattern delayPattern = Pattern.compile("time\\s*[=<]\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher delayMatcher = delayPattern.matcher(response);
        int lastDelay = 0;

        while (delayMatcher.find()) {
            lastDelay = Integer.parseInt(delayMatcher.group(1));
        }

        if (sentCount == 0) {
            Matcher replyMatcher = Pattern.compile("Reply from", Pattern.CASE_INSENSITIVE).matcher(response);
            int replyCount = 0;

            while (replyMatcher.find()) {
                replyCount += 1;
            }

            if (replyCount > 0) {
                sentCount = replyCount;
                receivedCount = replyCount;
            }
        }

        return new TerminalPingParseResult(sentCount > 0 && receivedCount > 0, sentCount, receivedCount, lastDelay, lastTtl);
    }

    static String trySetPortPowerState(DeviceSelection device, PortSelection port, boolean powerOn) {
        if (IntrospectReflect.invokeMethodIfPresent(port.portObject, "setPower", Boolean.valueOf(powerOn))) {
            return "port";
        }

        return null;
    }

    static String buildPortStateJsonPayload(
        boolean observed,
        DeviceSelection device,
        PortSelection port,
        PortStateMutationResult mutationResult
    ) {
        StringBuilder json = new StringBuilder();
        json.append('{');
        json.append("\"observed\":").append(observed ? "true" : "false");

        if (mutationResult != null) {
            json.append(",\"ok\":").append(mutationResult.ok ? "true" : "false");
        }

        json.append(",\"port\":{");
        json.append("\"device\":").append(IntrospectJson.jsonQuoted(device.deviceName));
        json.append(",\"port\":").append(IntrospectJson.jsonQuoted(port.portName));
        json.append(",\"linked\":").append(IntrospectReflect.readBooleanProperty(port.portObject, "getLink", true) ? "true" : "false");
        boolean supportsPortPowerRead = IntrospectReflect.findMethod(port.portObject.getClass(), "isPowerOn") != null;
        json.append(",\"powerOn\":").append(supportsPortPowerRead ? (IntrospectReflect.readBooleanProperty(port.portObject, "isPowerOn", false) ? "true" : "false") : "null");
        json.append(",\"portUp\":").append(IntrospectReflect.readBooleanProperty(port.portObject, "isPortUp", false) ? "true" : "false");
        json.append(",\"protocolUp\":").append(IntrospectReflect.readBooleanProperty(port.portObject, "isProtocolUp", false) ? "true" : "false");
        json.append(",\"lightStatus\":").append(IntrospectJson.jsonQuoted(IntrospectReflect.readStringProperty(port.portObject, "getLightStatus")));
        json.append(",\"devicePower\":").append(IntrospectReflect.readBooleanProperty(device.device, "getPower", false) ? "true" : "false");
        json.append(",\"supportsPortPowerRead\":").append(supportsPortPowerRead ? "true" : "false");
        json.append(",\"supportsPortPowerWrite\":").append(IntrospectReflect.findMethod(port.portObject.getClass(), "setPower", Boolean.TYPE) != null ? "true" : "false");
        json.append(",\"supportsDevicePowerWrite\":").append(IntrospectReflect.findMethod(device.device.getClass(), "setPower", Boolean.TYPE) != null ? "true" : "false");
        json.append('}');

        if (mutationResult != null) {
            json.append(",\"appliedVia\":").append(IntrospectJson.jsonQuoted(mutationResult.appliedVia));
            json.append(",\"response\":").append(IntrospectJson.jsonQuoted(mutationResult.response));
        }

        json.append('}');
        return json.toString();
    }


    static PingExecutionResult observePingProcess(Object pingProcess, int repeatCount, int timeoutSeconds) {
        int sentCount = 0;
        int receivedCount = 0;
        int lastDelay = 0;
        int lastTtl = 0;
        long deadline = System.currentTimeMillis() + Math.max(1500L, ((long) repeatCount * (long) timeoutSeconds * 1000L) + 1500L);

        while (System.currentTimeMillis() < deadline) {
            sentCount = IntrospectReflect.readIntProperty(pingProcess, "getSentCount");
            receivedCount = IntrospectReflect.readIntProperty(pingProcess, "getReceivedCount");
            lastDelay = IntrospectReflect.readIntProperty(pingProcess, "getLastDelay");
            lastTtl = IntrospectReflect.readIntProperty(pingProcess, "getLastTtl");

            if (sentCount >= repeatCount) {
                break;
            }

            try {
                Thread.sleep(200L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        return new PingExecutionResult(true, receivedCount > 0, "icmp-process", sentCount, receivedCount, lastDelay, lastTtl, "");
    }



    static void appendPortsJson(StringBuilder json, DeviceSelection selection) {
        json.append(",\"ports\":[");

        for (int portIndex = 0; portIndex < selection.ports.size(); portIndex++) {
            Object port = selection.ports.get(portIndex);

            if (portIndex > 0) {
                json.append(',');
            }

            json.append('{');
            json.append("\"portIndex\":").append(portIndex);
            json.append(",\"name\":").append(IntrospectJson.jsonQuoted(IntrospectReflect.readStringProperty(port, "getName")));
            json.append(",\"linked\":").append(IntrospectReflect.readBooleanProperty(port, "getLink", true) ? "true" : "false");
            json.append('}');
        }

        json.append(']');
    }

    static void appendNeighborsJson(StringBuilder json, Object network, DeviceSelection selection) throws Exception {
        Method getLinkCountMethod = network.getClass().getMethod("getLinkCount");
        Method getLinkAtMethod = network.getClass().getMethod("getLinkAt", Integer.TYPE);
        int linkCount = ((Number) getLinkCountMethod.invoke(network)).intValue();
        List<LinkEndpoints> resolvedEndpoints = IntrospectLogic.shouldUsePortStateGraph(network, linkCount)
            ? IntrospectLogic.derivePortStateLinkEndpoints(network, linkCount)
            : IntrospectLogic.resolveAllLinkEndpoints(network);
        boolean firstNeighbor = true;
        json.append(",\"neighbors\":[");

        for (int linkIndex = 0; linkIndex < linkCount; linkIndex++) {
            Object link = getLinkAtMethod.invoke(network, Integer.valueOf(linkIndex));
            LinkEndpoints endpoints = resolvedEndpoints.get(linkIndex);
            LinkEndpoint localEndpoint = null;
            LinkEndpoint remoteEndpoint = null;

            if (endpoints.left.deviceIndex == selection.deviceIndex) {
                localEndpoint = endpoints.left;
                remoteEndpoint = endpoints.right;
            } else if (endpoints.right.deviceIndex == selection.deviceIndex) {
                localEndpoint = endpoints.right;
                remoteEndpoint = endpoints.left;
            }

            if (localEndpoint == null) {
                continue;
            }

            if (!firstNeighbor) {
                json.append(',');
            }

            firstNeighbor = false;
            json.append('{');
            json.append("\"linkIndex\":").append(linkIndex);
            json.append(",\"connectionType\":").append(IntrospectJson.jsonQuoted(IntrospectReflect.readStringProperty(link, "getConnectionType")));
            json.append(",\"localPortIndex\":").append(localEndpoint.portIndex);
            json.append(",\"localPortName\":").append(IntrospectJson.jsonQuoted(localEndpoint.portName));
            json.append(",\"neighborObserved\":").append(remoteEndpoint.observed ? "true" : "false");
            json.append(",\"neighborDeviceIndex\":").append(remoteEndpoint.deviceIndex);
            json.append(",\"neighborDeviceName\":").append(IntrospectJson.jsonQuoted(remoteEndpoint.deviceName));
            json.append(",\"neighborDeviceType\":").append(IntrospectJson.jsonQuoted(remoteEndpoint.deviceType));
            json.append(",\"neighborPortIndex\":").append(remoteEndpoint.portIndex);
            json.append(",\"neighborPortName\":").append(IntrospectJson.jsonQuoted(remoteEndpoint.portName));
            json.append('}');
        }

        json.append(']');
    }

    static boolean matchesCandidate(int deviceIndex, int portIndex, PortCandidate candidate) {
        return candidate.deviceIndex == deviceIndex && candidate.portIndex == portIndex;
    }

    static String portCandidateKey(PortCandidate candidate) {
        return candidate == null ? "" : portCandidateKey(candidate.deviceIndex, candidate.portIndex);
    }

    static String portCandidateKey(int deviceIndex, int portIndex) {
        return deviceIndex + ":" + portIndex;
    }

    static String linkEndpointKey(LinkEndpoint endpoint) {
        return endpoint == null ? "" : portCandidateKey(endpoint.deviceIndex, endpoint.portIndex);
    }

    static void incrementDeviceUsage(Map<Integer, Integer> counts, int deviceIndex) {
        Integer current = counts.get(Integer.valueOf(deviceIndex));
        counts.put(Integer.valueOf(deviceIndex), Integer.valueOf(current == null ? 1 : current.intValue() + 1));
    }

    static PortCandidate findLinkedAlternativePortCandidate(
        Object network,
        PortIdentity targetIdentity,
        PortCandidate disallowedCandidate,
        PortCandidate currentCandidate,
        LinkResolutionContext context
    ) {
        if (network == null || targetIdentity == null) {
            return null;
        }

        String targetPreferredName = preferredPortName(targetIdentity);

        if (targetPreferredName.isEmpty() && targetIdentity.portNameNumber.isEmpty() && targetIdentity.primaryName.isEmpty()) {
            return null;
        }

        try {
            Method getDeviceCountMethod = network.getClass().getMethod("getDeviceCount");
            Method getDeviceAtMethod = network.getClass().getMethod("getDeviceAt", Integer.TYPE);
            int deviceCount = ((Number) getDeviceCountMethod.invoke(network)).intValue();
            List<PortCandidate> matches = new ArrayList<PortCandidate>();

            for (int deviceIndex = 0; deviceIndex < deviceCount; deviceIndex++) {
                Object device = getDeviceAtMethod.invoke(network, Integer.valueOf(deviceIndex));
                String deviceName = IntrospectReflect.readStringProperty(device, "getName");
                String deviceType = IntrospectReflect.readStringProperty(device, "getType");
                List<Object> ports = IntrospectLogic.readPortObjects(device);

                for (int portIndex = 0; portIndex < ports.size(); portIndex++) {
                    Object candidatePort = ports.get(portIndex);

                    if (!IntrospectReflect.readBooleanProperty(candidatePort, "getLink", true)) {
                        continue;
                    }

                    if (disallowedCandidate != null && matchesCandidate(deviceIndex, portIndex, disallowedCandidate)) {
                        continue;
                    }

                    PortIdentity candidateIdentity = readPortIdentity(candidatePort);
                    String candidatePreferredName = preferredPortName(candidateIdentity);
                    boolean nameMatch = !targetPreferredName.isEmpty() && targetPreferredName.equals(candidatePreferredName)
                        || !targetIdentity.portNameNumber.isEmpty() && targetIdentity.portNameNumber.equals(candidateIdentity.portNameNumber)
                        || !targetIdentity.primaryName.isEmpty() && targetIdentity.primaryName.equals(candidateIdentity.primaryName);

                    if (!nameMatch) {
                        continue;
                    }

                    if (currentCandidate != null && currentCandidate.deviceIndex == deviceIndex && currentCandidate.portIndex == portIndex) {
                        continue;
                    }

                    if (context != null && context.usedPortKeys.contains(portCandidateKey(deviceIndex, portIndex))) {
                        continue;
                    }

                    int usageBonus = context == null ? 0 : (context.deviceUsageCounts.containsKey(Integer.valueOf(deviceIndex)) ? context.deviceUsageCounts.get(Integer.valueOf(deviceIndex)).intValue() * 1000 : 0);
                    matches.add(new PortCandidate(deviceIndex, deviceName, deviceType, portIndex, candidatePreferredName, 50000 + usageBonus));
                }
            }

            if (matches.size() == 1) {
                return matches.get(0);
            }

            if (matches.size() > 1) {
                PortCandidate best = null;

                for (PortCandidate candidate : matches) {
                    if (best == null || candidate.score > best.score) {
                        best = candidate;
                    }
                }

                return best;
            }
        } catch (Throwable throwable) {
            return null;
        }

        return null;
    }

    static boolean strictPortIdentityMatches(PortIdentity candidateIdentity, PortIdentity targetIdentity) {
        if (candidateIdentity == null || targetIdentity == null) {
            return false;
        }

        if (targetIdentity.deviceName.isEmpty()) {
            return false;
        }

        if (!targetIdentity.deviceName.equals(candidateIdentity.deviceName)) {
            return false;
        }

        String targetPreferredName = preferredPortName(targetIdentity);
        String candidatePreferredName = preferredPortName(candidateIdentity);

        if (!targetPreferredName.isEmpty() && targetPreferredName.equals(candidatePreferredName)) {
            return true;
        }

        if (!targetIdentity.portNameNumber.isEmpty() && targetIdentity.portNameNumber.equals(candidateIdentity.portNameNumber)) {
            return true;
        }

        return !targetIdentity.primaryName.isEmpty() && targetIdentity.primaryName.equals(candidateIdentity.primaryName);
    }

    static LinkEndpoint toLinkEndpoint(PortCandidate candidate, PortIdentity identity) {
        if (candidate != null) {
            return new LinkEndpoint(
                true,
                candidate.deviceIndex,
                candidate.deviceName,
                candidate.deviceType,
                candidate.portIndex,
                candidate.portName
            );
        }

        return new LinkEndpoint(
            false,
            -1,
            identity.deviceName,
            identity.deviceType,
            -1,
            preferredPortName(identity)
        );
    }

    static int scorePortMatch(
        Object candidatePort,
        Object targetPort,
        PortIdentity candidateIdentity,
        PortIdentity targetIdentity,
        String deviceName,
        String deviceType
    ) {
        if (candidatePort == null || targetPort == null) {
            return Integer.MIN_VALUE;
        }

        int score = 0;

        if (candidatePort == targetPort) {
            score += 1000;
        }

        try {
            if (candidatePort.equals(targetPort)) {
                score += 800;
            }
        } catch (Throwable throwable) {
            // Ignore equals failures and keep scoring on reflective fields.
        }

        if (!targetIdentity.deviceName.isEmpty() && targetIdentity.deviceName.equals(deviceName)) {
            score += 200;
        }

        if (!targetIdentity.deviceType.isEmpty() && targetIdentity.deviceType.equals(deviceType)) {
            score += 100;
        }

        if (!targetIdentity.primaryName.isEmpty() && targetIdentity.primaryName.equals(candidateIdentity.primaryName)) {
            score += 300;
        }

        if (!targetIdentity.secondaryName.isEmpty() && targetIdentity.secondaryName.equals(candidateIdentity.secondaryName)) {
            score += 150;
        }

        if (!targetIdentity.tertiaryName.isEmpty() && targetIdentity.tertiaryName.equals(candidateIdentity.tertiaryName)) {
            score += 75;
        }

        if (!targetIdentity.remotePortName.isEmpty() && targetIdentity.remotePortName.equals(candidateIdentity.remotePortName)) {
            score += 25;
        }

        return score;
    }

    static void appendLinkEndpointJson(StringBuilder json, String prefix, LinkEndpoint endpoint) {
        json.append(",\"").append(prefix).append("Observed\":").append(endpoint.observed ? "true" : "false");
        json.append(",\"").append(prefix).append("DeviceIndex\":").append(endpoint.deviceIndex);
        json.append(",\"").append(prefix).append("DeviceName\":").append(IntrospectJson.jsonQuoted(endpoint.deviceName));
        json.append(",\"").append(prefix).append("DeviceType\":").append(IntrospectJson.jsonQuoted(endpoint.deviceType));
        json.append(",\"").append(prefix).append("PortIndex\":").append(endpoint.portIndex);
        json.append(",\"").append(prefix).append("PortName\":").append(IntrospectJson.jsonQuoted(endpoint.portName));
    }

    static List<Object> readPortObjects(Object device) {
        ArrayList<Object> ports = new ArrayList<Object>();

        if (device == null) {
            return ports;
        }

        try {
            Object portCollection = device.getClass().getMethod("getPorts").invoke(device);
            appendPortObjects(ports, device, portCollection);

            if (!ports.isEmpty()) {
                return ports;
            }
        } catch (Throwable throwable) {
            // Fall through to index-based lookup.
        }

        int portCount = IntrospectReflect.readIntProperty(device, "getPortCount");

        if (portCount <= 0) {
            return ports;
        }

        try {
            Method getPortAtMethod = device.getClass().getMethod("getPortAt", Integer.TYPE);

            for (int portIndex = 0; portIndex < portCount; portIndex++) {
                Object port = getPortAtMethod.invoke(device, Integer.valueOf(portIndex));

                if (port != null) {
                    ports.add(port);
                }
            }
        } catch (Throwable throwable) {
            // Leave ports empty if the framework does not expose indexed access.
        }

        return ports;
    }

    static void appendPortObjects(List<Object> ports, Object device, Object portCollection) throws Exception {
        if (portCollection == null) {
            return;
        }

        if (portCollection instanceof Iterable<?>) {
            for (Object port : (Iterable<?>) portCollection) {
                Object resolvedPort = resolvePortObject(device, port);

                if (resolvedPort != null) {
                    ports.add(resolvedPort);
                }
            }

            return;
        }

        Class<?> collectionClass = portCollection.getClass();

        if (collectionClass.isArray()) {
            int length = java.lang.reflect.Array.getLength(portCollection);

            for (int index = 0; index < length; index++) {
                Object port = java.lang.reflect.Array.get(portCollection, index);
                Object resolvedPort = resolvePortObject(device, port);

                if (resolvedPort != null) {
                    ports.add(resolvedPort);
                }
            }

            return;
        }

        if (portCollection instanceof List<?>) {
            for (Object port : (List<?>) portCollection) {
                Object resolvedPort = resolvePortObject(device, port);

                if (resolvedPort != null) {
                    ports.add(resolvedPort);
                }
            }
        }
    }

    static Object resolvePortObject(Object device, Object portCandidate) {
        if (portCandidate == null) {
            return null;
        }

        if (!(portCandidate instanceof String)) {
            return portCandidate;
        }

        try {
            Method getPortMethod = device.getClass().getMethod("getPort", String.class);
            Object resolvedPort = getPortMethod.invoke(device, String.valueOf(portCandidate));

            if (resolvedPort != null) {
                return resolvedPort;
            }
        } catch (Throwable throwable) {
            return null;
        }

        return null;
    }


    static PortIdentity readPortIdentity(Object port) {
        String primaryName = IntrospectReflect.readFirstNonBlankString(port, "getName", "getPortName", "getShortName", "getDisplayName", "getPortNameNumber");
        String secondaryName = IntrospectReflect.readFirstNonBlankString(port, "getCanonicalName", "getLongName", "getInterfaceName", "getTerminalTypeShortString");
        String tertiaryName = IntrospectReflect.readFirstNonBlankString(port, "getDescription");
        String portNameNumber = IntrospectReflect.readFirstNonBlankString(port, "getPortNameNumber");
        String remotePortName = IntrospectReflect.readFirstNonBlankString(port, "getRemotePortName");
        Object owner = IntrospectReflect.readFirstNonNullObject(port, "getDevice", "getParentDevice", "getOwner", "getNode", "getParent");
        String deviceName = IntrospectReflect.readFirstNonBlankString(port, "getDeviceName", "getParentDeviceName", "getOwnerName");
        String deviceType = IntrospectReflect.readFirstNonBlankString(port, "getDeviceType", "getParentDeviceType", "getOwnerType");

        if (owner != null) {
            if (deviceName.isEmpty()) {
                deviceName = IntrospectReflect.readFirstNonBlankString(owner, "getName", "getDisplayName");
            }

            if (deviceType.isEmpty()) {
                deviceType = IntrospectReflect.readFirstNonBlankString(owner, "getType", "getDeviceType");
            }
        }

        if (primaryName.isEmpty()) {
            primaryName = String.valueOf(port);
        }

        return new PortIdentity(primaryName, secondaryName, tertiaryName, portNameNumber, remotePortName, deviceName, deviceType);
    }

    static String preferredPortName(PortIdentity identity) {
        if (!identity.primaryName.isEmpty()) {
            return identity.primaryName;
        }

        if (!identity.secondaryName.isEmpty()) {
            return identity.secondaryName;
        }

        if (!identity.portNameNumber.isEmpty()) {
            return identity.portNameNumber;
        }

        return identity.tertiaryName;
    }

}
