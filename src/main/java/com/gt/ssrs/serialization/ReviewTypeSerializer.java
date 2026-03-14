package com.gt.ssrs.serialization;

import com.gt.ssrs.model.ReviewType;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

@Component
public class ReviewTypeSerializer extends ValueSerializer<ReviewType> {
    @Override
    public void serialize(ReviewType reviewType, JsonGenerator jsonGenerator, SerializationContext ctxt) throws JacksonException {
        jsonGenerator.writeNumber(reviewType.getReviewTypeId());
    }
}
