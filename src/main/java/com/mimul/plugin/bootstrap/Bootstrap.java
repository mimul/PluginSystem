package com.mimul.plugin.bootstrap;

import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Maps;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Stage;
import com.google.inject.spi.Message;
import com.mimul.plugin.module.ConditionalModule;
import com.mimul.plugin.module.PluginModule;
import io.airlift.bootstrap.LifeCycleManager;
import io.airlift.bootstrap.LifeCycleModule;
import io.airlift.configuration.ConfigurationAwareModule;
import io.airlift.configuration.ConfigurationFactory;
import io.airlift.configuration.ConfigurationInspector;
import io.airlift.configuration.ConfigurationLoader;
import io.airlift.configuration.ConfigurationModule;
import io.airlift.configuration.ValidationErrorModule;
import io.airlift.configuration.WarningsMonitor;
import io.airlift.log.Logger;
import io.airlift.log.Logging;
import io.airlift.log.LoggingConfiguration;
import org.apache.commons.lang.StringUtils;

import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;

import static com.google.common.collect.Maps.fromProperties;

public class Bootstrap {
	private static final String PROPERTY_NAME_COLUMN = "PROPERTY";
	private static final String DEFAULT_VALUE_COLUMN = "DEFAULT";
	private static final String CURRENT_VALUE_COLUMN = "RUNTIME";
	private static final String DESCRIPTION_COLUMN = "DESCRIPTION";

	private final Logger log = Logger.get(Bootstrap.class);
	private final Set<Module> modules;

	private Map<String, String> requiredConfigurationProperties;
	private Map<String, String> optionalConfigurationProperties;
	private boolean initializeLogging = true;
	private boolean quiet;
	private boolean strictConfig;
	private boolean requireExplicitBindings = true;

	private boolean initialized;

	public Bootstrap(Iterable<? extends Module> modules) {
		this.modules = ImmutableSet.copyOf(modules);
	}

	public Injector initialize() throws Exception {
		Preconditions.checkState(!initialized, "Already initialized");
		initialized = true;
		Logging logging = null;
		if (this.initializeLogging) {
			java.util.logging.Logger rootLogger = java.util.logging.Logger.getLogger("");
			Handler[] handlers = rootLogger.getHandlers();
			logging = Logging.initialize();
			for (Handler handler : handlers) {
				if (handler instanceof ConsoleHandler) {
					continue;
				}
				rootLogger.addHandler(handler);
			}
		}
		Thread.currentThread()
				.setUncaughtExceptionHandler((t, e) -> log.error(e, "Uncaught exception in thread %s", t.getName()));

		Map<String, String> requiredProperties;
		ConfigurationFactory configurationFactory;
		if (requiredConfigurationProperties == null) {
			// initialize configuration
			log.info("Loading configuration");
			ConfigurationLoader loader = new ConfigurationLoader();

			requiredProperties = Collections.emptyMap();
			String configFile = System.getProperty("config");
			if (configFile != null) {
				requiredProperties = loader.loadPropertiesFrom(configFile);
			}
		} else {
			requiredProperties = requiredConfigurationProperties;
		}
		SortedMap<String, String> properties = Maps.newTreeMap();
		if (optionalConfigurationProperties != null) {
			properties.putAll(optionalConfigurationProperties);
		}
		properties.putAll(requiredProperties);
		properties.putAll(fromProperties(System.getProperties()));
		properties = ImmutableSortedMap.copyOf(properties);

		configurationFactory = new ConfigurationFactory(properties);
		if (logging != null) {
			log.info("Initializing logging");
			LoggingConfiguration messages1 = configurationFactory.build(LoggingConfiguration.class);
			logging.configure(messages1);
		}

		verifyUniqueModuleNames();
		final WarningsMonitor warningsMonitor = message -> log.warn(message);

		ImmutableSet.Builder<Module> installedModulesBuilder = ImmutableSet.builder();
		for (Module module : modules) {
			ConditionalModule annotation = module.getClass().getAnnotation(ConditionalModule.class);
			if (annotation != null) {
				String value = configurationFactory.getProperties().get(annotation.config());
				String annValue = annotation.value();
				configurationFactory.consumeProperty(annotation.config());
				if (!((StringUtils.isEmpty(annValue) && StringUtils.isNotEmpty(value))
						|| StringUtils.equals(annValue, value))) {
					continue;
				}
			}
			if (module instanceof ConfigurationAwareModule) {
				ConfigurationAwareModule configurationAwareModule = (ConfigurationAwareModule) module;
				configurationAwareModule.setConfigurationFactory(configurationFactory);
			}
			installedModulesBuilder.add(module);
		}
		Set<Module> installedModules = installedModulesBuilder.build();

		// Validate configuration
		List<Message> messages;
		try {
			messages = SystemRegistry.validate(configurationFactory, installedModules, warningsMonitor);
		} catch (Exception e) {
			log.error(e, "validate error.");
			throw Throwables.propagate(e);
		}

		// at this point all config file properties should be used
		// so we can calculate the unused properties
		final TreeMap<String, String> unusedProperties = Maps.newTreeMap();
		unusedProperties.putAll(requiredProperties);
		unusedProperties.keySet().removeAll(configurationFactory.getUsedProperties());

		// Log effective configuration
		if (!quiet) {
			logConfiguration(configurationFactory, unusedProperties);
		}

		// system modules
		ImmutableList.Builder<Module> moduleList = ImmutableList.builder();
		moduleList.add(new LifeCycleModule());
		moduleList.add(new ConfigurationModule(configurationFactory));
		if (!messages.isEmpty()) {
			moduleList.add(new ValidationErrorModule(messages));
		}
		moduleList.add(binder -> {
			binder.bind(WarningsMonitor.class).toInstance(warningsMonitor);
		});

		moduleList.add(binder -> {
			// binder.disableCircularProxies();
			if (requireExplicitBindings) {
				binder.requireExplicitBindings();
			}
		});

		// todo this should be part of the ValidationErrorModule
		if (strictConfig) {
			moduleList.add(binder -> {
				for (Map.Entry<String, String> unusedProperty : unusedProperties.entrySet()) {
					binder.addError("Configuration property '%s=%s' was not used", unusedProperty.getKey(),
							unusedProperty.getValue());
				}
			});
		}

		moduleList.add(binder -> {
			binder.bind(SystemRegistry.class).toInstance(new SystemRegistry(modules, installedModules));
		});

		moduleList.addAll(installedModules);

		// injector 생성.
		Injector injector;
		try {
			injector = Guice.createInjector(Stage.PRODUCTION, moduleList.build());
		} catch (Exception e) {
			throw Throwables.propagate(e);
		}

		// life-cycle manager 객체 생성
		LifeCycleManager lifeCycleManager = injector.getInstance(LifeCycleManager.class);
		// services 시작.
		if (lifeCycleManager.size() > 0) {
			lifeCycleManager.start();
		}
		return injector;
	}

