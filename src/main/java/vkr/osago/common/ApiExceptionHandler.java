package vkr.osago.common;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public Map<String, Object> maxUpload(MaxUploadSizeExceededException ex) {
        return Map.of(
                "timestamp", Instant.now().toString(),
                "status", 413,
                "error", "Payload Too Large",
                "message", "Файл слишком большой. Максимальный размер одного файла — 25MB."
        );
    }

    @ExceptionHandler(MultipartException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public Map<String, Object> multipart(MultipartException ex) {
        return Map.of(
                "timestamp", Instant.now().toString(),
                "status", 413,
                "error", "Payload Too Large",
                "message", "Превышен лимит загрузки. Уменьшите размер файла или загружайте файлы по одному."
        );
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> any(Exception ex) {
        return Map.of(
                "timestamp", Instant.now().toString(),
                "status", 500,
                "error", "Internal Server Error",
                "exception", ex.getClass().getName(),
                "message", ex.getMessage() == null ? "(no message)" : ex.getMessage()
        );
    }


    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> badRequest(IllegalArgumentException ex) {
        return Map.of(
                "timestamp", Instant.now().toString(),
                "status", 400,
                "error", "Bad Request",
                "message", ex.getMessage()
        );
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> conflict(IllegalStateException ex) {
        return Map.of(
                "timestamp", Instant.now().toString(),
                "status", 409,
                "error", "Conflict",
                "message", ex.getMessage()
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> notFound(NoResourceFoundException ex) {
        return Map.of(
                "timestamp", Instant.now().toString(),
                "status", 404,
                "error", "Not Found",
                "message", ex.getMessage()
        );
    }
}
