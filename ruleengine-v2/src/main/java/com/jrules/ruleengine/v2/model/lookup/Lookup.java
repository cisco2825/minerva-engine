package com.jrules.ruleengine.v2.model.lookup;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", defaultImpl = InlineLookup.class)
@JsonSubTypes({
    @JsonSubTypes.Type(value = InlineLookup.class, name = "INLINE"),
    @JsonSubTypes.Type(value = FileLookup.class,   name = "FILE")
})
public abstract class Lookup {
    public abstract LookupType getType();
}
