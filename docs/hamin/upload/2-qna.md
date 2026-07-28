# Presigned URL 구현 관련 Q&A 요약

`1-presigned-upload-flow.md` 구현 과정에서 오간 질문과 답변을 정리한다.

## 1. 이 API가 백엔드에서 처리하는 게 맞는가?

실제 이미지 바이트는 프론트가 S3로 직접 PUT 업로드한다. 그럼에도 presigned URL 발급은 백엔드가 해야 한다:

- 프론트(브라우저)가 AWS 자격 증명을 직접 들고 있으면 번들에 키가 노출되어 버킷 전체가 위험해진다.
- 백엔드는 파일 바이트를 옮기지 않고, "누가/어디에/어떤 조건으로/몇 분 동안" 업로드할 수 있는지만 결정해서 서명한다.
- 반대로 프론트→백엔드→S3로 파일을 중계하면 대역폭이 두 배로 들고 백엔드가 멀티파트 업로드를 버퍼링해야 해서 비효율적이다.
- `auction` 도메인의 `ImageUploadPort.resolveImages(uploadTokens)`가 이미 "토큰 목록을 받아 처리"하는 구조로 설계돼 있어, presigned URL 발급 API가 있다는 걸 전제로 한다.

## 2. presigned URL은 언제 발급하는가?

페이지 진입 시점이 아니라 **사용자가 실제로 이미지 파일을 선택한 시점**에 발급한다.

- presigned URL은 유효기간이 짧아서(기본 300초), 페이지 진입과 동시에 미리 발급해두면 실제 업로드 시점엔 만료될 수 있다.
- `contentType`처럼 실제 파일의 메타데이터가 있어야 정확한 서명이 가능한데, 페이지 진입 시점엔 아직 어떤 파일을 고를지 모른다.
- 그래서 API는 파일 1개가 아니라 **배열을 받아 배열로 응답하는 배치형**으로 설계했다 (사용자가 한 번에 여러 장을 선택해도 API 호출은 1회, 최대 10개).

## 3. `upload_token`은 정확히 무엇인가?

두 가지 선택지가 있었다.

- **A안 (채택)**: `upload_token` = S3 object key 자체. 추가 저장소 없이 `ImageUploadPort`가 기대하는 문자열 토큰 계약과 자연스럽게 맞아떨어진다. `AuctionImage.imagePath`가 단순 String 컬럼이라 이 방식이 가장 단순하다. 단, 클라이언트가 임의 문자열을 조작해서 보낼 수 있다는 약점이 있는데, 이는 추후 실제 어댑터가 S3에 해당 key가 실제로 존재하는지(HeadObject) 검증하는 것으로 방어하기로 하고 이번 스코프에서는 제외했다.
- B안: 별도 opaque 토큰을 발급하고 "토큰 → 실제 key" 매핑을 Redis/DB에 TTL과 함께 저장. 더 안전하지만 이 프로젝트엔 아직 그런 저장소가 없어 기각.

## 4. AWS 자격 증명 / 버킷 구성

- 자격 증명은 `DefaultCredentialsProvider`를 사용하기로 함 — 코드/설정에 액세스 키를 하드코딩하지 않고, EC2 인스턴스 프로파일(IAM Role) 등 SDK 기본 체인을 통해 자동으로 자격 증명을 찾는다. 백엔드가 EC2에 배포되는 구조와 자연스럽게 맞는다.
- 버킷은 최종적으로 프론트엔드 CloudFront 배포에 이미 쓰이던 `2gether-cloudfront-bucket-869652444193-ap-northeast-2-an`을 그대로 재사용하기로 함 (별도 버킷 신설이 아니라 기존 버킷 안에서 `/frontend`, `/upload` prefix로 분리). S3 key(`upload_token`) prefix도 원래 디렉토리 이름 관례에 맞춰 `uploads/`가 아니라 **`upload/`**(단수형)로 정정했다.
- 이 버킷은 퍼블릭 액세스 전체 차단 상태이고, 버킷 정책은 CloudFront 서비스 프린시펄에게 `s3:GetObject`만 허용한다. EC2 Role은 이 정책과 별개로, **동일 계정이라 버킷 정책 수정 없이 IAM Role의 identity-based policy만으로 `upload/*`에 대한 `s3:PutObject`를 허용**할 수 있었다 (계정이 다를 때만 버킷 정책 쪽에도 별도 허용이 필요함).
- presigned PUT은 서명된 요청이라 Block Public Access, CORS 미설정 여부와 무관하게 동작하지만, 브라우저에서 직접 PUT하려면 별도로 버킷 CORS에 `PUT` 메서드와 프론트 origin(`https://dbidding.shop`, 로컬 개발 origin)을 허용해야 한다 — 이 프로젝트의 API 자체도 이미 `WebConfig`에서 `http://localhost:*`를 허용하고 있어, 로컬 개발 origin을 CORS에 추가하는 것도 그보다 좁은 범위라 별도 위험이 없다고 판단했다.

