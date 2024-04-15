package com.gt.ssrs.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.gt.ssrs.model.ReviewMode;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ReviewModeSerializer extends JsonSerializer<ReviewMode> {
    @Override
    public void serialize(ReviewMode reviewMode, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        jsonGenerator.writeNumber(reviewMode.getReviewModeId());
    }
}
