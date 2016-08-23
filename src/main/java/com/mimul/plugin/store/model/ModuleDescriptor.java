package com.mimul.plugin.store.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Optional;

public class ModuleDescriptor {
	public final String name;
	public final String description;
	public final String className;
	public final boolean isActive;
	public final Optional<Condition> condition;
	public final List<ConfigItem> properties;

	@JsonCreator
	public ModuleDescriptor(@JsonProperty("name") String name, @JsonProperty("description") String description,
			@JsonProperty("className") String className, @JsonProperty("isActive") boolean isActive,
			@JsonProperty("condition") Optional<Condition> condition,
			@JsonProperty("properties") List<ConfigItem> properties) {
		this.name = name;
		this.description = description;
		this.className = className;
		this.isActive = isActive;
		this.condition = condition;
		this.properties = properties;
	}
}
