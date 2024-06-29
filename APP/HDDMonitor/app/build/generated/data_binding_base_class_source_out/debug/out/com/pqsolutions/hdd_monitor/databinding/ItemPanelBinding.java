// Generated by view binder compiler. Do not edit!
package com.pqsolutions.hdd_monitor.databinding;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.viewbinding.ViewBinding;
import androidx.viewbinding.ViewBindings;
import com.pqsolutions.hdd_monitor.R;
import java.lang.NullPointerException;
import java.lang.Override;
import java.lang.String;

public final class ItemPanelBinding implements ViewBinding {
  @NonNull
  private final ConstraintLayout rootView;

  @NonNull
  public final TextView panelLocation;

  @NonNull
  public final TextView panelName;

  private ItemPanelBinding(@NonNull ConstraintLayout rootView, @NonNull TextView panelLocation,
      @NonNull TextView panelName) {
    this.rootView = rootView;
    this.panelLocation = panelLocation;
    this.panelName = panelName;
  }

  @Override
  @NonNull
  public ConstraintLayout getRoot() {
    return rootView;
  }

  @NonNull
  public static ItemPanelBinding inflate(@NonNull LayoutInflater inflater) {
    return inflate(inflater, null, false);
  }

  @NonNull
  public static ItemPanelBinding inflate(@NonNull LayoutInflater inflater,
      @Nullable ViewGroup parent, boolean attachToParent) {
    View root = inflater.inflate(R.layout.item_panel, parent, false);
    if (attachToParent) {
      parent.addView(root);
    }
    return bind(root);
  }

  @NonNull
  public static ItemPanelBinding bind(@NonNull View rootView) {
    // The body of this method is generated in a way you would not otherwise write.
    // This is done to optimize the compiled bytecode for size and performance.
    int id;
    missingId: {
      id = R.id.panelLocation;
      TextView panelLocation = ViewBindings.findChildViewById(rootView, id);
      if (panelLocation == null) {
        break missingId;
      }

      id = R.id.panelName;
      TextView panelName = ViewBindings.findChildViewById(rootView, id);
      if (panelName == null) {
        break missingId;
      }

      return new ItemPanelBinding((ConstraintLayout) rootView, panelLocation, panelName);
    }
    String missingId = rootView.getResources().getResourceName(id);
    throw new NullPointerException("Missing required view with ID: ".concat(missingId));
  }
}
