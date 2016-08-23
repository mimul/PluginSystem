package com.mimul.plugin.store.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Condition {
	public final String property;
	public final String expectedValue;

	public Condition(@JsonProperty("property") String property, @JsonProperty("expectedValue") String expectedValue) {
		this.property = property;
		this.expectedValue = expectedValue;
	}
}
