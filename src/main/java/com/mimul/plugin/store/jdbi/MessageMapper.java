package com.mimul.plugin.store.jdbi;

import com.mimul.plugin.store.model.Message;
import org.joda.time.DateTime;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * Created by mimul on 2016. 8. 17..
 */
public class MessageMapper implements ResultSetMapper<Message> {
	@Override
	public Message map(int i, ResultSet r, StatementContext ctx) throws SQLException {
		DateTime date = null;
		Timestamp timestamp = r.getTimestamp("read_date");
		if (timestamp != null) {
			date = new DateTime(timestamp.getTime());
		}
		return new Message(r.getLong("id"), r.getString("from_subs_id"), r.getString("to_subs_id"),
				r.getLong("mq_topic_id"), r.getString("payload"), r.getString("read_yn"), r.getString("bookmark_yn"),
				date, new DateTime(r.getTimestamp("send_date").getTime()));
	}
}
