package com.polymer.adapter.in.web;

import com.polymer.application.exception.BusinessConflictException;
import com.polymer.application.exception.ForbiddenOperationException;
import com.polymer.application.exception.OrderNotFoundException;
import com.polymer.application.exception.PersistenceUnavailableException;
import com.polymer.application.exception.ResourceNotFoundException;
import com.polymer.application.exception.RiskUnavailableException;
import com.polymer.domain.fulfillment.TransitionNotAllowedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
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

    @ExceptionHandler({MissingRequestHeaderException.class, MissingServletRequestParameterException.class})
    ProblemDetail missingInput(Exception error) {
        return problem(HttpStatus.BAD_REQUEST, "Solicitud inválida", error.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail resourceNotFound(ResourceNotFoundException error) {
        return problem(HttpStatus.NOT_FOUND, "No encontrado", error.getMessage());
    }

    @ExceptionHandler(BusinessConflictException.class)
    ProblemDetail conflict(BusinessConflictException error) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "Conflicto con el estado actual", error.getMessage());
        problem.setProperty("code", error.code());
        return problem;
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    ProblemDetail forbidden(ForbiddenOperationException error) {
        return problem(HttpStatus.FORBIDDEN, "Acceso denegado", error.getMessage());
    }

    @ExceptionHandler(TransitionNotAllowedException.class)
    ProblemDetail transition(TransitionNotAllowedException error) {
        return error.roleDenied()
                ? problem(HttpStatus.FORBIDDEN, "Transición no permitida para tu rol", error.getMessage())
                : problem(HttpStatus.CONFLICT, "Transición no válida", error.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
