package com.pqsolutions.hdd_monitor.databinding;
import com.pqsolutions.hdd_monitor.R;
import com.pqsolutions.hdd_monitor.BR;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.view.View;
@SuppressWarnings("unchecked")
public class ItemPanelBindingImpl extends ItemPanelBinding  {

    @Nullable
    private static final androidx.databinding.ViewDataBinding.IncludedLayouts sIncludes;
    @Nullable
    private static final android.util.SparseIntArray sViewsWithIds;
    static {
        sIncludes = null;
        sViewsWithIds = new android.util.SparseIntArray();
        sViewsWithIds.put(R.id.relayAlarmaIcon, 3);
        sViewsWithIds.put(R.id.relayAlarmaText, 4);
        sViewsWithIds.put(R.id.relayProblemaIcon, 5);
        sViewsWithIds.put(R.id.relayProblemaText, 6);
        sViewsWithIds.put(R.id.relaySupervisionIcon, 7);
        sViewsWithIds.put(R.id.relaySupervisionText, 8);
    }
    // views
    @NonNull
    private final android.widget.LinearLayout mboundView0;
    // variables
    // values
    // listeners
    // Inverse Binding Event Handlers

    public ItemPanelBindingImpl(@Nullable androidx.databinding.DataBindingComponent bindingComponent, @NonNull View root) {
        this(bindingComponent, root, mapBindings(bindingComponent, root, 9, sIncludes, sViewsWithIds));
    }
    private ItemPanelBindingImpl(androidx.databinding.DataBindingComponent bindingComponent, View root, Object[] bindings) {
        super(bindingComponent, root, 0
            , (android.widget.TextView) bindings[2]
            , (android.widget.TextView) bindings[1]
            , (android.widget.ImageView) bindings[3]
            , (android.widget.TextView) bindings[4]
            , (android.widget.ImageView) bindings[5]
            , (android.widget.TextView) bindings[6]
            , (android.widget.ImageView) bindings[7]
            , (android.widget.TextView) bindings[8]
            );
        this.mboundView0 = (android.widget.LinearLayout) bindings[0];
        this.mboundView0.setTag(null);
        this.panelLocation.setTag(null);
        this.panelName.setTag(null);
        setRootTag(root);
        // listeners
        invalidateAll();
    }

    @Override
    public void invalidateAll() {
        synchronized(this) {
                mDirtyFlags = 0x2L;
        }
        requestRebind();
    }

    @Override
    public boolean hasPendingBindings() {
        synchronized(this) {
            if (mDirtyFlags != 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean setVariable(int variableId, @Nullable Object variable)  {
        boolean variableSet = true;
        if (BR.panel == variableId) {
            setPanel((com.pqsolutions.hdd_monitor.Panel) variable);
        }
        else {
            variableSet = false;
        }
            return variableSet;
    }

    public void setPanel(@Nullable com.pqsolutions.hdd_monitor.Panel Panel) {
        this.mPanel = Panel;
        synchronized(this) {
            mDirtyFlags |= 0x1L;
        }
        notifyPropertyChanged(BR.panel);
        super.requestRebind();
    }

    @Override
    protected boolean onFieldChange(int localFieldId, Object object, int fieldId) {
        switch (localFieldId) {
        }
        return false;
    }

    @Override
    protected void executeBindings() {
        long dirtyFlags = 0;
        synchronized(this) {
            dirtyFlags = mDirtyFlags;
            mDirtyFlags = 0;
        }
        com.pqsolutions.hdd_monitor.Panel panel = mPanel;
        java.lang.String PanelName1 = null;
        java.lang.String PanelLocation1 = null;

        if ((dirtyFlags & 0x3L) != 0) {



                if (panel != null) {
                    // read panel.name
                    PanelName1 = panel.getName();
                    // read panel.location
                    PanelLocation1 = panel.getLocation();
                }
        }
        // batch finished
        if ((dirtyFlags & 0x3L) != 0) {
            // api target 1

            androidx.databinding.adapters.TextViewBindingAdapter.setText(this.panelLocation, PanelLocation1);
            androidx.databinding.adapters.TextViewBindingAdapter.setText(this.panelName, PanelName1);
        }
    }
    // Listener Stub Implementations
    // callback impls
    // dirty flag
    private  long mDirtyFlags = 0xffffffffffffffffL;
    /* flag mapping
        flag 0 (0x1L): panel
        flag 1 (0x2L): null
    flag mapping end*/
    //end
}