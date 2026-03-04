package com.example.seaweedfs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "seaweedfs")
public class SeaweedfsProperties {

    private Filer filer = new Filer();
    private Jwt   jwt   = new Jwt();

    public Filer getFiler() { return filer; }
    public void setFiler(Filer filer) { this.filer = filer; }

    public Jwt getJwt() { return jwt; }
    public void setJwt(Jwt jwt) { this.jwt = jwt; }

    public static class Filer {
        private String endpoint;
        private Buckets buckets = new Buckets();

        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

        public Buckets getBuckets() { return buckets; }
        public void setBuckets(Buckets buckets) { this.buckets = buckets; }

        public static class Buckets {
            private String inbox;
            private String processed;

            public String getInbox() { return inbox; }
            public void setInbox(String inbox) { this.inbox = inbox; }

            public String getProcessed() { return processed; }
            public void setProcessed(String processed) { this.processed = processed; }
        }
    }

    public static class Jwt {
        private String writeSecret;
        private String readSecret;
        private int    ttlSeconds = 30;

        public String getWriteSecret() { return writeSecret; }
        public void setWriteSecret(String writeSecret) { this.writeSecret = writeSecret; }

        public String getReadSecret() { return readSecret; }
        public void setReadSecret(String readSecret) { this.readSecret = readSecret; }

        public int getTtlSeconds() { return ttlSeconds; }
        public void setTtlSeconds(int ttlSeconds) { this.ttlSeconds = ttlSeconds; }
    }
}
