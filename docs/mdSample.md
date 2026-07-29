### Request

**Query parameter**

| **key** | **설명** | **value 타입** | **예시** |
| --- | --- | --- | --- |
| gatheringId | 모임 ID | Long | 1 |

**Request Example**

```
GET /gathering/join-request/1
```

### Response

| **key** | **설명** | **value 타입** | **Nullable** | **예시** |
| --- | --- | --- | --- | --- |
| success | 성공 여부 | boolean | N | true |
| message | 응답 메시지 | String | N | "OK" |
| data[].gatheringUserId | 가입 신청 ID (수락/거절 시 사용) | Long | N | 5 |
| data[].nickName | 신청자 닉네임 | String | N | "홍길동" |
| data[].userProfileUrl | 신청자 프로필 사진 URL | String | Y | "http://localhost:8080/yeogi/files/profile.png" |

**Example**

```jsx
{
  "success": true,
  "message": "OK",
  "data": [
    {
      "gatheringUserId": 5,
      "nickName": "홍길동",
      "userProfileUrl": "http://localhost:8080/yeogi/files/profile.png"
    },
    {
      "gatheringUserId": 6,
      "nickName": "이서연",
      "userProfileUrl": "http://localhost:8080/yeogi/files/profile2.png"
    }
  ]
}
```

### Status

| status | response content |
| --- | --- |
| 200 | 조회 성공 |
| 401 | 인증 실패 |
| 403 | 권한 없음 (리더가 아님) |
| 404 | 모임을 찾을 수 없음 |