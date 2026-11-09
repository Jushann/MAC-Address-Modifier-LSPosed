package dev.local.wifimac;
import java.util.Locale;
import java.security.SecureRandom;
public final class MacRules {
    private MacRules() {}
    public static String normalize(String input) {
        if (input == null) throw new IllegalArgumentException("请输入 MAC 地址");
        String mac = input.trim().toLowerCase(Locale.ROOT);
        if (!mac.matches("[0-9a-f]{2}(:[0-9a-f]{2}){5}"))
            throw new IllegalArgumentException("格式应为 02:12:34:56:78:9a");
        if ((Integer.parseInt(mac.substring(0, 2), 16) & 1) != 0)
            throw new IllegalArgumentException("不能使用组播或广播地址");
        if (mac.equals("00:00:00:00:00:00") || mac.equals("02:00:00:00:00:00"))
            throw new IllegalArgumentException("不能使用全零或 Android 占位地址");
        return mac;
    }
    public static String random() {
        byte[] b = new byte[6]; new SecureRandom().nextBytes(b);
        b[0] = (byte) ((b[0] & 0xfc) | 2);
        StringBuilder s = new StringBuilder();
        for (byte value : b) {
            if (s.length() > 0) s.append(':');
            s.append(String.format(Locale.ROOT, "%02x", value & 255));
        }
        return s.toString();
    }
}
