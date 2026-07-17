package io.github.apitestkit.generated.contract;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ApiResponseVO<T> {
    private Integer code;
    private String msg;
    private T data;

    @JsonProperty("error_message")
    private String errorMessage;

    @JsonProperty("error_code")
    private String errorCode;

    public Integer getCode() { return code; }
    public void setCode(Integer code) { this.code = code; }
    public String getMsg() { return msg; }
    public void setMsg(String msg) { this.msg = msg; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
}
