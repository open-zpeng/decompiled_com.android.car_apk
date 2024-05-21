package junit.framework;
/* loaded from: classes3.dex */
public class ComparisonCompactor {
    private static final String DELTA_END = "]";
    private static final String DELTA_START = "[";
    private static final String ELLIPSIS = "...";
    private String fActual;
    private int fContextLength;
    private String fExpected;
    private int fPrefix;
    private int fSuffix;

    public ComparisonCompactor(int contextLength, String expected, String actual) {
        this.fContextLength = contextLength;
        this.fExpected = expected;
        this.fActual = actual;
    }

    public String compact(String message) {
        if (this.fExpected == null || this.fActual == null || areStringsEqual()) {
            String expected = this.fExpected;
            return Assert.format(message, expected, this.fActual);
        }
        findCommonPrefix();
        findCommonSuffix();
        String expected2 = compactString(this.fExpected);
        String actual = compactString(this.fActual);
        return Assert.format(message, expected2, actual);
    }

    private String compactString(String source) {
        String result = DELTA_START + source.substring(this.fPrefix, (source.length() - this.fSuffix) + 1) + DELTA_END;
        if (this.fPrefix > 0) {
            result = computeCommonPrefix() + result;
        }
        if (this.fSuffix > 0) {
            return result + computeCommonSuffix();
        }
        return result;
    }

    private void findCommonPrefix() {
        this.fPrefix = 0;
        int end = Math.min(this.fExpected.length(), this.fActual.length());
        while (true) {
            int i = this.fPrefix;
            if (i < end && this.fExpected.charAt(i) == this.fActual.charAt(this.fPrefix)) {
                this.fPrefix++;
            } else {
                return;
            }
        }
    }

    private void findCommonSuffix() {
        int expectedSuffix = this.fExpected.length() - 1;
        int actualSuffix = this.fActual.length() - 1;
        while (true) {
            int i = this.fPrefix;
            if (actualSuffix < i || expectedSuffix < i || this.fExpected.charAt(expectedSuffix) != this.fActual.charAt(actualSuffix)) {
                break;
            }
            actualSuffix--;
            expectedSuffix--;
        }
        this.fSuffix = this.fExpected.length() - expectedSuffix;
    }

    private String computeCommonPrefix() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.fPrefix > this.fContextLength ? ELLIPSIS : "");
        sb.append(this.fExpected.substring(Math.max(0, this.fPrefix - this.fContextLength), this.fPrefix));
        return sb.toString();
    }

    private String computeCommonSuffix() {
        int end = Math.min((this.fExpected.length() - this.fSuffix) + 1 + this.fContextLength, this.fExpected.length());
        StringBuilder sb = new StringBuilder();
        String str = this.fExpected;
        sb.append(str.substring((str.length() - this.fSuffix) + 1, end));
        sb.append((this.fExpected.length() - this.fSuffix) + 1 < this.fExpected.length() - this.fContextLength ? ELLIPSIS : "");
        return sb.toString();
    }

    private boolean areStringsEqual() {
        return this.fExpected.equals(this.fActual);
    }
}
