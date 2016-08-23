package com.mimul.plugin.module;

import com.google.inject.Binder;
import com.google.inject.name.Names;
import io.airlift.configuration.ConfigDefaults;
import io.airlift.configuration.ConfigurationAwareModule;
import io.airlift.configuration.ConfigurationFactory;

import javax.validation.constraints.NotNull;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static io.airlift.configuration.ConfigBinder.configBinder;

public abstract class PluginModule implements ConfigurationAwareModule {
	private ConfigurationFactory configurationFactory;
	private Binder binder;

	@Override
	public synchronized void setConfigurationFactory(ConfigurationFactory configurationFactory) {
		this.configurationFactory = checkNotNull(configurationFactory, "configurationFactory is null");
	}

	@Override
	public void configure(Binder binder) {
		checkState(this.binder == null, "re-entry not allowed");
		this.binder = checkNotNull(binder, "binder is null");
		try {
			setup(binder);
		} finally {
			this.binder = null;
		}
	}

	// 설정 파일과 Config 클래스 매핑 처리
	protected synchronized <T> T buildConfigObject(Class<T> configClass, String prefix) {
		configBinder(binder).bindConfig(configClass, prefix != null ? Names.named(prefix) : null, prefix);
		try {
			Method method = configurationFactory.getClass().getDeclaredMethod("build", Class.class, String.class,
					ConfigDefaults.class);
			method.setAccessible(true);
			Object invoke = method.invoke(configurationFactory, configClass, prefix, ConfigDefaults.noDefaults());
			Field instance = invoke.getClass().getDeclaredField("instance");
			instance.setAccessible(true);
			return (T) instance.get(invoke);
		} catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | NoSuchFieldException e) {
			throw new IllegalStateException("configuration error. ", e);
		}
	}

	// 클래스 바인딩
	protected abstract void setup(Binder binder);

	@NotNull
	public abstract String name();

	public abstract String description();
}
