import dev.local.wifimac.MacRules;
import dev.local.wifimac.SessionRule;
public class RulesTest {
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) {
        check(MacRules.normalize(" 00:11:22:AA:BB:CC ").equals("00:11:22:aa:bb:cc"));
        String[] bad = {null, "", "00:00:00:00:00:00", "02:00:00:00:00:00", "ff:ff:ff:ff:ff:ff",
            "01:11:22:33:44:55", "03:11:22:33:44:55", "02:xx:22:33:44:55", "02:11:22:33:44",
            "02:11:22:33:44:55;reboot", "02:11:22:33:44:55\nextra"};
        for (String s : bad) {
            try { MacRules.normalize(s); throw new AssertionError("Accepted invalid address: " + s); }
            catch (IllegalArgumentException expected) {}
        }
        for (int i = 0; i < 5000; i++) {
            String mac = MacRules.random(); check(mac.equals(MacRules.normalize(mac)));
            check((Integer.parseInt(mac.substring(0, 2), 16) & 3) == 2);
        }
        SessionRule r = new SessionRule("7|\"Example\"WPA_PSK", "02:11:22:33:44:55");
        check(r.matches("7|\"Example\"WPA_PSK", true));
        check(!r.matches("7|\"Example\"WPA_PSK", false)); // Secondary interface.
        check(!r.matches("8|\"Example\"WPA_PSK", true));  // Same SSID, different saved network.
        check(!r.matches("7|\"Example\"NONE", true));     // Same SSID, different security.
        check(!r.matches("7|\"Other\"WPA_PSK", true));
        check(!r.matches(null, true));
        System.out.println("PASS: input validation, 5000 generated addresses, primary/network/profile isolation");
    }
}
