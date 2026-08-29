package com.llm.nexusai_gateway.Model;

import java.util.List;

public class OpenAiEmbeddingResponse {

    private String object = "list";
    private List<EmbeddingData> data;
    private String model;
    private OpenAiChatCompletionResponse.Usage usage;

    public static class EmbeddingData {
        private String object = "embedding";
        private int index = 0;
        private float[] embedding;

        public EmbeddingData() {}

        public EmbeddingData(int index, float[] embedding) {
            this.index = index;
            this.embedding = embedding;
        }

        public String getObject() { return object; }
        public int getIndex() { return index; }
        public float[] getEmbedding() { return embedding; }
    }

    public OpenAiEmbeddingResponse() {}

    public OpenAiEmbeddingResponse(List<EmbeddingData> data, String model, OpenAiChatCompletionResponse.Usage usage) {
        this.data = data;
        this.model = model;
        this.usage = usage;
    }

    public String getObject() { return object; }
    public List<EmbeddingData> getData() { return data; }
    public String getModel() { return model; }
    public OpenAiChatCompletionResponse.Usage getUsage() { return usage; }
}
