package com.gt.ssrs.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.gt.ssrs.model.ReviewType;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ReviewTypeSerializer extends JsonSerializer<ReviewType> {
    @Override
    public void serialize(ReviewType reviewType, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        jsonGenerator.writeNumber(reviewType.getReviewTypeId());
    }
}
