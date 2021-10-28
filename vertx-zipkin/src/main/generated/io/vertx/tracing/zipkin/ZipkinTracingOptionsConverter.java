package io.vertx.tracing.zipkin;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.impl.JsonUtil;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Converter and mapper for {@link io.vertx.tracing.zipkin.ZipkinTracingOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.tracing.zipkin.ZipkinTracingOptions} original class using Vert.x codegen.
 */
public class ZipkinTracingOptionsConverter {


  private static final Base64.Decoder BASE64_DECODER = JsonUtil.BASE64_DECODER;
  private static final Base64.Encoder BASE64_ENCODER = JsonUtil.BASE64_ENCODER;

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, ZipkinTracingOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "senderOptions":
          if (member.getValue() instanceof JsonObject) {
            obj.setSenderOptions(new io.vertx.tracing.zipkin.HttpSenderOptions((io.vertx.core.json.JsonObject)member.getValue()));
          }
          break;
        case "serviceName":
          if (member.getValue() instanceof String) {
            obj.setServiceName((String)member.getValue());
          }
          break;
        case "supportsJoin":
          if (member.getValue() instanceof Boolean) {
            obj.setSupportsJoin((Boolean)member.getValue());
          }
          break;
      }
    }
  }

   static void toJson(ZipkinTracingOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(ZipkinTracingOptions obj, java.util.Map<String, Object> json) {
    if (obj.getSenderOptions() != null) {
      json.put("senderOptions", obj.getSenderOptions().toJson());
    }
    if (obj.getServiceName() != null) {
      json.put("serviceName", obj.getServiceName());
    }
    json.put("supportsJoin", obj.isSupportsJoin());
  }
}
