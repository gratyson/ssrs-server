package com.gt.ssrs.exception;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// Thrown when a request is made for a resource that the user does not have access to
@ResponseStatus(value = HttpStatus.FORBIDDEN)
public class UserAccessException extends RuntimeException {

    public UserAccessException(String msg) {
        super(msg);
    }

    public UserAccessException(String msg, Exception ex) {
        super(msg, ex);
    }


}
