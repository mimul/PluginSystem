package com.mimul.plugin.bootstrap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.inject.Binding;
import com.google.inject.ConfigurationException;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.spi.DefaultElementVisitor;
import com.google.inject.spi.Element;
import com.google.inject.spi.Elements;
import com.google.inject.spi.Message;
import com.google.inject.spi.ProviderInstanceBinding;
import com.mimul.plugin.module.ConditionalModule;
import com.mimul.plugin.module.PluginModule;
import com.mimul.plugin.store.model.Condition;
import com.mimul.plugin.store.model.ConfigItem;
import com.mimul.plugin.store.model.ModuleDescriptor;
import io.airlift.configuration.ConfigurationAwareProvider;
import io.airlift.configuration.ConfigurationFactory;
import io.airlift.configuration.ConfigurationInspector;
import io.airlift.configuration.WarningsMonitor;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;

public class SystemRegistry {
	private final Set<Module> modules;
	private final Set<Module> installedModules;
	private List<ModuleDescriptor> moduleDescriptors;

	public SystemRegistry(Set<Module> modules, Set<Module> installedModules) {
		this.modules = modules;
		this.installedModules = installedModules;
	}

	public static List<Message> validate(ConfigurationFactory factory, Iterable<? extends Module> modules,
			WarningsMonitor monitor) {
		final List<Message> messages = Lists.newArrayList();

		for (final Element element : Elements.getElements(modules)) {
			element.acceptVisitor(new DefaultElementVisitor<Void>() {
				public <T> Void visit(Binding<T> binding) {
					// look for ConfigurationProviders...
					if (binding instanceof ProviderInstanceBinding) {
						ProviderInstanceBinding<?> providerInstanceBinding = (ProviderInstanceBinding<?>) binding;
						Provider<?> provider = providerInstanceBinding.getProviderInstance();
						if (provider instanceof ConfigurationAwareProvider) {
							ConfigurationAwareProvider<?> configurationProvider = (ConfigurationAwareProvider<?>) provider;
							// give the provider the configuration factory
							configurationProvider.setConfigurationFactory(factory);
							configurationProvider.setWarningsMonitor(monitor);
							try {
								// call the getter which will cause object creation
								configurationProvider.get();
							} catch (ConfigurationException e) {
								// if we got errors, add them to the errors list
								messages.addAll(e.getErrorMessages().stream()
										.map(message -> new Message(singletonList(binding.getSource()),
												message.getMessage(), message.getCause()))
										.collect(Collectors.toList()));
							}
						}
					}
					return null;
				}
			});
		}
		return messages;
	}

	// 설정 정보와 함께 플러그인 모듈 로딩
	private List<ModuleDescriptor> createModuleDescriptor() {
		return modules.stream().filter(module -> module instanceof PluginModule).map(module -> {
			PluginModule pluginModule = (PluginModule) module;
			ConditionalModule annotation = pluginModule.getClass().getAnnotation(ConditionalModule.class);
			Optional<Condition> condition;
			if (annotation != null) {
				condition = Optional.of(new Condition(annotation.config(), annotation.value()));
			} else {
				condition = Optional.empty();
			}
			ConfigurationFactory otherConfigurationFactory = new ConfigurationFactory(ImmutableMap.of());
			validate(otherConfigurationFactory, ImmutableList.of(module), warning -> {
			});
			ImmutableList.Builder<ConfigItem> attributesBuilder = ImmutableList.builder();
			ConfigurationInspector configurationInspector = new ConfigurationInspector();
			for (ConfigurationInspector.ConfigRecord<?> record : configurationInspector
					.inspect(otherConfigurationFactory)) {
				for (ConfigurationInspector.ConfigAttribute attribute : record.getAttributes()) {
					attributesBuilder.add(new ConfigItem(attribute.getPropertyName(), attribute.getDefaultValue(),
							attribute.getDescription()));
				}
			}
			return new ModuleDescriptor(pluginModule.name(), pluginModule.description(),
					pluginModule.getClass().getName(), installedModules.contains(module), condition,
					attributesBuilder.build());
		}).collect(Collectors.toList());
	}

	public List<ModuleDescriptor> getModules() {
		if (moduleDescriptors == null) {
			this.moduleDescriptors = createModuleDescriptor();
		}
		return moduleDescriptors;
	}
}
