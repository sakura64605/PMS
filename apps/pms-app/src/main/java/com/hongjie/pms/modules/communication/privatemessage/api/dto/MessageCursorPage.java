package com.hongjie.pms.modules.communication.privatemessage.api.dto;

import java.util.List;

/** History is newest first; nextCursor is the last record ID, exclusive on the next request. */
public record MessageCursorPage(List<MessageDto> records, Long nextCursor, boolean hasMore) {}
