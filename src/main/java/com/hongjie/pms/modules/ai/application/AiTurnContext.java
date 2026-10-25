package com.hongjie.pms.modules.ai.application;
/** Request-local correlation between a persisted assistant message and the model response. */
public final class AiTurnContext implements AutoCloseable {
    private static final ThreadLocal<AiTurnContext> CURRENT=new ThreadLocal<>();
    private String assistantId;
    private AiTurnContext(){}
    public static AiTurnContext open(){
        if(CURRENT.get()!=null)throw new IllegalStateException("Nested AI turn");
        var context=new AiTurnContext();CURRENT.set(context);return context;
    }
    public static void saved(String role,String id){
        var context=CURRENT.get();if(context!=null&&"assistant".equals(role))context.assistantId=id;
    }
    public String assistantId(){return assistantId;}
    @Override public void close(){CURRENT.remove();}
}
