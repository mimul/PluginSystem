package com.mimul.plugin.store.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ConfigItem {
	public final String property;
	public final String defaultValue;
	public final String description;

	@JsonCreator
	public ConfigItem(@JsonProperty("property") String property, @JsonProperty("defaultValue") String defaultValue,
			@JsonProperty("description") String description) {
		this.property = property;
		this.defaultValue = defaultValue;
		this.description = description;
	}
}
