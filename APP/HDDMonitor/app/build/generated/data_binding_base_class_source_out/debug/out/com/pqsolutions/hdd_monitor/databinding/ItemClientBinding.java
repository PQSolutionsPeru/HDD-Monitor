// Generated by data binding compiler. Do not edit!
package com.pqsolutions.hdd_monitor.databinding;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.ViewDataBinding;
import com.pqsolutions.hdd_monitor.R;
import java.lang.Deprecated;
import java.lang.Object;

public abstract class ItemClientBinding extends ViewDataBinding {
  @NonNull
  public final TextView clientName;

  protected ItemClientBinding(Object _bindingComponent, View _root, int _localFieldCount,
      TextView clientName) {
    super(_bindingComponent, _root, _localFieldCount);
    this.clientName = clientName;
  }

  @NonNull
  public static ItemClientBinding inflate(@NonNull LayoutInflater inflater,
      @Nullable ViewGroup root, boolean attachToRoot) {
    return inflate(inflater, root, attachToRoot, DataBindingUtil.getDefaultComponent());
  }

  /**
   * This method receives DataBindingComponent instance as type Object instead of
   * type DataBindingComponent to avoid causing too many compilation errors if
   * compilation fails for another reason.
   * https://issuetracker.google.com/issues/116541301
   * @Deprecated Use DataBindingUtil.inflate(inflater, R.layout.item_client, root, attachToRoot, component)
   */
  @NonNull
  @Deprecated
  public static ItemClientBinding inflate(@NonNull LayoutInflater inflater,
      @Nullable ViewGroup root, boolean attachToRoot, @Nullable Object component) {
    return ViewDataBinding.<ItemClientBinding>inflateInternal(inflater, R.layout.item_client, root, attachToRoot, component);
  }

  @NonNull
  public static ItemClientBinding inflate(@NonNull LayoutInflater inflater) {
    return inflate(inflater, DataBindingUtil.getDefaultComponent());
  }

  /**
   * This method receives DataBindingComponent instance as type Object instead of
   * type DataBindingComponent to avoid causing too many compilation errors if
   * compilation fails for another reason.
   * https://issuetracker.google.com/issues/116541301
   * @Deprecated Use DataBindingUtil.inflate(inflater, R.layout.item_client, null, false, component)
   */
  @NonNull
  @Deprecated
  public static ItemClientBinding inflate(@NonNull LayoutInflater inflater,
      @Nullable Object component) {
    return ViewDataBinding.<ItemClientBinding>inflateInternal(inflater, R.layout.item_client, null, false, component);
  }

  public static ItemClientBinding bind(@NonNull View view) {
    return bind(view, DataBindingUtil.getDefaultComponent());
  }

  /**
   * This method receives DataBindingComponent instance as type Object instead of
   * type DataBindingComponent to avoid causing too many compilation errors if
   * compilation fails for another reason.
   * https://issuetracker.google.com/issues/116541301
   * @Deprecated Use DataBindingUtil.bind(view, component)
   */
  @Deprecated
  public static ItemClientBinding bind(@NonNull View view, @Nullable Object component) {
    return (ItemClientBinding)bind(component, view, R.layout.item_client);
  }
}