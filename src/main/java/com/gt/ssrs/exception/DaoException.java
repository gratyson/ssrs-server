package com.gt.ssrs.exception;

public class DaoException extends RuntimeException {

    public DaoException(String errMsg)  {
        super(errMsg);
    }

    public DaoException(String errMsg, Exception ex) {
        super(errMsg, ex);
    }
}
