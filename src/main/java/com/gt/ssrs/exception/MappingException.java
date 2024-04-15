package com.gt.ssrs.exception;

public class MappingException extends RuntimeException {

    public MappingException(String errMsg)  {
        super(errMsg);
    }

    public MappingException(String errMsg, Exception ex) {
        super(errMsg, ex);
    }
}
