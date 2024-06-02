package androidx.databinding;

public class DataBinderMapperImpl extends MergedDataBinderMapper {
  DataBinderMapperImpl() {
    addMapper(new com.pqsolutions.hdd_monitor.DataBinderMapperImpl());
  }
}
