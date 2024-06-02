package com.pqsolutions.hdd_monitor.databinding;
import com.pqsolutions.hdd_monitor.R;
import com.pqsolutions.hdd_monitor.BR;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.view.View;
@SuppressWarnings("unchecked")
public class ItemRelayBindingImpl extends ItemRelayBinding  {

    @Nullable
    private static final androidx.databinding.ViewDataBinding.IncludedLayouts sIncludes;
    @Nullable
    private static final android.util.SparseIntArray sViewsWithIds;
    static {
        sIncludes = null;
        sViewsWithIds = null;
    }
    // views
    @NonNull
    private final androidx.constraintlayout.widget.ConstraintLayout mboundView0;
    // variables
    // values
    // listeners
    // Inverse Binding Event Handlers

    public ItemRelayBindingImpl(@Nullable androidx.databinding.DataBindingComponent bindingComponent, @NonNull View root) {
        this(bindingComponent, root, mapBindings(bindingComponent, root, 3, sIncludes, sViewsWithIds));
    }
    private ItemRelayBindingImpl(androidx.databinding.DataBindingComponent bindingComponent, View root, Object[] bindings) {
        super(bindingComponent, root, 0
            , (android.widget.TextView) bindings[1]
            , (android.widget.ImageView) bindings[2]
            );
        this.mboundView0 = (androidx.constraintlayout.widget.ConstraintLayout) bindings[0];
        this.mboundView0.setTag(null);
        this.relayNameTextView.setTag(null);
        this.relayStatusImageView.setTag(null);
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
        if (BR.relay == variableId) {
            setRelay((com.pqsolutions.hdd_monitor.Relay) variable);
        }
        else {
            variableSet = false;
        }
            return variableSet;
    }

    public void setRelay(@Nullable com.pqsolutions.hdd_monitor.Relay Relay) {
        this.mRelay = Relay;
        synchronized(this) {
            mDirtyFlags |= 0x1L;
        }
        notifyPropertyChanged(BR.relay);
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
        java.lang.String relayName = null;
        boolean relayStatusEqualsJavaLangStringOK = false;
        com.pqsolutions.hdd_monitor.Relay relay = mRelay;
        java.lang.String relayStatus = null;
        android.graphics.drawable.Drawable relayStatusEqualsJavaLangStringOKRelayStatusImageViewAndroidDrawableIcCheckGreenRelayStatusImageViewAndroidDrawableIcCloseRed = null;

        if ((dirtyFlags & 0x3L) != 0) {



                if (relay != null) {
                    // read relay.name
                    relayName = relay.getName();
                    // read relay.status
                    relayStatus = relay.getStatus();
                }


                if (relayStatus != null) {
                    // read relay.status.equals("OK")
                    relayStatusEqualsJavaLangStringOK = relayStatus.equals("OK");
                }
            if((dirtyFlags & 0x3L) != 0) {
                if(relayStatusEqualsJavaLangStringOK) {
                        dirtyFlags |= 0x8L;
                }
                else {
                        dirtyFlags |= 0x4L;
                }
            }


                // read relay.status.equals("OK") ? @android:drawable/ic_check_green : @android:drawable/ic_close_red
                relayStatusEqualsJavaLangStringOKRelayStatusImageViewAndroidDrawableIcCheckGreenRelayStatusImageViewAndroidDrawableIcCloseRed = ((relayStatusEqualsJavaLangStringOK) ? (androidx.appcompat.content.res.AppCompatResources.getDrawable(relayStatusImageView.getContext(), R.drawable.ic_check_green)) : (androidx.appcompat.content.res.AppCompatResources.getDrawable(relayStatusImageView.getContext(), R.drawable.ic_close_red)));
        }
        // batch finished
        if ((dirtyFlags & 0x3L) != 0) {
            // api target 1

            androidx.databinding.adapters.TextViewBindingAdapter.setText(this.relayNameTextView, relayName);
            androidx.databinding.adapters.ImageViewBindingAdapter.setImageDrawable(this.relayStatusImageView, relayStatusEqualsJavaLangStringOKRelayStatusImageViewAndroidDrawableIcCheckGreenRelayStatusImageViewAndroidDrawableIcCloseRed);
        }
    }
    // Listener Stub Implementations
    // callback impls
    // dirty flag
    private  long mDirtyFlags = 0xffffffffffffffffL;
    /* flag mapping
        flag 0 (0x1L): relay
        flag 1 (0x2L): null
        flag 2 (0x3L): relay.status.equals("OK") ? @android:drawable/ic_check_green : @android:drawable/ic_close_red
        flag 3 (0x4L): relay.status.equals("OK") ? @android:drawable/ic_check_green : @android:drawable/ic_close_red
    flag mapping end*/
    //end
}