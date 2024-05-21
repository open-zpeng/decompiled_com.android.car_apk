package junit.runner;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.NumberFormat;
import java.util.Properties;
import junit.framework.AssertionFailedError;
import junit.framework.Test;
import junit.framework.TestListener;
import junit.framework.TestSuite;
/* loaded from: classes3.dex */
public abstract class BaseTestRunner implements TestListener {
    public static final String SUITE_METHODNAME = "suite";
    private static Properties fPreferences;
    static boolean fgFilterStack = true;
    static int fgMaxMessageLength;
    boolean fLoading = true;

    protected abstract void runFailed(String str);

    public abstract void testEnded(String str);

    public abstract void testFailed(int i, Test test, Throwable th);

    public abstract void testStarted(String str);

    static {
        fgMaxMessageLength = 500;
        fgMaxMessageLength = getPreference("maxmessage", fgMaxMessageLength);
    }

    @Override // junit.framework.TestListener
    public synchronized void startTest(Test test) {
        testStarted(test.toString());
    }

    protected static void setPreferences(Properties preferences) {
        fPreferences = preferences;
    }

    protected static Properties getPreferences() {
        if (fPreferences == null) {
            fPreferences = new Properties();
            fPreferences.put("loading", "true");
            fPreferences.put("filterstack", "true");
            readPreferences();
        }
        return fPreferences;
    }

    public static void savePreferences() throws IOException {
        FileOutputStream fos = new FileOutputStream(getPreferencesFile());
        try {
            getPreferences().store(fos, "");
        } finally {
            fos.close();
        }
    }

    public static void setPreference(String key, String value) {
        getPreferences().put(key, value);
    }

    @Override // junit.framework.TestListener
    public synchronized void endTest(Test test) {
        testEnded(test.toString());
    }

    @Override // junit.framework.TestListener
    public synchronized void addError(Test test, Throwable e) {
        testFailed(1, test, e);
    }

    @Override // junit.framework.TestListener
    public synchronized void addFailure(Test test, AssertionFailedError e) {
        testFailed(2, test, e);
    }

    public Test getTest(String suiteClassName) {
        if (suiteClassName.length() <= 0) {
            clearStatus();
            return null;
        }
        try {
            Class<?> testClass = loadSuiteClass(suiteClassName);
            try {
                Method suiteMethod = testClass.getMethod(SUITE_METHODNAME, new Class[0]);
                if (!Modifier.isStatic(suiteMethod.getModifiers())) {
                    runFailed("Suite() method must be static");
                    return null;
                }
                try {
                    Test test = (Test) suiteMethod.invoke(null, new Object[0]);
                    if (test == null) {
                        return test;
                    }
                    clearStatus();
                    return test;
                } catch (IllegalAccessException e) {
                    runFailed("Failed to invoke suite():" + e.toString());
                    return null;
                } catch (InvocationTargetException e2) {
                    runFailed("Failed to invoke suite():" + e2.getTargetException().toString());
                    return null;
                }
            } catch (Exception e3) {
                clearStatus();
                return new TestSuite(testClass);
            }
        } catch (ClassNotFoundException e4) {
            String clazz = e4.getMessage();
            if (clazz == null) {
                clazz = suiteClassName;
            }
            runFailed("Class not found \"" + clazz + "\"");
            return null;
        } catch (Exception e5) {
            runFailed("Error: " + e5.toString());
            return null;
        }
    }

    public String elapsedTimeAsString(long runTime) {
        return NumberFormat.getInstance().format(runTime / 1000.0d);
    }

    protected String processArguments(String[] args) {
        String suiteName = null;
        int i = 0;
        while (i < args.length) {
            if (args[i].equals("-noloading")) {
                setLoading(false);
            } else if (args[i].equals("-nofilterstack")) {
                fgFilterStack = false;
            } else if (args[i].equals("-c")) {
                if (args.length > i + 1) {
                    suiteName = extractClassName(args[i + 1]);
                } else {
                    System.out.println("Missing Test class name");
                }
                i++;
            } else {
                suiteName = args[i];
            }
            i++;
        }
        return suiteName;
    }

    public void setLoading(boolean enable) {
        this.fLoading = enable;
    }

    public String extractClassName(String className) {
        if (className.startsWith("Default package for")) {
            return className.substring(className.lastIndexOf(".") + 1);
        }
        return className;
    }

    public static String truncate(String s) {
        if (fgMaxMessageLength != -1 && s.length() > fgMaxMessageLength) {
            return s.substring(0, fgMaxMessageLength) + "...";
        }
        return s;
    }

    /* JADX INFO: Access modifiers changed from: protected */
    public Class<?> loadSuiteClass(String suiteClassName) throws ClassNotFoundException {
        return Class.forName(suiteClassName);
    }

    protected void clearStatus() {
    }

    protected boolean useReloadingTestSuiteLoader() {
        return getPreference("loading").equals("true") && this.fLoading;
    }

    private static File getPreferencesFile() {
        String home = System.getProperty("user.home");
        return new File(home, "junit.properties");
    }

    /* JADX WARN: Unsupported multi-entry loop pattern (BACK_EDGE: B:6:0x0023 -> B:21:0x0037). Please submit an issue!!! */
    private static void readPreferences() {
        InputStream is = null;
        try {
            try {
                is = new FileInputStream(getPreferencesFile());
                setPreferences(new Properties(getPreferences()));
                getPreferences().load(is);
                is.close();
            } catch (IOException e) {
            }
        } catch (IOException e2) {
            if (is != null) {
                is.close();
            }
        } catch (Throwable th) {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e3) {
                }
            }
            throw th;
        }
    }

    public static String getPreference(String key) {
        return getPreferences().getProperty(key);
    }

    public static int getPreference(String key, int dflt) {
        String value = getPreference(key);
        if (value == null) {
            return dflt;
        }
        try {
            int intValue = Integer.parseInt(value);
            return intValue;
        } catch (NumberFormatException e) {
            return dflt;
        }
    }

    public static String getFilteredTrace(Throwable e) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        e.printStackTrace(writer);
        String trace = stringWriter.toString();
        return getFilteredTrace(trace);
    }

    public static String getFilteredTrace(String stack) {
        if (showStackRaw()) {
            return stack;
        }
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        StringReader sr = new StringReader(stack);
        BufferedReader br = new BufferedReader(sr);
        while (true) {
            try {
                String line = br.readLine();
                if (line != null) {
                    if (!filterLine(line)) {
                        pw.println(line);
                    }
                } else {
                    return sw.toString();
                }
            } catch (Exception e) {
                return stack;
            }
        }
    }

    protected static boolean showStackRaw() {
        return (getPreference("filterstack").equals("true") && fgFilterStack) ? false : true;
    }

    static boolean filterLine(String line) {
        String[] patterns = {"junit.framework.TestCase", "junit.framework.TestResult", "junit.framework.TestSuite", "junit.framework.Assert.", "junit.swingui.TestRunner", "junit.awtui.TestRunner", "junit.textui.TestRunner", "java.lang.reflect.Method.invoke("};
        for (String str : patterns) {
            if (line.indexOf(str) > 0) {
                return true;
            }
        }
        return false;
    }
}
