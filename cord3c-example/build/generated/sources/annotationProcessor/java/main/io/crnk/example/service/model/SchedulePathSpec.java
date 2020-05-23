package io.crnk.example.service.model;

import io.crnk.core.queryspec.AbstractPathSpec;
import io.crnk.core.queryspec.PathSpec;
import io.crnk.core.queryspec.internal.typed.ResourcePathSpec;
import javax.annotation.Generated;

@Generated("Generated by Crnk annotation processor")
public class SchedulePathSpec extends ResourcePathSpec {
 public static SchedulePathSpec schedulePathSpec = new SchedulePathSpec();

 public SchedulePathSpec() {
  super(PathSpec.empty());
 }

 public SchedulePathSpec(PathSpec pathSpec) {
  super(pathSpec);
 }

 protected SchedulePathSpec(AbstractPathSpec spec) {
  super(spec);
 }

 protected SchedulePathSpec bindSpec(AbstractPathSpec spec) {
  return new SchedulePathSpec(spec);
 }
}
