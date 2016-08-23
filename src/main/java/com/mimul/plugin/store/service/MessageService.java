package com.mimul.plugin.store.service;

import com.mimul.plugin.store.model.Message;

import java.util.List;

public interface MessageService {
  List<Message> getChatMessages(Message message);
}
