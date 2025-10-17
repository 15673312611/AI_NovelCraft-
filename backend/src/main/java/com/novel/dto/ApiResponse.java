package com.novel.dto;

public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private String code;
    private long timestamp;

    public ApiResponse() {
        this.timestamp = System.currentTimeMillis();
    }

    public ApiResponse(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    public ApiResponse(boolean success, String message, T data, String code) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.code = code;
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> ApiResponse<T> success(T data) { return new ApiResponse<>(true, "操作成功", data, "200"); }
    public static <T> ApiResponse<T> success(String message, T data) { return new ApiResponse<>(true, message, data, "200"); }
    public static ApiResponse<Void> success() { return new ApiResponse<>(true, "操作成功", null, "200"); }
    public static ApiResponse<Void> success(String message) { return new ApiResponse<>(true, message, null, "200"); }
    public static <T> ApiResponse<T> error(String message) { return new ApiResponse<>(false, message, null, "400"); }
    public static <T> ApiResponse<T> error(String message, String code) { return new ApiResponse<>(false, message, null, code); }
    public static <T> ApiResponse<T> error(String message, T data) { return new ApiResponse<>(false, message, data, "400"); }
    public static <T> ApiResponse<T> error(String message, T data, String code) { return new ApiResponse<>(false, message, data, code); }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    @Override public String toString() {
        return "ApiResponse{" +
            "success=" + success +
            ", message='" + message + '\'' +
            ", data=" + data +
            ", code='" + code + '\'' +
            ", timestamp=" + timestamp +
            '}';
    }
}

