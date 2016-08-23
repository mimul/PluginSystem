package com.mimul.plugin.module;

import com.google.auto.service.AutoService;
import com.google.inject.Binder;
import com.google.inject.Scopes;
import com.google.inject.name.Names;
import com.mimul.plugin.config.JDBCConfig;
import com.mimul.plugin.store.service.MessageService;
import com.mimul.plugin.store.service.MessageServiceImpl;
import com.mimul.plugin.store.util.JDBCPoolDataSource;

@AutoService(PluginModule.class)
@ConditionalModule(config = "plugin.adapter.mysql", value = "true")
public class MysqlModule extends PluginModule {
	@Override
	protected void setup(Binder binder) {
		JDBCConfig config = buildConfigObject(JDBCConfig.class, "plugin.adapter.mysql");
		binder.bind(JDBCPoolDataSource.class).annotatedWith(Names.named("plugin.adapter.mysql"))
				.toInstance(JDBCPoolDataSource.getOrCreateDataSource(config));
		binder.bind(MessageService.class).to(MessageServiceImpl.class).in(Scopes.SINGLETON);
	}

	@Override
	public String name() {
		return "MySQL Plugin Module";
	}

	@Override
	public String description() {
		return "MySQL Plugi Module";
	}
}
