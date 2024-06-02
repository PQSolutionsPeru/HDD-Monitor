package com.pqsolutions.hdd_monitor.databinding;
import com.pqsolutions.hdd_monitor.R;
import com.pqsolutions.hdd_monitor.BR;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.view.View;
@SuppressWarnings("unchecked")
public class ActivityAddEditAlertBindingImpl extends ActivityAddEditAlertBinding  {

    @Nullable
    private static final androidx.databinding.ViewDataBinding.IncludedLayouts sIncludes;
    @Nullable
    private static final android.util.SparseIntArray sViewsWithIds;
    static {
        sIncludes = null;
        sViewsWithIds = new android.util.SparseIntArray();
        sViewsWithIds.put(R.id.title, 4);
        sViewsWithIds.put(R.id.saveButton, 5);
        sViewsWithIds.put(R.id.bottom_navigation, 6);
    }
    // views
    @NonNull
    private final androidx.constraintlayout.widget.ConstraintLayout mboundView0;
    // variables
    // values
    // listeners
    // Inverse Binding Event Handlers
    private androidx.databinding.InverseBindingListener editTextStatusandroidTextAttrChanged = new androidx.databinding.InverseBindingListener() {
        @Override
        public void onChange() {
            // Inverse of alert.status
            //         is alert.setStatus((java.lang.String) callbackArg_0)
            java.lang.String callbackArg_0 = androidx.databinding.adapters.TextViewBindingAdapter.getTextString(editTextStatus);
            // localize variables for thread safety
            // alert.status
            java.lang.String alertStatus = null;
            // alert
            com.pqsolutions.hdd_monitor.Alert alert = mAlert;
            // alert != null
            boolean alertJavaLangObjectNull = false;



            alertJavaLangObjectNull = (alert) != (null);
            if (alertJavaLangObjectNull) {




                alert.setStatus(((java.lang.String) (callbackArg_0)));
            }
        }
    };
    private androidx.databinding.InverseBindingListener editTextTextandroidTextAttrChanged = new androidx.databinding.InverseBindingListener() {
        @Override
        public void onChange() {
            // Inverse of alert.text
            //         is alert.setText((java.lang.String) callbackArg_0)
            java.lang.String callbackArg_0 = androidx.databinding.adapters.TextViewBindingAdapter.getTextString(editTextText);
            // localize variables for thread safety
            // alert
            com.pqsolutions.hdd_monitor.Alert alert = mAlert;
            // alert.text
            java.lang.String alertText = null;
            // alert != null
            boolean alertJavaLangObjectNull = false;



            alertJavaLangObjectNull = (alert) != (null);
            if (alertJavaLangObjectNull) {




                alert.setText(((java.lang.String) (callbackArg_0)));
            }
        }
    };
    private androidx.databinding.InverseBindingListener editTextTitleandroidTextAttrChanged = new androidx.databinding.InverseBindingListener() {
        @Override
        public void onChange() {
            // Inverse of alert.title
            //         is alert.setTitle((java.lang.String) callbackArg_0)
            java.lang.String callbackArg_0 = androidx.databinding.adapters.TextViewBindingAdapter.getTextString(editTextTitle);
            // localize variables for thread safety
            // alert
            com.pqsolutions.hdd_monitor.Alert alert = mAlert;
            // alert != null
            boolean alertJavaLangObjectNull = false;
            // alert.title
            java.lang.String alertTitle = null;



            alertJavaLangObjectNull = (alert) != (null);
            if (alertJavaLangObjectNull) {




                alert.setTitle(((java.lang.String) (callbackArg_0)));
            }
        }
    };

    public ActivityAddEditAlertBindingImpl(@Nullable androidx.databinding.DataBindingComponent bindingComponent, @NonNull View root) {
        this(bindingComponent, root, mapBindings(bindingComponent, root, 7, sIncludes, sViewsWithIds));
    }
    private ActivityAddEditAlertBindingImpl(androidx.databinding.DataBindingComponent bindingComponent, View root, Object[] bindings) {
        super(bindingComponent, root, 0
            , (com.google.android.material.bottomnavigation.BottomNavigationView) bindings[6]
            , (android.widget.EditText) bindings[3]
            , (android.widget.EditText) bindings[2]
            , (android.widget.EditText) bindings[1]
            , (android.widget.Button) bindings[5]
            , (android.widget.TextView) bindings[4]
            );
        this.editTextStatus.setTag(null);
        this.editTextText.setTag(null);
        this.editTextTitle.setTag(null);
        this.mboundView0 = (androidx.constraintlayout.widget.ConstraintLayout) bindings[0];
        this.mboundView0.setTag(null);
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
        if (BR.alert == variableId) {
            setAlert((com.pqsolutions.hdd_monitor.Alert) variable);
        }
        else {
            variableSet = false;
        }
            return variableSet;
    }

    public void setAlert(@Nullable com.pqsolutions.hdd_monitor.Alert Alert) {
        this.mAlert = Alert;
        synchronized(this) {
            mDirtyFlags |= 0x1L;
        }
        notifyPropertyChanged(BR.alert);
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
        java.lang.String alertStatus = null;
        java.lang.String alertText = null;
        java.lang.String alertTitle = null;
        com.pqsolutions.hdd_monitor.Alert alert = mAlert;

        if ((dirtyFlags & 0x3L) != 0) {



                if (alert != null) {
                    // read alert.status
                    alertStatus = alert.getStatus();
                    // read alert.text
                    alertText = alert.getText();
                    // read alert.title
                    alertTitle = alert.getTitle();
                }
        }
        // batch finished
        if ((dirtyFlags & 0x3L) != 0) {
            // api target 1

            androidx.databinding.adapters.TextViewBindingAdapter.setText(this.editTextStatus, alertStatus);
            androidx.databinding.adapters.TextViewBindingAdapter.setText(this.editTextText, alertText);
            androidx.databinding.adapters.TextViewBindingAdapter.setText(this.editTextTitle, alertTitle);
        }
        if ((dirtyFlags & 0x2L) != 0) {
            // api target 1

            androidx.databinding.adapters.TextViewBindingAdapter.setTextWatcher(this.editTextStatus, (androidx.databinding.adapters.TextViewBindingAdapter.BeforeTextChanged)null, (androidx.databinding.adapters.TextViewBindingAdapter.OnTextChanged)null, (androidx.databinding.adapters.TextViewBindingAdapter.AfterTextChanged)null, editTextStatusandroidTextAttrChanged);
            androidx.databinding.adapters.TextViewBindingAdapter.setTextWatcher(this.editTextText, (androidx.databinding.adapters.TextViewBindingAdapter.BeforeTextChanged)null, (androidx.databinding.adapters.TextViewBindingAdapter.OnTextChanged)null, (androidx.databinding.adapters.TextViewBindingAdapter.AfterTextChanged)null, editTextTextandroidTextAttrChanged);
            androidx.databinding.adapters.TextViewBindingAdapter.setTextWatcher(this.editTextTitle, (androidx.databinding.adapters.TextViewBindingAdapter.BeforeTextChanged)null, (androidx.databinding.adapters.TextViewBindingAdapter.OnTextChanged)null, (androidx.databinding.adapters.TextViewBindingAdapter.AfterTextChanged)null, editTextTitleandroidTextAttrChanged);
        }
    }
    // Listener Stub Implementations
    // callback impls
    // dirty flag
    private  long mDirtyFlags = 0xffffffffffffffffL;
    /* flag mapping
        flag 0 (0x1L): alert
        flag 1 (0x2L): null
    flag mapping end*/
    //end
}