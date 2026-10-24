package org.whitecn.net.theDeadlies;

public enum ConfigKeys {
    LANGUAGE("language"),
    INVITE_TIMEOUT_TIME("invite_timeout_time");

    private final String path;

    ConfigKeys(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }
}
