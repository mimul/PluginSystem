package com.mimul.plugin.store.service;

import com.google.inject.name.Named;
import com.mimul.plugin.store.dao.MessageDao;
import com.mimul.plugin.store.model.Message;
import com.mimul.plugin.store.util.JDBCPoolDataSource;
import org.skife.jdbi.v2.DBI;

import javax.inject.Inject;
import java.util.List;

public class MessageServiceImpl implements MessageService {
	private final MessageDao messageDao;

	@Inject
	public MessageServiceImpl(@Named("plugin.adapter.mysql") JDBCPoolDataSource dataSource) {
		DBI dbi = new DBI(dataSource);
		this.messageDao = dbi.onDemand(MessageDao.class);
	}

	@Override
	public List<Message> getChatMessages(Message message) {
		return messageDao.getChatMessages(message);
	}
}
