package packettracer.exapp.utils;

import java.lang.reflect.InvocationTargetException;

public final class ThrowableUtils {
    private ThrowableUtils() {
    }

    public static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isEmpty() ? "(no message)" : message;
    }

    public static String describeThrowable(Throwable throwable) {
        Throwable rootCause = throwable;

        if (throwable instanceof InvocationTargetException && throwable.getCause() != null) {
            rootCause = throwable.getCause();
        }

        if (rootCause == throwable) {
            return String.format("%s: %s", throwable.getClass().getName(), safeMessage(throwable));
        }

        return String.format("%s: %s (root cause %s: %s)", throwable.getClass().getName(), safeMessage(throwable), rootCause.getClass().getName(), safeMessage(rootCause));
    }

    public static String firstConfiguredValue(String firstCandidate, String secondCandidate) {
        if (firstCandidate != null) {
            String trimmedValue = firstCandidate.trim();

            if (!trimmedValue.isEmpty()) {
                return trimmedValue;
            }
        }

        if (secondCandidate != null) {
            String trimmedValue = secondCandidate.trim();

            if (!trimmedValue.isEmpty()) {
                return trimmedValue;
            }
        }

        return null;
    }
}
