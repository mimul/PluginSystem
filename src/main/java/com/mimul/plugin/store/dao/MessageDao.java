package com.mimul.plugin.store.dao;

import com.mimul.plugin.store.jdbi.MessageMapper;
import com.mimul.plugin.store.model.Message;
import io.airlift.log.Logger;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.sqlobject.mixins.GetHandle;

import java.util.List;

/**
 * Created by mimul on 2016. 8. 17..
 */
public abstract class MessageDao implements GetHandle {
	private final Logger log = Logger.get(MessageDao.class);

	public List<Message> getChatMessages(Message message) {
		try (Handle handle = getHandle()) {
			return handle
					.createQuery(
							"SELECT id, from_subs_id, to_subs_id, mq_topic_id, payload, read_yn, bookmark_yn, read_date, send_date\n"
									+ "FROM mq_chat\n" + "WHERE to_subs_id = :toSubsId LIMIT 5")
					.bind("toSubsId", message.getToSubsId()).map(new MessageMapper()).list();
		} catch (Exception e) {
			log.error(e, "getChatMessages caught exception");
			return null;
		}
	}
}
