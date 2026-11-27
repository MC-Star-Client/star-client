package dev.performanceclient.profile;

import java.util.Locale;
import java.util.Map;

/** JVM/Umgebungs-Erkennung. Keine nativen Grafikfunktionen beim Mod-Start aufrufen. */
public record PlatformInfo(String os, String architecture, String windowSystem) {
    public static PlatformInfo detect() {
        String name = System.getProperty("os.name", "unknown").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "unknown").toLowerCase(Locale.ROOT);
        String system = name.contains("win") ? "windows" :
                name.contains("mac") ? "macos" :
                name.contains("linux") ? "linux" : "other";
        String cpu = switch (arch) {
            case "amd64", "x86_64" -> "x64";
            case "aarch64", "arm64" -> "arm64";
            default -> arch;
        };
        Map<String, String> env = System.getenv();
        String window = switch (system) {
            case "linux" -> {
                String session = env.getOrDefault("XDG_SESSION_TYPE", "").toLowerCase(Locale.ROOT);
                if (session.equals("wayland")) yield "wayland-session";
                if (session.equals("x11")) yield "x11-session";
                yield "unknown-session";
            }
            case "windows" -> "win32";
            case "macos" -> "cocoa";
            default -> "unknown";
        };
        // PRÜFEN: Die Sitzungsart beweist nicht, ob Minecraft nativ Wayland oder XWayland nutzt.
        return new PlatformInfo(system, cpu, window);
    }
}
