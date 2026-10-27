package com.hongjie.pms.modules.communication.privatemessage.application;

import com.hongjie.pms.modules.communication.privatemessage.api.dto.MessageDto;

public record PrivateMessageCommitted(Long recipient, MessageDto message) {}
