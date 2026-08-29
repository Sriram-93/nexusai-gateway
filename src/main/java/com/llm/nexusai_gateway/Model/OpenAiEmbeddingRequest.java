package com.llm.nexusai_gateway.Model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenAiEmbeddingRequest {

    private String input;
    private String model = "text-embedding-all-minilm-l6-v2";

    public OpenAiEmbeddingRequest() {}

    public OpenAiEmbeddingRequest(String input, String model) {
        this.input = input;
        this.model = model;
    }

    public String getInput() { return input; }
    public void setInput(String input) { this.input = input; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
}
