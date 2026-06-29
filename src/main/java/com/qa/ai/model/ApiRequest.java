package com.qa.ai.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

/**
 * Represents the HTTP request portion of a Claude-generated API test case.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiRequest {

    private String method;   // GET, POST, PUT, PATCH, DELETE
    private String url;
    private Map<String, String> params;    // query params
    private Map<String, String> headers;
    private String body;                   // raw JSON body string

    public ApiRequest() {}

    public String getMethod()  { return method;  }
    public void setMethod(String method)  { this.method = method; }

    public String getUrl()     { return url;     }
    public void setUrl(String url)        { this.url = url; }

    public Map<String, String> getParams()   { return params;   }
    public void setParams(Map<String, String> params)   { this.params = params; }

    public Map<String, String> getHeaders()  { return headers;  }
    public void setHeaders(Map<String, String> headers) { this.headers = headers; }

    public String getBody()    { return body;    }
    public void setBody(String body)      { this.body = body; }
}
