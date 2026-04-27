package com.hongjie.pms.modules.search.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class PetPostUpdatedEvent extends ApplicationEvent {
    
    private final Long petId;
    private final String updateType;  // like, comment, view, content, audit, favorite
    
    public PetPostUpdatedEvent(Object source, Long petId, String updateType) {
        super(source);
        this.petId = petId;
        this.updateType = updateType;
    }
}