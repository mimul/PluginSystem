package com.mimul.plugin.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JSR310Module;

import java.text.SimpleDateFormat;

/**
 * Created by mimul on 2016. 8. 23..
 */
public class JsonHelper {
  private final static ObjectMapper mapper = new ObjectMapper();
  private final static ObjectMapper prettyMapper = new ObjectMapper();

  static {
    prettyMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
    mapper.registerModule(new JSR310Module());
    mapper.registerModule(new Jdk8Module());
    mapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX"));
  }

  public static String encode(Object obj, boolean prettyPrint) {
    try {
      return (prettyPrint ? prettyMapper : mapper).writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Object is not json serializable", e);
    }
  }

  public static String encode(Object obj) {
    return encode(obj, false);
  }

}
