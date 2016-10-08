package org.jocean.zkoss.builder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.lang.Objects;
import org.zkoss.zul.AbstractListModel;
import org.zkoss.zul.ListModel;
import org.zkoss.zul.event.ListDataEvent;
import org.zkoss.zul.ext.Sortable;

import rx.functions.Action1;
import rx.functions.Func0;
import rx.functions.Func2;

public class ZModels {
    
    @SuppressWarnings("unused")
    private static final Logger LOG = 
            LoggerFactory.getLogger(ZModels.class);
    
    private ZModels() {
        throw new IllegalStateException("No instances!");
    }
    
    public static <T> Func2<Integer, Integer, List<T>> fetchPageOf(final T[] array) {
        return new Func2<Integer, Integer, List<T>>() {
            @Override
            public List<T> call(final Integer offset, final Integer count) {
                return Arrays.asList(Arrays.copyOfRange(array, offset, offset + count));
            }};
    }
    
    public static <T> Func0<Integer> fetchTotalSizeOf(final T[] array) {
        return new Func0<Integer>() {
            @Override
            public Integer call() {
                return array.length;
            }};
    }
    
    public static <T> Action1<Comparator<T>> sortModelOf(final T[] array) {
        return new Action1<Comparator<T>>() {
            @Override
            public void call(final Comparator<T> cmpr) {
                Arrays.sort(array, cmpr);
            }};
    }
    
    public static <T> ListModel<T> buildListModel(
            final int countPerPage,
            final Func2<Integer, Integer, List<T>> fetchPage,
            final Func0<Integer> fetchTotalSize
            ) {
        return buildListModel(countPerPage, fetchPage, fetchTotalSize, null);
    }
    
    public static <T> ListModel<T> buildListModel(
            final int countPerPage,
            final Func2<Integer, Integer, List<T>> fetchPage,
            final Func0<Integer> fetchTotalSize,
            final Action1<Comparator<T>> sortModel
            ) {
        class ListModelImpl extends AbstractListModel<T> implements Sortable<T> {
            private final List<T> _cache = new ArrayList<>();
            private int _currentOffset = -countPerPage;
            private int _totalSize = -1;
            private static final long serialVersionUID = 1L;

            private Comparator<T> _sorting;
            private boolean _sortDir;
            
            @Override
            public T getElementAt(final int index) {
                if ((index < _currentOffset)
                  || (index >= _currentOffset + countPerPage)) {
                    _currentOffset = (index / countPerPage) * countPerPage;
                    _cache.clear();
                    _cache.addAll(fetchPage.call(_currentOffset, countPerPage));
                }
                return _cache.get(index % countPerPage);
            }

            @Override
            public int getSize() {
                if (_totalSize < 0) {
                    _totalSize = fetchTotalSize.call();
                }
                return _totalSize;
            }
            
            @Override
            public void sort(final Comparator<T> cmpr, final boolean ascending) {
                if (null != sortModel) {
                    sortModel.call(cmpr);
                    _sorting = cmpr;
                    _sortDir = ascending;
                    _currentOffset = -countPerPage;
                    _totalSize = -1;
                    _cache.clear();
                    fireEvent(ListDataEvent.STRUCTURE_CHANGED, -1, -1);
                }
            }

            @Override
            public String getSortDirection(final Comparator<T> cmpr) {
                if (Objects.equals(_sorting, cmpr))
                    return _sortDir ?
                            "ascending" : "descending";
                return "natural";
            }
            
        }
        return new ListModelImpl();
    }
}
