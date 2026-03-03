# ms-file (S3 ACL Demo version)

## here's what we're building:

- Buckets: inbox (raw uploads) → processed (output)
- Actors & permissions:
    - client → presigned PUT-only to inbox
    - processor service → read inbox, write processed
    - reader service → read-only on processed

SeaweedFS implements S3-compatible IAM via its filer component. Access control works exactly like AWS IAM: you create users, attach policies with Allow/Deny statements, and issue access key + secret key pairs.

## Example policies

```json
{
  "identities": [
    {
      "name": "client-uploader",
      "credentials": [
        {
          "accessKey": "client-access-key",
          "secretKey": "client-secret-key"
        }
      ],
      "actions": [
        "s3:PutObject:inbox/*",
        "s3:AbortMultipartUpload:inbox/*",
        "s3:ListMultipartUploadParts:inbox/*",
        "s3:CreateMultipartUpload:inbox/*"
      ]
    },
    {
      "name": "processor-service",
      "credentials": [
        {
          "accessKey": "processor-access-key",
          "secretKey": "processor-secret-key"
        }
      ],
      "actions": [
        "s3:GetObject:inbox/*",
        "s3:ListBucket:inbox",
        "s3:PutObject:processed/*",
        "s3:DeleteObject:processed/*"
      ]
    },
    {
      "name": "reader-service",
      "credentials": [
        {
          "accessKey": "reader-access-key",
          "secretKey": "reader-secret-key"
        }
      ],
      "actions": [
        "s3:GetObject:processed/*",
        "s3:ListBucket:processed"
      ]
    },
    {
      "name": "admin",
      "credentials": [
        {
          "accessKey": "admin-access-key",
          "secretKey": "admin-secret-key"
        }
      ],
      "actions": [
        "Admin"
      ]
    }
  ]
}
```

## Permission Matrix Summary
| Actor | inbox read | inbox write | processed read | processed write |
|-------|------------|-------------|----------------|-----------------|
| client-uploader | ❌         | ✅ (presigned only) | ❌             | ❌              |
| processor-service | ✅         | ❌          | ❌             | ✅              |
| reader-service    | ❌         | ❌          | ✅             | ❌              |
| admin             | ✅         | ✅          | ✅             | ✅              |

## Project Structure
```
.
├── docker-compose.yml              ← updated (adds demo-app service)
├── config/s3.json                  ← unchanged from original guide
├── scripts/init.sh                 ← unchanged from original guide
└── seaweedfs-s3-demo/
    ├── Dockerfile
    ├── pom.xml
    └── src/main/
        ├── resources/application.yml
        └── java/com/example/seaweedfs/
            ├── SeaweedfsS3DemoApplication.java
            ├── config/
            │   ├── SeaweedfsProperties.java  ← binds application.yml
            │   └── S3ClientConfig.java       ← one S3Client bean per identity
            ├── service/
            │   ├── ClientUploadService.java  ← presigned PUT URLs
            │   ├── ProcessorService.java     ← reads inbox, writes processed
            │   └── ReaderService.java        ← reads processed only
            └── controller/
                └── DemoController.java       ← exposes everything as REST
```

## Key Design Decisions

- One `S3Client` bean per identity — the AWS SDK signs every request using the credentials embedded in the client at construction time. There's no "per-request identity switch", so separate beans is the idiomatic pattern. Think of each bean as a pre-loaded staff badge.
- forcePathStyle(true) — SeaweedFS doesn't support virtual-hosted style URLs (inbox.seaweedfs-s3:8333), only path-style (seaweedfs-s3:8333/inbox). This is a common gotcha.
- S3Presigner for the client identity — presigning is a separate object from S3Client in AWS SDK v2. It only computes the URL locally — no network call until the end-user actually uploads.
- depends_on: service_completed_successfully — the app waits for seaweedfs-init to finish (buckets exist) before starting.


## About techstack

- java 21
- spring boot 4.0.2
- aws sdk v2

### note about company's libraries

```xml
<!-- Insert following snippet into your pom.xml -->

<project>
  <distributionManagement>
    <snapshotRepository>
      <id>artifact-registry</id>
      <url>artifactregistry://us-central1-maven.pkg.dev/cirrus-repo/cirrus-maven</url>
    </snapshotRepository>
    <repository>
      <id>artifact-registry</id>
      <url>artifactregistry://us-central1-maven.pkg.dev/cirrus-repo/cirrus-maven</url>
    </repository>
  </distributionManagement>

  <repositories>
    <repository>
      <id>artifact-registry</id>
      <url>artifactregistry://us-central1-maven.pkg.dev/cirrus-repo/cirrus-maven</url>
      <releases>
        <enabled>true</enabled>
      </releases>
      <snapshots>
        <enabled>true</enabled>
      </snapshots>
    </repository>
  </repositories>

  <build>
    <extensions>
      <extension>
        <groupId>com.google.cloud.artifactregistry</groupId>
        <artifactId>artifactregistry-maven-wagon</artifactId>
        <version>2.2.0</version>
      </extension>
    </extensions>
  </build>
</project>
```