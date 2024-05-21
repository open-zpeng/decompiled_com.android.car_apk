package junit.textui;

import androidx.mediarouter.media.MediaRouteProviderProtocol;
import java.io.PrintStream;
import java.text.NumberFormat;
import java.util.Enumeration;
import junit.framework.AssertionFailedError;
import junit.framework.Test;
import junit.framework.TestFailure;
import junit.framework.TestListener;
import junit.framework.TestResult;
import junit.runner.BaseTestRunner;
/* loaded from: classes3.dex */
public class ResultPrinter implements TestListener {
    int fColumn = 0;
    PrintStream fWriter;

    public ResultPrinter(PrintStream writer) {
        this.fWriter = writer;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public synchronized void print(TestResult result, long runTime) {
        printHeader(runTime);
        printErrors(result);
        printFailures(result);
        printFooter(result);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void printWaitPrompt() {
        getWriter().println();
        getWriter().println("<RETURN> to continue");
    }

    protected void printHeader(long runTime) {
        getWriter().println();
        PrintStream writer = getWriter();
        writer.println("Time: " + elapsedTimeAsString(runTime));
    }

    protected void printErrors(TestResult result) {
        printDefects(result.errors(), result.errorCount(), MediaRouteProviderProtocol.SERVICE_DATA_ERROR);
    }

    protected void printFailures(TestResult result) {
        printDefects(result.failures(), result.failureCount(), "failure");
    }

    protected void printDefects(Enumeration<TestFailure> booBoos, int count, String type) {
        if (count == 0) {
            return;
        }
        if (count == 1) {
            PrintStream writer = getWriter();
            writer.println("There was " + count + " " + type + ":");
        } else {
            PrintStream writer2 = getWriter();
            writer2.println("There were " + count + " " + type + "s:");
        }
        int i = 1;
        while (booBoos.hasMoreElements()) {
            printDefect(booBoos.nextElement(), i);
            i++;
        }
    }

    public void printDefect(TestFailure booBoo, int count) {
        printDefectHeader(booBoo, count);
        printDefectTrace(booBoo);
    }

    protected void printDefectHeader(TestFailure booBoo, int count) {
        PrintStream writer = getWriter();
        writer.print(count + ") " + booBoo.failedTest());
    }

    protected void printDefectTrace(TestFailure booBoo) {
        getWriter().print(BaseTestRunner.getFilteredTrace(booBoo.trace()));
    }

    protected void printFooter(TestResult result) {
        if (result.wasSuccessful()) {
            getWriter().println();
            getWriter().print("OK");
            PrintStream writer = getWriter();
            StringBuilder sb = new StringBuilder();
            sb.append(" (");
            sb.append(result.runCount());
            sb.append(" test");
            sb.append(result.runCount() == 1 ? "" : "s");
            sb.append(")");
            writer.println(sb.toString());
        } else {
            getWriter().println();
            getWriter().println("FAILURES!!!");
            PrintStream writer2 = getWriter();
            writer2.println("Tests run: " + result.runCount() + ",  Failures: " + result.failureCount() + ",  Errors: " + result.errorCount());
        }
        getWriter().println();
    }

    protected String elapsedTimeAsString(long runTime) {
        return NumberFormat.getInstance().format(runTime / 1000.0d);
    }

    public PrintStream getWriter() {
        return this.fWriter;
    }

    @Override // junit.framework.TestListener
    public void addError(Test test, Throwable e) {
        getWriter().print("E");
    }

    @Override // junit.framework.TestListener
    public void addFailure(Test test, AssertionFailedError t) {
        getWriter().print("F");
    }

    @Override // junit.framework.TestListener
    public void endTest(Test test) {
    }

    @Override // junit.framework.TestListener
    public void startTest(Test test) {
        getWriter().print(".");
        int i = this.fColumn;
        this.fColumn = i + 1;
        if (i >= 40) {
            getWriter().println();
            this.fColumn = 0;
        }
    }
}
