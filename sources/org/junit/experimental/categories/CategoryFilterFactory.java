package org.junit.experimental.categories;

import java.util.ArrayList;
import java.util.List;
import org.junit.internal.Classes;
import org.junit.runner.FilterFactory;
import org.junit.runner.FilterFactoryParams;
import org.junit.runner.manipulation.Filter;
/* loaded from: classes3.dex */
abstract class CategoryFilterFactory implements FilterFactory {
    protected abstract Filter createFilter(List<Class<?>> list);

    @Override // org.junit.runner.FilterFactory
    public Filter createFilter(FilterFactoryParams params) throws FilterFactory.FilterNotCreatedException {
        try {
            return createFilter(parseCategories(params.getArgs()));
        } catch (ClassNotFoundException e) {
            throw new FilterFactory.FilterNotCreatedException(e);
        }
    }

    private List<Class<?>> parseCategories(String categories) throws ClassNotFoundException {
        String[] split;
        List<Class<?>> categoryClasses = new ArrayList<>();
        for (String category : categories.split(",")) {
            Class<?> categoryClass = Classes.getClass(category);
            categoryClasses.add(categoryClass);
        }
        return categoryClasses;
    }
}
