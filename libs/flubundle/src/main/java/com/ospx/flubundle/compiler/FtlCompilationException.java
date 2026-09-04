package com.ospx.flubundle.compiler;

public class FtlCompilationException extends RuntimeException {

    private final CompilationResult result;

    public FtlCompilationException(String message, CompilationResult result) {
        super(message);
        this.result = result;
    }

    public CompilationResult result() {
        return result;
    }
}
