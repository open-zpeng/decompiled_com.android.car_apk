package junit.runner;
/* loaded from: classes3.dex */
public class Version {
    private Version() {
    }

    public static String id() {
        return "4.12-SNAPSHOT";
    }

    public static void main(String[] args) {
        System.out.println(id());
    }
}