## 4-1. EC2 IAM Role/정책 실제 구성

백엔드가 배포되는 EC2 인스턴스가 presigned URL을 발급할 때 쓸 자격 증명 경로를 실제로 만들었다.

- **Role**: `2gether-ec2-s3-uploader` — 신뢰 주체(trust policy)는 `ec2.amazonaws.com` (EC2 서비스만 이 Role을 assume 가능).
- **연결 방식**: 이 Role을 인스턴스 프로파일로 만들어 백엔드 EC2 인스턴스에 직접 연결 (콘솔 → EC2 → 인스턴스 → 보안 → IAM 역할 수정).
- **첨부한 정책**: `dbidding-upload-put-policy` (최소 권한, `upload/*`로 한정):
  ```json
  {
    "Version": "2012-10-17",
    "Statement": [
      {
        "Effect": "Allow",
        "Action": ["s3:PutObject"],
        "Resource": "arn:aws:s3:::2gether-cloudfront-bucket-869652444193-ap-northeast-2-an/upload/*"
      }
    ]
  }
  ```
- `s3:ListBucket`, `s3:GetObject`, `s3:DeleteObject`는 의도적으로 부여하지 않았다 — presigned URL 발급 API는 PutObject만 필요하고, 나머지 권한을 열어주는 건 불필요하게 공격 표면을 넓히는 것이라 판단했다 (실제로 검증 중 `ListObjectsV2` 호출이 `AccessDenied`로 막힌 것도 이 설계가 의도대로 동작한 것).
- 버킷 정책(`PolicyForCloudFrontPrivateContent`)은 건드리지 않았다 — EC2와 버킷이 같은 AWS 계정이라, Role의 identity-based policy만으로 충분했다 ([4](#4-aws-자격-증명--버킷-구성) 참고).

## 5. 코드 관련 Q&A

- **`@Configuration`과 `@EnableConfigurationProperties`는 서로 의존하는 어노테이션인가?** 아니다. `@Configuration`은 `@Bean` 메서드를 정의하기 위한 것이고, `@EnableConfigurationProperties`는 `@ConfigurationProperties` 클래스(`S3UploadProperties`)를 빈으로 등록하기 위한 것으로 서로 독립적이다. 같은 클래스(`S3Config`)에 같이 붙인 건 편의상일 뿐, 어디에 붙여도 무방하다.
- **`S3UploadProperties`가 `S3Config`보다 먼저 생성되는 걸 어떻게 보장하는가?** 스프링 빈 생성 순서는 선언 순서가 아니라 의존성 그래프로 결정된다. `S3PresignedUrlProvider`가 생성자에서 `S3UploadProperties`를 요구하면, 스프링이 그 빈을 먼저 만들어서 주입한다.
- **`DefaultCredentialsProvider.create()`는 왜 안 쓰는가?** deprecated 되어 있다 (싱글턴 인스턴스를 공유해서 한쪽에서 close하면 다른 쪽에 영향을 줄 수 있음). `DefaultCredentialsProvider.builder().build()`로 독립 인스턴스를 생성하는 게 SDK 권장 방식이다.

## 6. 왜 로컬에서 바로 테스트하지 못했고, 어떻게 대체 검증했는가

**로컬 테스트가 막혔던 이유**

- 이 AWS 계정의 개인 IAM 사용자(`user/edu/haimin13`)는 자기 자신의 액세스 키를 스스로 발급할 권한(`iam:CreateAccessKey`)이 막혀 있었다 (부트캠프용 계정의 의도된 제약으로 추정). 그래서 로컬 머신에 `aws configure`로 넣을 자격 증명 자체를 만들 수 없었다.
- SSH 접속용 `2gether-backend-key.pem`은 EC2 리눅스 로그인용 키일 뿐 AWS API 자격 증명과는 무관해 대체할 수 없었다.
- Role을 로컬에서 `sts:AssumeRole`로 빌려 쓰는 방법도 검토했지만, trust policy가 EC2 서비스만 신뢰하도록 되어 있어 개인 사용자가 즉시 assume할 방법은 없었다 (관리자가 trust policy를 바꿔줘야 하는 별도 요청 사항으로 남김).

**대신 어떻게 검증했는가 — EC2 SSH + boto3로 실제 환경 재현**

로컬에 자격 증명을 만드는 대신, presigned URL을 실제로 발급/소비할 EC2 인스턴스에 직접 SSH로 들어가서, 그 인스턴스에 이미 붙어있는 IAM Role(`2gether-ec2-s3-uploader`)을 그대로 이용해 검증했다.

1. **Role이 인스턴스에 정상 연결됐는지 확인** — IMDSv2 토큰을 받아 메타데이터 엔드포인트(`/latest/meta-data/iam/security-credentials/`)를 조회해 Role 이름이 나오는지 확인.
2. **Role의 S3 쓰기 권한 확인** — `python3 -m venv`로 격리된 가상환경을 만들고 그 안에만 `boto3`를 설치해(시스템 파이썬은 PEP 668로 보호돼 있어 건드리지 않음), `s3.put_object(...)`로 `upload/` prefix에 실제로 쓸 수 있는지 확인. 성공(200 OK). `list_objects_v2`는 권한 범위 밖이라 `AccessDenied`가 났는데, 이는 정책이 `s3:PutObject`만 최소 권한으로 허용했기 때문이며 우리 기능엔 애초에 필요 없는 권한이라 문제 아님.
3. **실제 presigned PUT 흐름 재현** — `boto3`의 `generate_presigned_url("put_object", ...)`로, 우리 Java 코드(`S3Presigner.presignPutObject`)와 동일한 SigV4 presigning 메커니즘을 사용해 URL을 발급하고, 그 URL로 로컬 머신에서 자격 증명 없이 `curl -X PUT`을 실행해 200 OK를 받았다. presigned URL은 서명이 URL 안에 담겨 있어 발급은 자격 증명이 있는 곳(EC2)에서, 소비는 아무 데서나(로컬) 할 수 있다는 점을 이용한 것.
   - 중간에 `s3.amazonaws.com` 글로벌 엔드포인트로 서명해 307 리다이렉트가 난 적이 있었는데, `endpoint_url="https://s3.ap-northeast-2.amazonaws.com"`로 리전 엔드포인트를 명시해 해결했다. AWS SDK v2(`S3Presigner`)는 `Region.of(...)`를 명시하면 기본적으로 리전 엔드포인트를 사용하므로, 이건 boto3 테스트 스크립트에서만 발생한 이슈로 보고 있다.
4. 버킷 안 테스트 오브젝트는 이 Role에 삭제 권한이 없어 별도 콘솔 권한으로 삭제했다. 검증에 쓴 venv(`~/s3test-venv`)와 apt로 설치한 `python3-venv`/`python3-pip`는 디스크 용량이 크지 않고 이후에도 유용할 수 있어 **의도적으로 지우지 않고 EC2 인스턴스에 남겨뒀다**.

**결론**: 우리 Java 코드의 로직(개수·contentType 검증, key 생성 규칙)은 `UploadServiceTest`/`UploadControllerTest`로, AWS 쪽 권한·presigned URL 매커니즘은 위 EC2 재현 테스트로 각각 검증되어, 실제 배포 시 이 API가 정상 동작할 것으로 확인했다. 다만 Spring Boot 앱 자체를 그 환경에서 직접 기동해본 것은 아니라서, 완전한 end-to-end 검증(우리 컨트롤러가 실제로 이 흐름을 그대로 재현하는지)은 배포 이후 실제 호출로 한 번 더 확인이 필요하다.

## 참고

- 구현 흐름 전체: [`1-presigned-upload-flow.md`](./1-presigned-upload-flow.md)
- 관련 이슈: [#36](https://github.com/softeerbootcamp-8th/WEB-TEAM2-2gether/issues/36)

> 이 문서는 claude의 도움을 받아 작성하였습니다.