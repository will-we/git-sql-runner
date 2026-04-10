package io.will_we.project.gitsqlrunner.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

public final class CompatCollections {
  private CompatCollections() {
  }

  public static List<String> distinctNonBlank(List<String> values) {
    if (values == null || values.isEmpty()) {
      return Collections.emptyList();
    }
    LinkedHashSet<String> normalized = new LinkedHashSet<String>();
    for (String value : values) {
      String trimmed = CompatText.trimToNull(value);
      if (trimmed != null) {
        normalized.add(trimmed);
      }
    }
    return new ArrayList<String>(normalized);
  }
}
