package ee.carlrobert.codegpt.codedown;

import ee.carlrobert.codegpt.codedown.filters.CompositeElementFilter;
import ee.carlrobert.codegpt.codedown.filters.ImplementationWhiteList;

public class CodeDownParams {
    public static final String PACKAGE_INDICATOR = ".*";
    public static final String RECURSIVE_PACKAGE_INDICATOR = ".**";

    private int _maxDepth = 3;
    private boolean _allowRecursion = false;
    @Deprecated(since = "2.2.0", forRemoval = true)
    private boolean smartInterface = false;
    private final CompositeElementFilter _methodFilter = new CompositeElementFilter();
    private final ImplementationWhiteList _implFilter = new ImplementationWhiteList();

    public CodeDownParams() {
    }

    public int getMaxDepth() {
        return _maxDepth;
    }

    public void setMaxDepth(int maxDepth) {
        this._maxDepth = maxDepth;
    }

    public boolean isNotAllowRecursion() {
        return !_allowRecursion;
    }

    public void setAllowRecursion(boolean allowRecursion) {
        this._allowRecursion = allowRecursion;
    }

    @Deprecated(since = "2.2.0", forRemoval = true)
    public boolean isSmartInterface() {
        return smartInterface;
    }

    @Deprecated(since = "2.2.0", forRemoval = true)
    public void setSmartInterface(boolean smartInterface) {
        this.smartInterface = smartInterface;
    }

    public CompositeElementFilter getMethodFilter() {
        return _methodFilter;
    }

    public ImplementationWhiteList getImplementationWhiteList() {
        return _implFilter;
    }
}

