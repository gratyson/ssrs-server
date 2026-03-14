package com.gt.ssrs.serialization;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import com.gt.ssrs.model.ReviewMode;
import org.springframework.stereotype.Component;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

@Component
public class ReviewModeSerializer extends ValueSerializer<ReviewMode> {
    @Override
    public void serialize(ReviewMode reviewMode, JsonGenerator jsonGenerator, SerializationContext ctxt) throws JacksonException {
        jsonGenerator.writeNumber(reviewMode.getReviewModeId());
    }
}
