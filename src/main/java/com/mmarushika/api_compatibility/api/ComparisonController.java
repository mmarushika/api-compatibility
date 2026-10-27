package com.mmarushika.api_compatibility.api;

import com.mmarushika.api_compatibility.exception.InvalidOpenApiException;
import com.mmarushika.api_compatibility.model.DetectedChange;
import com.mmarushika.api_compatibility.service.ComparisonService;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ComparisonController {

    private final ComparisonService comparisonService;

    public ComparisonController(ComparisonService comparisonService) {
        this.comparisonService = comparisonService;
    }

    @PostMapping("/compare")
    public List<DetectedChange> compare(@RequestBody CompareRequest request) {
        validate(request);
        return comparisonService.compare(request.oldDocument(), request.newDocument());
    }

    @PostMapping("/compare/report")
    public ResponseEntity<ComparisonReport> report(
            @RequestBody CompareRequest request,
            @RequestParam(defaultValue = "false") boolean failOnBreaking) {

        validate(request);
        return respond(
                comparisonService.compare(request.oldDocument(), request.newDocument()),
                failOnBreaking);
    }

    @PostMapping(value = "/compare/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ComparisonReport> compareFiles(
            @RequestPart("old") MultipartFile oldDocument,
            @RequestPart("new") MultipartFile newDocument,
            @RequestParam(defaultValue = "false") boolean failOnBreaking) {

        return respond(
                comparisonService.compare(read(oldDocument), read(newDocument)),
                failOnBreaking);
    }

    @ExceptionHandler(InvalidOpenApiException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse invalidDocument(InvalidOpenApiException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleUnexpectedError(Exception exception) {
        return new ErrorResponse("An unexpected error occurred during comparison: " + exception.getMessage());
    }

    private static void validate(CompareRequest request) {
        if (request == null) {
            throw new InvalidOpenApiException("Request body cannot be null.");
        }
        if (request.oldDocument() == null || request.oldDocument().trim().isEmpty()) {
            throw new InvalidOpenApiException("Baseline specification (oldDocument) cannot be empty.");
        }
        if (request.newDocument() == null || request.newDocument().trim().isEmpty()) {
            throw new InvalidOpenApiException("Candidate specification (newDocument) cannot be empty.");
        }
    }

    private static ResponseEntity<ComparisonReport> respond(
            List<DetectedChange> changes, boolean failOnBreaking) {

        ComparisonReport report = ComparisonReport.of(changes);

        return failOnBreaking && report.breaking()
                ? ResponseEntity.status(HttpStatus.CONFLICT).body(report)
                : ResponseEntity.ok(report);
    }

    private static String read(MultipartFile document) {
        if (document == null || document.isEmpty()) {
            throw new InvalidOpenApiException(
                    "Uploaded file " + (document == null ? "document" : document.getOriginalFilename()) + " is empty.");
        }
        try {
            return new String(document.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new InvalidOpenApiException(
                    "Could not read " + document.getOriginalFilename() + ".");
        }
    }

    public record ErrorResponse(String message) {
    }
}

