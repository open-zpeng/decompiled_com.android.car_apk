package org.junit.runner;

import org.junit.runner.manipulation.Filter;
/* loaded from: classes3.dex */
public interface FilterFactory {
    Filter createFilter(FilterFactoryParams filterFactoryParams) throws FilterNotCreatedException;

    /* loaded from: classes3.dex */
    public static class FilterNotCreatedException extends Exception {
        public FilterNotCreatedException(Exception exception) {
            super(exception.getMessage(), exception);
        }
    }
}
