package org.homio.addon.tuya.internal.util;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import org.homio.addon.tuya.internal.cloud.dto.DeviceSchema;
import org.homio.api.model.endpoint.DeviceEndpoint.EndpointType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.StringReader;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.homio.api.util.JsonUtils.OBJECT_MAPPER;

/**
 * Wrapper for the information of a single datapoint
 */
@Getter
@Setter
@Accessors(chain = true)
public class SchemaDp {

  private static final Map<String, EndpointType> REMOTE_LOCAL_TYPE_MAP = Map.of(
    "Boolean", EndpointType.bool,
    "Enum", EndpointType.select,
    "Integer", EndpointType.number,
    "Json", EndpointType.string);

  public int dp;
  private @NotNull EndpointType type = EndpointType.string;
  private @NotNull String code = "";

  private @Nullable Integer dp2;
  private @Nullable Boolean writable;
  private @Nullable Boolean readable;
  private @NotNull ObjectNode meta = OBJECT_MAPPER.createObjectNode();

  @JsonIgnore
  private Set<String> range;
  @JsonIgnore
  private Float min;
  @JsonIgnore
  private Float max;

  @SneakyThrows
  public static SchemaDp parse(DeviceSchema.Description description) {
    SchemaDp schemaDp = new SchemaDp();
    schemaDp.code = description.code.replace("_v2", "");
    schemaDp.dp = description.dp_id;
    schemaDp.type = REMOTE_LOCAL_TYPE_MAP.getOrDefault(description.type, EndpointType.string);
    schemaDp.meta = OBJECT_MAPPER.readValue(description.values, ObjectNode.class);
    return schemaDp;
  }

  @SneakyThrows
  public void mergeMeta(String updateValue) {
    if (!meta.isEmpty()) {
      meta = OBJECT_MAPPER.readerForUpdating(meta).readValue(new StringReader(updateValue));
    }
  }

  @JsonIgnore
  public String getUnit() {
    return meta.path("unit").asText();
  }

  @JsonIgnore
  public float getMin() {
    if (min == null) {
      min = (float) meta.path("min").asDouble(0);
    }
    return min;
  }

  @JsonIgnore
  public float getMax() {
    if (max == null) {
      max = (float) meta.path("max").asDouble(0);
    }
    return max;
  }

  @JsonIgnore
  public Set<String> getRange() {
    if (range == null) {
      range = new LinkedHashSet<>();
      if (meta.has("range")) {
        for (JsonNode jsonNode : meta.get("range")) {
          range.add(jsonNode.asText());
        }
      }
    }
    return range;
  }
}
