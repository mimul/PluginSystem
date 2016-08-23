package com.mimul.plugin.module;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.mimul.plugin.bootstrap.Bootstrap;
import com.mimul.plugin.store.model.Message;
import com.mimul.plugin.store.service.MessageService;
import io.airlift.log.Logger;

import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

import static java.lang.String.format;

public class Main {
	private static final Logger log = Logger.get(Main.class);

	public static Set<Module> getModules() {
		ImmutableSet.Builder<Module> builder = ImmutableSet.builder();
		ServiceLoader<PluginModule> modules = ServiceLoader.load(PluginModule.class);
		for (Module module : modules) {
			if (!(module instanceof PluginModule)) {
				throw new IllegalStateException(
						format("Module 은 PluginModule 의 하위 클래스여야 함. %s", module.getClass().getName()));
			}
			log.info("Module = " + module.getClass().getName());
			PluginModule pluginModule = (PluginModule) module;
			builder.add(pluginModule);
		}
		return builder.build();
	}

	public static void main(String[] args) throws Throwable {
		if (args.length > 0) {
			System.setProperty("config", args[0]);
		}
		Bootstrap app = new Bootstrap(getModules());
		app.requireExplicitBindings(false);
		Injector injector = app.strictConfig().initialize();
		List<Message> messages = injector.getInstance(MessageService.class).getChatMessages(
				new Message(null, null, "1", null, null, null, null, null, null));
		log.debug("messages=" + messages.toString());
	}
}
