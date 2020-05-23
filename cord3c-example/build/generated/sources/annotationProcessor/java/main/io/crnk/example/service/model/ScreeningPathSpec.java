package io.crnk.example.service.model;

import io.crnk.core.queryspec.AbstractPathSpec;
import io.crnk.core.queryspec.PathSpec;
import io.crnk.core.queryspec.internal.typed.ResourcePathSpec;
import javax.annotation.Generated;

@Generated("Generated by Crnk annotation processor")
public class ScreeningPathSpec extends ResourcePathSpec {
 public static ScreeningPathSpec screeningPathSpec = new ScreeningPathSpec();

 public ScreeningPathSpec() {
  super(PathSpec.empty());
 }

 public ScreeningPathSpec(PathSpec pathSpec) {
  super(pathSpec);
 }

 protected ScreeningPathSpec(AbstractPathSpec spec) {
  super(spec);
 }

 protected ScreeningPathSpec bindSpec(AbstractPathSpec spec) {
  return new ScreeningPathSpec(spec);
 }
}
