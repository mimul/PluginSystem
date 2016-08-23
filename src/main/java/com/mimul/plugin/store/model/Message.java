package com.mimul.plugin.store.model;

import lombok.Data;
import org.joda.time.DateTime;

import java.io.Serializable;

@Data
public class Message implements Serializable {
	private static final long serialVersionUID = -7596866205624900141L;

	private final Long id;

	private final String fromSubsId;

	private final String toSubsId;

	private final Long topicId;

	private final String payload;

	private String readYn;

	private String bookmarkYn;

	private DateTime readDate;

	private DateTime sendDate;

	public Message(Long id, String fromSubsId, String toSubsId, Long topicId, String payload, String readYn,
			String bookmarkYn, DateTime readDate, DateTime sendDate) {
		this.id = id;
		this.fromSubsId = fromSubsId;
		this.toSubsId = toSubsId;
		this.topicId = topicId;
		this.payload = payload;
		this.readYn = readYn;
		this.bookmarkYn = bookmarkYn;
		this.readDate = readDate;
		this.sendDate = sendDate;
	}
}
