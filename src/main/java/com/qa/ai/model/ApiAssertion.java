package com.qa.ai.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents one assertion Claude generates for an API test case.
 *
 * Supported assertion types:
 *   status_code   — response HTTP status equals expected integer
 *   json_path     — JSON path expression resolves to expected value
 *   header        — response header (path = header name) equals expected
 *   body_contains — response body string contains expected substring
 *   not_empty     — JSON path result is not null / not empty
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiAssertion {

    private String type;
    private String path;        // JSON path or header name
    private String expected;    // expected value as string

    public ApiAssertion() {}

    public String getType()     { return type;     }
    public void setType(String type)          { this.type = type; }

    public String getPath()     { return path;     }
    public void setPath(String path)          { this.path = path; }

    public String getExpected() { return expected; }
    public void setExpected(String expected)  { this.expected = expected; }

    @Override
    public String toString() {
        return String.format("Assertion[type=%s, path=%s, expected=%s]", type, path, expected);
    }
}
