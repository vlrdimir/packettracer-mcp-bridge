package packettracer.exapp.core;

import java.util.ArrayList;
import java.util.List;

public final class BootstrapReport {
    private final List<String> details = new ArrayList<String>();
    private boolean successful;
    private boolean partiallyBlocked;

    public void addDetail(String detail) {
        details.add(detail);
    }

    public List<String> getDetails() {
        return details;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public boolean isPartiallyBlocked() {
        return partiallyBlocked && !successful;
    }

    public void markSuccessful() {
        successful = true;
        partiallyBlocked = false;
    }

    public void markPartiallyBlocked() {
        partiallyBlocked = true;
    }
}
