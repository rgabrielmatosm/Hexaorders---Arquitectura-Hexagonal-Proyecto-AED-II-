package com.hexaorders.adapter.in.web;

import com.hexaorders.application.exception.OrderNotFoundException;
import com.hexaorders.application.exception.PersistenceUnavailableException;
import com.hexaorders.application.exception.RiskUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public final class ApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalidInput(IllegalArgumentException error) {
        return problem(HttpStatus.BAD_REQUEST, "Solicitud inválida", error.getMessage());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail malformedRequest(Exception ignored) {
        return problem(HttpStatus.BAD_REQUEST, "Solicitud inválida", "Revise el JSON, los tipos de datos y el UUID del pedido");
    }

    @ExceptionHandler(OrderNotFoundException.class)
    ProblemDetail notFound(OrderNotFoundException error) {
        return problem(HttpStatus.NOT_FOUND, "Pedido no encontrado", error.getMessage());
    }

    @ExceptionHandler(RiskUnavailableException.class)
    ProblemDetail riskUnavailable(RiskUnavailableException ignored) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Motor de riesgo no disponible", "No se creó ningún pedido; vuelva a intentar más tarde");
    }

    @ExceptionHandler(PersistenceUnavailableException.class)
    ProblemDetail databaseUnavailable(PersistenceUnavailableException ignored) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Persistencia no disponible", "No se pudo completar la operación");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
