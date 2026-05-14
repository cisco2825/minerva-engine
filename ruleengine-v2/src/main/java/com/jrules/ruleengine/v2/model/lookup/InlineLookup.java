package com.jrules.ruleengine.v2.model.lookup;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
public class InlineLookup extends Lookup {

    private List<Object> values;

    @Override
    public LookupType getType() {
        return LookupType.INLINE;
    }
}
