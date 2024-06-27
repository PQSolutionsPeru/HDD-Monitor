package com.pqsolutions.hdd_monitor;

import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.View;
import androidx.databinding.DataBinderMapper;
import androidx.databinding.DataBindingComponent;
import androidx.databinding.ViewDataBinding;
import com.pqsolutions.hdd_monitor.databinding.ActivityAddEditAlertBindingImpl;
import com.pqsolutions.hdd_monitor.databinding.ActivityAddEditUserBindingImpl;
import com.pqsolutions.hdd_monitor.databinding.ActivityAdminMainBindingImpl;
import com.pqsolutions.hdd_monitor.databinding.ActivityAlertManagementBindingImpl;
import com.pqsolutions.hdd_monitor.databinding.ActivityClientPanelBindingImpl;
import com.pqsolutions.hdd_monitor.databinding.ActivityEventHistoryBindingImpl;
import com.pqsolutions.hdd_monitor.databinding.ActivityEventSchedulerBindingImpl;
import com.pqsolutions.hdd_monitor.databinding.ActivityLoginBindingImpl;
import com.pqsolutions.hdd_monitor.databinding.ActivityReportBindingImpl;
import com.pqsolutions.hdd_monitor.databinding.ActivityUserManagementBindingImpl;
import com.pqsolutions.hdd_monitor.databinding.ItemAlertBindingImpl;
import com.pqsolutions.hdd_monitor.databinding.ItemClientBindingImpl;
import com.pqsolutions.hdd_monitor.databinding.ItemEventBindingImpl;
import com.pqsolutions.hdd_monitor.databinding.ItemPanelBindingImpl;
import com.pqsolutions.hdd_monitor.databinding.ItemRelayBindingImpl;
import com.pqsolutions.hdd_monitor.databinding.ItemUserBindingImpl;
import java.lang.IllegalArgumentException;
import java.lang.Integer;
import java.lang.Object;
import java.lang.Override;
import java.lang.RuntimeException;
import java.lang.String;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class DataBinderMapperImpl extends DataBinderMapper {
  private static final int LAYOUT_ACTIVITYADDEDITALERT = 1;

  private static final int LAYOUT_ACTIVITYADDEDITUSER = 2;

  private static final int LAYOUT_ACTIVITYADMINMAIN = 3;

  private static final int LAYOUT_ACTIVITYALERTMANAGEMENT = 4;

  private static final int LAYOUT_ACTIVITYCLIENTPANEL = 5;

  private static final int LAYOUT_ACTIVITYEVENTHISTORY = 6;

  private static final int LAYOUT_ACTIVITYEVENTSCHEDULER = 7;

  private static final int LAYOUT_ACTIVITYLOGIN = 8;

  private static final int LAYOUT_ACTIVITYREPORT = 9;

  private static final int LAYOUT_ACTIVITYUSERMANAGEMENT = 10;

  private static final int LAYOUT_ITEMALERT = 11;

  private static final int LAYOUT_ITEMCLIENT = 12;

  private static final int LAYOUT_ITEMEVENT = 13;

  private static final int LAYOUT_ITEMPANEL = 14;

  private static final int LAYOUT_ITEMRELAY = 15;

  private static final int LAYOUT_ITEMUSER = 16;

  private static final SparseIntArray INTERNAL_LAYOUT_ID_LOOKUP = new SparseIntArray(16);

  static {
    INTERNAL_LAYOUT_ID_LOOKUP.put(com.pqsolutions.hdd_monitor.R.layout.activity_add_edit_alert, LAYOUT_ACTIVITYADDEDITALERT);
    INTERNAL_LAYOUT_ID_LOOKUP.put(com.pqsolutions.hdd_monitor.R.layout.activity_add_edit_user, LAYOUT_ACTIVITYADDEDITUSER);
    INTERNAL_LAYOUT_ID_LOOKUP.put(com.pqsolutions.hdd_monitor.R.layout.activity_admin_main, LAYOUT_ACTIVITYADMINMAIN);
    INTERNAL_LAYOUT_ID_LOOKUP.put(com.pqsolutions.hdd_monitor.R.layout.activity_alert_management, LAYOUT_ACTIVITYALERTMANAGEMENT);
    INTERNAL_LAYOUT_ID_LOOKUP.put(com.pqsolutions.hdd_monitor.R.layout.activity_client_panel, LAYOUT_ACTIVITYCLIENTPANEL);
    INTERNAL_LAYOUT_ID_LOOKUP.put(com.pqsolutions.hdd_monitor.R.layout.activity_event_history, LAYOUT_ACTIVITYEVENTHISTORY);
    INTERNAL_LAYOUT_ID_LOOKUP.put(com.pqsolutions.hdd_monitor.R.layout.activity_event_scheduler, LAYOUT_ACTIVITYEVENTSCHEDULER);
    INTERNAL_LAYOUT_ID_LOOKUP.put(com.pqsolutions.hdd_monitor.R.layout.activity_login, LAYOUT_ACTIVITYLOGIN);
    INTERNAL_LAYOUT_ID_LOOKUP.put(com.pqsolutions.hdd_monitor.R.layout.activity_report, LAYOUT_ACTIVITYREPORT);
    INTERNAL_LAYOUT_ID_LOOKUP.put(com.pqsolutions.hdd_monitor.R.layout.activity_user_management, LAYOUT_ACTIVITYUSERMANAGEMENT);
    INTERNAL_LAYOUT_ID_LOOKUP.put(com.pqsolutions.hdd_monitor.R.layout.item_alert, LAYOUT_ITEMALERT);
    INTERNAL_LAYOUT_ID_LOOKUP.put(com.pqsolutions.hdd_monitor.R.layout.item_client, LAYOUT_ITEMCLIENT);
    INTERNAL_LAYOUT_ID_LOOKUP.put(com.pqsolutions.hdd_monitor.R.layout.item_event, LAYOUT_ITEMEVENT);
    INTERNAL_LAYOUT_ID_LOOKUP.put(com.pqsolutions.hdd_monitor.R.layout.item_panel, LAYOUT_ITEMPANEL);
    INTERNAL_LAYOUT_ID_LOOKUP.put(com.pqsolutions.hdd_monitor.R.layout.item_relay, LAYOUT_ITEMRELAY);
    INTERNAL_LAYOUT_ID_LOOKUP.put(com.pqsolutions.hdd_monitor.R.layout.item_user, LAYOUT_ITEMUSER);
  }

  @Override
  public ViewDataBinding getDataBinder(DataBindingComponent component, View view, int layoutId) {
    int localizedLayoutId = INTERNAL_LAYOUT_ID_LOOKUP.get(layoutId);
    if(localizedLayoutId > 0) {
      final Object tag = view.getTag();
      if(tag == null) {
        throw new RuntimeException("view must have a tag");
      }
      switch(localizedLayoutId) {
        case  LAYOUT_ACTIVITYADDEDITALERT: {
          if ("layout/activity_add_edit_alert_0".equals(tag)) {
            return new ActivityAddEditAlertBindingImpl(component, view);
          }
          throw new IllegalArgumentException("The tag for activity_add_edit_alert is invalid. Received: " + tag);
        }
        case  LAYOUT_ACTIVITYADDEDITUSER: {
          if ("layout/activity_add_edit_user_0".equals(tag)) {
            return new ActivityAddEditUserBindingImpl(component, view);
          }
          throw new IllegalArgumentException("The tag for activity_add_edit_user is invalid. Received: " + tag);
        }
        case  LAYOUT_ACTIVITYADMINMAIN: {
          if ("layout/activity_admin_main_0".equals(tag)) {
            return new ActivityAdminMainBindingImpl(component, view);
          }
          throw new IllegalArgumentException("The tag for activity_admin_main is invalid. Received: " + tag);
        }
        case  LAYOUT_ACTIVITYALERTMANAGEMENT: {
          if ("layout/activity_alert_management_0".equals(tag)) {
            return new ActivityAlertManagementBindingImpl(component, view);
          }
          throw new IllegalArgumentException("The tag for activity_alert_management is invalid. Received: " + tag);
        }
        case  LAYOUT_ACTIVITYCLIENTPANEL: {
          if ("layout/activity_client_panel_0".equals(tag)) {
            return new ActivityClientPanelBindingImpl(component, view);
          }
          throw new IllegalArgumentException("The tag for activity_client_panel is invalid. Received: " + tag);
        }
        case  LAYOUT_ACTIVITYEVENTHISTORY: {
          if ("layout/activity_event_history_0".equals(tag)) {
            return new ActivityEventHistoryBindingImpl(component, view);
          }
          throw new IllegalArgumentException("The tag for activity_event_history is invalid. Received: " + tag);
        }
        case  LAYOUT_ACTIVITYEVENTSCHEDULER: {
          if ("layout/activity_event_scheduler_0".equals(tag)) {
            return new ActivityEventSchedulerBindingImpl(component, view);
          }
          throw new IllegalArgumentException("The tag for activity_event_scheduler is invalid. Received: " + tag);
        }
        case  LAYOUT_ACTIVITYLOGIN: {
          if ("layout/activity_login_0".equals(tag)) {
            return new ActivityLoginBindingImpl(component, view);
          }
          throw new IllegalArgumentException("The tag for activity_login is invalid. Received: " + tag);
        }
        case  LAYOUT_ACTIVITYREPORT: {
          if ("layout/activity_report_0".equals(tag)) {
            return new ActivityReportBindingImpl(component, view);
          }
          throw new IllegalArgumentException("The tag for activity_report is invalid. Received: " + tag);
        }
        case  LAYOUT_ACTIVITYUSERMANAGEMENT: {
          if ("layout/activity_user_management_0".equals(tag)) {
            return new ActivityUserManagementBindingImpl(component, view);
          }
          throw new IllegalArgumentException("The tag for activity_user_management is invalid. Received: " + tag);
        }
        case  LAYOUT_ITEMALERT: {
          if ("layout/item_alert_0".equals(tag)) {
            return new ItemAlertBindingImpl(component, view);
          }
          throw new IllegalArgumentException("The tag for item_alert is invalid. Received: " + tag);
        }
        case  LAYOUT_ITEMCLIENT: {
          if ("layout/item_client_0".equals(tag)) {
            return new ItemClientBindingImpl(component, view);
          }
          throw new IllegalArgumentException("The tag for item_client is invalid. Received: " + tag);
        }
        case  LAYOUT_ITEMEVENT: {
          if ("layout/item_event_0".equals(tag)) {
            return new ItemEventBindingImpl(component, view);
          }
          throw new IllegalArgumentException("The tag for item_event is invalid. Received: " + tag);
        }
        case  LAYOUT_ITEMPANEL: {
          if ("layout/item_panel_0".equals(tag)) {
            return new ItemPanelBindingImpl(component, view);
          }
          throw new IllegalArgumentException("The tag for item_panel is invalid. Received: " + tag);
        }
        case  LAYOUT_ITEMRELAY: {
          if ("layout/item_relay_0".equals(tag)) {
            return new ItemRelayBindingImpl(component, view);
          }
          throw new IllegalArgumentException("The tag for item_relay is invalid. Received: " + tag);
        }
        case  LAYOUT_ITEMUSER: {
          if ("layout/item_user_0".equals(tag)) {
            return new ItemUserBindingImpl(component, view);
          }
          throw new IllegalArgumentException("The tag for item_user is invalid. Received: " + tag);
        }
      }
    }
    return null;
  }

  @Override
  public ViewDataBinding getDataBinder(DataBindingComponent component, View[] views, int layoutId) {
    if(views == null || views.length == 0) {
      return null;
    }
    int localizedLayoutId = INTERNAL_LAYOUT_ID_LOOKUP.get(layoutId);
    if(localizedLayoutId > 0) {
      final Object tag = views[0].getTag();
      if(tag == null) {
        throw new RuntimeException("view must have a tag");
      }
      switch(localizedLayoutId) {
      }
    }
    return null;
  }

  @Override
  public int getLayoutId(String tag) {
    if (tag == null) {
      return 0;
    }
    Integer tmpVal = InnerLayoutIdLookup.sKeys.get(tag);
    return tmpVal == null ? 0 : tmpVal;
  }

  @Override
  public String convertBrIdToString(int localId) {
    String tmpVal = InnerBrLookup.sKeys.get(localId);
    return tmpVal;
  }

  @Override
  public List<DataBinderMapper> collectDependencies() {
    ArrayList<DataBinderMapper> result = new ArrayList<DataBinderMapper>(1);
    result.add(new androidx.databinding.library.baseAdapters.DataBinderMapperImpl());
    return result;
  }

  private static class InnerBrLookup {
    static final SparseArray<String> sKeys = new SparseArray<String>(6);

    static {
      sKeys.put(0, "_all");
      sKeys.put(1, "alert");
      sKeys.put(2, "event");
      sKeys.put(3, "panel");
      sKeys.put(4, "relay");
      sKeys.put(5, "user");
    }
  }

  private static class InnerLayoutIdLookup {
    static final HashMap<String, Integer> sKeys = new HashMap<String, Integer>(16);

    static {
      sKeys.put("layout/activity_add_edit_alert_0", com.pqsolutions.hdd_monitor.R.layout.activity_add_edit_alert);
      sKeys.put("layout/activity_add_edit_user_0", com.pqsolutions.hdd_monitor.R.layout.activity_add_edit_user);
      sKeys.put("layout/activity_admin_main_0", com.pqsolutions.hdd_monitor.R.layout.activity_admin_main);
      sKeys.put("layout/activity_alert_management_0", com.pqsolutions.hdd_monitor.R.layout.activity_alert_management);
      sKeys.put("layout/activity_client_panel_0", com.pqsolutions.hdd_monitor.R.layout.activity_client_panel);
      sKeys.put("layout/activity_event_history_0", com.pqsolutions.hdd_monitor.R.layout.activity_event_history);
      sKeys.put("layout/activity_event_scheduler_0", com.pqsolutions.hdd_monitor.R.layout.activity_event_scheduler);
      sKeys.put("layout/activity_login_0", com.pqsolutions.hdd_monitor.R.layout.activity_login);
      sKeys.put("layout/activity_report_0", com.pqsolutions.hdd_monitor.R.layout.activity_report);
      sKeys.put("layout/activity_user_management_0", com.pqsolutions.hdd_monitor.R.layout.activity_user_management);
      sKeys.put("layout/item_alert_0", com.pqsolutions.hdd_monitor.R.layout.item_alert);
      sKeys.put("layout/item_client_0", com.pqsolutions.hdd_monitor.R.layout.item_client);
      sKeys.put("layout/item_event_0", com.pqsolutions.hdd_monitor.R.layout.item_event);
      sKeys.put("layout/item_panel_0", com.pqsolutions.hdd_monitor.R.layout.item_panel);
      sKeys.put("layout/item_relay_0", com.pqsolutions.hdd_monitor.R.layout.item_relay);
      sKeys.put("layout/item_user_0", com.pqsolutions.hdd_monitor.R.layout.item_user);
    }
  }
}
