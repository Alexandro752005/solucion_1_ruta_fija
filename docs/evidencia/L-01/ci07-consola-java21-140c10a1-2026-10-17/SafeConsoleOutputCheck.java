import java.io.Console;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/** Output-only diagnostic of the existing JAR adapter, with fictitious markers. */
public final class SafeConsoleOutputCheck {
    public static void main(String[] args) {
        Console console = System.console();
        System.out.println("SYNTHETIC_CONSOLE_DIAGNOSTIC_ONLY=true");
        System.out.println("JAVA_RUNTIME_VERSION=" + System.getProperty("java.runtime.version"));
        if (console == null) {
            System.out.println("CONSOLE_OUTPUT_DIAGNOSTIC=UNAVAILABLE_NO_CONSOLE");
            System.exit(2);
            return;
        }
        try {
            Class<?> adapter = Class.forName("pe.rutafija.identity.recoverycli.SuperAdminRecoveryCli$ConsoleTerminal");
            Constructor<?> constructor = adapter.getDeclaredConstructor(Console.class);
            constructor.setAccessible(true);
            Object terminal = constructor.newInstance(console);
            Method printf = adapter.getDeclaredMethod("printf", String.class, Object[].class);
            Method println = adapter.getDeclaredMethod("println", String.class);
            printf.setAccessible(true);
            println.setAccessible(true);
            printf.invoke(terminal, new Object[]{"SYNTHETIC_PRINTF_BEGIN%n", new Object[0]});
            println.invoke(terminal, "SYNTHETIC_PRINTLN_BEGIN");
            println.invoke(terminal, "SYNTHETIC_VALUE=" + "X".repeat(43));
            println.invoke(terminal, "SYNTHETIC_PRINTLN_END");
            printf.invoke(terminal, new Object[]{"SYNTHETIC_PRINTF_END%n", new Object[0]});
            System.out.println("CONSOLE_OUTPUT_DIAGNOSTIC_CALLS_COMPLETED=true");
            System.out.println("HTTP_DB_SMTP_PRIVATE_FILES=NONE");
        } catch (Exception failure) {
            System.out.println("CONSOLE_OUTPUT_DIAGNOSTIC=REFUSED_DETAILS_REDACTED");
            System.exit(1);
        }
    }
}
