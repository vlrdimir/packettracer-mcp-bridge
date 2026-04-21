package org.apache.commons.logging;

import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class LogFactory {
    private LogFactory() {
    }

    public static Log getLog(Class<?> clazz) {
        String loggerName = clazz == null ? LogFactory.class.getName() : clazz.getName();
        return new JulLog(Logger.getLogger(loggerName));
    }

    public static Log getLog(String name) {
        String loggerName = name == null || name.isEmpty() ? LogFactory.class.getName() : name;
        return new JulLog(Logger.getLogger(loggerName));
    }

    private static final class JulLog implements Log {
        private final Logger logger;

        private JulLog(Logger logger) {
            this.logger = logger;
        }

        @Override
        public boolean isDebugEnabled() {
            return logger.isLoggable(Level.FINE);
        }

        @Override
        public boolean isErrorEnabled() {
            return logger.isLoggable(Level.SEVERE);
        }

        @Override
        public boolean isFatalEnabled() {
            return logger.isLoggable(Level.SEVERE);
        }

        @Override
        public boolean isInfoEnabled() {
            return logger.isLoggable(Level.INFO);
        }

        @Override
        public boolean isTraceEnabled() {
            return logger.isLoggable(Level.FINER);
        }

        @Override
        public boolean isWarnEnabled() {
            return logger.isLoggable(Level.WARNING);
        }

        @Override
        public void trace(Object message) {
            log(Level.FINER, message, null);
        }

        @Override
        public void trace(Object message, Throwable throwable) {
            log(Level.FINER, message, throwable);
        }

        @Override
        public void debug(Object message) {
            log(Level.FINE, message, null);
        }

        @Override
        public void debug(Object message, Throwable throwable) {
            log(Level.FINE, message, throwable);
        }

        @Override
        public void info(Object message) {
            log(Level.INFO, message, null);
        }

        @Override
        public void info(Object message, Throwable throwable) {
            log(Level.INFO, message, throwable);
        }

        @Override
        public void warn(Object message) {
            log(Level.WARNING, message, null);
        }

        @Override
        public void warn(Object message, Throwable throwable) {
            log(Level.WARNING, message, throwable);
        }

        @Override
        public void error(Object message) {
            log(Level.SEVERE, message, null);
        }

        @Override
        public void error(Object message, Throwable throwable) {
            log(Level.SEVERE, message, throwable);
        }

        @Override
        public void fatal(Object message) {
            log(Level.SEVERE, message, null);
        }

        @Override
        public void fatal(Object message, Throwable throwable) {
            log(Level.SEVERE, message, throwable);
        }

        private void log(Level level, Object message, Throwable throwable) {
            String renderedMessage = message == null ? "null" : message.toString();

            if (throwable == null) {
                logger.log(level, renderedMessage);
            } else {
                logger.log(level, renderedMessage, throwable);
            }
        }
    }
}
