package packettracer.exapp.lib.introspect;

import packettracer.exapp.utils.ThrowableUtils;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Reflection helpers for Packet Tracer IPC objects.
 */
final class IntrospectReflect {
    private IntrospectReflect() {
    }

    static RuntimeException debugFailure(String callSite, Throwable throwable) {
        return new RuntimeException(
            callSite + " [thread=" + Thread.currentThread().getName() + "] -> " + ThrowableUtils.describeThrowable(throwable),
            throwable
        );
    }

    static Object createIpAddress(String dottedQuad) {
        try {
            Class<?> implClass = Class.forName("com.cisco.pt.impl.IPAddressImpl");
            Method parseMethod = implClass.getMethod("parseIPAddress", String.class);
            Object value = parseMethod.invoke(null, dottedQuad);

            if (value != null) {
                return value;
            }

            Constructor<?> constructor = implClass.getConstructor(String.class);
            return constructor.newInstance(dottedQuad);
        } catch (Throwable throwable) {
            return null;
        }
    }

    static Method findMethod(Class<?> type, String methodName, Class<?>... parameterTypes) {
        try {
            return type.getMethod(methodName, parameterTypes);
        } catch (Throwable throwable) {
            return null;
        }
    }

    static Method findMethodByName(Class<?> type, String methodName, int parameterCount) {
        try {
            for (Method method : type.getMethods()) {
                if (method.getName().equals(methodName) && method.getParameterCount() == parameterCount) {
                    return method;
                }
            }
        } catch (Throwable throwable) {
            return null;
        }

        return null;
    }

    static Method findIpSubnetMaskSetter(Class<?> portClass) {
        Method setter = null;

        try {
            Class<?> ipAddressInterface = Class.forName("com.cisco.pt.IPAddress");
            setter = findMethod(portClass, "setIpSubnetMask", ipAddressInterface, ipAddressInterface);
        } catch (Throwable throwable) {
            setter = null;
        }

        if (setter != null) {
            return setter;
        }

        return findMethodByName(portClass, "setIpSubnetMask", 2);
    }

    static String readStringProperty(Object target, String methodName) {
        if (target == null) {
            return "";
        }

        try {
            Object value = target.getClass().getMethod(methodName).invoke(target);
            return value == null ? "" : String.valueOf(value);
        } catch (Throwable throwable) {
            return "";
        }
    }

    static Object readObjectProperty(Object target, String methodName) {
        if (target == null) {
            return null;
        }

        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (Throwable throwable) {
            return null;
        }
    }

    static int readIntProperty(Object target, String methodName) {
        if (target == null) {
            return 0;
        }

        try {
            Object value = target.getClass().getMethod(methodName).invoke(target);
            return value instanceof Number ? ((Number) value).intValue() : 0;
        } catch (Throwable throwable) {
            return 0;
        }
    }

    static boolean readBooleanProperty(Object target, String methodName, boolean nonNullMeansTrue) {
        if (target == null) {
            return false;
        }

        try {
            Object value = target.getClass().getMethod(methodName).invoke(target);

            if (value instanceof Boolean) {
                return ((Boolean) value).booleanValue();
            }

            return nonNullMeansTrue && value != null;
        } catch (Throwable throwable) {
            return false;
        }
    }

    static String readFirstNonBlankString(Object target, String... methodNames) {
        for (String methodName : methodNames) {
            String value = readStringProperty(target, methodName);

            if (!value.isEmpty()) {
                return value;
            }
        }

        return "";
    }

    static Object readFirstNonNullObject(Object target, String... methodNames) {
        for (String methodName : methodNames) {
            Object value = readObjectProperty(target, methodName);

            if (value != null) {
                return value;
            }
        }

        return null;
    }

    static Integer readOptionalIntProperty(Object target, String... methodNames) {
        if (target == null) {
            return null;
        }

        for (String methodName : methodNames) {
            try {
                Method method = findMethodByName(target.getClass(), methodName, 0);

                if (method != null) {
                    Object value = method.invoke(target);

                    if (value instanceof Number) {
                        return Integer.valueOf(((Number) value).intValue());
                    }
                }
            } catch (Throwable throwable) {
                return null;
            }
        }

        return null;
    }