	private void verifyUniqueModuleNames() {
		HashMap<String, Module> strings = new HashMap<>();

		for (Module module : modules) {
			if (module instanceof PluginModule) {
				String name = ((PluginModule) module).name();
				if (strings.containsKey(name)) {
					throw new IllegalStateException(String.format("동일한 모듈이 이미 있음. %s, %s 는 %s 이란 이름을 동일하게 사용하고 있다.",
							module.getClass().getName(), strings.get(name).getClass().getName(), name));
				}
			}
		}
	}

	private void logConfiguration(ConfigurationFactory configurationFactory, Map<String, String> unusedProperties) {
		ColumnPrinter columnPrinter = makePrinterForConfiguration(configurationFactory);

		try (PrintWriter out = new PrintWriter(new LoggingWriter(log, LoggingWriter.Type.INFO))) {
			columnPrinter.print(out);
		}

		// unused properties 경고 알림.
		if (!unusedProperties.isEmpty()) {
			log.warn("사용하지 않는 프로퍼티.");
			for (Map.Entry<String, String> unusedProperty : unusedProperties.entrySet()) {
				log.warn("%s=%s", unusedProperty.getKey(), unusedProperty.getValue());
			}
			log.warn("");
		}
	}

	//  설정값 저장(디폴트, 현재값)
	private ColumnPrinter makePrinterForConfiguration(ConfigurationFactory configurationFactory) {
		ConfigurationInspector configurationInspector = new ConfigurationInspector();
		ColumnPrinter columnPrinter = new ColumnPrinter();
		columnPrinter.addColumn(PROPERTY_NAME_COLUMN);
		columnPrinter.addColumn(DEFAULT_VALUE_COLUMN);
		columnPrinter.addColumn(CURRENT_VALUE_COLUMN);
		columnPrinter.addColumn(DESCRIPTION_COLUMN);

		for (ConfigurationInspector.ConfigRecord<?> record : configurationInspector.inspect(configurationFactory)) {
			for (ConfigurationInspector.ConfigAttribute attribute : record.getAttributes()) {
				//log.info("PROPERTY=" + attribute.getPropertyName() + ", RUNTIME=" + attribute.getCurrentValue());
				columnPrinter.addValue(PROPERTY_NAME_COLUMN, attribute.getPropertyName());
				columnPrinter.addValue(DEFAULT_VALUE_COLUMN, attribute.getDefaultValue());
				columnPrinter.addValue(CURRENT_VALUE_COLUMN, attribute.getCurrentValue());
				columnPrinter.addValue(DESCRIPTION_COLUMN, attribute.getDescription());
			}
		}
		return columnPrinter;
	}

	@Beta
	public Bootstrap setRequiredConfigurationProperty(String key, String value) {
		if (this.requiredConfigurationProperties == null) {
			this.requiredConfigurationProperties = new TreeMap<>();
		}
		this.requiredConfigurationProperties.put(key, value);
		return this;
	}

	@Beta
	public Bootstrap setRequiredConfigurationProperties(Map<String, String> requiredConfigurationProperties) {
		if (this.requiredConfigurationProperties == null) {
			this.requiredConfigurationProperties = new TreeMap<>();
		}
		this.requiredConfigurationProperties.putAll(requiredConfigurationProperties);
		return this;
	}

	@Beta
	public Bootstrap setOptionalConfigurationProperty(String key, String value) {
		if (this.optionalConfigurationProperties == null) {
			this.optionalConfigurationProperties = new TreeMap<>();
		}
		this.optionalConfigurationProperties.put(key, value);
		return this;
	}

	@Beta
	public Bootstrap setOptionalConfigurationProperties(Map<String, String> optionalConfigurationProperties) {
		if (this.optionalConfigurationProperties == null) {
			this.optionalConfigurationProperties = new TreeMap<>();
		}
		this.optionalConfigurationProperties.putAll(optionalConfigurationProperties);
		return this;
	}

	@Beta
	public Bootstrap doNotInitializeLogging() {
		this.initializeLogging = false;
		return this;
	}

	public Bootstrap quiet() {
		this.quiet = true;
		return this;
	}

	public Bootstrap strictConfig() {
		this.strictConfig = true;
		return this;
	}

	public Bootstrap requireExplicitBindings(boolean requireExplicitBindings) {
		this.requireExplicitBindings = requireExplicitBindings;
		return this;
	}
}
