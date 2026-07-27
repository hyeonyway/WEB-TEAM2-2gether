# WEB-TEAM2-2gether
소프티어 8기 백엔드 2조입니다.

# Dibidding

> 신뢰성 있는 포켓몬 카드 경매 플랫폼입니다.
 
---
 
## 📌 목차
- [팀원 소개](#팀원-소개)
- [협업 전략](#협업-전략)
- [그라운드룰](#그라운드룰)
- [기획 링크](#기획-링크)
- [공통 자료](#공통-자료)
- [문서 자료](#문서-자료)
---
 
## 팀원 소개
 
| 이름 | 역할 | GitHub | 담당 파트 |
| --- | --- | --- | --- |
| 김현문 | Backend | [@github](https://github.com) |  |
| 이은기 | Backend | [@github](https://github.com) |  |
| 임하민 | Backend | [@github](https://github.com) |  |
| 정세호 | Backend | [@github](https://github.com) |  |
 
---
 
## 협업 전략
 
### 브랜치 전략 : GitHub Flow
 
```
production
 └─ dev
     ├─ feature/이슈번호-description
     ├─ fix/이슈번호-description
     ├─ hotfix/이슈번호-description
     ├─ refactor/이슈번호-description
     └─ chore/이슈번호-description
```
 
- 작업 성격에 맞는 브랜치를 생성한 뒤 `dev` → `production` 순으로 병합
- 작업 브랜치에서 이슈 단위 작업 후 `dev`로 PR
- `dev`가 안정화되면 `production`으로 병합하여 배포
### 브랜치 네이밍 컨벤션
 
```
feature/{이슈번호}-{설명}
fix/{이슈번호}-{설명}
hotfix/{이슈번호}-{설명}
refactor/{이슈번호}-{설명}
chore/{이슈번호}-{설명}
```
 
- `feature`: 새로운 기능 개발
- `fix`: 개발 중 발견된 버그 수정
- `hotfix`: 운영 환경의 긴급한 문제 수정
- `refactor`: 기능 변경 없는 코드 구조 개선
- `chore`: 설정, 문서, 빌드 등 기타 작업
- 설명은 영문 kebab-case로 작성
- 예: `feature/1-login-page`, `fix/12-user-api`, `chore/10-branch-convention`
### 머지 전략
 
- 작업 브랜치에서 `dev`로 병합 전, **rebase로 커밋 히스토리 정리 후 병합**
- 순서: `dev` 최신 내용 rebase → 충돌 해결 → merge
```bash
  git checkout feature/1-login-page
  git fetch origin
  git rebase origin/dev
  # 충돌 해결 후
  git push --force-with-lease
  # PR 생성 후 dev에 병합
```
- `dev → production` 병합 시에도 동일하게 rebase 후 병합하여 히스토리를 깔끔하게 유지
### 분업 방식
- 담당 영역과 겹치는 부분 발생 시 협의 방법 명시
### 의존적인 작업 처리
- Mock 데이터 활용 여부 및 사용 규칙
- 선행 작업 지연 시 커뮤니케이션 방식 (예: 슬랙 채널에 즉시 공유)
 
## 그라운드룰
 
- [] 의견 공유를 원활하게 하기 위한 친목 활동 (미정) 증바람/생맥/
- [] 아침 10시30~11시 회의 - 데일리 스크럼
- [] 10-19에는 프로젝트에 열심히 집중하자. 10-7
- [] 개인적인 소통은 카톡, 업무는 슬랙
- [] 불만사항 있으면 바로바로 이야기하기
- [] 긍정적인 언어 사용하기
- [] 하루에 1번씩 다같이 소나무공원가서 사담하기
---
 
## 기획 링크
 
| 구분 | 링크 |
| --- | --- |
| 기획서 | (링크 삽입) |
| API 명세서 | (링크 삽입) |
 
---
 
## 공통 자료
 
### 커밋 템플릿
```
type: 커밋 메시지 제목
 
- 상세 내용 1
- 상세 내용 2
 
관련 이슈: #이슈번호
```
 
**type 종류**
- `feat` : 새로운 기능 추가
- `fix` : 버그 수정
- `docs` : 문서 수정
- `style` : 코드 포맷팅, 세미콜론 누락 등
- `refactor` : 코드 리팩토링
- `test` : 테스트 코드 추가
- `chore` : 빌드 설정, 패키지 매니저 수정 등
### 이슈 템플릿
```
## 개요
어떤 작업/문제인지 간단히 설명
 
## 작업 내용
- [ ] 할 일 1
- [ ] 할 일 2
 
## 참고 사항
관련 링크, 스크린샷 등
```

### PR 템플릿
```
## 개요
어떤 작업을 했는지 간단히 설명
 
## 작업 내용
- [ ] 작업 1
- [ ] 작업 2
 
## 관련 이슈
closes #이슈번호
 
## 스크린샷 (선택)
(UI 변경 시 첨부)
 
## 리뷰 요청 사항
- 중점적으로 봐줬으면 하는 부분
- 논의가 필요한 부분
 
## 체크리스트
- [ ] 로컬에서 정상 동작 확인
- [ ] 컨벤션(네이밍, 코드 스타일) 준수
- [ ] `dev` 브랜치 기준 rebase 완료
```

---
 
## 문서 자료
 
| 구분 | 링크 |
| --- | --- |
| 회의록 | (GitHub Wiki) |
| 이슈 트래커 | (GitHub Issues / Jira 링크) |
| 위키 | (GitHub Wiki)
