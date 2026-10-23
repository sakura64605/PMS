package com.hongjie.pms.refactor.privateMessage;

import com.hongjie.pms.modules.privateMessage.dto.MessageDto;
public record PrivateMessageCommitted(Long recipient,MessageDto message) {}
