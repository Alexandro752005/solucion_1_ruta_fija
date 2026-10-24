import java.io.ObjectStreamClass;

/** Inspects serial descriptors without constructing application objects. */
public final class SerialUidAudit {
    public static void main(String[] args) throws ClassNotFoundException {
        for (String name : args) {
            Class<?> type = Class.forName(name, false, ClassLoader.getSystemClassLoader());
            ObjectStreamClass descriptor = ObjectStreamClass.lookup(type);
            if (descriptor == null) throw new IllegalArgumentException("SERIALIZABLE_TYPE_REQUIRED");
            System.out.println(name + "=" + descriptor.getSerialVersionUID());
        }
    }
}