    static Object readFieldValue(Object target, String fieldName) {
        if (target == null) {
            return null;
        }

        try {
            return target.getClass().getField(fieldName).get(target);
        } catch (Throwable throwable) {
            return null;
        }
    }

    static List<String> readIndexedStringValues(
        Object target,
        String countMethodName,
        String valueMethodName
    ) {
        List<String> values = new ArrayList<String>();

        if (target == null) {
            return values;
        }

        try {
            Method countMethod = target.getClass().getMethod(countMethodName);
            Method valueMethod = target.getClass().getMethod(valueMethodName, Integer.TYPE);
            int count = ((Number) countMethod.invoke(target)).intValue();

            for (int index = 0; index < count; index++) {
                Object rawValue = valueMethod.invoke(target, Integer.valueOf(index));
                String value = rawValue == null ? "" : String.valueOf(rawValue);

                if (!value.isEmpty() && values.indexOf(value) < 0) {
                    values.add(value);
                }
            }
        } catch (Throwable throwable) {
            return values;
        }

        return values;
    }

    static String summarizeInterestingMethods(Class<?> type) {
        StringBuilder summary = new StringBuilder();

        try {
            for (Method method : type.getMethods()) {
                String methodName = method.getName();

                if (
                    methodName.contains("Ip") ||
                    methodName.contains("Subnet") ||
                    methodName.contains("Dhcp") ||
                    methodName.contains("Port")
                ) {
                    if (summary.length() > 0) {
                        summary.append(',');
                    }

                    summary.append(methodName).append('/').append(method.getParameterCount());
                }
            }
        } catch (Throwable throwable) {
            return "method-enumeration-failed";
        }

        return summary.toString();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static Object readConnectTypeEnum(String connectionType) {
        try {
            Class enumClass = Class.forName("com.cisco.pt.ipc.enums.ConnectType");
            return Enum.valueOf(enumClass, connectionType);
        } catch (Throwable throwable) {
            return null;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static Object readDeviceTypeEnum(String deviceType) {
        try {
            Class enumClass = Class.forName("com.cisco.pt.ipc.enums.DeviceType");
            return Enum.valueOf(enumClass, deviceType);
        } catch (Throwable throwable) {
            return null;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static Object readModuleTypeEnum(String moduleType) {
        if (moduleType == null || moduleType.trim().isEmpty()) {
            return null;
        }

        String normalized = moduleType.trim();

        try {
            Class enumClass = Class.forName("com.cisco.pt.ipc.enums.ModuleType");

            for (Object constant : enumClass.getEnumConstants()) {
                String name = String.valueOf(constant);

                if (normalized.equals(name) || normalized.equalsIgnoreCase(name)) {
                    return constant;
                }

                String compact = name;
                if (compact.startsWith("e") && compact.length() > 1) {
                    compact = compact.substring(1);
                }
                compact = compact.replace("Pt", "").replace("Module", "");
                compact = compact.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toUpperCase();
                String requestedCompact = normalized.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toUpperCase();

                if (requestedCompact.equals(compact)) {
                    return constant;
                }
            }
        } catch (Throwable throwable) {
            return null;
        }

        return null;
    }

    static Integer parseSelectorIndex(String selector) {
        try {
            return Integer.valueOf(selector);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    static Object readProcessObject(Object device, String... processNames) {
        if (device == null) {
            return null;
        }

        Method getProcessMethod = findMethodByName(device.getClass(), "getProcess", 1);

        if (getProcessMethod == null) {
            return null;
        }

        for (String processName : processNames) {
            try {
                Object process = getProcessMethod.invoke(device, processName);

                if (process != null) {
                    return process;
                }
            } catch (Throwable throwable) {
                return null;
            }
        }

        return null;
    }

    static boolean invokeMethodIfPresent(Object target, String methodName, Object argument) {
        if (target == null) {
            return false;
        }

        try {
            Method method = findMethodByName(target.getClass(), methodName, 1);

            if (method == null) {
                return false;
            }

            method.invoke(target, argument);
            return true;
        } catch (Throwable throwable) {
            return false;
        }
    }
}
