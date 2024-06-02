package com.pqsolutions.hdd_monitor.databinding;
import com.pqsolutions.hdd_monitor.R;
import com.pqsolutions.hdd_monitor.BR;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.view.View;
@SuppressWarnings("unchecked")
public class ItemEventBindingImpl extends ItemEventBinding  {

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
    private final android.widget.LinearLayout mboundView0;
    // variables
    // values
    // listeners
    // Inverse Binding Event Handlers

    public ItemEventBindingImpl(@Nullable androidx.databinding.DataBindingComponent bindingComponent, @NonNull View root) {
        this(bindingComponent, root, mapBindings(bindingComponent, root, 5, sIncludes, sViewsWithIds));
    }
    private ItemEventBindingImpl(androidx.databinding.DataBindingComponent bindingComponent, View root, Object[] bindings) {
        super(bindingComponent, root, 0
            , (android.widget.TextView) bindings[1]
            , (android.widget.TextView) bindings[3]
            , (android.widget.TextView) bindings[4]
            , (android.widget.TextView) bindings[2]
            );
        this.eventDateTime.setTag(null);
        this.eventDescription.setTag(null);
        this.eventSolvedStatus.setTag(null);
        this.eventType.setTag(null);
        this.mboundView0 = (android.widget.LinearLayout) bindings[0];
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
        if (BR.event == variableId) {
            setEvent((com.pqsolutions.hdd_monitor.Event) variable);
        }
        else {
            variableSet = false;
        }
            return variableSet;
    }

    public void setEvent(@Nullable com.pqsolutions.hdd_monitor.Event Event) {
        this.mEvent = Event;
        synchronized(this) {
            mDirtyFlags |= 0x1L;
        }
        notifyPropertyChanged(BR.event);
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
        java.lang.String EventSolvedStatus1 = null;
        java.lang.String EventDateTime1 = null;
        java.lang.String javaLangStringResueltoEventSolvedStatus = null;
        java.lang.String EventDescription1 = null;
        com.pqsolutions.hdd_monitor.Event event = mEvent;
        java.lang.String EventType1 = null;

        if ((dirtyFlags & 0x3L) != 0) {



                if (event != null) {
                    // read event.solved_status
                    EventSolvedStatus1 = event.getSolved_status();
                    // read event.date_time
                    EventDateTime1 = event.getDate_time();
                    // read event.description
                    EventDescription1 = event.getDescription();
                    // read event.type
                    EventType1 = event.getType();
                }


                // read ("Resuelto: ") + (event.solved_status)
                javaLangStringResueltoEventSolvedStatus = ("Resuelto: ") + (EventSolvedStatus1);
        }
        // batch finished
        if ((dirtyFlags & 0x3L) != 0) {
            // api target 1

            androidx.databinding.adapters.TextViewBindingAdapter.setText(this.eventDateTime, EventDateTime1);
            androidx.databinding.adapters.TextViewBindingAdapter.setText(this.eventDescription, EventDescription1);
            androidx.databinding.adapters.TextViewBindingAdapter.setText(this.eventSolvedStatus, javaLangStringResueltoEventSolvedStatus);
            androidx.databinding.adapters.TextViewBindingAdapter.setText(this.eventType, EventType1);
        }
    }
    // Listener Stub Implementations
    // callback impls
    // dirty flag
    private  long mDirtyFlags = 0xffffffffffffffffL;
    /* flag mapping
        flag 0 (0x1L): event
        flag 1 (0x2L): null
    flag mapping end*/
    //end
}