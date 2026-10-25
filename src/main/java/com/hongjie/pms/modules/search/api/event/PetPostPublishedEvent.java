package com.hongjie.pms.modules.search.api.event;

import com.hongjie.pms.modules.content.petpost.infrastructure.persistence.entity.PetPost;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class PetPostPublishedEvent extends ApplicationEvent {
    private final PetPost petPost;
    
    public PetPostPublishedEvent(Object source, PetPost petPost) {
        super(source);
        this.petPost = petPost;
    }
}