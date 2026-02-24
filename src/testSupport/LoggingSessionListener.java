package testSupport;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

public class LoggingSessionListener implements LauncherSessionListener {
    @Override
    public void launcherSessionClosed(LauncherSession session) {
        LoggingExtension.launcherSessionListenerFired = true;
        LoggingExtension ext = LoggingExtension.instance;
        if (ext == null) return;
        ext.doSessionFlush();
    }
}
