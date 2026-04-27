package com.hongjie.pms.modules.search.event;

import com.hongjie.pms.modules.petpost.entity.PetPost;
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