package com.hongjie.pms.platform.cache.serialization;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.jsontype.*;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

/** Keeps persisted type IDs stable across package moves, including nested DTOs. */
public final class CompatibleRedisSerializer implements RedisSerializer<Object> {
    private final ObjectMapper mapper;
    private static final Map<String,String> CURRENT=loadAliases();
    private static final Map<String,String> WIRE=invert(CURRENT);
    private static final Pattern PROJECT_TYPE=Pattern.compile("com\\.hongjie\\.pms\\.[A-Za-z0-9_.$]+");

    public CompatibleRedisSerializer(ObjectMapper mapper, JsonTypeInfo.As inclusion) {
        this.mapper=mapper;
        var typing=new ObjectMapper.DefaultTypeResolverBuilder(ObjectMapper.DefaultTyping.NON_FINAL,mapper.getPolymorphicTypeValidator()) {
            @Override protected TypeIdResolver idResolver(MapperConfig<?> config, JavaType baseType,
                    PolymorphicTypeValidator validator, Collection<NamedType> subtypes, boolean serialize, boolean deserialize) {
                var delegate=super.idResolver(config,baseType,validator,subtypes,serialize,deserialize);
                return new TypeIdResolver() {
                    public void init(JavaType type){delegate.init(type);}
                    public String idFromValue(Object value){return translate(delegate.idFromValue(value),WIRE);}
                    public String idFromValueAndType(Object value,Class<?> type){return translate(delegate.idFromValueAndType(value,type),WIRE);}
                    public String idFromBaseType(){return translate(delegate.idFromBaseType(),WIRE);}
                    public JavaType typeFromId(DatabindContext context,String id)throws IOException{return delegate.typeFromId(context,translate(id,CURRENT));}
                    public String getDescForKnownTypeIds(){return delegate.getDescForKnownTypeIds();}
                    public JsonTypeInfo.Id getMechanism(){return delegate.getMechanism();}
                };
            }
        };
        typing.init(JsonTypeInfo.Id.CLASS,null);typing.inclusion(inclusion);mapper.setDefaultTyping(typing);
    }
    @Override public byte[] serialize(Object value)throws SerializationException {
        if(value==null)return new byte[0];
        try{return mapper.writeValueAsBytes(value);}catch(IOException e){throw new SerializationException("Cannot serialize cache value",e);}
    }
    @Override public Object deserialize(byte[] bytes)throws SerializationException {
        if(bytes==null||bytes.length==0)return null;
        try{return mapper.readValue(bytes,Object.class);}catch(IOException e){throw new SerializationException("Cannot deserialize cache value",e);}
    }
    private static String translate(String id,Map<String,String> aliases){
        if(id==null)return null;
        return PROJECT_TYPE.matcher(id).replaceAll(match->{
            String name=match.group(),prefix=name;
            while(true){
                String replacement=aliases.get(prefix);
                if(replacement!=null)return java.util.regex.Matcher.quoteReplacement(replacement+name.substring(prefix.length()));
                int nested=prefix.lastIndexOf('$');if(nested<0)return java.util.regex.Matcher.quoteReplacement(name);
                prefix=prefix.substring(0,nested);
            }
        });
    }
    private static Map<String,String> loadAliases(){
        var values=new Properties();
        try(var input=CompatibleRedisSerializer.class.getResourceAsStream("/compatibility/java-type-aliases.properties")){
            if(input==null)throw new IllegalStateException("Missing persisted cache type aliases");values.load(input);
        }catch(IOException e){throw new IllegalStateException("Cannot load persisted cache type aliases",e);}
        Map<String,String> result=new HashMap<>();values.forEach((old,current)->result.put((String)old,(String)current));return Map.copyOf(result);
    }
    private static Map<String,String> invert(Map<String,String> aliases){
        Map<String,String> result=new HashMap<>();aliases.forEach((old,current)->{
            if(result.put(current,old)!=null)throw new IllegalStateException("Ambiguous persisted cache type alias: "+current);
        });return Map.copyOf(result);
    }
}
