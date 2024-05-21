package org.junit.runner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.internal.Classes;
import org.junit.runner.FilterFactory;
import org.junit.runner.manipulation.Filter;
import org.junit.runners.model.InitializationError;
/* JADX INFO: Access modifiers changed from: package-private */
/* loaded from: classes3.dex */
public class JUnitCommandLineParseResult {
    private final List<String> filterSpecs = new ArrayList();
    private final List<Class<?>> classes = new ArrayList();
    private final List<Throwable> parserErrors = new ArrayList();

    JUnitCommandLineParseResult() {
    }

    public List<String> getFilterSpecs() {
        return Collections.unmodifiableList(this.filterSpecs);
    }

    public List<Class<?>> getClasses() {
        return Collections.unmodifiableList(this.classes);
    }

    public static JUnitCommandLineParseResult parse(String[] args) {
        JUnitCommandLineParseResult result = new JUnitCommandLineParseResult();
        result.parseArgs(args);
        return result;
    }

    private void parseArgs(String[] args) {
        parseParameters(parseOptions(args));
    }

    /* JADX WARN: Code restructure failed: missing block: B:29:0x0097, code lost:
        return new java.lang.String[0];
     */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    java.lang.String[] parseOptions(java.lang.String... r7) {
        /*
            r6 = this;
            r0 = 0
        L1:
            int r1 = r7.length
            if (r0 == r1) goto L94
            r1 = r7[r0]
            java.lang.String r2 = "--"
            boolean r3 = r1.equals(r2)
            if (r3 == 0) goto L16
            int r2 = r0 + 1
            int r3 = r7.length
            java.lang.String[] r2 = r6.copyArray(r7, r2, r3)
            return r2
        L16:
            boolean r2 = r1.startsWith(r2)
            if (r2 == 0) goto L8e
            java.lang.String r2 = "--filter="
            boolean r2 = r1.startsWith(r2)
            java.lang.String r3 = "--filter"
            if (r2 != 0) goto L4e
            boolean r2 = r1.equals(r3)
            if (r2 == 0) goto L2d
            goto L4e
        L2d:
            java.util.List<java.lang.Throwable> r2 = r6.parserErrors
            org.junit.runner.JUnitCommandLineParseResult$CommandLineParserError r3 = new org.junit.runner.JUnitCommandLineParseResult$CommandLineParserError
            java.lang.StringBuilder r4 = new java.lang.StringBuilder
            r4.<init>()
            java.lang.String r5 = "JUnit knows nothing about the "
            r4.append(r5)
            r4.append(r1)
            java.lang.String r5 = " option"
            r4.append(r5)
            java.lang.String r4 = r4.toString()
            r3.<init>(r4)
            r2.add(r3)
            goto L8a
        L4e:
            boolean r2 = r1.equals(r3)
            if (r2 == 0) goto L78
            int r0 = r0 + 1
            int r2 = r7.length
            if (r0 >= r2) goto L5c
            r2 = r7[r0]
            goto L84
        L5c:
            java.util.List<java.lang.Throwable> r2 = r6.parserErrors
            org.junit.runner.JUnitCommandLineParseResult$CommandLineParserError r3 = new org.junit.runner.JUnitCommandLineParseResult$CommandLineParserError
            java.lang.StringBuilder r4 = new java.lang.StringBuilder
            r4.<init>()
            r4.append(r1)
            java.lang.String r5 = " value not specified"
            r4.append(r5)
            java.lang.String r4 = r4.toString()
            r3.<init>(r4)
            r2.add(r3)
            goto L94
        L78:
            r2 = 61
            int r2 = r1.indexOf(r2)
            int r2 = r2 + 1
            java.lang.String r2 = r1.substring(r2)
        L84:
            java.util.List<java.lang.String> r3 = r6.filterSpecs
            r3.add(r2)
        L8a:
            int r0 = r0 + 1
            goto L1
        L8e:
            int r2 = r7.length
            java.lang.String[] r2 = r6.copyArray(r7, r0, r2)
            return r2
        L94:
            r0 = 0
            java.lang.String[] r0 = new java.lang.String[r0]
            return r0
        */
        throw new UnsupportedOperationException("Method not decompiled: org.junit.runner.JUnitCommandLineParseResult.parseOptions(java.lang.String[]):java.lang.String[]");
    }

    private String[] copyArray(String[] args, int from, int to) {
        ArrayList<String> result = new ArrayList<>();
        for (int j = from; j != to; j++) {
            result.add(args[j]);
        }
        int j2 = result.size();
        return (String[]) result.toArray(new String[j2]);
    }

    void parseParameters(String[] args) {
        for (String arg : args) {
            try {
                this.classes.add(Classes.getClass(arg));
            } catch (ClassNotFoundException e) {
                this.parserErrors.add(new IllegalArgumentException("Could not find class [" + arg + "]", e));
            }
        }
    }

    private Request errorReport(Throwable cause) {
        return Request.errorReport(JUnitCommandLineParseResult.class, cause);
    }

    public Request createRequest(Computer computer) {
        if (this.parserErrors.isEmpty()) {
            List<Class<?>> list = this.classes;
            Request request = Request.classes(computer, (Class[]) list.toArray(new Class[list.size()]));
            return applyFilterSpecs(request);
        }
        return errorReport(new InitializationError(this.parserErrors));
    }

    private Request applyFilterSpecs(Request request) {
        try {
            for (String filterSpec : this.filterSpecs) {
                Filter filter = FilterFactories.createFilterFromFilterSpec(request, filterSpec);
                request = request.filterWith(filter);
            }
            return request;
        } catch (FilterFactory.FilterNotCreatedException e) {
            return errorReport(e);
        }
    }

    /* loaded from: classes3.dex */
    public static class CommandLineParserError extends Exception {
        private static final long serialVersionUID = 1;

        public CommandLineParserError(String message) {
            super(message);
        }
    }
}
