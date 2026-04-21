package org.apache.commons.logging;

public interface Log {
    boolean isDebugEnabled();

    boolean isErrorEnabled();

    boolean isFatalEnabled();

    boolean isInfoEnabled();

    boolean isTraceEnabled();

    boolean isWarnEnabled();

    void trace(Object message);

    void trace(Object message, Throwable throwable);

    void debug(Object message);

    void debug(Object message, Throwable throwable);

    void info(Object message);

    void info(Object message, Throwable throwable);

    void warn(Object message);

    void warn(Object message, Throwable throwable);

    void error(Object message);

    void error(Object message, Throwable throwable);

    void fatal(Object message);

    void fatal(Object message, Throwable throwable);
}
