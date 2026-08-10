package com.clicker.mousehub.common;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NO_CONTENT;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void clientDisconnectDoesNotAttemptToSerializeAnApiError() {
        var response = handler.io(new IOException("Broken pipe"));

        assertThat(response.getStatusCode()).isEqualTo(NO_CONTENT);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void unrelatedIoFailureRemainsAnInternalError() {
        var response = handler.io(new IOException("disk read failed"));

        assertThat(response.getStatusCode()).isEqualTo(INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNull();
    }
}
