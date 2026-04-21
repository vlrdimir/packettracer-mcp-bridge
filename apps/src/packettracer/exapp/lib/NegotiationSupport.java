package packettracer.exapp.lib;

import com.cisco.pt.ptmp.PacketTracerSession;
import com.cisco.pt.ptmp.PacketTracerSessionFactory;
import packettracer.exapp.core.BootstrapReport;
import packettracer.exapp.utils.PtAppMetaAuthLoader;
import packettracer.exapp.utils.PtAppMetaAuthLoader.PtAppMetaAuth;
import packettracer.exapp.utils.ThrowableUtils;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public final class NegotiationSupport {
    public static final String CONNECTION_NEGOTIATION_PROPERTIES_CLASS = "com.cisco.pt.ptmp.ConnectionNegotiationProperties";
    public static final String CONNECTION_NEGOTIATION_PROPERTIES_IMPL_CLASS = "com.cisco.pt.ptmp.impl.ConnectionNegotiationPropertiesImpl";
    public static final String[] CONNECT_OPTIONS_PROVIDER_CLASSES = {
        "com.cisco.pt.impl.OptionsManager",
        "com.cisco.pt.ptmp.OptionsManager",
        "com.cisco.pt.ptmp.impl.OptionsManager"
    };

    private NegotiationSupport() {
    }

    public static NegotiationBootstrap createNegotiationBootstrap(
        BootstrapReport report,
        Class<?> anchorClass,
        String authApplicationProperty,
        String authApplicationEnv,
        String authSecretProperty,
        String authSecretEnv,
        String ptAppMetaFilename
    ) {
        Class<?> negotiationClass;

        try {
            negotiationClass = Class.forName(CONNECTION_NEGOTIATION_PROPERTIES_CLASS, true, anchorClass.getClassLoader());
        } catch (Throwable throwable) {
            report.addDetail(String.format("ConnectionNegotiationProperties class resolution failed with %s: %s", throwable.getClass().getName(), ThrowableUtils.safeMessage(throwable)));
            return null;
        }

        FrameworkConnectOptions frameworkConnectOptions = tryLoadFrameworkConnectOpts(negotiationClass, report, anchorClass);
        Object negotiationProperties;
        String sourceDescription;

        if (frameworkConnectOptions != null) {
            negotiationProperties = frameworkConnectOptions.getNegotiationProperties();
            sourceDescription = frameworkConnectOptions.getSourceDescription();
            report.addDetail(String.format("Using framework connect defaults from %s as the negotiation base before applying conservative overrides.", sourceDescription));
        } else {
            negotiationProperties = instantiateNegotiationProperties(negotiationClass, report, anchorClass);

            if (negotiationProperties == null) {
                return null;
            }

            sourceDescription = CONNECTION_NEGOTIATION_PROPERTIES_IMPL_CLASS;
            report.addDetail(String.format("Framework connect defaults were unavailable, so the bootstrap is falling back to %s before applying conservative overrides.", sourceDescription));
        }

        report.addDetail(String.format("Prepared ConnectionNegotiationProperties via %s: %s", sourceDescription, negotiationProperties.getClass().getName()));
        applyConservativeNegotiationDefaults(negotiationProperties, report, anchorClass);
        applyAuthOverrides(negotiationProperties, report, anchorClass, authApplicationProperty, authApplicationEnv, authSecretProperty, authSecretEnv, ptAppMetaFilename);
        report.addDetail(describeNegotiationProperties(negotiationProperties));
        return new NegotiationBootstrap(negotiationClass, negotiationProperties);
    }

    public static PacketTracerSession openSessionWithNegotiation(PacketTracerSessionFactory sessionFactory, String host, int port, NegotiationBootstrap negotiationBootstrap, BootstrapReport report) throws Exception {
        Method method = findOpenSessionMethod(sessionFactory, negotiationBootstrap.getNegotiationClass());

        if (method == null) {
            report.addDetail("PacketTracerSessionFactory implementation did not expose openSession(String, int, ConnectionNegotiationProperties).");
            return null;
        }

        Object value = method.invoke(sessionFactory, host, Integer.valueOf(port), negotiationBootstrap.getNegotiationProperties());

        if (value == null) {
            return null;
        }

        if (!PacketTracerSession.class.isInstance(value)) {
            report.addDetail(String.format("openSession(String, int, ConnectionNegotiationProperties) returned unexpected type: %s", value.getClass().getName()));
            return null;
        }

        return PacketTracerSession.class.cast(value);
    }

    private static FrameworkConnectOptions tryLoadFrameworkConnectOpts(Class<?> negotiationClass, BootstrapReport report, Class<?> anchorClass) {
        for (String className : CONNECT_OPTIONS_PROVIDER_CLASSES) {
            try {
                Class<?> providerClass = Class.forName(className, true, anchorClass.getClassLoader());
                Method method = providerClass.getMethod("getConnectOpts");
                Object providerTarget = resolveConnectOptionsProviderTarget(providerClass, method, report);
                Object value = method.invoke(providerTarget);

                if (value == null) {
                    report.addDetail(String.format("%s#getConnectOpts() returned null.", className));
                    continue;
                }

                if (!negotiationClass.isInstance(value)) {
                    report.addDetail(String.format("%s#getConnectOpts() returned %s instead of ConnectionNegotiationProperties.", className, value.getClass().getName()));
                    continue;
                }

                report.addDetail(String.format("Loaded ConnectionNegotiationProperties from %s#getConnectOpts().", className));
                return new FrameworkConnectOptions(value, className + "#getConnectOpts()");
            } catch (ClassNotFoundException exception) {
                report.addDetail(String.format("Connect-options provider not present on classpath: %s", className));
            } catch (NoSuchMethodException exception) {
                report.addDetail(String.format("Connect-options provider missing getConnectOpts(): %s", className));
            } catch (Throwable throwable) {
                report.addDetail(String.format("%s#getConnectOpts() failed with %s: %s", className, throwable.getClass().getName(), ThrowableUtils.safeMessage(throwable)));
            }
        }

        return null;
    }

    private static Object resolveConnectOptionsProviderTarget(Class<?> providerClass, Method method, BootstrapReport report) throws Exception {
        if (Modifier.isStatic(method.getModifiers())) {
            return null;
        }

        try {
            Method instanceMethod = providerClass.getMethod("getInstance");

            if (Modifier.isStatic(instanceMethod.getModifiers()) && providerClass.isAssignableFrom(instanceMethod.getReturnType())) {
                Object instance = instanceMethod.invoke(null);

                if (instance != null) {
                    report.addDetail(String.format("Using %s#getInstance() to call non-static getConnectOpts().", providerClass.getName()));
                    return instance;
                }

                report.addDetail(String.format("%s#getInstance() returned null while preparing non-static getConnectOpts().", providerClass.getName()));
            }
        } catch (NoSuchMethodException exception) {
            report.addDetail(String.format("%s does not expose static getInstance(); trying constructor for non-static getConnectOpts().", providerClass.getName()));
        }

        Constructor<?> constructor = providerClass.getDeclaredConstructor();

        if (!constructor.canAccess(null)) {
            constructor.setAccessible(true);
        }

        Object instance = constructor.newInstance();
        report.addDetail(String.format("Constructed %s directly to call non-static getConnectOpts().", providerClass.getName()));
        return instance;
    }

    private static Object instantiateNegotiationProperties(Class<?> negotiationClass, BootstrapReport report, Class<?> anchorClass) {
        try {
            Class<?> implementationClass = Class.forName(CONNECTION_NEGOTIATION_PROPERTIES_IMPL_CLASS, true, anchorClass.getClassLoader());

            if (!negotiationClass.isAssignableFrom(implementationClass)) {
                report.addDetail(String.format("ConnectionNegotiationProperties implementation %s does not implement the expected interface.", implementationClass.getName()));
                return null;
            }

            Constructor<?> constructor = implementationClass.getDeclaredConstructor();
            Object value = constructor.newInstance();
            report.addDetail(String.format("Instantiated ConnectionNegotiationProperties via %s because no framework connect-options provider produced a value.", implementationClass.getName()));
            return value;
        } catch (Throwable throwable) {
            report.addDetail(String.format("ConnectionNegotiationProperties implementation construction failed with %s: %s", throwable.getClass().getName(), ThrowableUtils.safeMessage(throwable)));
            return null;
        }
    }

    private static void applyAuthOverrides(
        Object negotiationProperties,
        BootstrapReport report,
        Class<?> anchorClass,
        String authApplicationProperty,
        String authApplicationEnv,
        String authSecretProperty,
        String authSecretEnv,
        String ptAppMetaFilename
    ) {
        String authApplicationOverride = ThrowableUtils.firstConfiguredValue(System.getProperty(authApplicationProperty), System.getenv(authApplicationEnv));
        String authSecretOverride = ThrowableUtils.firstConfiguredValue(System.getProperty(authSecretProperty), System.getenv(authSecretEnv));
        PtAppMetaAuth ptAppMetaAuth = null;

        if (authApplicationOverride == null || authSecretOverride == null) {
            ptAppMetaAuth = PtAppMetaAuthLoader.tryLoad(report, ptAppMetaFilename, anchorClass);
        }

        applyNegotiationAuthValue(
            negotiationProperties,
            "authentication application",
            "setAuthenticationApplication",
            authApplicationOverride,
            authApplicationProperty,
            authApplicationEnv,
            ptAppMetaAuth == null ? null : ptAppMetaAuth.getApplicationId(),
            ptAppMetaAuth == null ? null : ptAppMetaAuth.describeSource(),
            report,
            false,
            ptAppMetaFilename
        );
        applyNegotiationAuthValue(
            negotiationProperties,
            "authentication secret",
            "setAuthenticationSecret",
            authSecretOverride,
            authSecretProperty,
            authSecretEnv,
            ptAppMetaAuth == null ? null : ptAppMetaAuth.getSharedKey(),
            ptAppMetaAuth == null ? null : ptAppMetaAuth.describeSource(),
            report,
            true,
            ptAppMetaFilename
        );
    }

    private static void applyNegotiationAuthValue(
        Object negotiationProperties,
        String label,
        String setterName,
        String explicitValue,
        String propertyName,
        String envName,
        String metadataValue,
        String metadataSourceDescription,
        BootstrapReport report,
        boolean secretValue,
        String ptAppMetaFilename
    ) {
        if (explicitValue != null) {
            if (setNegotiationStringProperty(negotiationProperties, setterName, explicitValue)) {
                report.addDetail(String.format("Authentication source for %s: explicit override via -D%s or %s (%s).", label, propertyName, envName, describeAppliedAuthValue(explicitValue, secretValue)));
            } else {
                report.addDetail(String.format("Authentication source for %s should have been an explicit override via -D%s or %s, but ConnectionNegotiationProperties did not expose %s(String).", label, propertyName, envName, setterName));
            }

            return;
        }

        if (metadataValue != null) {
            if (setNegotiationStringProperty(negotiationProperties, setterName, metadataValue)) {
                report.addDetail(String.format("Authentication source for %s: %s %s (%s).", label, ptAppMetaFilename, metadataSourceDescription, describeAppliedAuthValue(metadataValue, secretValue)));
            } else {
                report.addDetail(String.format("Authentication source for %s fell back to %s %s, but ConnectionNegotiationProperties did not expose %s(String); framework defaults remain in place.", label, ptAppMetaFilename, metadataSourceDescription, setterName));
            }

            return;
        }

        report.addDetail(String.format("Authentication source for %s: framework defaults remain in place because no explicit override or usable %s value was found.", label, ptAppMetaFilename));
    }

    private static String describeAppliedAuthValue(String value, boolean secretValue) {
        if (secretValue) {
            return String.format("present (%d characters)", Integer.valueOf(value.length()));
        }

        return value;
    }

    private static void applyConservativeNegotiationDefaults(Object negotiationProperties, BootstrapReport report, Class<?> anchorClass) {
        applyNegotiationIntDefault(negotiationProperties, "getVersion", "setVersion", "PTMP_VERSION", report, anchorClass);
        applyNegotiationIntDefault(negotiationProperties, "getEncoding", "setEncoding", "TEXT_ENCODING", report, anchorClass);
        applyNegotiationIntDefault(negotiationProperties, "getEncryption", "setEncryption", "NO_ENCRYPTION", report, anchorClass);
        applyNegotiationIntDefault(negotiationProperties, "getCompression", "setCompression", "NOT_COMPRESSED", report, anchorClass);
        applyNegotiationIntDefault(negotiationProperties, "getAuthentication", "setAuthentication", "CLEAR_TEXT_AUTH", report, anchorClass);
    }

    private static void applyNegotiationIntDefault(Object negotiationProperties, String getterName, String setterName, String constantName, BootstrapReport report, Class<?> anchorClass) {
        Integer currentValue = getNegotiationIntProperty(negotiationProperties, getterName);

        if (currentValue == null) {
            report.addDetail(String.format("Could not inspect ConnectionNegotiationProperties.%s() before considering default %s.", getterName, constantName));
            return;
        }

        if (currentValue.intValue() != 0) {
            return;
        }

        Integer defaultValue = resolveNegotiationConstant(constantName, anchorClass);

        if (defaultValue == null) {
            report.addDetail(String.format("Could not resolve ConnectionNegotiationProperties.%s for conservative defaulting.", constantName));
            return;
        }

        if (setNegotiationIntProperty(negotiationProperties, setterName, defaultValue.intValue())) {
            report.addDetail(String.format("Applied conservative negotiation default %s=%d because %s() was unset.", constantName, defaultValue, getterName));
        } else {
            report.addDetail(String.format("ConnectionNegotiationProperties did not expose %s(int) while applying default %s.", setterName, constantName));
        }
    }

    private static Method findOpenSessionMethod(PacketTracerSessionFactory sessionFactory, Class<?> negotiationClass) {
        Method[] methods = sessionFactory.getClass().getMethods();

        for (Method method : methods) {
            if (!"openSession".equals(method.getName())) {
                continue;
            }

            Class<?>[] parameterTypes = method.getParameterTypes();

            if (parameterTypes.length != 3) {
                continue;
            }

            if (!String.class.equals(parameterTypes[0])) {
                continue;
            }

            if (!Integer.TYPE.equals(parameterTypes[1]) && !Integer.class.equals(parameterTypes[1])) {
                continue;
            }

            if (!parameterTypes[2].isAssignableFrom(negotiationClass)) {
                continue;
            }

            return method;
        }

        return null;
    }

    private static boolean setNegotiationStringProperty(Object target, String methodName, String value) {
        try {
            Method method = target.getClass().getMethod(methodName, String.class);
            method.invoke(target, value);
            return true;
        } catch (Throwable throwable) {
            return false;
        }
    }

    private static boolean setNegotiationIntProperty(Object target, String methodName, int value) {
        try {
            Method method = target.getClass().getMethod(methodName, Integer.TYPE);
            method.invoke(target, Integer.valueOf(value));
            return true;
        } catch (Throwable throwable) {
            return false;
        }
    }

    private static Integer getNegotiationIntProperty(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);

            if (!(value instanceof Number)) {
                return null;
            }

            return Integer.valueOf(((Number) value).intValue());
        } catch (Throwable throwable) {
            return null;
        }
    }

    private static Integer resolveNegotiationConstant(String constantName, Class<?> anchorClass) {
        try {
            Class<?> negotiationClass = Class.forName(CONNECTION_NEGOTIATION_PROPERTIES_CLASS, false, anchorClass.getClassLoader());
            Object value = negotiationClass.getField(constantName).get(null);

            if (!(value instanceof Number)) {
                return null;
            }

            return Integer.valueOf(((Number) value).intValue());
        } catch (Throwable throwable) {
            return null;
        }
    }

    private static String describeNegotiationStringProperty(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);

            if (value == null) {
                return "unset";
            }

            String text = String.valueOf(value);
            return text.isEmpty() ? "empty" : text;
        } catch (Throwable throwable) {
            return String.format("unavailable (%s: %s)", throwable.getClass().getName(), ThrowableUtils.safeMessage(throwable));
        }
    }

    private static String describeNegotiationSecret(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);

            if (value == null) {
                return "unset";
            }

            String text = String.valueOf(value);

            if (text.isEmpty()) {
                return "empty";
            }

            return String.format("present (%d characters)", Integer.valueOf(text.length()));
        } catch (Throwable throwable) {
            return String.format("unavailable (%s: %s)", throwable.getClass().getName(), ThrowableUtils.safeMessage(throwable));
        }
    }

    private static String describeNegotiationValue(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            return value == null ? "unset" : String.valueOf(value);
        } catch (Throwable throwable) {
            return String.format("unavailable (%s: %s)", throwable.getClass().getName(), ThrowableUtils.safeMessage(throwable));
        }
    }

    public static String describeNegotiationProperties(Object negotiationProperties) {
        return String.format(
            "Negotiation properties summary: authApplication=%s, authSecret=%s, clientUid=%s, serverUid=%s, signature=%s, version=%s, authenticationMode=%s, keepAlivePeriod=%s",
            describeNegotiationStringProperty(negotiationProperties, "getAuthenticationApplication"),
            describeNegotiationSecret(negotiationProperties, "getAuthenticationSecret"),
            describeNegotiationStringProperty(negotiationProperties, "getClientUid"),
            describeNegotiationStringProperty(negotiationProperties, "getServerUid"),
            describeNegotiationStringProperty(negotiationProperties, "getSignature"),
            describeNegotiationValue(negotiationProperties, "getVersion"),
            describeNegotiationValue(negotiationProperties, "getAuthentication"),
            describeNegotiationValue(negotiationProperties, "getKeepAlivePeriod")
        );
    }

    public static final class NegotiationBootstrap {
        private final Class<?> negotiationClass;
        private final Object negotiationProperties;

        private NegotiationBootstrap(Class<?> negotiationClass, Object negotiationProperties) {
            this.negotiationClass = negotiationClass;
            this.negotiationProperties = negotiationProperties;
        }

        public Class<?> getNegotiationClass() {
            return negotiationClass;
        }

        public Object getNegotiationProperties() {
            return negotiationProperties;
        }
    }

    private static final class FrameworkConnectOptions {
        private final Object negotiationProperties;
        private final String sourceDescription;

        private FrameworkConnectOptions(Object negotiationProperties, String sourceDescription) {
            this.negotiationProperties = negotiationProperties;
            this.sourceDescription = sourceDescription;
        }

        private Object getNegotiationProperties() {
            return negotiationProperties;
        }

        private String getSourceDescription() {
            return sourceDescription;
        }
    }
}
