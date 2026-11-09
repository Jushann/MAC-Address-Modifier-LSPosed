package dev.local.wifimac;
// In-memory only: no saved Wi-Fi profile or credential is modified.
public final class SessionRule {
    public final String key, mac;
    public SessionRule(String key, String mac) {
        if (key == null || key.isEmpty()) throw new IllegalArgumentException("缺少网络标识");
        this.key = key; this.mac = MacRules.normalize(mac);
    }
    public boolean matches(String candidate, boolean primary) {
        return primary && key.equals(candidate);
    }
}
